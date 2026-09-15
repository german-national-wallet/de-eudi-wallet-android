package eu.europa.ec.authenticationlogic.controller.appattestation


import app.cash.turbine.test
import eu.europa.ec.authenticationlogic.controller.storage.RegistrationData
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.base.TestApplication
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContext
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.PrivateKey

private const val mockChallenge = "mockChallenge"
private const val mockWalletInstanceId = "mockWalletInstanceId"
private const val mockWalletInstanceRevocationCode = "mockWalletInstanceRevocationCode"
private const val mockMdvmToken = "mockMdvmToken"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class AppAttestationControllerTest {
    @Mock
    private lateinit var walletRegistrationController: WalletRegistrationController

    @Mock
    private lateinit var jwtBuilder: JwtBuilder

    @Mock
    private lateinit var walletRegistrationStorageController: WalletRegistrationStorageController

    @Mock
    private lateinit var mdvmAuthContextProvider: MdvmAuthContextProvider

    @Mock
    private lateinit var walletAttestationController: WalletAttestationController

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var mockMdvmAuthPrivateKey: PrivateKey

    private lateinit var closeable: AutoCloseable

    private val subject: AppAttestationControllerImpl by lazy {
        AppAttestationControllerImpl(
            walletRegistrationController,
            jwtBuilder,
            walletRegistrationStorageController,
            mdvmAuthContextProvider,
            walletAttestationController,
            logController
        )
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(
                ApiResult.Success(
                    MdvmAuthContext(
                        mdvmToken = mockMdvmToken,
                        mdvmAuthPrvk = mockMdvmAuthPrivateKey,
                    )
                )
            )
        }
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `registerWallet emits Failure when challenge request fails`() = runTest {
        //When
        whenever(walletRegistrationController.getChallenge()).thenReturn(
            flowOf(
                RegistrationChallengePartialState.Failure(
                    errorCode = "ErrorCode",
                    traceId = "traceId"
                )
            )
        )
        //Then
        subject.registerWallet().test {
            assertEquals(
                WalletRegistrationPartialState.Failure(
                    errorCode = "ErrorCode",
                    traceId = "traceId"
                ), awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `registerWallet emits Success and stores instance id when challenge request succeeds and wallet instance id requests succeeds`() =
        runTest {
            //When
            whenever(walletRegistrationController.getChallenge()).thenReturn(
                flowOf(RegistrationChallengePartialState.Success(mockChallenge))
            )

            whenever(
                walletRegistrationController.getWalletInstanceId(
                    authChallenge = mockChallenge,
                    mdvmToken = mockMdvmToken,
                    mdvmAuthPrvk = mockMdvmAuthPrivateKey,
                )
            ).thenReturn(
                flowOf(
                    WalletInstancePartialState.Success(
                        walletInstanceId = mockWalletInstanceId,
                        walletInstanceRevocationCode = mockWalletInstanceRevocationCode,
                    )
                )
            )

            //Then
            subject.registerWallet().test {
                val result = awaitItem()
                assert(result is WalletRegistrationPartialState.Success)

                verify(walletRegistrationStorageController).saveWalletRegistration(
                    RegistrationData(
                        walletInstanceId = mockWalletInstanceId,
                        walletInstanceRevocationCode = mockWalletInstanceRevocationCode,
                    )
                )

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun `registerWallet emits Failure when MDVM auth context is unavailable`() = runTest {
        // When
        whenever(walletRegistrationController.getChallenge()).thenReturn(
            flowOf(RegistrationChallengePartialState.Success(mockChallenge))
        )
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(
            ApiResult.Failure(MdvmError(MdvmErrorType.MDVM_KEY_NOT_FOUND, serverResponse = null))
        )

        //Then
        subject.registerWallet().test {
            assertEquals(
                WalletRegistrationPartialState.Failure(
                    errorCode = "MDVM_KEY_NOT_FOUND",
                    traceId = null
                ), awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `registerWallet emits Failure when wallet instance request fails`() = runTest {
        // When
        whenever(walletRegistrationController.getChallenge()).thenReturn(
            flowOf(RegistrationChallengePartialState.Success(mockChallenge))
        )

        whenever(
            walletRegistrationController.getWalletInstanceId(
                authChallenge = mockChallenge,
                mdvmToken = mockMdvmToken,
                mdvmAuthPrvk = mockMdvmAuthPrivateKey,
            )
        ).thenReturn(
            /* value = */ flowOf(
                WalletInstancePartialState.Failure(
                    errorCode = "UNKNOWN",
                    traceId = null
                )
            )
        )

        //Then
        subject.registerWallet().test {
            assertEquals(
                WalletRegistrationPartialState.Failure(
                    errorCode = "UNKNOWN",
                    traceId = null
                ), awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
