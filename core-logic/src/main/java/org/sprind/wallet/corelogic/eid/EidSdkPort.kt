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
import kotlinx.coroutines.flow.Flow
import org.sprind.wallet.businesslogic.model.UserPin

/**
 * The eID SDK, as commands and the [signals] it answers with, in plain values that a test can make
 * up. Commands sent while the SDK is not connected are dropped, and a code with them erased.
 *
 * Public only so that the tests of the screens on top of [EidEngine] can run the real engine.
 */
interface EidSdkPort {

    /** What the SDK reports, in the order it reported it, nothing dropped. For one collector. */
    val signals: Flow<SdkSignal>

    val isConnected: Boolean

    /** Connects to the SDK, which reports [SdkSignal.Started] once it takes workflows. */
    fun connect()

    fun disconnect()

    fun runAuth(tcTokenUrl: String)

    fun runChangePin()

    fun accept()

    /** Null answers for the simulated card, which takes no PIN. */
    fun setPin(pin: UserPin?)

    fun setCan(can: UserPin)

    /** Null answers for the simulated card, which takes no new PIN. */
    fun setNewPin(newPin: UserPin?)

    fun setPuk(puk: UserPin)

    fun insertSimulatedCard(pseudonymKey: String)

    fun cancel()

    fun continueWorkflow()

    fun requestStatus()

    fun onNfcTag(tag: Tag)
}

data class CardInfo(
    val isDeactivated: Boolean,
    val isPukInoperative: Boolean,
    val pinRetryCounter: Int?,
)

/** What the SDK reports. An [isInputRejected] code was refused as malformed, at no attempt's cost. */
sealed interface SdkSignal {

    data object Started : SdkSignal

    data object InsertCard : SdkSignal

    data class AccessRights(
        val required: List<AccessRight>,
        val effective: List<AccessRight>,
        val transactionInfo: String?,
    ) : SdkSignal

    data object AccessRightsMissing : SdkSignal

    /** A [card] of null on an [attached] reader is a card taken off it. */
    data class Reader(val attached: Boolean, val card: CardInfo?) : SdkSignal

    data class EnterPin(val isInputRejected: Boolean, val card: CardInfo?) : SdkSignal

    data class EnterCan(val isInputRejected: Boolean, val card: CardInfo?) : SdkSignal

    data class EnterNewPin(val isInputRejected: Boolean) : SdkSignal

    data class EnterPuk(val card: CardInfo?) : SdkSignal

    data class Paused(val cause: String) : SdkSignal

    data class AuthSucceeded(val redirectUrl: String) : SdkSignal {

        // Fetching the redirect yields the authorization code, so it stays out of the logs.
        override fun toString() = "AuthSucceeded(redirectUrl=<redacted>)"
    }

    data class AuthFailed(val code: String) : SdkSignal

    data object AuthStartFailed : SdkSignal

    data object ChangePinSucceeded : SdkSignal

    data class ChangePinFailed(val code: String) : SdkSignal

    data class Status(val isWorkflowRunning: Boolean, val progress: Int?) : SdkSignal

    /** The SDK refused [command], not being in a state to take it. */
    data class BadState(val command: String) : SdkSignal

    /** A failure the SDK reports while its workflow still runs. */
    data class Error(val code: String) : SdkSignal
}

/** Consumes this pin into the [String] the SDK insists on: the one copy that cannot be erased. */
internal fun UserPin.toSdkString(): String = getAndClear().use { String(it.chars) }

/** Consumes and throws away this pin, for the paths that cannot deliver it after all. */
fun UserPin.discard() {
    getAndClear().close()
}
