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

package org.sprind.wallet.authenticationlogic.controller.rwsca

import okio.ByteString.Companion.toByteString
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.model.map
import org.sprind.wallet.networklogic.common.model.mapError
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaCreatedKeys
import org.sprind.wallet.authenticationlogic.model.RwscaError.FromMdvm
import org.sprind.wallet.authenticationlogic.model.RwscaResult
import org.sprind.wallet.authenticationlogic.model.RwscaSignature
import org.sprind.wallet.authenticationlogic.model.toRwscaError
import org.sprind.wallet.authenticationlogic.model.RwscaWrappedKey
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.rwsca.api.RwscaApiClient
import org.sprind.wallet.networklogic.rwsca.api.SigningRwscaApiClient
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaCreateKeysRequest
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaInitializePinAndStartPinSessionRequest
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaSignDataRequest
import java.security.KeyPair

interface RwscaController {
    enum class StartPinSessionMode {
        /**
         * The first time we are starting a pin session for this PIN for this rWSCA registration.
         * This could be on first use, or if the rWSCA registration was deleted and recreated,
         * or if the PIN was changed (the latter two options do not yet exist as of March 2026.
         */
        InitialPinSession,

        /**
         * A subsequent time we are starting a pin session for this PIN for this rWSCA
         * registration.
         */
        SubsequentPinSession,
    }

    suspend fun createKeys(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        numberOfKeys: Int,
        ppCNonce: String,
    ): RwscaResult<RwscaCreatedKeys>

    suspend fun deleteAccount(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
    ): RwscaResult<Unit>

    suspend fun register(mdvmRegistration: MdvmRegistration): RwscaResult<RwscaRegistration>

    suspend fun signData(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        pinSession: RwscaPinSession,
        wrappedPrivateKey: String,
        keyBindingDataHash: String,
    ): RwscaResult<RwscaSignature>

    suspend fun startPinSession(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        withPinKeys: WithPinKeys,
        mode: StartPinSessionMode,
    ): RwscaResult<RwscaPinSession>

    /**
     * The ability to run a sensitive block of code that requires the
     * wi_rwsca_pin_* key pair. Through this interface, the knowledge
     * how to construct and manage the lifetime of those keys can be
     * decoupled (live outside [RwscaController]) from the logic inside
     * [RwscaController] that uses the keys.
     */
    interface WithPinKeys {
        /**
         * Runs the given block, passing it wiRwscaPinKeys that are generated
         * on-the-fly and whose lifetime ends when the block exits.
         */
        suspend fun <T> execute(
            block: suspend (wiRwscaPinKeys: KeyPair) -> T
        ): T
    }
}

internal class RwscaControllerImpl(
    private val rwscaApiClient: RwscaApiClient,
    private val mdvmKeyManager: MdvmKeyManager,
): RwscaController {
    private data class ChallengeContext(
        val rwscaAuthChallenge: String,
        val wiMdvmAuthKeys: KeyPair,
    )

    private suspend fun prepareChallenge(mdvmRegistration: MdvmRegistration): RwscaResult<ChallengeContext> {
        val challengeResponse = rwscaApiClient.challenge()
        val rwscaAuthChallenge = when (challengeResponse) {
            is ApiResult.Failure -> return ApiResult.Failure(challengeResponse.error.toRwscaError())
            is ApiResult.Success -> challengeResponse.response.rwsca_auth_challenge
        }
        val existingWiMdvmAuthKeysAlias = mdvmRegistration.wi_mdvm_auth_keys_alias

        val wiMdvmAuthKeys = mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias = existingWiMdvmAuthKeysAlias)
            ?:
            return ApiResult.Failure(
                FromMdvm(MdvmError(MdvmErrorType.MDVM_KEY_NOT_FOUND, serverResponse = null))
            )
        return ApiResult.Success(
            ChallengeContext(
                rwscaAuthChallenge = rwscaAuthChallenge,
                wiMdvmAuthKeys = wiMdvmAuthKeys,
            )
        )
    }

    private data class SigningApiContext(
        val signingApi: SigningRwscaApiClient,
        val rwscaAuthChallenge: String,
        val wiMdvmAuthKeys: KeyPair,
    )

    private suspend fun prepareSigningApi(mdvmRegistration: MdvmRegistration): RwscaResult<SigningApiContext> {
        val ctx = when(val result = prepareChallenge(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val signingApi = rwscaApiClient.signingApi(
            mdvmAuthPrvk = ctx.wiMdvmAuthKeys.private,
        )
        return ApiResult.Success(
            SigningApiContext(
                signingApi = signingApi,
                rwscaAuthChallenge = ctx.rwscaAuthChallenge,
                wiMdvmAuthKeys = ctx.wiMdvmAuthKeys,
            )
        )
    }

    override suspend fun createKeys(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        numberOfKeys: Int,
        ppCNonce: String,
    ): RwscaResult<RwscaCreatedKeys> {
        val ctx = when (val result = prepareSigningApi(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val request = RwscaCreateKeysRequest(
            number_of_keys = numberOfKeys,
            pp_c_nonce = ppCNonce,
        )
        return ctx.signingApi.createKeys(
            request = request,
            authChallenge = ctx.rwscaAuthChallenge,
            mdvmToken = mdvmRegistration.mdvm_token,
            rwscaAccountId = rwscaRegistration.rwsca_account_id,
        ).map {
            RwscaCreatedKeys(
                wrappedKeys = it.rwsca_wi_keys.map { key ->
                    RwscaWrappedKey(
                        encodedPublicKey = key.rwscd_wi_pubk,
                        wrappedPrivateKey = key.rwsca_wi_wrapped_prvk,
                    )
                },
                walletTrustEvidence = it.rwsca_wte,
            )
        }.mapError { it.toRwscaError() }
    }

    override suspend fun deleteAccount(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration
    ): RwscaResult<Unit> {
        val ctx = when (val result = prepareSigningApi(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        return ctx.signingApi.deleteAccount(
            authChallenge = ctx.rwscaAuthChallenge,
            mdvmToken = mdvmRegistration.mdvm_token,
            rwscaAccountId = rwscaRegistration.rwsca_account_id,
        ).mapError { it.toRwscaError() }
    }

    override suspend fun register(mdvmRegistration: MdvmRegistration): RwscaResult<RwscaRegistration> {
        val ctx = when (val result = prepareSigningApi(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        return ctx.signingApi.register(
            ctx.rwscaAuthChallenge,
            mdvmRegistration.mdvm_token
        ).map {
            RwscaRegistration(
                rwsca_account_id = it.rwsca_account_id,
            )
        }.mapError { it.toRwscaError() }
    }

    override suspend fun signData(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        pinSession: RwscaPinSession,
        wrappedPrivateKey: String,
        keyBindingDataHash: String,
    ): RwscaResult<RwscaSignature> {
        val ctx = when (val result = prepareSigningApi(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val request = RwscaSignDataRequest(
            rwsca_wi_wrapped_prvk = wrappedPrivateKey,
            wi_key_binding_data_hash = keyBindingDataHash,
        )
        return ctx.signingApi.signData(
            request = request,
            authChallenge = ctx.rwscaAuthChallenge,
            mdvmToken = mdvmRegistration.mdvm_token,
            rwscaAccountId = rwscaRegistration.rwsca_account_id,
            rwscaPinSessionToken = pinSession.rwsca_pin_session_token_jwt,
        ).map { RwscaSignature(it.rwscd_key_binding_signature) }
         .mapError { it.toRwscaError() }
    }

    override suspend fun startPinSession(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        withPinKeys: RwscaController.WithPinKeys,
        mode: RwscaController.StartPinSessionMode,
    ): RwscaResult<RwscaPinSession> {
        val ctx = when(val result = prepareChallenge(mdvmRegistration)) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        val initializeFirst = (mode == RwscaController.StartPinSessionMode.InitialPinSession)
        // The lifetime of wiRwscaPinKeys only lasts for the duration of the below block.
        // Do not hold a reference outside of the below block.
        return withPinKeys.execute { wiRwscaPinKeys ->
            val api = rwscaApiClient.doubleSigningApi(
                mdvmAuthPrvk = ctx.wiMdvmAuthKeys.private,
                rwscaPinPrvk = wiRwscaPinKeys.private,
            )
            if (initializeFirst) {
                val request = RwscaInitializePinAndStartPinSessionRequest(
                    wi_rwsca_pin_pubk = wiRwscaPinKeys.public.encoded.toByteString().base64(),
                )
                api.initializePinAndStartPinSession(
                    request,
                    authChallenge = ctx.rwscaAuthChallenge,
                    mdvmToken = mdvmRegistration.mdvm_token,
                    rwscaAccountId = rwscaRegistration.rwsca_account_id,
                ).map { response ->
                    RwscaPinSession(
                        rwsca_pin_session_token_jwt = response.rwsca_pin_session_token,
                    )
                }.mapError { it.toRwscaError() }
            } else {
                api.startPinSession(
                    authChallenge = ctx.rwscaAuthChallenge,
                    mdvmToken = mdvmRegistration.mdvm_token,
                    rwscaAccountId = rwscaRegistration.rwsca_account_id,
                ).map { response ->
                    RwscaPinSession(
                        rwsca_pin_session_token_jwt = response.rwsca_pin_session_token,
                    )
                }.mapError { it.toRwscaError() }
            }
        }
    }
}
