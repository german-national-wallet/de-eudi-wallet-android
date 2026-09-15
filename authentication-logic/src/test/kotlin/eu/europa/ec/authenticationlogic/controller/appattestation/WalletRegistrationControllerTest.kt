package eu.europa.ec.authenticationlogic.controller.appattestation

import app.cash.turbine.test
import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClient
import org.sprind.wallet.networklogic.walletbackend.api.SigningWalletApiClient
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletErrorResponse
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletChallengeResponse
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletRegisterResponse
import eu.europa.ec.testfeature.mockedExceptionWithNoMessage
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.trace.TracedIOException
import java.net.UnknownHostException
import java.security.PrivateKey

private const val CLIENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"


class WalletRegistrationControllerTest {
    @Mock
    private lateinit var apiClient: WalletApiClient

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var signingWalletApiClient: SigningWalletApiClient

    @Mock
    private lateinit var mdvmAuthPrvk: PrivateKey

    private lateinit var closeable: AutoCloseable

    private val subject: WalletRegistrationControllerImpl by lazy {
        WalletRegistrationControllerImpl(apiClient, logController)
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `getChallenge - emits Success with challenge on API success`() = runTest {
        val response = ApiResult.Success(WalletChallengeResponse(challenge = "test_challenge"))
        whenever(apiClient.getChallenge()).thenReturn(response)

        // Act & Assert
        subject.getChallenge().test {
            assertEquals(RegistrationChallengePartialState.Success("test_challenge"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `getChallenge - emits Failure on API failure`() = runTest {
        //When
        val response = ApiResult.Failure(WalletErrorResponse("errorCode", "traceId"))
        whenever(apiClient.getChallenge()).thenReturn(response)
        // Then
        subject.getChallenge().test {
            assertEquals(RegistrationChallengePartialState.Failure("errorCode", "traceId"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `getChallenge - emits Failure on API Call Exception`() = runTest {
        //When
        whenever(apiClient.getChallenge()).thenThrow(mockedExceptionWithNoMessage)

        // Then
        subject.getChallenge().test {
            assertEquals(RegistrationChallengePartialState.Failure("UNKNOWN", null), awaitItem())
            awaitComplete()
            verify(logController).e(any(), eq(mockedExceptionWithNoMessage))
        }
    }

    @Test
    fun `getChallenge - reports the client trace id when the backend cannot be reached`() =
        runTest {
            val unreachable = TracedIOException(
                traceId = CLIENT_TRACE_ID,
                cause = UnknownHostException("no dns"),
            )
            // thenAnswer, because Mockito rejects a checked exception that the suspend function
            // does not declare.
            whenever(apiClient.getChallenge()).thenAnswer { throw unreachable }

            subject.getChallenge().test {
                assertEquals(
                    RegistrationChallengePartialState.Failure("NO_INTERNET", CLIENT_TRACE_ID),
                    awaitItem()
                )
                awaitComplete()
            }
        }

    @Test
    fun `getWalletInstanceId - reports the client trace id when the backend cannot be reached`() =
        runTest {
            val unreachable = TracedIOException(
                traceId = CLIENT_TRACE_ID,
                cause = UnknownHostException("no dns"),
            )
            whenever(apiClient.signingApi(any())).thenAnswer { throw unreachable }

            subject.getWalletInstanceId(
                authChallenge = "test_challenge",
                mdvmToken = "test_token",
                mdvmAuthPrvk = mdvmAuthPrvk,
            ).test {
                assertEquals(
                    WalletInstancePartialState.Failure("NO_INTERNET", CLIENT_TRACE_ID),
                    awaitItem()
                )
                awaitComplete()
            }
        }

    @Test
    fun `getWalletInstanceId - emits Success with wallet instance id on API success`() = runTest {
        // When
        val authChallenge = "test_challenge"
        val mdvmToken = "test_mdvm_token"
        val walletInstanceId = "test_wallet_instance_id"
        val walletInstanceRevocationCode = "test_wallet_instance_revocation_code"
        val response = WalletRegisterResponse(
            walletInstanceId = walletInstanceId,
            walletInstanceRevocationCode = walletInstanceRevocationCode
        )
        val result = ApiResult.Success(response)

        whenever(apiClient.signingApi(mdvmAuthPrvk)).thenReturn(signingWalletApiClient)
        whenever(signingWalletApiClient.register(authChallenge, mdvmToken)).thenReturn(result)

        // Then
        subject.getWalletInstanceId(authChallenge, mdvmToken, mdvmAuthPrvk).test {
            assertEquals(
                WalletInstancePartialState.Success(
                    walletInstanceId = walletInstanceId,
                    walletInstanceRevocationCode = walletInstanceRevocationCode,
                ), awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWalletInstanceId - emits Failure on API Failure`() = runTest {
        // When
        val authChallenge = "test_challenge"
        val mdvmToken = "test_mdvm_token"

        val response = WalletErrorResponse("errorCode", "traceId")
        val result = ApiResult.Failure(response)

        whenever(apiClient.signingApi(mdvmAuthPrvk)).thenReturn(signingWalletApiClient)
        whenever(signingWalletApiClient.register(authChallenge, mdvmToken)).thenReturn(result)

        // Then
        subject.getWalletInstanceId(authChallenge, mdvmToken, mdvmAuthPrvk).test {
            assertEquals(WalletInstancePartialState.Failure("errorCode", "traceId"), awaitItem())
            cancelAndIgnoreRemainingEvents()

        }
    }

    @Test
    fun `getWalletInstanceId - emits Failure on API Call Exception`() = runTest {
        // When
        val authChallenge = "test_challenge"
        val mdvmToken = "test_mdvm_token"

        whenever(apiClient.signingApi(mdvmAuthPrvk)).thenReturn(signingWalletApiClient)
        whenever(signingWalletApiClient.register(authChallenge, mdvmToken)).thenThrow(mockedExceptionWithNoMessage)

        // Then
        subject.getWalletInstanceId(authChallenge, mdvmToken, mdvmAuthPrvk).test {
            assertEquals(WalletInstancePartialState.Failure("UNKNOWN", null), awaitItem())
            cancelAndIgnoreRemainingEvents()

            verify(logController).e(any(), eq(mockedExceptionWithNoMessage))
        }
    }
}
