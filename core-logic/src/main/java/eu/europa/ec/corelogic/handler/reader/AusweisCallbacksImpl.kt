package eu.europa.ec.corelogic.handler.reader

import android.net.Uri
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRights
import com.governikus.ausweisapp.sdkwrapper.card.core.AuthResult
import com.governikus.ausweisapp.sdkwrapper.card.core.Cause
import com.governikus.ausweisapp.sdkwrapper.card.core.CertificateDescription
import com.governikus.ausweisapp.sdkwrapper.card.core.ChangePinResult
import com.governikus.ausweisapp.sdkwrapper.card.core.Reader
import com.governikus.ausweisapp.sdkwrapper.card.core.VersionInfo
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowCallbacks
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowProgress
import com.governikus.ausweisapp.sdkwrapper.card.core.WrapperError
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.handler.reader.model.IdentificationRequest
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * AuthState hold the current state of the workflow controller bound to the SDK
 *
 * {"cmd": "RUN_AUTH", "tcTokenURL": "https://test.governikus-eid.de/AusweisAuskunft/WebServiceRequesterServlet"}
 *
 * {"msg": "AUTH"}
 *
 * {"msg": "ACCESS_RIGHTS", "chat": {"effective":["FamilyName","GivenNames","DocumentType"],"optional":["GivenNames"],"required":["FamilyName","DocumentType"]}}
 *
 * {"cmd": "ACCEPT"}
 *
 * {"msg": "ENTER_PIN", "reader": {"attached":true,"card":{"inoperative":false,"deactivated":false,"retryCounter":3},"keypad":false,"name":"NFC"}}
 *
 * {"cmd": "SET_PIN", "value": "123456"}
 *
 * {"msg": "AUTH","result": {"major":"http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok"},"url":"https://test.governikus-eid.de/DEMO/?refID=123456"}
 */

/**
 * Class to communicate events to the UI
 */
sealed class WorkflowEvent {
    data object Idle : WorkflowEvent() // started point
    data object InsertCardRequested : WorkflowEvent()
    data object CardDeactivated : WorkflowEvent()
    data object Return : WorkflowEvent()
    data object CardRecognized : WorkflowEvent()
    data object CardRemoved : WorkflowEvent()
    data object IdentificationCancelled : WorkflowEvent() // return if this case?
    data class AuthenticationCompleted(val redirectURL: String) : WorkflowEvent()
    data object ReadyToStart : WorkflowEvent()
    data object NewPinRequested : WorkflowEvent()
    data object OnAccessRights : WorkflowEvent()
    data class ShowError(val message: String) : WorkflowEvent()
    data class PinRequested(val pinRetryCounter: Int) : WorkflowEvent()
    data class ReadingProgress(val progress: Int) :
        WorkflowEvent()  // find a way to only update the subtitle


    data class AuthenticationFailed(val message: String) : WorkflowEvent()
    data class AuthenticationStartFailed(val message: String) : WorkflowEvent()
    data object AuthenticationStarted : WorkflowEvent()
    data class BadState(val message: String) : WorkflowEvent()
    data object ChangePinSuccess : WorkflowEvent()
    data object ChangePinError : WorkflowEvent()
    data object ChangePinStarted : WorkflowEvent()
    data object EnterCan : WorkflowEvent()
    data object EnterCanError : WorkflowEvent()
    data object EnterNewPin : WorkflowEvent()
    data object EnterNewPinError : WorkflowEvent()
    data object EnterPuk : WorkflowEvent()
    data object EnterPukError : WorkflowEvent()
}

interface ExposedAuthStateAndEvents {
    val events: SharedFlow<WorkflowEvent>
}

class AusweisCallbacksImpl(
    private val coroutineScope: CoroutineScope,
    private val _events: MutableSharedFlow<WorkflowEvent>,
    private val logController: LogController,
    private val resourceProvider: ResourceProvider,
) : WorkflowCallbacks, ExposedAuthStateAndEvents {
    private val logTag = javaClass.simpleName
    // Expose the read-only versions of the events
    override val events: SharedFlow<WorkflowEvent> get() = _events

    override fun onAccessRights(
        error: String?,
        accessRights: AccessRights?,
    ) {
        error?.let {

            logController.e(
                tag = logTag,
                message = { it })
        }
        when {
            (accessRights == null) -> {
                logController.e(
                    tag = logTag,
                    message = { "onAccessRights: Access rights missing." }
                )
                // what do we need to show?
                return
            }

            (accessRights.effectiveRights == accessRights.requiredRights) -> {
                val identificationRequest = IdentificationRequest(
                    accessRights.requiredRights,
                    accessRights.transactionInfo
                )
                logController.d(tag = logTag) { "onAccessRights: $identificationRequest" }
                coroutineScope.launch {
                    _events.emit(WorkflowEvent.OnAccessRights)
                }
            }

            else -> {
                // there are different rights to accept and we need to have a flow for this?
                logController.d(tag = logTag) { "onAccessRights: $accessRights" }
            }
        }
    }


    override fun onAuthenticationCompleted(authResult: AuthResult) {
        logController.d(
            tag = logTag,
            message = { "onAuthenticationCompleted, Authentication completed" })
        with(authResult) {
            when {
                result == null -> onAuthenticationProcessFailed() // Process Failed
                url == null -> onAuthenticationRedirectFailed() // Redirect Failed
                else -> decodeAuthentication(authResult) // Handle major code
            }
        }
    }

    // Decodes the [authResult] returned by the eID authentication workflow and emits the
    // corresponding [WorkflowEvent] to the UI. The SDK reports the outcome in
    // [AuthResult.result] as a URI whose last `#` fragment is the result major code
    // (e.g. `ok`, `error`). The redirect [AuthResult.url] also carries a `ResultMajor`
    // query parameter that duplicates this outcome; it is stripped here so the UI can
    // use the URL as-is. On success emits [WorkflowEvent.AuthenticationCompleted] with
    // the cleaned redirect URL; on failure emits [WorkflowEvent.AuthenticationFailed]
    // via [logErrorAndEmit]. Requires non-null [AuthResult.result] and [AuthResult.url];
    // null cases are handled by the caller [onAuthenticationCompleted].
    private fun decodeAuthentication(
        authResult: AuthResult
    ) {
        val result = requireNotNull(authResult.result)
        val url = requireNotNull(authResult.url)

        val majorCode = result.major.split("#").last()
        val resultMajorQueryParam = "ResultMajor"
        val redirectURL = url.removeQueryParameter(resultMajorQueryParam)

        if (majorCode != "error") {
            logController.d(
                tag = logTag,
                message = { "onAuthenticationCompleted, Authentication Success: $authResult" },
            )
            logController.d(tag = logTag) { "onAuthenticationCompleted, redirectURL: $redirectURL" }
            coroutineScope.launch {
                _events.emit(WorkflowEvent.AuthenticationCompleted(redirectURL.toString()))
            }
        } else {
            logErrorAndEmit(
                message = "onAuthenticationCompleted, Authentication failed $redirectURL ${result.minor} ${result.major}",
                event = WorkflowEvent.AuthenticationFailed(resourceProvider.genericErrorMessage()),
            )
        }
    }

    // Logs [message] at the level selected by [logger] and then emits [event] to the UI via [_events],
    // consolidating the repeated "log then emit" pattern used across the callback methods.
    private fun logAndEmit(
        logger: (String, () -> String) -> Unit,
        message: String,
        event: WorkflowEvent,
    ) {
        logger(logTag) { message }
        coroutineScope.launch {
            _events.emit(event)
        }
    }

    // Logs [message] as an error and emits [event]; convenience wrapper around [logAndEmit] for failure paths.
    private fun logErrorAndEmit(message: String, event: WorkflowEvent) =
        logAndEmit(logController::e, message, event)

    // Logs [message] as debug and emits [event]; convenience wrapper around [logAndEmit] for success paths.
    private fun logDebugAndEmit(message: String, event: WorkflowEvent) =
        logAndEmit(logController::d, message, event)

    // Handles the case where the authentication process itself failed (no [AuthResult.result]),
    // logging an error and emitting [WorkflowEvent.AuthenticationFailed].
    private fun onAuthenticationProcessFailed() = logErrorAndEmit(
        message =  "onAuthenticationCompleted, process failed",
        event = WorkflowEvent.AuthenticationFailed("onAuthenticationCompleted, Process Failed")
    )

    // Handles the case where the redirect URL is missing from the authentication result
    // (no [AuthResult.url]), logging an error and emitting [WorkflowEvent.AuthenticationFailed].
    private fun onAuthenticationRedirectFailed() = logErrorAndEmit(
        message = "onAuthenticationCompleted, redirect failed",
        event = WorkflowEvent.AuthenticationFailed("onAuthenticationCompleted, Redirect Failed")
    )

    override fun onAuthenticationStartFailed(error: String) = logErrorAndEmit(
        message = "onAuthenticationCompleted, $error",
        event = WorkflowEvent.AuthenticationStartFailed(resourceProvider.genericErrorMessage()),
    )

    override fun onAuthenticationStarted() {
        coroutineScope.launch {
            _events.emit(WorkflowEvent.AuthenticationStarted)
        }
    }

    override fun onBadState(error: String) = logErrorAndEmit(
        message = "onBadState, $error",
        event = WorkflowEvent.BadState(resourceProvider.genericErrorMessage()),
    )

    override fun onCertificate(certificateDescription: CertificateDescription) {
        logController.d(tag = logTag) { "onCertificate, $certificateDescription" }
    }

    override fun onChangePinCompleted(changePinResult: ChangePinResult) {
        if (changePinResult.success) {
            logDebugAndEmit(
                message = "onChangePinCompleted, New PIN has been set successfully.",
                event = WorkflowEvent.ChangePinSuccess,
            )
        } else {
            logErrorAndEmit(
                message = "onChangePinCompleted, Changing PIN failed. Error: ${changePinResult.reason}",
                event = WorkflowEvent.ChangePinError,
            )
        }
    }

    override fun onChangePinStarted() = logDebugAndEmit(
        message = "onChangePinStarted, Change pin started",
        event = WorkflowEvent.ChangePinStarted,
    )

    override fun onEnterCan(
        error: String?,
        reader: Reader,
    ) {
        if (error == null) {

            reader.card?.let {
                logController.d(
                    logTag,
                    message = { "onEnterCan, pin retry counter: ${reader.card?.pinRetryCounter}" }
                )

                if (it.deactivated == true) {
                    logController.d(
                        logTag,
                        message = { "onEnterCan, card deactivated" }
                    )

                    coroutineScope.launch {
                        _events.emit(WorkflowEvent.CardDeactivated)
                    }
                } else {
                    coroutineScope.launch {
                        _events.emit(WorkflowEvent.EnterCan)
                    }
                }
            }

        } else {
            logErrorAndEmit(
                message = "onEnterCan, $error",
                event = WorkflowEvent.EnterCanError,
            )
        }
    }

    override fun onEnterNewPin(
        error: String?,
        reader: Reader,
    ) {
        if (error == null) {
            coroutineScope.launch {
                _events.emit(WorkflowEvent.EnterNewPin)
            }
        } else {
            logErrorAndEmit(
                message = "onEnterNewPin, $error",
                event = WorkflowEvent.EnterNewPinError,
            )
        }
    }

    override fun onEnterPin(
        error: String?,
        reader: Reader,
    ) {
        error?.let { logController.e(logTag, message = { "onEnterPin, $it" }) }
        reader.card?.let {
            logController.d(
                logTag,
                message = { "onEnterPin, pin retry counter: ${reader.card?.pinRetryCounter}" }
            )

            if (it.deactivated == true) {
                logController.d(
                    logTag,
                    message = { "onEnterPin, card deactivated" }
                )

                coroutineScope.launch {
                    _events.emit(WorkflowEvent.CardDeactivated)
                }
            } else {
                coroutineScope.launch {
                    _events.emit(WorkflowEvent.PinRequested(it.pinRetryCounter ?: 0))
                }
            }
        } ?: run {
            coroutineScope.launch {
                _events.emit(WorkflowEvent.ShowError(resourceProvider.genericErrorMessage()))
            }
        }
    }

    override fun onEnterPuk(
        error: String?,
        reader: Reader,
    ) {
        if (error == null) {
            coroutineScope.launch {
                _events.emit(WorkflowEvent.EnterPuk)
            }
        } else {
            logErrorAndEmit(
                message = "onEnterPuk, $error",
                event = WorkflowEvent.EnterPukError,
            )
        }
    }

    override fun onInfo(versionInfo: VersionInfo) {
        logController.d(
            logTag,
            message = { "on info: $versionInfo" })
    }

    override fun onInsertCard(error: String?) {
        error?.let { logController.e(logTag, message = { "onInsertCard, $it" }) }
        coroutineScope.launch {
            logController.d(
                logTag,
                message = { "onInsertCard" })
            _events.emit(WorkflowEvent.InsertCardRequested)
        }
    }

    override fun onInternalError(error: String) {
        logController.e(logTag, message = { "onInternalError, $error" })
    }

    override fun onPause(cause: Cause) {
        // a lot of the states for card will be handle here to the UI
        logController.e(logTag, message = { "onPause, ${cause.rawName}" })
    }

    override fun onReader(reader: Reader?) {
        logController.d(logTag, message = { "onReader" })
        if (reader == null) {
            logController.d(
                logTag,
                message = { "onReader, No reader available." })
            return
        }

        reader.card?.let {
            logController.d(
                logTag,
                message = { "onReader with card details : ${reader.card.toString()}" })
            coroutineScope.launch {
                if (it.deactivated == true) {
                    logController.d(
                        logTag,
                        message = { "onReader, card deactivated" })
                    _events.emit(WorkflowEvent.CardDeactivated)
                } else {
                    _events.emit(WorkflowEvent.CardRecognized)
                }
            }
        }
        if (reader.card == null && reader.attached) {

            coroutineScope.launch {
                logController.d(
                    logTag,
                    message = { "onReader, card removed" })
                _events.emit(WorkflowEvent.CardRemoved)
            }
        }
    }

    override fun onReaderList(readers: List<Reader>?) {
        logController.d(
            logTag,
            message = { "onReaderList: ${readers.toString()}" })
    }

    override fun onStarted() = logDebugAndEmit(
        message = "onStarted, now we can start to authenticate",
        event = WorkflowEvent.ReadyToStart,
    )

    override fun onStatus(workflowProgress: WorkflowProgress) = logDebugAndEmit(
        message = "onStatus with state ${workflowProgress.state} and progress ${workflowProgress.progress}",
        event = WorkflowEvent.ReadingProgress(workflowProgress.progress ?: 0),
    )

    override fun onWrapperError(error: WrapperError) {
        logController.e(
            tag = logTag,
            message = { "onWrapperError, ${error.error} - ${error.msg}" })
    }

    private fun Uri.removeQueryParameter(parameterToRemove: String): Uri = this
            .buildUpon()
            .clearQuery()
            .also { builder ->
                this.queryParameterNames.forEach {
                    if (it != parameterToRemove) {
                        builder.appendQueryParameter(it, this.getQueryParameter(it))
                    }
                }
            }
            .build()

}
