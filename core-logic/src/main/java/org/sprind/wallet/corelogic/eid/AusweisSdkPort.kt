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

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import androidx.core.net.toUri
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRights
import com.governikus.ausweisapp.sdkwrapper.card.core.AuthResult
import com.governikus.ausweisapp.sdkwrapper.card.core.Card
import com.governikus.ausweisapp.sdkwrapper.card.core.Cause
import com.governikus.ausweisapp.sdkwrapper.card.core.CertificateDescription
import com.governikus.ausweisapp.sdkwrapper.card.core.ChangePinResult
import com.governikus.ausweisapp.sdkwrapper.card.core.Reader
import com.governikus.ausweisapp.sdkwrapper.card.core.VersionInfo
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowCallbacks
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowController
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowProgress
import com.governikus.ausweisapp.sdkwrapper.card.core.WrapperError
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.corelogic.cardreader.SimulatedEidCard

/** [EidSdkPort] on the AusweisApp SDK wrapper's [WorkflowController]. */
internal class AusweisSdkPort(
    private val context: Context,
    private val workflowController: WorkflowController,
    private val isDeveloperMode: Boolean,
    private val logController: LogController,
) : EidSdkPort, WorkflowCallbacks {

    private val logTag = javaClass.simpleName

    // Each callback is queued the moment it comes, on whatever thread it comes on, so that none is
    // lost while nobody collects and none overtakes another.
    private val queue = Channel<SdkSignal>(Channel.UNLIMITED)

    override val signals: Flow<SdkSignal> = queue.receiveAsFlow()

    override val isConnected: Boolean get() = workflowController.isStarted

    // The wrapper keeps its callbacks in a list, so registering twice would deliver every callback
    // twice. isStarted is no guide to it: it stays false until the SDK service is bound.
    private var isRegistered = false

    override fun connect() {
        if (!isRegistered) {
            workflowController.registerCallbacks(this)
            isRegistered = true
        }
        // A no-op while the SDK is starting or started.
        workflowController.start(context)
    }

    override fun disconnect() {
        workflowController.unregisterCallbacks(this)
        isRegistered = false
        workflowController.stop()
    }

    override fun runAuth(tcTokenUrl: String) = command("RUN_AUTH") {
        workflowController.startAuthentication(
            tcTokenUrl.toUri(),
            developerMode = isDeveloperMode,
            status = isDeveloperMode,
        )
    }

    override fun runChangePin() = command("RUN_CHANGE_PIN") { workflowController.startChangePin() }

    override fun accept() = command("ACCEPT") { workflowController.accept() }

    override fun setPin(pin: UserPin?) =
        command("SET_PIN", pin) { workflowController.setPin(pin?.toSdkString()) }

    override fun setCan(can: UserPin) =
        command("SET_CAN", can) { workflowController.setCan(can.toSdkString()) }

    override fun setNewPin(newPin: UserPin?) =
        command("SET_NEW_PIN", newPin) { workflowController.setNewPin(newPin?.toSdkString()) }

    override fun setPuk(puk: UserPin) =
        command("SET_PUK", puk) { workflowController.setPuk(puk.toSdkString()) }

    override fun insertSimulatedCard(pseudonymKey: String) = command("SET_CARD") {
        workflowController.setCard(
            SimulatedEidCard.READER_NAME,
            SimulatedEidCard.create(pseudonymKey),
        )
    }

    override fun cancel() = command("CANCEL") { workflowController.cancel() }

    override fun continueWorkflow() = command("CONTINUE") { workflowController.continueWorkflow() }

    override fun requestStatus() = command("GET_STATUS") { workflowController.getStatus() }

    override fun onNfcTag(tag: Tag) = command("NFC_TAG") { workflowController.onNfcTagDetected(tag) }

    private inline fun command(name: String, code: UserPin? = null, send: () -> Unit) {
        if (!workflowController.isStarted) {
            logController.d(logTag) { "$name not sent: the SDK is not connected" }
            code?.discard()
            return
        }
        logController.d(logTag) { "Send $name" }
        send()
    }

    private fun signal(signal: SdkSignal) {
        logController.d(logTag) { "Signal $signal" }
        queue.trySend(signal)
    }

    override fun onStarted() = signal(SdkSignal.Started)

    override fun onAuthenticationStarted() {
        logController.d(logTag) { "onAuthenticationStarted" }
    }

    override fun onAuthenticationStartFailed(error: String) {
        logController.e(logTag) { "onAuthenticationStartFailed, $error" }
        signal(SdkSignal.AuthStartFailed)
    }

    // The SDK gives the outcome as a URI whose last fragment is the result major (ok, error). The
    // redirect repeats it as a ResultMajor parameter, stripped so that the URL can be used as-is.
    override fun onAuthenticationCompleted(authResult: AuthResult) {
        val result = authResult.result
        val url = authResult.url
        signal(
            when {
                result == null -> SdkSignal.AuthFailed(EidFailure.AUTHENTICATION_FAILED)
                result.major.substringAfterLast('#') == "error" -> {
                    logController.e(logTag) { "Authentication failed: ${result.minor} ${result.reason}" }
                    SdkSignal.AuthFailed(result.reason ?: EidFailure.AUTHENTICATION_FAILED)
                }

                url == null -> SdkSignal.AuthFailed(EidFailure.REDIRECT_MISSING)
                else -> SdkSignal.AuthSucceeded(url.withoutQueryParameter("ResultMajor").toString())
            }
        )
    }

    override fun onChangePinStarted() {
        logController.d(logTag) { "onChangePinStarted" }
    }

    override fun onAccessRights(error: String?, accessRights: AccessRights?) {
        error?.let { logController.e(logTag) { "onAccessRights, $it" } }
        signal(
            if (accessRights == null) {
                SdkSignal.AccessRightsMissing
            } else {
                SdkSignal.AccessRights(
                    required = accessRights.requiredRights,
                    effective = accessRights.effectiveRights,
                    transactionInfo = accessRights.transactionInfo,
                )
            }
        )
    }

    override fun onCertificate(certificateDescription: CertificateDescription) {
        logController.d(logTag) { "onCertificate, $certificateDescription" }
    }

    override fun onInsertCard(error: String?) {
        error?.let { logController.e(logTag) { "onInsertCard, $it" } }
        signal(SdkSignal.InsertCard)
    }

    override fun onPause(cause: Cause) = signal(SdkSignal.Paused(cause.rawName))

    override fun onReader(reader: Reader?) {
        reader ?: return
        signal(SdkSignal.Reader(attached = reader.attached, card = reader.card?.toInfo()))
    }

    override fun onReaderList(readers: List<Reader>?) {
        logController.d(logTag) { "onReaderList, $readers" }
    }

    override fun onEnterPin(error: String?, reader: Reader) {
        error?.let { logController.e(logTag) { "onEnterPin, $it" } }
        signal(SdkSignal.EnterPin(isInputRejected = error != null, card = reader.card?.toInfo()))
    }

    override fun onEnterNewPin(error: String?, reader: Reader) {
        error?.let { logController.e(logTag) { "onEnterNewPin, $it" } }
        signal(SdkSignal.EnterNewPin(isInputRejected = error != null))
    }

    override fun onEnterPuk(error: String?, reader: Reader) {
        error?.let { logController.e(logTag) { "onEnterPuk, $it" } }
        signal(SdkSignal.EnterPuk(card = reader.card?.toInfo()))
    }

    override fun onEnterCan(error: String?, reader: Reader) {
        error?.let { logController.e(logTag) { "onEnterCan, $it" } }
        signal(SdkSignal.EnterCan(isInputRejected = error != null, card = reader.card?.toInfo()))
    }

    override fun onChangePinCompleted(changePinResult: ChangePinResult) = signal(
        if (changePinResult.success) {
            SdkSignal.ChangePinSucceeded
        } else {
            SdkSignal.ChangePinFailed(changePinResult.reason ?: EidFailure.CHANGE_PIN_FAILED)
        }
    )

    override fun onWrapperError(error: WrapperError) {
        logController.e(logTag) { "onWrapperError, ${error.error} - ${error.msg}" }
        signal(SdkSignal.Error(EidFailure.WRAPPER_ERROR))
    }

    override fun onStatus(workflowProgress: WorkflowProgress) = signal(
        SdkSignal.Status(
            isWorkflowRunning = workflowProgress.workflow != null,
            progress = workflowProgress.progress,
        )
    )

    override fun onInfo(versionInfo: VersionInfo) {
        logController.d(logTag) { "onInfo, $versionInfo" }
    }

    override fun onInternalError(error: String) {
        logController.e(logTag) { "onInternalError, $error" }
        signal(SdkSignal.Error(EidFailure.INTERNAL_ERROR))
    }

    override fun onBadState(error: String) = signal(SdkSignal.BadState(command = error))

    private fun Card.toInfo() = CardInfo(
        isDeactivated = deactivated == true,
        isPukInoperative = inoperative == true,
        pinRetryCounter = pinRetryCounter,
    )

    private fun Uri.withoutQueryParameter(name: String): Uri = buildUpon()
        .clearQuery()
        .also { builder ->
            queryParameterNames.filter { it != name }.forEach {
                builder.appendQueryParameter(it, getQueryParameter(it))
            }
        }
        .build()
}
