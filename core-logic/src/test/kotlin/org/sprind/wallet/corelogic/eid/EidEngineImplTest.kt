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
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRight
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.businesslogic.model.UserPinImpl
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EidEngineImplTest {

    private val port = FakeEidSdkPort()

    private fun TestScope.engine(): EidEngine =
        EidEngineImpl(port, backgroundScope, mock(LogController::class.java))

    /** Starts an authentication on a connected SDK and clears the commands that took. */
    private fun TestScope.runningAuth(
        engine: EidEngine,
        card: EidCardSource = EidCardSource.Physical,
    ): EidWorkflow<AuthOutcome> {
        val workflow = engine.authenticate(TC_TOKEN_URL, card)
        runCurrent()
        port.emit(SdkSignal.Started)
        runCurrent()
        port.commands.clear()
        return workflow
    }

    private fun TestScope.emit(signal: SdkSignal) {
        port.emit(signal)
        runCurrent()
    }

    @Test
    fun `an authentication waits for the SDK to start`() = runTest {
        engine().authenticate(TC_TOKEN_URL)
        runCurrent()
        assertEquals(listOf("CONNECT"), port.commands)

        emit(SdkSignal.Started)

        assertEquals(listOf("CONNECT", "RUN_AUTH"), port.commands)
    }

    @Test
    fun `a failed connection fails the queued run and drops the connection attempt`() = runTest {
        val workflow = engine().authenticate(TC_TOKEN_URL)
        runCurrent()

        emit(SdkSignal.Error(EidFailure.WRAPPER_ERROR))

        assertEquals(EidResult.Failure(EidFailure(EidFailure.WRAPPER_ERROR)), workflow.outcome.await())
        assertEquals(listOf("CONNECT", "DISCONNECT"), port.commands)
    }

    @Test
    fun `an SDK that never starts fails the queued run, and a late start is ignored`() = runTest {
        val engine = engine()
        val workflow = engine.authenticate(TC_TOKEN_URL)
        runCurrent()

        advanceTimeBy(15.seconds)
        runCurrent()
        assertEquals(EidResult.Failure(EidFailure(EidFailure.SDK_START_FAILED)), workflow.outcome.await())
        assertFalse(port.isConnected)

        emit(SdkSignal.Started)
        assertEquals(listOf("CONNECT", "DISCONNECT"), port.commands)

        engine.authenticate(TC_TOKEN_URL)
        runCurrent()
        emit(SdkSignal.Started)
        assertEquals(listOf("CONNECT", "DISCONNECT", "CONNECT", "RUN_AUTH"), port.commands)
    }

    @Test
    fun `a connection lost since the SDK started is started afresh`() = runTest {
        val engine = engine()
        val first = runningAuth(engine)
        emit(SdkSignal.AuthSucceeded(REDIRECT_URL))
        first.outcome.await()
        port.loseConnection()

        engine.authenticate(TC_TOKEN_URL)
        runCurrent()
        assertEquals(listOf("CONNECT"), port.commands)

        emit(SdkSignal.Started)
        assertEquals(listOf("CONNECT", "RUN_AUTH"), port.commands)
    }

    @Test
    fun `an authentication is accepted, answered with the PIN and succeeds`() = runTest {
        val workflow = runningAuth(engine())

        emit(SdkSignal.AccessRights(listOf(AccessRight.FAMILY_NAME), listOf(AccessRight.FAMILY_NAME), null))
        (workflow.request.value as EidRequest.AccessRights).accept()
        runCurrent()
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))
        val pin = workflow.request.value as EidRequest.Pin
        assertEquals(PreviousAttempt.NONE, pin.previousAttempt)
        pin.answer(userPin("123456"))
        runCurrent()
        emit(SdkSignal.AuthSucceeded(REDIRECT_URL))

        assertEquals(listOf("ACCEPT", "SET_PIN 123456"), port.commands)
        assertEquals(EidResult.Success(AuthOutcome(REDIRECT_URL)), workflow.outcome.await())
        assertNull(workflow.request.value)
    }

    @Test
    fun `a PIN asked again tells a wrong one from one the card never judged`() = runTest {
        val workflow = runningAuth(engine())

        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))
        (workflow.request.value as EidRequest.Pin).answer(userPin("111111"))
        runCurrent()
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 2)))
        val afterWrong = workflow.request.value as EidRequest.Pin
        afterWrong.answer(userPin("222222"))
        runCurrent()
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 2)))
        val afterDroppedRead = workflow.request.value as EidRequest.Pin

        assertEquals(PreviousAttempt.WRONG, afterWrong.previousAttempt)
        assertEquals(PreviousAttempt.NOT_JUDGED, afterDroppedRead.previousAttempt)
    }

    @Test
    fun `a request takes one answer and erases any later one`() = runTest {
        val workflow = runningAuth(engine())
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))
        val request = workflow.request.value as EidRequest.Pin
        val second = userPin("654321")

        request.answer(userPin("123456"))
        request.answer(second)
        runCurrent()

        assertEquals(listOf("SET_PIN 123456"), port.commands)
        assertThrows(IllegalStateException::class.java) { second.getAndClear() }
    }

    @Test
    fun `a request the SDK moved on from is not sent`() = runTest {
        val workflow = runningAuth(engine())
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))
        val stale = workflow.request.value as EidRequest.Pin
        emit(SdkSignal.InsertCard)
        val pin = userPin("123456")

        stale.answer(pin)
        runCurrent()

        assertTrue(port.commands.isEmpty())
        assertThrows(IllegalStateException::class.java) { pin.getAndClear() }
    }

    @Test
    fun `a second workflow starts only once the first has ended, which keeps its outcome`() = runTest {
        val engine = engine()
        val first = runningAuth(engine)

        val second = engine.authenticate(TC_TOKEN_URL)
        runCurrent()
        assertEquals(listOf("CANCEL"), port.commands)

        // The cancelled run's outcome comes late, as USER_CANCELLED does in WD-5070.
        emit(SdkSignal.AuthFailed("User_Cancelled"))

        assertEquals(EidResult.Cancelled, first.outcome.await())
        assertEquals(listOf("CANCEL", "RUN_AUTH"), port.commands)
        assertFalse(second.outcome.isCompleted)
    }

    @Test
    fun `an authentication completed after its cancel is cancelled`() = runTest {
        val workflow = runningAuth(engine())
        workflow.cancel()
        runCurrent()

        emit(SdkSignal.AuthSucceeded(REDIRECT_URL))

        assertEquals(EidResult.Cancelled, workflow.outcome.await())
    }

    @Test
    fun `a PIN change completed after its cancel is a success`() = runTest {
        val engine = engine()
        val workflow = engine.changePin()
        runCurrent()
        emit(SdkSignal.Started)
        workflow.cancel()
        runCurrent()

        emit(SdkSignal.ChangePinSucceeded)

        assertEquals(EidResult.Success(PinChanged), workflow.outcome.await())
    }

    @Test
    fun `an end the SDK does not report is found through its status`() = runTest {
        val workflow = runningAuth(engine())
        workflow.cancel()
        val cancel = launch { workflow.join() }
        runCurrent()

        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(listOf("CANCEL", "GET_STATUS"), port.commands)
        assertFalse(cancel.isCompleted)

        emit(SdkSignal.Status(isWorkflowRunning = false, progress = null))

        assertTrue(cancel.isCompleted)
        assertEquals(EidResult.Cancelled, workflow.outcome.await())
    }

    @Test
    fun `a refused cancel means nothing ran`() = runTest {
        val workflow = runningAuth(engine())
        workflow.cancel()
        val cancel = launch { workflow.join() }
        runCurrent()

        emit(SdkSignal.BadState("CANCEL"))

        assertTrue(cancel.isCompleted)
    }

    @Test
    fun `a refused answer is asked for again, and fails the run once no workflow runs`() = runTest {
        val workflow = runningAuth(engine())
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))
        (workflow.request.value as EidRequest.Pin).answer(userPin("123456"))
        runCurrent()

        emit(SdkSignal.BadState("SET_PIN"))
        assertTrue(workflow.request.value is EidRequest.Pin)
        assertEquals("GET_STATUS", port.commands.last())

        emit(SdkSignal.Status(isWorkflowRunning = false, progress = null))

        assertEquals(
            EidResult.Failure(EidFailure(EidFailure.BAD_STATE_PREFIX + "SET_PIN")),
            workflow.outcome.await(),
        )
    }

    @Test
    fun `a deactivated card fails the run at once and ends its workflow`() = runTest {
        val workflow = runningAuth(engine())

        emit(SdkSignal.Reader(attached = true, card = card(pinRetryCounter = 3, isDeactivated = true)))

        assertEquals(
            EidResult.Failure(EidFailure(EidFailure.CARD_DEACTIVATED)),
            workflow.outcome.await(),
        )
        assertEquals(listOf("CANCEL"), port.commands)
    }

    @Test
    fun `the simulated card is presented on request and answers its own PIN`() = runTest {
        val workflow = runningAuth(engine(), EidCardSource.Simulated(PSEUDONYM_KEY))

        emit(SdkSignal.InsertCard)
        val insertCard = workflow.request.value as EidRequest.InsertCard
        assertTrue(insertCard.acceptsSimulatedCard)
        insertCard.presentSimulatedCard()
        runCurrent()
        emit(SdkSignal.EnterPin(isInputRejected = false, card = card(pinRetryCounter = 3)))

        assertEquals(listOf("SET_CARD $PSEUDONYM_KEY", "SET_PIN none"), port.commands)
        assertNull(workflow.request.value)
    }

    @Test
    fun `a pause is shown on the reader and the workflow continued`() = runTest {
        val workflow = runningAuth(engine())

        emit(SdkSignal.Paused("BadCardPosition"))

        assertEquals(ReaderState.Paused("BadCardPosition"), workflow.reader.value)
        assertEquals(listOf("CONTINUE"), port.commands)
    }

    @Test
    fun `an idle SDK is disconnected after a while`() = runTest {
        val workflow = runningAuth(engine())
        emit(SdkSignal.AuthSucceeded(REDIRECT_URL))
        workflow.outcome.await()

        advanceTimeBy(9.seconds)
        runCurrent()
        assertTrue(port.isConnected)
        advanceTimeBy(1.seconds)
        runCurrent()

        assertFalse(port.isConnected)
    }

    private fun card(pinRetryCounter: Int, isDeactivated: Boolean = false) =
        CardInfo(isDeactivated = isDeactivated, isPukInoperative = false, pinRetryCounter = pinRetryCounter)

    private fun userPin(digits: String): UserPin = UserPinImpl(digits)
}

private const val TC_TOKEN_URL = "https://pid.example/tcToken"
private const val REDIRECT_URL = "https://pid.example/refresh?code=1"
private const val PSEUDONYM_KEY = "pseudonym-key"
