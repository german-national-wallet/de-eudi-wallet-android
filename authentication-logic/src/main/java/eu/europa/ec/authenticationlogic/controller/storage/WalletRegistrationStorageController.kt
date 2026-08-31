package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoder
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class RegistrationData(
    val walletInstanceId: String,
    val walletInstanceRevocationCode: String,
)

interface WalletRegistrationStorageController {
    suspend fun getWalletRegistration(): RegistrationData?
    suspend fun saveWalletRegistration(data: RegistrationData)
}

class WalletRegistrationStorageControllerImpl(
    private val storageConfig: StorageConfig,
    private val cryptoController: CryptoController,
    private val base64EncoderDecoder: Base64EncoderDecoder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WalletRegistrationStorageController {
    override suspend fun getWalletRegistration(): RegistrationData? =
        withContext(ioDispatcher) {
            val walletRegistration =
                storageConfig.walletRegistrationStorageProvider.getWalletRegistration() ?: return@withContext null

            val cipher = cryptoController.getWalletRegistrationCipher(
                ivBytes = walletRegistration.ivString.decodeFromPemBase64String() ?: ByteArray(0)
            )

            val decryptedData = cryptoController.encryptDecryptData(
                cipher = cipher,
                byteArray = walletRegistration.encryptedString
                    .decodeFromPemBase64String() ?: ByteArray(0)
            )
            val serializedString = String(decryptedData, Charsets.UTF_8)
            try {
                val deserializedData = Json.decodeFromString<RegistrationData>(serializedString)
                return@withContext deserializedData
            } catch (e: Exception) {
                if (e is SerializationException || e is IllegalArgumentException) {
                    return@withContext null
                } else {
                    throw e
                }
            }
        }

    override suspend fun saveWalletRegistration(data: RegistrationData) {
        withContext(ioDispatcher) {
            val cipher = cryptoController.getWalletRegistrationCipher(encrypt = true)
            storageConfig.walletRegistrationStorageProvider.storeWalletRegistration(
                WalletRegistration(
                    encryptedString = cryptoController.encryptDecryptData(
                        cipher = cipher,
                        byteArray = Json.encodeToString(data).toByteArray(StandardCharsets.UTF_8)
                    ).encodeToPemBase64String().orEmpty(),
                    ivString = cipher?.iv?.encodeToPemBase64String().orEmpty()
                )
            )
        }
    }

    private fun ByteArray.encodeToPemBase64String(): String? =
        base64EncoderDecoder.encodeToPemBase64String(this)

    private fun String.decodeFromPemBase64String(): ByteArray? =
        base64EncoderDecoder.decodeFromPemBase64String(this)
}