package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoder
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets
import java.util.Base64
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
        ).thenReturn(mockCipher)
        whenever(cryptoController.encryptDecryptData(mockCipher, encryptedData)).thenReturn(
            decryptedData
        )
        val result = subject.getWalletRegistration()

        // Then
        assertEquals(data, result)
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

        whenever(cryptoController.getWalletRegistrationCipher(encrypt = true)).thenReturn(mockCipher)
        whenever(
            cryptoController.encryptDecryptData(
                mockCipher,
                decryptedData
            )
        ).thenReturn(encryptedData)


        subject.saveWalletRegistration(data)

        // Then
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
}