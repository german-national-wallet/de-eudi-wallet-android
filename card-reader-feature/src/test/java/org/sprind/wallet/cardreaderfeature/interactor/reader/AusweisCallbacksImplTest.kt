package org.sprind.wallet.cardreaderfeature.interactor.reader

import android.net.Uri
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRight
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRights
import com.governikus.ausweisapp.sdkwrapper.card.core.AuthResult
import com.governikus.ausweisapp.sdkwrapper.card.core.AuthResultData
import com.governikus.ausweisapp.sdkwrapper.card.core.Card
import com.governikus.ausweisapp.sdkwrapper.card.core.Cause
import com.governikus.ausweisapp.sdkwrapper.card.core.ChangePinResult
import com.governikus.ausweisapp.sdkwrapper.card.core.Reader
import com.governikus.ausweisapp.sdkwrapper.card.core.VersionInfo
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowProgress
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowProgressType
import com.governikus.ausweisapp.sdkwrapper.card.core.WrapperError
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.corelogic.handler.reader.AusweisCallbacksImpl
import eu.europa.ec.corelogic.handler.reader.WorkflowEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.intArrayOf
import kotlin.test.assertEquals
import androidx.core.net.toUri

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class AusweisCallbacksImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var configLogic: ConfigLogic

    private lateinit var closeable: AutoCloseable

    private lateinit var subject: AusweisCallbacksImpl

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        subject =
            AusweisCallbacksImpl(
                TestScope(),
                MutableSharedFlow(),
                logController,
                resourceProvider,
            )
    }

    @Test
    fun `onAccessRights with matching rights emits OnAccessRights event`() = runTest {
        val accessRights =
            AccessRights(
                listOf(AccessRight.ADDRESS),
                listOf(AccessRight.ADDRESS),
                listOf(AccessRight.ADDRESS),
                transactionInfo = "info",
                null
            )
        subject.onAccessRights(null, accessRights)
        subject.events.runFlowTest {
            assertEquals(
                WorkflowEvent.OnAccessRights,
                awaitItem()
            )
        }

    }

    @Test
    fun `onAccessRights with error and accessRights object is null, emits logs error for both flows`() =
        runTest {
            subject.onAccessRights("some error", null)
            verify(logController, times(2)).e(any(), any<() -> String>())
        }

    @Test
    fun `onAuthenticationCompleted with MajorCode is OK emits AuthenticationCompleted event with redirectUrl`() =
        runTest {
            val url = "https://example.com?ResultMajor=OK".toUri()
            val authResult = AuthResult(
                result = AuthResultData(
                    "code#OK", "#minor", "0000",
                    "description", null, null
                ),
                url = url
            )
            subject.onAuthenticationCompleted(authResult)
            verify(logController, times(3)).d(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.AuthenticationCompleted("https://example.com"),
                    awaitItem()
                )
            }

        }

    @Test
    fun `onAuthenticationCompleted with MajorCode is error emits ShowError with generic message`() =
        runTest {
            whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
            val url = "https://example.com?ResultMajor=error".toUri()
            val authResult = AuthResult(
                result = AuthResultData(
                    "code#error", "#minor", "0000",
                    "description", null, null
                ),
                url = url
            )
            subject.onAuthenticationCompleted(authResult)
            verify(logController, times(1)).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.AuthenticationFailed("Generic error"),
                    awaitItem()
                )
            }
        }

    @Test
    fun `onAuthenticationCompleted with null result emits AuthenticationFailed with process failed message`() =
        runTest {
            val authResult = AuthResult(
                result = null,
                url = "https://example.com".toUri()
            )
            subject.onAuthenticationCompleted(authResult)
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.AuthenticationFailed("onAuthenticationCompleted, Process Failed"),
                    awaitItem()
                )
            }
        }

    @Test
    fun `onAuthenticationCompleted with null url emits AuthenticationFailed with redirect failed message`() =
        runTest {
            val authResult = AuthResult(
                result = AuthResultData(
                    "code#OK", "#minor", "0000",
                    "description", null, null
                ),
                url = null
            )
            subject.onAuthenticationCompleted(authResult)
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.AuthenticationFailed("onAuthenticationCompleted, Redirect Failed"),
                    awaitItem()
                )
            }
        }

    @Test
    fun `onAuthenticationStartFailed is triggered emits ShowError with generic message`() =
        runTest {
            whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
            subject.onAuthenticationStartFailed("failed")
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.AuthenticationStartFailed("Generic error"),
                    awaitItem()
                )
            }
        }

    @Test
    fun `onBadState is triggered emits ShowError with generic message`() =
        runTest {
            whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
            subject.onBadState("bad state")
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.BadState("Generic error"),
                    awaitItem()
                )
            }
        }

    @Test
    fun `onChangePinCompleted  success then logs results`() =
        runTest {
            subject.onChangePinCompleted(ChangePinResult(true, null))
            verify(logController).d(any(), any<() -> String>())
        }

    @Test
    fun `onChangePinCompleted  failed then logs results`() =
        runTest {
            subject.onChangePinCompleted(ChangePinResult(false, null))
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onChangePinStarted  then log value`() =
        runTest {
            subject.onChangePinStarted()
            verify(logController).d(any(), any<() -> String>())
        }

    @Test
    fun `onEnterCan has error then log value`() =
        runTest {
            subject.onEnterCan(error = "error", mock(Reader::class.java))
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onEnterNewPin has error then log value`() =
        runTest {
            subject.onEnterNewPin(error = "error", Reader("card", true, true, true, null))
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onEnterPin has no error and card is available then PinRequested event`() =
        runTest {
            subject.onEnterPin(
                error = null,
                Reader("card", true, true, true, Card(false, false, 2))
            )
            verify(logController).d(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.PinRequested(2), awaitItem())
            }
        }

    @Test
    fun `onEnterPin has error and card is not available, then log and Show generic error`() =
        runTest {
            whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
            subject.onEnterPin(
                error = "error",
                Reader("card", true, true, true, null)
            )
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.ShowError("Generic error"), awaitItem())
            }
        }


    @Test
    fun `onEnterPuk has error then log value`() =
        runTest {
            subject.onEnterPuk(error = "error", Reader("card", true, true, true, null))
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onInfo then log sdk info`() =
        runTest {
            subject.onInfo(VersionInfo("", "", "", "", "", "", ""))
            verify(logController).d(any(), any<() -> String>())
        }

    @Test
    fun `onInsertCard has no error then InsertCardRequested event`() =
        runTest {
            subject.onInsertCard(null)
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.InsertCardRequested, awaitItem())
            }
        }

    @Test
    fun `onInsertCard has error then log error and then InsertCardRequested event`() =
        runTest {
            subject.onInsertCard("error")
            verify(logController).e(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.InsertCardRequested, awaitItem())
            }
        }

    @Test
    fun `onInternalError then log error`() =
        runTest {
            subject.onInternalError("error")
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onPause then log error`() =
        runTest {
            subject.onPause(Cause.BadCardPosition)
            verify(logController).e(any(), any<() -> String>())
        }

    @Test
    fun `onReader is null then log status`() =
        runTest {
            subject.onReader(null)
            verify(logController, times(2)).d(any(), any<() -> String>())
        }

    @Test
    fun `onReader has a Card and is deactivated then CardDeactivated event`() =
        runTest {
            subject.onReader(
                Reader(
                    "card", true, true, true,
                    Card(deactivated = true, true, 0)
                ))
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.CardDeactivated, awaitItem())
            }
        }

    @Test
    fun `onReaderList then log error`() =
        runTest {
            subject.onReaderList(emptyList<Reader>())
            verify(logController).d(any(), any<() -> String>())
        }

    @Test
    fun `onStarted then log and ReadyToStart event`() =
        runTest {
            subject.onStarted()
            verify(logController).d(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(WorkflowEvent.ReadyToStart, awaitItem())
            }
        }

    @Test
    fun `onStatus when isCardAttached is true with WorkflowProgress then ReadingProgress event `() =
        runTest {
            whenever(configLogic.appBuildType).thenReturn(AppBuildType.DEBUG)
            subject.onStatus(WorkflowProgress(WorkflowProgressType.AUTHENTICATION, 80, "reading"))
            verify(logController, times(1)).d(any(), any<() -> String>())
            subject.events.runFlowTest {
                assertEquals(
                    WorkflowEvent.ReadingProgress(80), awaitItem()
                )
            }
        }

    @Test
    fun `onWrapperError then log wrapper error`() =
        runTest {
            subject.onWrapperError(WrapperError("msg", "error"))
            verify(logController).e(any(), any<() -> String>())
        }

    @After
    fun tearDown() {
        closeable.close()
    }
}