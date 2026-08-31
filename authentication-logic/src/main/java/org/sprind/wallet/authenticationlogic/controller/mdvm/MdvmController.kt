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

package org.sprind.wallet.authenticationlogic.controller.mdvm

import android.os.Build
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.config.ConfigLogic
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.MdvmResult
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.model.map
import org.sprind.wallet.networklogic.common.model.mapError
import org.sprind.wallet.networklogic.mdvm.api.MdvmApiClient
import org.sprind.wallet.networklogic.mdvm.api.MdvmSkipIntegrityChecksHeaderValue
import org.sprind.wallet.networklogic.mdvm.model.MdvmApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRegisterRequest
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRenewalRequest
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmChallengeResponse

interface MdvmController {
    suspend fun register(): MdvmResult<MdvmRegistration>

    /**
     * @param mdvmWiId The mdvm_wi_id from a previously obtained and persisted [MdvmRegistration].
     * @param wiMdvmAuthKeysAlias The KeyStore alias of the wi_mdvm_auth key pair that was
     *   previously [register]ed, as stored in [MdvmRegistration.wi_mdvm_auth_keys_alias].
     */
    suspend fun renewal(mdvmWiId: String, wiMdvmAuthKeysAlias: String): MdvmResult<MdvmRegistration>
}

internal class MdvmControllerImpl(
    private val mdvmApiClient: MdvmApiClient,
    private val mdvmKeyManager: MdvmKeyManager,
    private val configLogic: ConfigLogic,
) : MdvmController {

    private data class ChallengeContext(
        val authChallenge: String,
        val challengeNonce: ByteString,
        val skipIntegrityChecks: MdvmSkipIntegrityChecksHeaderValue,
    )

    /**
     * Fetches a challenge for the MDVM register/renewal flow.
     */
    private suspend fun prepareChallenge(): MdvmResult<ChallengeContext> {
        val challengeResponse: MdvmApiResult<MdvmChallengeResponse> = mdvmApiClient.challenge()
        if (challengeResponse is ApiResult.Failure) {
            return ApiResult.Failure(challengeResponse.error.toMdvmError())
        }
        // This is a SignedJWT (serialized to String) with claims "iat" and "nonce".
        val mdvmAuthChallengeJwt = (challengeResponse as ApiResult.Success).response.mdvm_auth_challenge
        val challengeNonce = mdvmAuthChallengeJwt.encodeUtf8().sha256()

        val skipIntegrityChecks = MdvmSkipIntegrityChecksHeaderValue.fromValue(
            BuildConfig.MDVM_SKIP_INTEGRITY_CHECKS_HEADER_VALUE
        )
        @Suppress("KotlinConstantConditions")
        return ApiResult.Success(ChallengeContext(
            authChallenge = mdvmAuthChallengeJwt,
            challengeNonce = challengeNonce,
            skipIntegrityChecks = skipIntegrityChecks,
        ))
    }

    override suspend fun register(): MdvmResult<MdvmRegistration> {
        val ctx = when (val result = prepareChallenge()) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val wiMdvmAuthKeysInfo = mdvmKeyManager.createAuthKeys(ctx.challengeNonce)
        val signingApiClient = mdvmApiClient.signingApi(
            wiMdvmAuthPrvk = wiMdvmAuthKeysInfo.keyPair.private,
        )
        return signingApiClient.register(
            request = MdvmRegisterRequest(
                wi_mdvm_auth_pubk = wiMdvmAuthKeysInfo.keyPair.public.encoded.toByteString().base64(),
                wi_device_class = getDeviceClass(),
                wi_android_key_attestation = getWiAndroidKeyAttestation(wiMdvmAuthKeysInfo),
                pap_playintegrity_attestation = "",
            ),
            authChallenge = ctx.authChallenge,
            skipIntegrityChecks = ctx.skipIntegrityChecks,
        ).map { response ->
            MdvmRegistration(
                mdvm_wi_id = response.mdvm_wi_id,
                mdvm_token = response.mdvm_token,
                wi_mdvm_auth_keys_alias = wiMdvmAuthKeysInfo.keystoreAlias,
            )
        }.mapError { it.toMdvmError() }
    }

    override suspend fun renewal(
        mdvmWiId: String,
        wiMdvmAuthKeysAlias: String,
    ): MdvmResult<MdvmRegistration> {
        val ctx = when (val result = prepareChallenge()) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val wiMdvmAuthKeys = mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias)
            ?:
            // This shouldn't happen unless we start protecting the key with biometrics and
            // the enrolled biometrics (fingerprints) then change. If and when we do this,
            // we'd have to catch this case and re-do the registration from scratch.
            return ApiResult.Failure(
                MdvmError(MdvmErrorType.MDVM_KEY_NOT_FOUND, serverResponse = null)
            )
        val wiMdvmReattestKeysInfo = mdvmKeyManager.createReattestKeys(ctx.challengeNonce)
        val doubleSigningApiClient = mdvmApiClient.doubleSigningApi(
            wiMdvmAuthPrvk = wiMdvmAuthKeys.private,
            wiMdvmReattestPrvk = wiMdvmReattestKeysInfo.keyPair.private
        )
        try {
            return doubleSigningApiClient.renewal(
                request = MdvmRenewalRequest(
                    wi_device_class = getDeviceClass(),
                    wi_android_key_attestation = getWiAndroidKeyAttestation(wiMdvmReattestKeysInfo),
                    pap_playintegrity_attestation = "",
                ),
                mdvmWiId = mdvmWiId,
                authChallenge = ctx.authChallenge,
                skipIntegrityChecks = ctx.skipIntegrityChecks,
            )
                .map { response ->
                    MdvmRegistration(
                        mdvm_wi_id = mdvmWiId,
                        mdvm_token = response.mdvm_token,
                        wi_mdvm_auth_keys_alias = wiMdvmAuthKeysAlias,
                    )
                }
                .mapError { it.toMdvmError() }
        } finally {
            mdvmKeyManager.deleteReattestKeys()
        }
    }

    private fun getWiAndroidKeyAttestation(keyMaterial: MdvmKeyInfo): List<String> =
        keyMaterial.attestationChain.map { it.encoded.toByteString().base64() }

    private fun getDeviceClass(): Map<String, String> = mapOf(
        "device" to Build.DEVICE,
        "versionRelease" to Build.VERSION.RELEASE,
        "versionPatch" to Build.VERSION.SECURITY_PATCH,
        "model" to Build.MODEL,
        "product" to Build.PRODUCT,
        "hardware" to Build.HARDWARE,
        "id" to Build.ID,
    )
}
