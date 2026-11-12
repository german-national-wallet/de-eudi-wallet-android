/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.corelogic.eid

import android.nfc.Tag
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sprind.wallet.businesslogic.model.UserPin
import kotlin.time.Duration.Companion.seconds

/**
 * Runs eID workflows on the SDK, one at a time.
 *
 * Starting a workflow while another runs ends that one first, and only once the SDK has reported
 * its end is the new one started: the SDK takes one workflow at a time and refuses a second.
 */
interface EidEngine {

    fun authenticate(
        tcTokenUrl: String,
        card: EidCardSource = EidCardSource.Physical,
    ): EidWorkflow<AuthOutcome>

    fun changePin(card: EidCardSource = EidCardSource.Physical): EidWorkflow<PinChanged>

    fun onNfcTag(tag: Tag)
}

/**
 * [EidEngine] as an actor: commands and SDK signals are queued into one coroutine, which alone
 * holds the state of the SDK's protocol, so that each is dealt with whole and in order.
 */
class EidEngineImpl(
    private val port: EidSdkPort,
    private val scope: CoroutineScope,
    private val logController: LogController,
) : EidEngine {

    private val logTag = javaClass.simpleName

    private val inputs = Channel<Input>(Channel.UNLIMITED)

    // Touched by the actor coroutine alone.
    private var isReady = false
    private var current: Run<*>? = null
    private var queued: Run<*>? = null
    private var endCheck: Job? = null
    private var startCheck: Job? = null
    private var idleDisconnect: Job? = null

    init {
        scope.launch { port.signals.collect { inputs.send(Input.Signal(it)) } }
        scope.launch { for (input in inputs) handle(input) }
    }

    override fun authenticate(tcTokenUrl: String, card: EidCardSource): EidWorkflow<AuthOutcome> =
        Run<AuthOutcome>(WorkflowKind.AUTHENTICATION, card, tcTokenUrl).also {
            inputs.trySend(Input.Start(it))
        }

    override fun changePin(card: EidCardSource): EidWorkflow<PinChanged> =
        Run<PinChanged>(WorkflowKind.CHANGE_PIN, card, tcTokenUrl = null).also {
            inputs.trySend(Input.Start(it))
        }

    override fun onNfcTag(tag: Tag) = port.onNfcTag(tag)

    private fun handle(input: Input) {
        when (input) {
            is Input.Start -> start(input.run)
            is Input.Cancel -> cancel(input.run)
            is Input.Answer -> deliver(input)
            is Input.EndCheckDue -> if (current === input.run && input.run.phase == Phase.ENDING) {
                checkEnd()
            }

            Input.StartCheckDue -> if (!isReady) failQueued(EidFailure.SDK_START_FAILED)
            Input.IdleCheckDue -> disconnectIfIdle()
            is Input.Signal -> onSignal(input.signal)
        }
    }

    private fun start(run: Run<*>) {
        idleDisconnect?.cancel()
        // Only the latest start waits on the running workflow: one that never ran is just dropped.
        queued?.let { finish(it, EidResult.Cancelled) }
        queued = run
        val running = current
        when {
            running != null -> if (running.phase == Phase.RUNNING) end(running)
            // A connection lost since the SDK started, as when its service died, is started afresh:
            // commands would otherwise be dropped, and the workflow never run.
            !isReady || !port.isConnected -> {
                isReady = false
                if (!port.isConnected) port.connect()
                scheduleStartCheck()
            }

            else -> startQueued()
        }
    }

    // Should the SDK never report that it started, nothing else would start the queued workflow.
    private fun scheduleStartCheck() {
        startCheck?.cancel()
        startCheck = scope.launch {
            delay(START_CHECK_DELAY)
            inputs.send(Input.StartCheckDue)
        }
    }

    /**
     * Fails the queued run, if any, which the SDK could not be started for, and drops the
     * connection attempt so that the next workflow starts the SDK afresh. The attempt is dropped
     * even with no run queued: one still binding was passed over by the idle disconnect.
     */
    private fun failQueued(code: String) {
        startCheck?.cancel()
        queued?.let { run ->
            queued = null
            logController.e(logTag) { "Workflow failed before it started: $code" }
            finish(run, failure(code))
        }
        port.disconnect()
    }

    private fun startQueued() {
        val run = queued ?: return
        if (!isReady || current != null) return
        queued = null
        current = run
        run.phase = Phase.RUNNING
        when (run.kind) {
            WorkflowKind.AUTHENTICATION -> port.runAuth(checkNotNull(run.tcTokenUrl))
            WorkflowKind.CHANGE_PIN -> port.runChangePin()
        }
    }

    private fun cancel(run: Run<*>) {
        when (run.phase) {
            Phase.QUEUED -> {
                if (queued === run) queued = null
                finish(run, EidResult.Cancelled)
                if (current == null) scheduleIdleDisconnect()
            }

            // What the SDK reports for its end decides the outcome: a PIN change can still land.
            Phase.RUNNING -> end(run)
            Phase.ENDING, Phase.ENDED -> Unit
        }
    }

    /** Fails [run] at once, and ends its SDK workflow, which nothing can come of any more. */
    private fun fail(run: Run<*>, code: String) {
        logController.e(logTag) { "Workflow failed: $code" }
        run.complete(EidResult.Failure(EidFailure(code)))
        if (run.phase == Phase.RUNNING) end(run)
    }

    /**
     * Asks the SDK to end [run]. Should it never report the end, its status is asked for after a
     * while, and no workflow running then counts as the end.
     */
    private fun end(run: Run<*>) {
        run.phase = Phase.ENDING
        run.requestState.value = null
        port.cancel()
        endCheck?.cancel()
        endCheck = scope.launch {
            delay(UNREPORTED_END_CHECK_DELAY)
            inputs.send(Input.EndCheckDue(run))
        }
    }

    private fun checkEnd() {
        if (port.isConnected) port.requestStatus() else onSdkEnded(EidResult.Cancelled)
    }

    /** The SDK workflow of the current run has ended, with [result] unless the run has one already. */
    private fun onSdkEnded(result: EidResult<*>) {
        val run = current ?: return
        endCheck?.cancel()
        current = null
        finish(run, result)
        if (queued != null) startQueued() else scheduleIdleDisconnect()
    }

    private fun finish(run: Run<*>, result: EidResult<*>) {
        run.complete(result)
        run.phase = Phase.ENDED
        run.requestState.value = null
        run.ended.complete(Unit)
    }

    // The connection outlives a workflow for a while, so that one following another, as the
    // issuance follows a PIN change, does not wait on the SDK to start again.
    private fun scheduleIdleDisconnect() {
        idleDisconnect?.cancel()
        idleDisconnect = scope.launch {
            delay(IDLE_DISCONNECT_DELAY)
            inputs.send(Input.IdleCheckDue)
        }
    }

    private fun disconnectIfIdle() {
        if (current != null || queued != null) return
        isReady = false
        if (port.isConnected) port.disconnect()
    }

    private fun onSignal(signal: SdkSignal) {
        if (signal == SdkSignal.Started) {
            // A start reported after the connection was dropped is not one commands can go to.
            if (!port.isConnected) {
                logController.d(logTag) { "Ignored, the SDK is not connected: $signal" }
                return
            }
            startCheck?.cancel()
            isReady = true
            startQueued()
            return
        }
        val run = current
        if (run == null) {
            // An error while the SDK starts, such as a failed connection, is the end of that start.
            if (signal is SdkSignal.Error && !isReady && queued != null) {
                failQueued(signal.code)
            } else {
                logController.d(logTag) { "Ignored, no workflow of ours runs: $signal" }
            }
            return
        }
        when (signal) {
            is SdkSignal.Status -> onStatus(run, signal)
            is SdkSignal.BadState -> onBadState(run, signal.command)
            is SdkSignal.AuthSucceeded,
            is SdkSignal.AuthFailed,
            SdkSignal.AuthStartFailed,
            SdkSignal.ChangePinSucceeded,
            is SdkSignal.ChangePinFailed,
                -> onCompleted(run, signal)

            else -> if (run.phase == Phase.RUNNING) {
                onRunning(run, signal)
            } else {
                logController.d(logTag) { "Ignored, the workflow is ending: $signal" }
            }
        }
    }

    private fun onCompleted(run: Run<*>, signal: SdkSignal) {
        val isAuth = run.kind == WorkflowKind.AUTHENTICATION
        val result: EidResult<*>? = when (signal) {
            is SdkSignal.AuthSucceeded ->
                if (isAuth) EidResult.Success(AuthOutcome(signal.redirectUrl)) else null

            is SdkSignal.AuthFailed -> if (isAuth) failure(signal.code) else null
            SdkSignal.AuthStartFailed ->
                if (isAuth) failure(EidFailure.AUTHENTICATION_START_FAILED) else null

            SdkSignal.ChangePinSucceeded -> if (!isAuth) EidResult.Success(PinChanged) else null
            is SdkSignal.ChangePinFailed -> if (!isAuth) failure(signal.code) else null
            else -> error("Not a completion: $signal")
        }
        when {
            // Whatever ends a workflow being ended is its end. Only a new PIN is no cancellation:
            // the card has it, whatever was meant to follow the cancel.
            run.phase == Phase.ENDING -> onSdkEnded(
                if (signal == SdkSignal.ChangePinSucceeded && result != null) result else EidResult.Cancelled
            )

            result == null -> logController.w(logTag) { "Ignored, not this workflow's end: $signal" }
            else -> onSdkEnded(result)
        }
    }

    private fun onStatus(run: Run<*>, status: SdkSignal.Status) {
        if (status.isWorkflowRunning) {
            // While a refusal is checked, a status at 0% is the answer to that check, not a read.
            val isCheckAnswer = run.refusedCommand != null && status.progress == 0
            if (run.phase == Phase.RUNNING && status.progress != null && !isCheckAnswer) {
                run.readerState.value = ReaderState.Reading(status.progress)
            }
            return
        }
        // No workflow runs: the one waited on has ended, or the one a refused command was for is gone.
        val refused = run.refusedCommand
        when {
            run.phase == Phase.ENDING -> onSdkEnded(EidResult.Cancelled)
            refused != null -> onSdkEnded(failure(EidFailure.BAD_STATE_PREFIX + refused))
            // Expected between a run command and the SDK starting the workflow; logged so that a
            // workflow that died without a completion can be told from that in a bug report.
            else -> logController.w(logTag) { "No workflow runs in the SDK, while ours does" }
        }
    }

    private fun onBadState(run: Run<*>, command: String) {
        when (command) {
            // Nothing ran to cancel, so the workflow waited on has ended.
            CANCEL_COMMAND -> if (run.phase == Phase.ENDING) onSdkEnded(EidResult.Cancelled)

            // A workflow not ours runs in the SDK. Ending it is what lets the next one start.
            RUN_AUTH_COMMAND, RUN_CHANGE_PIN_COMMAND ->
                fail(run, EidFailure.BAD_STATE_PREFIX + command)

            // An answer refused never reached the card, so the request it was for still stands.
            // Whether the workflow itself is still there is asked of the SDK.
            else -> {
                if (run.phase != Phase.RUNNING) return
                if (command == run.sentCommand && run.request.value == null) {
                    run.sentCode = null
                    run.reofferLast?.invoke()
                }
                run.refusedCommand = command
                if (port.isConnected) {
                    port.requestStatus()
                } else {
                    onSdkEnded(failure(EidFailure.BAD_STATE_PREFIX + command))
                }
            }
        }
    }

    private fun onRunning(run: Run<*>, signal: SdkSignal) {
        if (signal.isRequest()) run.refusedCommand = null
        when (signal) {
            SdkSignal.InsertCard -> {
                val simulated = run.card as? EidCardSource.Simulated
                offer(run) { submit ->
                    EidRequest.InsertCard(
                        presentSimulated = simulated?.let { card ->
                            {
                                submit(null, SET_CARD_COMMAND) {
                                    port.insertSimulatedCard(card.pseudonymKey)
                                }
                            }
                        },
                    )
                }
            }

            is SdkSignal.AccessRights -> offer(run) { submit ->
                EidRequest.AccessRights(signal.required, signal.effective, signal.transactionInfo) {
                    submit(null, ACCEPT_COMMAND) { port.accept() }
                }
            }

            SdkSignal.AccessRightsMissing -> fail(run, EidFailure.ACCESS_RIGHTS_MISSING)

            is SdkSignal.EnterPin -> onPinRequested(run, signal)

            is SdkSignal.EnterCan -> onCanRequested(run, signal)

            is SdkSignal.EnterNewPin -> onNewPinRequested(run, signal)

            is SdkSignal.EnterPuk -> onPukRequested(run, signal)

            is SdkSignal.Reader -> {
                val card = signal.card
                when {
                    card?.isDeactivated == true -> fail(run, EidFailure.CARD_DEACTIVATED)
                    card != null -> {
                        run.readerState.value = ReaderState.CardPresent
                        // The card answered the request for it.
                        if (run.request.value is EidRequest.InsertCard) run.requestState.value = null
                    }

                    signal.attached -> run.readerState.value = ReaderState.NoCard
                }
            }

            // The card lost contact mid-read: the SDK is told to carry on once it is back.
            is SdkSignal.Paused -> {
                run.readerState.value = ReaderState.Paused(signal.cause)
                port.continueWorkflow()
            }

            is SdkSignal.Error -> fail(run, signal.code)

            else -> logController.d(logTag) { "Ignored: $signal" }
        }
    }

    private fun onCanRequested(run: Run<*>, signal: SdkSignal.EnterCan) {
        val followsCan = run.takeSentCode() == SentCode.CAN
        if (signal.card?.isDeactivated == true) {
            fail(run, EidFailure.CARD_DEACTIVATED)
            return
        }
        offer(run) { submit ->
            EidRequest.Can(
                isRetry = signal.isInputRejected || followsCan,
                pinRetryCounter = signal.card?.pinRetryCounter,
            ) { can ->
                submit(can, SET_CAN_COMMAND) {
                    port.setCan(can)
                    run.sentCode = SentCode.CAN
                }
            }
        }
    }

    private fun onNewPinRequested(run: Run<*>, signal: SdkSignal.EnterNewPin) {
        val followsNewPin = run.takeSentCode() == SentCode.NEW_PIN
        if (run.card is EidCardSource.Simulated && !followsNewPin) {
            // The simulated card takes no new PIN: the request is answered with none.
            run.sentCode = SentCode.NEW_PIN
            run.sentCommand = SET_NEW_PIN_COMMAND
            port.setNewPin(null)
            return
        }
        offer(run) { submit ->
            EidRequest.NewPin(isRetry = signal.isInputRejected || followsNewPin) { newPin ->
                submit(newPin, SET_NEW_PIN_COMMAND) {
                    port.setNewPin(newPin)
                    run.sentCode = SentCode.NEW_PIN
                }
            }
        }
    }

    private fun onPukRequested(run: Run<*>, signal: SdkSignal.EnterPuk) {
        run.takeSentCode()
        val card = signal.card
        when {
            card == null -> fail(run, EidFailure.CARD_UNREADABLE)
            card.isDeactivated -> fail(run, EidFailure.CARD_DEACTIVATED)
            // A used-up PUK can no longer unblock the PIN.
            card.isPukInoperative -> fail(run, EidFailure.PUK_INOPERATIVE)
            else -> offer(run) { submit ->
                EidRequest.Puk { puk ->
                    submit(puk, SET_PUK_COMMAND) {
                        port.setPuk(puk)
                        run.sentCode = SentCode.PUK
                    }
                }
            }
        }
    }

    private fun onPinRequested(run: Run<*>, signal: SdkSignal.EnterPin) {
        val followsPin = run.takeSentCode() == SentCode.PIN
        val card = signal.card
        when {
            card == null -> fail(run, EidFailure.CARD_UNREADABLE)
            card.isDeactivated -> fail(run, EidFailure.CARD_DEACTIVATED)

            // The simulated card takes no PIN, and each request is the one moment the SDK accepts
            // being told so; no real PIN can so ever reach the simulator.
            run.card is EidCardSource.Simulated -> {
                run.sentCode = SentCode.PIN
                run.sentCommand = SET_PIN_COMMAND
                port.setPin(null)
            }

            else -> {
                val retryCounter = card.pinRetryCounter ?: 0
                val previousAttempt = when {
                    signal.isInputRejected -> PreviousAttempt.MALFORMED
                    !followsPin -> PreviousAttempt.NONE
                    // A wrong PIN costs an attempt, so an unchanged counter means it was never judged.
                    run.pinRetryCounterAnswered == retryCounter -> PreviousAttempt.NOT_JUDGED
                    else -> PreviousAttempt.WRONG
                }
                offer(run) { submit ->
                    EidRequest.Pin(retryCounter, previousAttempt) { pin ->
                        submit(pin, SET_PIN_COMMAND) {
                            port.setPin(pin)
                            run.sentCode = SentCode.PIN
                            run.pinRetryCounterAnswered = retryCounter
                        }
                    }
                }
            }
        }
    }

    /**
     * Makes the request [build] builds the one [run] waits on. Its answer is queued to the actor,
     * which sends it only if the request is still the one waited on.
     */
    private fun offer(run: Run<*>, build: (Submit) -> EidRequest) {
        lateinit var request: EidRequest
        request = build { code, command, send ->
            inputs.trySend(Input.Answer(run, request, code, command, send))
        }
        run.requestState.value = request
        run.reofferLast = { offer(run, build) }
    }

    private fun deliver(answer: Input.Answer) {
        val run = answer.run
        if (current !== run || run.phase != Phase.RUNNING || run.request.value !== answer.request) {
            logController.d(logTag) { "${answer.command} dropped: the SDK no longer waits on its request" }
            answer.code?.discard()
            return
        }
        run.requestState.value = null
        run.sentCommand = answer.command
        answer.send()
    }

    private fun failure(code: String) = EidResult.Failure(EidFailure(code))

    private enum class WorkflowKind { AUTHENTICATION, CHANGE_PIN }

    private enum class Phase { QUEUED, RUNNING, ENDING, ENDED }

    private enum class SentCode { PIN, CAN, NEW_PIN, PUK }

    private sealed interface Input {
        class Start(val run: Run<*>) : Input
        class Cancel(val run: Run<*>) : Input
        class Answer(
            val run: Run<*>,
            val request: EidRequest,
            val code: UserPin?,
            val command: String,
            val send: () -> Unit,
        ) : Input

        class EndCheckDue(val run: Run<*>) : Input
        data object StartCheckDue : Input
        data object IdleCheckDue : Input
        class Signal(val signal: SdkSignal) : Input
    }

    private inner class Run<R>(
        val kind: WorkflowKind,
        val card: EidCardSource,
        val tcTokenUrl: String?,
    ) : EidWorkflow<R> {

        val requestState = MutableStateFlow<EidRequest?>(null)
        override val request: StateFlow<EidRequest?> = requestState.asStateFlow()

        val readerState = MutableStateFlow<ReaderState>(ReaderState.NoCard)
        override val reader: StateFlow<ReaderState> = readerState.asStateFlow()

        private val result = CompletableDeferred<EidResult<R>>()
        override val outcome: Deferred<EidResult<R>> get() = result

        val ended = CompletableDeferred<Unit>()

        var phase = Phase.QUEUED

        /** The code last sent, until the next request tells whether the card took it. */
        var sentCode: SentCode? = null
        var pinRetryCounterAnswered: Int? = null
        var sentCommand: String? = null
        var refusedCommand: String? = null
        var reofferLast: (() -> Unit)? = null

        fun takeSentCode(): SentCode? = sentCode.also { sentCode = null }

        /** Settles the outcome, unless it is settled already. */
        fun complete(result: EidResult<*>) {
            // Failure and Cancelled hold no value, and a Success is only built for its own kind.
            @Suppress("UNCHECKED_CAST")
            this.result.complete(result as EidResult<R>)
        }

        override fun cancel() {
            inputs.trySend(Input.Cancel(this))
        }

        override suspend fun join() = ended.await()
    }
}

private typealias Submit = (code: UserPin?, command: String, send: () -> Unit) -> Unit

/** Whether the SDK sends this only while a workflow runs and waits on something. */
private fun SdkSignal.isRequest(): Boolean = when (this) {
    SdkSignal.InsertCard,
    is SdkSignal.AccessRights,
    is SdkSignal.EnterPin,
    is SdkSignal.EnterCan,
    is SdkSignal.EnterNewPin,
    is SdkSignal.EnterPuk,
        -> true

    else -> false
}

private const val CANCEL_COMMAND = "CANCEL"
private const val RUN_AUTH_COMMAND = "RUN_AUTH"
private const val RUN_CHANGE_PIN_COMMAND = "RUN_CHANGE_PIN"
private const val ACCEPT_COMMAND = "ACCEPT"
private const val SET_PIN_COMMAND = "SET_PIN"
private const val SET_CAN_COMMAND = "SET_CAN"
private const val SET_NEW_PIN_COMMAND = "SET_NEW_PIN"
private const val SET_PUK_COMMAND = "SET_PUK"
private const val SET_CARD_COMMAND = "SET_CARD"
private val START_CHECK_DELAY = 15.seconds
private val UNREPORTED_END_CHECK_DELAY = 3.seconds
private val IDLE_DISCONNECT_DELAY = 10.seconds
