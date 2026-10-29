package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoder
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import org.sprind.wallet.businesslogic.controller.crypto.CryptoError
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

private const val mockWalletInstanceId = "mockWalletInstanceId"
private const val mockWalletInstanceRevocationCode = "mockWalletInstanceRevocationCode"

class WalletRegistrationStorageControllerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var storageConfig: StorageConfig

    @Mock
    private lateinit var cryptoController: CryptoController

    @Mock
    private lateinit var walletRegistrationStorageProvider: WalletRegistrationStorageProvider

    @Mock
    private lateinit var base64EncoderDecoder: Base64EncoderDecoder

    private lateinit var closeable: AutoCloseable

    private val iv = "testIvString".toByteArray(StandardCharsets.UTF_8)
    private val encryptedData = "testEncryptedData".toByteArray(StandardCharsets.UTF_8)

    private val data = RegistrationData(
        walletInstanceId = mockWalletInstanceId,
        walletInstanceRevocationCode = mockWalletInstanceRevocationCode,
    )
    private val decryptedData = Json.encodeToString(data).toByteArray(StandardCharsets.UTF_8)

    private val encryptedString = Base64.getEncoder().encodeToString(encryptedData)
    private val ivString = Base64.getEncoder().encodeToString(iv)

    private val subject: WalletRegistrationStorageController by lazy {
        WalletRegistrationStorageControllerImpl(
            storageConfig,
            cryptoController,
            base64EncoderDecoder,
            ioDispatcher = coroutineRule.testDispatcher
        )
    }

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(storageConfig.walletRegistrationStorageProvider).thenReturn(
            walletRegistrationStorageProvider
        )

        whenever(base64EncoderDecoder.encodeToPemBase64String(encryptedData)).thenReturn(
            encryptedString
        )

        whenever(base64EncoderDecoder.encodeToPemBase64String(iv)).thenReturn(
            ivString
        )

        whenever(base64EncoderDecoder.decodeFromPemBase64String(encryptedString)).thenReturn(
            encryptedData
        )

        whenever(base64EncoderDecoder.decodeFromPemBase64String(ivString)).thenReturn(
            iv
        )
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `getWalletRegistration returns decrypted data`() = coroutineRule.runTest {
        // Given
        val walletRegistration = WalletRegistration(
            encryptedString = encryptedString,
            ivString = ivString
        )

        val mockCipher = mock<Cipher>()

        whenever(walletRegistrationStorageProvider.getWalletRegistration()).thenReturn(
            walletRegistration
        )

        whenever(
            cryptoController.getWalletRegistrationCipher(
                ivBytes = base64EncoderDecoder.decodeFromPemBase64String(
                    ivString
                )
            )
        ).thenReturn(Result.success(mockCipher))
        whenever(cryptoController.encryptDecryptData(mockCipher, encryptedData)).thenReturn(
            Result.success(decryptedData)
        )
        val result = subject.getWalletRegistration()

        // Then
        assertEquals(data, result.getOrThrow())
        verify(walletRegistrationStorageProvider).getWalletRegistration()
        verify(cryptoController).getWalletRegistrationCipher(
            ivBytes = base64EncoderDecoder.decodeFromPemBase64String(
                walletRegistration.ivString
            )
        )
        verify(cryptoController).encryptDecryptData(mockCipher, encryptedData)
    }

    @Test
    fun `saveWalletRegistration encrypts and stores data`() = coroutineRule.runTest {
        // Given
        val mockCipher = mock<Cipher>()
        whenever(mockCipher.iv).thenReturn(iv)

        whenever(cryptoController.getWalletRegistrationCipher(encrypt = true))
            .thenReturn(Result.success(mockCipher))
        whenever(
            cryptoController.encryptDecryptData(
                mockCipher,
                decryptedData
            )
        ).thenReturn(Result.success(encryptedData))


        val result = subject.saveWalletRegistration(data)

        // Then
        assertTrue(result.isSuccess)
        verify(cryptoController).getWalletRegistrationCipher(encrypt = true)
        verify(cryptoController).encryptDecryptData(
            mockCipher,
            decryptedData
        )
        verify(walletRegistrationStorageProvider).storeWalletRegistration(
            WalletRegistration(
                encryptedString = base64EncoderDecoder.encodeToPemBase64String(encryptedData)!!,
                ivString = base64EncoderDecoder.encodeToPemBase64String(iv)!!
            )
        )
    }

    @Test
    fun `getWalletRegistration fails when the stored data cannot be decrypted`() = coroutineRule.runTest {
        // Given
        val mockCipher = mock<Cipher>()
        whenever(walletRegistrationStorageProvider.getWalletRegistration()).thenReturn(
            WalletRegistration(encryptedString = encryptedString, ivString = ivString)
        )
        whenever(cryptoController.getWalletRegistrationCipher(ivBytes = iv))
            .thenReturn(Result.success(mockCipher))
        whenever(cryptoController.encryptDecryptData(mockCipher, encryptedData))
            .thenReturn(Result.failure(CryptoError.AuthenticationFailed(AEADBadTagException())))

        // Then
        val result = subject.getWalletRegistration()

        assertTrue(result.exceptionOrNull() is CryptoError.AuthenticationFailed)
    }

    @Test
    fun `getWalletRegistration succeeds with null when nothing is stored`() = coroutineRule.runTest {
        whenever(walletRegistrationStorageProvider.getWalletRegistration()).thenReturn(null)

        assertNull(subject.getWalletRegistration().getOrThrow())
    }

    @Test
    fun `saveWalletRegistration stores nothing when encryption fails`() = coroutineRule.runTest {
        // Given
        whenever(cryptoController.getWalletRegistrationCipher(encrypt = true))
            .thenReturn(Result.failure(CryptoError.KeyNotFound()))

        // Then
        val result = subject.saveWalletRegistration(data)

        assertTrue(result.exceptionOrNull() is CryptoError.KeyNotFound)
        verify(walletRegistrationStorageProvider, never()).storeWalletRegistration(any())
    }

    @Test
    fun `saveWalletRegistration fails instead of throwing when storing fails`() = coroutineRule.runTest {
        // Given
        val mockCipher = mock<Cipher>()
        whenever(mockCipher.iv).thenReturn(iv)
        whenever(cryptoController.getWalletRegistrationCipher(encrypt = true))
            .thenReturn(Result.success(mockCipher))
        whenever(cryptoController.encryptDecryptData(mockCipher, decryptedData))
            .thenReturn(Result.success(encryptedData))
        whenever(walletRegistrationStorageProvider.storeWalletRegistration(any()))
            .thenThrow(IllegalStateException("disk full"))

        // Then
        val result = subject.saveWalletRegistration(data)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
