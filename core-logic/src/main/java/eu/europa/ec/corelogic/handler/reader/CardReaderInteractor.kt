package eu.europa.ec.corelogic.handler.reader

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import com.governikus.ausweisapp.sdkwrapper.SDKWrapper
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowCallbacks
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.flow.SharedFlow
import org.sprind.wallet.businesslogic.model.UserPin

interface CardReaderInteractor {
    val eidFlow: SharedFlow<WorkflowEvent>
    fun handleNfcTag(tag: Tag)
    fun startCardReader()
    fun cancelIdentification()
    fun providePin(pin: UserPin?)
    fun provideCan(can: UserPin)
    fun acceptRights()
    fun startAuthentication(tcTokenUrl: Uri)
    fun startChangePin()
    fun provideNewPin(newPin: UserPin)
    fun setVirtualCard()
}

class CardReaderInteractorImpl(
    private val logController: LogController,
    private val context: Context,
    private val ausweisCallbacks: WorkflowCallbacks,
    private val ausweisSdkWrapper: SDKWrapper,
    private val configLogic: ConfigLogic
) : CardReaderInteractor {
    private val logTag = javaClass.simpleName

    override val eidFlow: SharedFlow<WorkflowEvent> =
        (ausweisCallbacks as ExposedAuthStateAndEvents).events

    override fun handleNfcTag(tag: Tag) {
        if (!ausweisSdkWrapper.workflowController.isStarted) {
            logController.e(
                tag = logTag,
                message = { "handleNfcTag ignored: the reader is not yet ready" })
            return
        }
        logController.d(
            tag = logTag,
            message = { "handleNfcTag: forwarding detected tag to the workflow controller" })
        ausweisSdkWrapper.workflowController.onNfcTagDetected(tag)
    }

    override fun setVirtualCard() {
        ausweisSdkWrapper.workflowController.setCard("Simulator", null)
    }

    override fun startCardReader() {
        logController.d(
            tag = logTag,
            message = { "Starting workflow controller." })
        if (!ausweisSdkWrapper.workflowController.isStarted) {
            ausweisSdkWrapper.workflowController.registerCallbacks(ausweisCallbacks)
            ausweisSdkWrapper.workflowController.start(context)
        }
    }

    override fun cancelIdentification() {
        logController.d(
            tag = logTag,
            message = { "Stopping workflow controller." })
        if (ausweisSdkWrapper.workflowController.isStarted) {
            ausweisSdkWrapper.workflowController.cancel()
        }
        ausweisSdkWrapper.workflowController.unregisterCallbacks(ausweisCallbacks)
        ausweisSdkWrapper.workflowController.stop()
    }

    override fun providePin(pin: UserPin?) {
        if (!ausweisSdkWrapper.workflowController.isStarted) {
            logController.d(
                tag = logTag,
                message = { "Provide PIN was triggered" })
            pin?.discard()
            return
        }
        ausweisSdkWrapper.workflowController.setPin(pin?.toSdkString())
    }

    override fun provideCan(can: UserPin) {
        if (!ausweisSdkWrapper.workflowController.isStarted) {
            logController.d(
                tag = logTag,
                message = { "Provide CAN was triggered but workflow controller is not started" })
            can.discard()
            return
        }
        ausweisSdkWrapper.workflowController.setCan(can.toSdkString())
    }

    override fun acceptRights() {
        ausweisSdkWrapper.workflowController.accept()
    }

    override fun startAuthentication(tcTokenUrl: Uri) {
        logController.d(logTag, message = { "Started authentication" })
        ausweisSdkWrapper.workflowController.startAuthentication(
            tcTokenUrl, developerMode = configLogic.appBuildType == AppBuildType.DEBUG,
            status = configLogic.appBuildType == AppBuildType.DEBUG
        )
    }

    override fun startChangePin() {
        logController.d(logTag, message = { "Started change pin" })
        ausweisSdkWrapper.workflowController.startChangePin()
    }

    override fun provideNewPin(newPin: UserPin) {
        logController.d(logTag, message = { "Set new pin" })
        ausweisSdkWrapper.workflowController.setNewPin(newPin.toSdkString())
    }
}

/**
 * Consumes this pin into the [String] the eID SDK insists on.
 *
 * This is the one place a code becomes a value that cannot be erased, and it is unavoidable:
 * `WorkflowController` takes a [String]. Everything upstream keeps the digits in a char array so
 * that the copy made here is the only one, and it is made as late as possible.
 */
private fun UserPin.toSdkString(): String = getAndClear().use { String(it.chars) }

/** Consumes and throws away this pin, for the paths that cannot deliver it after all. */
private fun UserPin.discard() {
    getAndClear().close()
}
