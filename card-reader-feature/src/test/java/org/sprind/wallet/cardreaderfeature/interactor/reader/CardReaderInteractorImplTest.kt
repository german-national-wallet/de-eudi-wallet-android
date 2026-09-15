package org.sprind.wallet.cardreaderfeature.interactor.reader

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import com.governikus.ausweisapp.sdkwrapper.SDKWrapper
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowCallbacks
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowController
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractor
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractorImpl
import org.sprind.wallet.businesslogic.model.UserPinImpl
import eu.europa.ec.corelogic.handler.reader.ExposedAuthStateAndEvents
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class CardReaderInteractorImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sdkWrapper: SDKWrapper

    @Mock
    private lateinit var mockedWorkflowController: WorkflowController


    private val callbacks = mock(
        WorkflowCallbacks::class.java,
        withSettings().extraInterfaces(ExposedAuthStateAndEvents::class.java)
    )

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var configLogic: ConfigLogic

    private lateinit var closable: AutoCloseable

    private lateinit var interactor: CardReaderInteractor

    @Before
    fun setUp() {
        closable = MockitoAnnotations.openMocks(this)
        whenever(sdkWrapper.workflowController).thenReturn(mockedWorkflowController)
        whenever(configLogic.appBuildType).thenReturn(AppBuildType.DEBUG)
        doNothing().`when`(mockedWorkflowController).setPin(anyString())
        doNothing().`when`(mockedWorkflowController).unregisterCallbacks(callbacks)
        doNothing().`when`(mockedWorkflowController).registerCallbacks(callbacks)
        doNothing().`when`(mockedWorkflowController).start(context)
        doNothing().`when`(mockedWorkflowController).accept()
        doNothing().`when`(mockedWorkflowController).cancel()
        doNothing().`when`(mockedWorkflowController).stop()

        interactor = CardReaderInteractorImpl(
            logController,
            context,
            callbacks,
            sdkWrapper,
            configLogic,
        )
    }

    @Test
    fun `startCardReader should start the workflowController and launch authentication`() =
        runTest {
            doNothing().`when`(logController).d(any(), any())

            interactor.startCardReader()

            verify(logController, times(1)).d(any(), any())
            verify(sdkWrapper.workflowController, times(1)).registerCallbacks(callbacks)
            verify(sdkWrapper.workflowController, times(1)).start(context)
        }

    @Test
    fun `when handleNfcTag and workflowController isStarted is true then onNfcTagDetected`() =
        runTest {
            val nfcTag = mock(Tag::class.java)
            whenever(sdkWrapper.workflowController.isStarted).thenReturn(true)
            doNothing().`when`(logController).e(tag = any(), message = any())

            interactor.handleNfcTag(nfcTag)

            verify(sdkWrapper.workflowController, times(1)).onNfcTagDetected(nfcTag)
        }

    @Test
    fun `when handleNfcTag and workflowController isStarted is false then log and return`() =
        runTest {
            val nfcTag = mock(Tag::class.java)
            whenever(sdkWrapper.workflowController.isStarted).thenReturn(false)
            doNothing().`when`(logController).e(tag = any(), message = any())

            interactor.handleNfcTag(nfcTag)

            verify(logController, times(1)).e(tag = any(), message = any())
        }

    @Test
    fun `when cancelIdentification and isStarted is true then unregisterCallbacks and cancel identification`() {
        doNothing().`when`(logController).d(any(), any())
        whenever(sdkWrapper.workflowController.isStarted).thenReturn(true)
        interactor.cancelIdentification()

        verify(logController, times(1)).d(any(), any())
        verify(sdkWrapper.workflowController, times(1)).cancel()
        verify(sdkWrapper.workflowController, times(1)).unregisterCallbacks(callbacks)
        verify(sdkWrapper.workflowController, times(1)).stop()
    }

    @Test
    fun `when cancelIdentification and isStarted is false then unregisterCallbacks and cancel identification`() {
        doNothing().`when`(logController).d(any(), any())
        whenever(sdkWrapper.workflowController.isStarted).thenReturn(false)
        interactor.cancelIdentification()

        verify(logController, times(1)).d(any(), any())
        verify(sdkWrapper.workflowController, times(0)).cancel()
        verify(sdkWrapper.workflowController, times(1)).unregisterCallbacks(callbacks)
        verify(sdkWrapper.workflowController, times(1)).stop()
    }

    @Test
    fun `when providePin and workflowController isStarted is false then return`() {
        doNothing().`when`(logController).d(tag = any(), message = any())
        whenever(sdkWrapper.workflowController.isStarted).thenReturn(false)

        interactor.providePin(UserPinImpl(""))

        verify(logController, times(1)).d(tag = any(), message = any())
    }

    @Test
    fun `when providePin and workflowController isStarted is true then setPin`() {
        whenever(sdkWrapper.workflowController.isStarted).thenReturn(true)

        interactor.providePin(UserPinImpl(""))

        verify(sdkWrapper.workflowController, times(1)).setPin(anyString())
    }

    @Test
    fun `when confirmAttributes then accept`() {
        interactor.acceptRights()

        verify(sdkWrapper.workflowController, times(1)).accept()
    }

    @Test
    fun `when startAuthentication then startAuthentication`() = runTest {
        val mockedUri = mock(Uri::class.java)
        doNothing().`when`(logController).d(any(), any())
        doNothing().`when`(mockedWorkflowController).unregisterCallbacks(callbacks)
        interactor.startAuthentication(mockedUri)

        verify(logController, times(1)).d(any(), any())
        verify(sdkWrapper.workflowController, times(1)).startAuthentication(mockedUri, true, true)
    }

    @After
    fun tearDown() {
        closable.close()
    }

}
