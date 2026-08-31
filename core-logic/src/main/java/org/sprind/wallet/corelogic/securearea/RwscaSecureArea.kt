/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.corelogic.securearea

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.securearea.RwscaKeyInfo
import eu.europa.ec.corelogic.securearea.exception.RwscaServerException
import kotlinx.io.bytestring.ByteString
import okio.ByteString.Companion.toByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.prompt.Reason
import org.multipaz.securearea.BatchCreateKeyResult
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.KeyLockedException
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.decodeBase64EcPublicKey
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.authenticationlogic.provider.RwscaRegistrationsProvider
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import java.util.Base64

class RwscaSecureArea(
    private val rwscaController: RwscaController,
    private val registrationsProvider: RwscaRegistrationsProvider,
    private val pinSessionHolder: RwscaPinSessionHolder,
    private val logController: LogController,
    private val storage: Storage,
    override val supportedAlgorithms: List<Algorithm>,
) : SecureArea {

    override val displayName: String = "rWSCA Secure Area"
    override val identifier: String = IDENTIFIER

    companion object {
        const val KEY_CURVE = "curve"
        const val KEY_ENCODED_PUBLIC_KEY = "encodedPublicKey"
        const val KEY_WRAPPED_PRIVATE_KEY = "wrappedPrivateKey"
        const val KEY_KEY_ALGORITHM = "keyAlgorithm"
        const val KEY_WALLET_TRUST_EVIDENCE = "walletTrustEvidence"
        const val KEY_WALLET_TRUST_EVIDENCE_NONCE = "walletTrustEvidenceNonce"

        const val IDENTIFIER = "RwscaSecureArea"
        const val RWSCA_METADATA_TABLE_NAME = "rwsca_metadata"

        val storageTableSpec = StorageTableSpec(
            RWSCA_METADATA_TABLE_NAME,
            supportExpiration = true,
            supportPartitions = true,
            schemaVersion = 0,
        )
    }

    override suspend fun batchCreateKey(
        numKeys: Int,
        createKeySettings: CreateKeySettings,
    ): BatchCreateKeyResult {
        logController.d(identifier) { "batchCreateKey for $numKeys keys" }

        val algorithm = createKeySettings.algorithm
        val curve = algorithm.curve
            ?: throw IllegalArgumentException("CreateKeySettings must specify a curve for the algorithm")

        val registrations = when (val result = registrationsProvider.getRegistrations()) {
            is ApiResult.Failure -> throw RwscaServerException(
                result.error.code, result.error.traceId
            )
            is ApiResult.Success -> result.response
        }

        val ppCNonce = (createKeySettings as? RwscaCreateKeySettings)?.ppCNonce?.trim()
            ?: throw IllegalArgumentException(
                "RwscaSecureArea requires RwscaCreateKeySettings with issuer c_nonce (pp_c_nonce)"
            )
        require(ppCNonce.isNotBlank()) { "RwscaSecureArea requires a non-empty pp_c_nonce" }

        // PID issuance architecture steps 050-055: the PID Provider c_nonce is used by
        // rWSCA Create Key to produce WTE for the generated device binding keys.
        val createdKeys = when (val result = rwscaController.createKeys(
            mdvmRegistration = registrations.mdvmRegistration,
            rwscaRegistration = registrations.rwscaRegistration,
            numberOfKeys = numKeys,
            ppCNonce = ppCNonce,
        )) {
            is ApiResult.Failure -> throw RwscaServerException(
                result.error.code, result.error.traceId
            )
            is ApiResult.Success -> result.response
        }

        // There is no useful recovery path for the caller if no keys were returned.
        if (createdKeys.wrappedKeys.isEmpty()) {
            throw IllegalStateException("rWSCA returned success but with an empty key list")
        }

        val storageTable = storage.getTable(spec = storageTableSpec)

        // Map each remote key to a local KeyInfo object
        val keyInfoList = createdKeys.wrappedKeys.map { wrappedKey ->
            // A new alias is generated locally for each key
            val alias = storageTable.insert(
                key = null,
                partitionId = identifier,
                data = ByteString(), // Placeholder data, will be updated by saveKey
            )

            // We're storing the (wrapped) key itself here. The backend has a separate key
            // with which it can decrypt (unwrap) the underlying key, but the wrapped key
            // is only stored on the client.
            saveKey(
                alias = alias,
                algorithm = algorithm,
                curve = curve,
                encodedPublicKey = wrappedKey.encodedPublicKey,
                wrappedPrivateKey = wrappedKey.wrappedPrivateKey,
                walletTrustEvidence = createdKeys.walletTrustEvidence,
                walletTrustEvidenceNonce = ppCNonce,
                storageTable = storageTable,
            )
            val ecPublicKey = wrappedKey.toEcPublicKey()

            // Return the fully formed KeyInfo object for this key
            RwscaKeyInfo(
                alias = alias,
                algorithm = algorithm,
                publicKey = ecPublicKey,
                attestation = KeyAttestation(ecPublicKey, null),
                walletTrustEvidence = createdKeys.walletTrustEvidence,
                walletTrustEvidenceNonce = ppCNonce,
            )
        }

        logController.d(identifier) { "batchCreateKey created ${keyInfoList.size} keys" }
        return BatchCreateKeyResult(keyInfoList, null)
    }

    override suspend fun createKey(alias: String?, createKeySettings: CreateKeySettings): KeyInfo {
        if (alias != null) {
            throw IllegalArgumentException("App-chosen alias is not supported. Alias must be null.")
        }
        return batchCreateKey(1, createKeySettings).keyInfos.firstOrNull()
            ?: throw IllegalStateException("Failed to create key from rWSCA.")
    }

    override suspend fun deleteKey(alias: String) {
        val storageTable = storage.getTable(spec = storageTableSpec)
        // remove from local storage only
        storageTable.delete(key = alias, partitionId = identifier)
        // Note: Unlike rWSCD / RemoteSecureStorage, the (wrapped) key is stored on the client,
        // so there is no server-side key that we'd need to tell the rWSCA backend to delete.
    }

    override suspend fun sign(
        alias: String,
        dataToSign: ByteArray,
        unlockReason: Reason,
    ): EcSignature {
        val storageTable = storage.getTable(spec = storageTableSpec)
        val metadata = loadKeyMetadata(alias, storageTable)
        val curveInt = metadata[KEY_CURVE].asNumber.toInt()
        val curve = EcCurve.fromInt(curveInt)
        val wrappedPrivateKey = metadata[KEY_WRAPPED_PRIVATE_KEY].asTstr

        val registrations = when (val result = registrationsProvider.getRegistrations()) {
            is ApiResult.Failure -> throw RwscaServerException(
                result.error.code, result.error.traceId
            )
            is ApiResult.Success -> result.response
        }

        val pinSession = pinSessionHolder.get()
            ?: throw KeyLockedException("No rWSCA PIN session available")

        val keyBindingDataHash = dataToSign.toByteString().sha256().base64()

        val signature = when (val result = rwscaController.signData(
            mdvmRegistration = registrations.mdvmRegistration,
            rwscaRegistration = registrations.rwscaRegistration,
            pinSession = pinSession,
            wrappedPrivateKey = wrappedPrivateKey,
            keyBindingDataHash = keyBindingDataHash,
        )) {
            is ApiResult.Failure -> {
                val error = result.error
                if (error is RwscaError.FromRwsca &&
                    error.type == RwscaErrorType.PIN_SESSION_TOKEN_VERIFICATION_FAILURE) {
                    pinSessionHolder.clear()
                    throw KeyLockedException("rWSCA PIN session expired or invalid")
                }
                throw RwscaServerException(error.code, error.traceId)
            }
            is ApiResult.Success -> result.response
        }

        logController.d(identifier) { "sign: received signature" }

        val derEncodedSignature = Base64.getDecoder().decode(signature.keyBindingSignature)
        return EcSignature.fromDerEncoded(curve.bitSize, derEncodedSignature)
    }

    override suspend fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        unlockReason: Reason,
    ): ByteArray {
        // TODO: Do we need to do anything here? RemoteSecureArea implemented keyAgreement but
        // returned a bogus value because RemoteWscdController.checkKeyAgreement returns ""
        throw UnsupportedOperationException("Key agreement is not supported by rWSCA")
    }

    override suspend fun getKeyInfo(alias: String): KeyInfo {
        logController.d(identifier) { "getKeyInfo for $alias" }
        val storageTable = storage.getTable(spec = storageTableSpec)
        val metadata = loadKeyMetadata(alias, storageTable)
        val encodedPublicKey = metadata[KEY_ENCODED_PUBLIC_KEY].asTstr
        val walletTrustEvidence = metadata.getOrNull(KEY_WALLET_TRUST_EVIDENCE)?.asTstr
        val walletTrustEvidenceNonce = metadata.getOrNull(KEY_WALLET_TRUST_EVIDENCE_NONCE)?.asTstr
        val ecPublicKey = decodeBase64EcPublicKey(encodedPublicKey)
        val algorithmName = metadata.getOrNull(KEY_KEY_ALGORITHM)?.asTstr ?: "ES256"
        val algorithm = when (algorithmName) {
            "ES256" -> Algorithm.ESP256
            "ES384" -> Algorithm.ESP384
            "ES512" -> Algorithm.ESP512
            else -> Algorithm.ESP256
        }
        return RwscaKeyInfo(
            alias = alias,
            publicKey = ecPublicKey,
            attestation = KeyAttestation(ecPublicKey, certChain = null),
            algorithm = algorithm,
            walletTrustEvidence = walletTrustEvidence,
            walletTrustEvidenceNonce = walletTrustEvidenceNonce,
        )
    }

    override suspend fun getKeyInvalidated(alias: String): Boolean = false

    private suspend fun saveKey(
        alias: String,
        algorithm: Algorithm,
        curve: EcCurve,
        encodedPublicKey: String,
        wrappedPrivateKey: String,
        walletTrustEvidence: String,
        walletTrustEvidenceNonce: String,
        storageTable: StorageTable,
    ) {
        val cborMap = CborMap.builder().also {
            it.put(KEY_CURVE, curve.coseCurveIdentifier)
            it.put(KEY_ENCODED_PUBLIC_KEY, encodedPublicKey)
            it.put(KEY_WRAPPED_PRIVATE_KEY, wrappedPrivateKey)
            it.put(KEY_WALLET_TRUST_EVIDENCE, walletTrustEvidence)
            it.put(KEY_WALLET_TRUST_EVIDENCE_NONCE, walletTrustEvidenceNonce)
            it.put(KEY_KEY_ALGORITHM, requireNotNull(algorithm.joseAlgorithmIdentifier) {
                "Algorithm $algorithm does not have a JOSE identifier"
            })
        }
        val encodedCbor = Cbor.encode(cborMap.end().build())
        logController.d(identifier) { "saveKey alias $alias" }
        storageTable.update(key = alias, partitionId = identifier, data = ByteString(encodedCbor))
    }

    /**
     * @throws IllegalArgumentException if no key with the given alias exists.
     */
    @Throws(IllegalArgumentException::class)
    private suspend fun loadKeyMetadata(alias: String, storageTable: StorageTable): DataItem {
        val data = storageTable.get(key = alias, partitionId = identifier)
            ?: throw IllegalArgumentException("No key info with given alias $alias")
        return Cbor.decode(data.toByteArray())
    }
}
