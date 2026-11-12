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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.sprind.wallet.businesslogic.model.UserPin

/** Records the commands sent to the SDK, codes in the clear, and plays the signals a test emits. */
internal class FakeEidSdkPort : EidSdkPort {

    val commands = mutableListOf<String>()

    private val queue = Channel<SdkSignal>(Channel.UNLIMITED)

    override val signals: Flow<SdkSignal> = queue.receiveAsFlow()

    override var isConnected = false
        private set

    fun emit(signal: SdkSignal) {
        queue.trySend(signal)
    }

    /** The SDK service went away, which the SDK does not report. */
    fun loseConnection() {
        isConnected = false
    }

    override fun connect() {
        commands += "CONNECT"
        isConnected = true
    }

    override fun disconnect() {
        commands += "DISCONNECT"
        isConnected = false
    }

    override fun runAuth(tcTokenUrl: String) {
        commands += "RUN_AUTH"
    }

    override fun runChangePin() {
        commands += "RUN_CHANGE_PIN"
    }

    override fun accept() {
        commands += "ACCEPT"
    }

    override fun setPin(pin: UserPin?) {
        commands += "SET_PIN ${pin.reveal()}"
    }

    override fun setCan(can: UserPin) {
        commands += "SET_CAN ${can.reveal()}"
    }

    override fun setNewPin(newPin: UserPin?) {
        commands += "SET_NEW_PIN ${newPin.reveal()}"
    }

    override fun setPuk(puk: UserPin) {
        commands += "SET_PUK ${puk.reveal()}"
    }

    override fun insertSimulatedCard(pseudonymKey: String) {
        commands += "SET_CARD $pseudonymKey"
    }

    override fun cancel() {
        commands += "CANCEL"
    }

    override fun continueWorkflow() {
        commands += "CONTINUE"
    }

    override fun requestStatus() {
        commands += "GET_STATUS"
    }

    override fun onNfcTag(tag: Tag) {
        commands += "NFC_TAG"
    }

    private fun UserPin?.reveal(): String = this?.toSdkString() ?: "none"
}
