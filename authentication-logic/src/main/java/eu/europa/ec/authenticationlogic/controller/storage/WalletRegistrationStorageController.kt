package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoder
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import org.sprind.wallet.businesslogic.controller.crypto.CryptoError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class RegistrationData(
    val walletInstanceId: String,
    val walletInstanceRevocationCode: String,
)

interface WalletRegistrationStorageController {
    /** Succeeds with null when nothing is stored; fails when the stored data cannot be read. */
    suspend fun getWalletRegistration(): Result<RegistrationData?>
    suspend fun saveWalletRegistration(data: RegistrationData): Result<Unit>
}

class WalletRegistrationStorageControllerImpl(
    private val storageConfig: StorageConfig,
    private val cryptoController: CryptoController,
    private val base64EncoderDecoder: Base64EncoderDecoder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WalletRegistrationStorageController {
    override suspend fun getWalletRegistration(): Result<RegistrationData?> =
        withContext(ioDispatcher) {
            val walletRegistration =
                storageConfig.walletRegistrationStorageProvider.getWalletRegistration()
                    ?: return@withContext Result.success(null)

            val ivBytes = walletRegistration.ivString.decodeFromPemBase64String()
            val encryptedBytes = walletRegistration.encryptedString.decodeFromPemBase64String()
            if (ivBytes == null || encryptedBytes == null) {
                return@withContext Result.failure(CryptoError.InvalidParameters())
            }

            cryptoController.getWalletRegistrationCipher(ivBytes = ivBytes)
                .mapCatching { cryptoController.encryptDecryptData(it, encryptedBytes).getOrThrow() }
                .mapCatching { Json.decodeFromString<RegistrationData>(String(it, Charsets.UTF_8)) }
        }

    override suspend fun saveWalletRegistration(data: RegistrationData): Result<Unit> =
        withContext(ioDispatcher) {
            cryptoController.getWalletRegistrationCipher(encrypt = true)
                .mapCatching { cipher ->
                    val encrypted = cryptoController.encryptDecryptData(
                        cipher = cipher,
                        byteArray = Json.encodeToString(data).toByteArray(StandardCharsets.UTF_8)
                    ).getOrThrow()
                    WalletRegistration(
                        encryptedString = checkNotNull(encrypted.encodeToPemBase64String()),
                        ivString = checkNotNull(cipher.iv.encodeToPemBase64String()),
                    )
                }
                .mapCatching { storageConfig.walletRegistrationStorageProvider.storeWalletRegistration(it) }
        }

    private fun ByteArray.encodeToPemBase64String(): String? =
        base64EncoderDecoder.encodeToPemBase64String(this)

    private fun String.decodeFromPemBase64String(): ByteArray? =
        base64EncoderDecoder.decodeFromPemBase64String(this)
}
