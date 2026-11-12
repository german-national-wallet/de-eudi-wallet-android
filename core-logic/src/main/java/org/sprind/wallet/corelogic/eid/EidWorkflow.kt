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

import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRight
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.config.isVirtual
import org.sprind.wallet.businesslogic.model.UserPin
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One run of an eID workflow: what it waits for from the user, what the card is doing, and the one
 * outcome it ends with.
 *
 * A handle belongs to its run alone. What the SDK reports for an earlier or a later run never
 * reaches it, so its [outcome] is always the outcome of the workflow it was started for.
 */
interface EidWorkflow<out R> {

    /** What the workflow waits for from the user right now, or null while it waits for nothing. */
    val request: StateFlow<EidRequest?>

    val reader: StateFlow<ReaderState>

    /** Completes exactly once, when the run has an outcome. */
    val outcome: Deferred<EidResult<R>>

    /** Asks the SDK to end this run. Does nothing for a run that has ended already. */
    fun cancel()

    /** Returns once the SDK has reported the end of this run, so that another workflow can follow. */
    suspend fun join()
}

/** Where the card a workflow reads comes from. */
sealed interface EidCardSource {

    /** A card on the NFC reader. */
    data object Physical : EidCardSource

    /** The simulated card, which takes no PIN and no new PIN: the engine answers those itself. */
    data class Simulated(val pseudonymKey: String) : EidCardSource
}

/** The card the user has chosen to be read. */
fun UserRuntimeConfig.eidCardSource(): EidCardSource = simulatedEidCardPseudonymKey
    ?.takeIf { eidCardType.isVirtual }
    ?.let(EidCardSource::Simulated)
    ?: EidCardSource.Physical

/**
 * Something the SDK waits for. Each request takes one answer: a later one is ignored, and a code
 * handed to it is erased unsent. A request the SDK has moved on from takes none at all.
 */
sealed class EidRequest {

    private val isAnswered = AtomicBoolean(false)

    protected fun claim(): Boolean = isAnswered.compareAndSet(false, true)

    protected fun answerOnce(code: UserPin, send: (UserPin) -> Unit) {
        if (claim()) send(code) else code.discard()
    }

    /** The SDK waits for a card. A physical one answers by being put on the reader. */
    class InsertCard(
        private val presentSimulated: (() -> Unit)?,
    ) : EidRequest() {

        val acceptsSimulatedCard: Boolean get() = presentSimulated != null

        /** Puts the simulated card on the reader; only for a workflow started with one. */
        fun presentSimulatedCard() {
            val present = checkNotNull(presentSimulated) { "This workflow reads a physical card" }
            if (claim()) present()
        }
    }

    /** The provider asks to read [effective]; accepting reads the optional rights along with [required]. */
    class AccessRights(
        val required: List<AccessRight>,
        val effective: List<AccessRight>,
        val transactionInfo: String?,
        private val send: () -> Unit,
    ) : EidRequest() {

        fun accept() {
            if (claim()) send()
        }
    }

    class Pin(
        val retryCounter: Int,
        val previousAttempt: PreviousAttempt,
        private val send: (UserPin) -> Unit,
    ) : EidRequest() {

        fun answer(pin: UserPin) = answerOnce(pin, send)
    }

    /** [isRetry] is the card or the SDK having refused the CAN sent last. */
    class Can(
        val isRetry: Boolean,
        val pinRetryCounter: Int?,
        private val send: (UserPin) -> Unit,
    ) : EidRequest() {

        fun answer(can: UserPin) = answerOnce(can, send)
    }

    /** [isRetry] is the SDK having refused the new PIN sent last. */
    class NewPin(
        val isRetry: Boolean,
        private val send: (UserPin) -> Unit,
    ) : EidRequest() {

        fun answer(newPin: UserPin) = answerOnce(newPin, send)
    }

    /** The PIN is blocked, and the PUK can unblock it. */
    class Puk(
        private val send: (UserPin) -> Unit,
    ) : EidRequest() {

        fun answer(puk: UserPin) = answerOnce(puk, send)
    }
}

/** What became of the PIN sent before the one a [EidRequest.Pin] asks for. */
enum class PreviousAttempt {

    /** No PIN was sent in this run yet. */
    NONE,

    /** The card judged it wrong, which cost an attempt. */
    WRONG,

    /**
     * The card never judged it: the retry counter did not change, so the read dropped before the
     * PIN got there. Sending it again costs nothing.
     */
    NOT_JUDGED,

    /** The SDK refused it as malformed, which costs no attempt. */
    MALFORMED,
}

sealed interface ReaderState {

    data object NoCard : ReaderState

    data object CardPresent : ReaderState

    data class Reading(val progress: Int) : ReaderState

    /** Contact with the card was lost mid-read; the run goes on once it is back. */
    data class Paused(val cause: String) : ReaderState
}

sealed interface EidResult<out R> {

    data class Success<R>(val value: R) : EidResult<R>

    data object Cancelled : EidResult<Nothing>

    data class Failure(val failure: EidFailure) : EidResult<Nothing>
}

/** The provider takes the issuance on at [redirectUrl]. */
data class AuthOutcome(val redirectUrl: String) {

    // Fetching the redirect yields the authorization code, so it stays out of the logs.
    override fun toString() = "AuthOutcome(redirectUrl=<redacted>)"
}

data object PinChanged

/**
 * Why a run failed. [code] is the SDK's failure code where it gave one
 * (https://www.ausweisapp.bund.de/sdk/failurecodes.html), otherwise one of the codes below.
 */
data class EidFailure(val code: String) {

    companion object {
        const val AUTHENTICATION_FAILED = "EID_AUTHENTICATION_FAILED"
        const val AUTHENTICATION_START_FAILED = "EID_AUTHENTICATION_START_FAILED"
        const val REDIRECT_MISSING = "EID_REDIRECT_MISSING"
        const val ACCESS_RIGHTS_MISSING = "EID_ACCESS_RIGHTS_MISSING"
        const val CHANGE_PIN_FAILED = "EID_CHANGE_PIN_FAILED"
        const val CARD_UNREADABLE = "EID_CARD_UNREADABLE"
        const val CARD_DEACTIVATED = "EID_CARD_DEACTIVATED"
        const val PUK_INOPERATIVE = "EID_PUK_INOPERATIVE"
        const val INTERNAL_ERROR = "EID_INTERNAL_ERROR"
        const val WRAPPER_ERROR = "EID_WRAPPER_ERROR"
        const val SDK_START_FAILED = "EID_SDK_START_FAILED"
        const val BAD_STATE_PREFIX = "EID_BAD_STATE_"
    }
}
