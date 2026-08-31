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

package org.sprind.wallet.pushnotificationsfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.delay
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApiClient
import org.sprind.wallet.networklogic.pushnotifications.api.SigningPushNotificationsApiClient
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsErrorResponse
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates FCM token registration with the push notifications backend and the
 * wallet-revocation flow triggered by push notifications.
 *
 * The revocation flow is driven by a single push action, [FcmMessageDispatcher.ACTION_RENEW_MDVM_TOKEN]:
 * the push is only a signal — the wallet self-locks *only* when the authenticated MDVM
 * renewal returns an explicit `ACCOUNT_REVOKED` error. The self-lock itself is performed by
 * the `RevocationHandlingMdvmInteractor` decorator wrapping [MdvmInteractor], so this
 * interactor only needs to trigger the renewal. Transient renewal errors never self-lock.
 * See `docs/.../03-data-flows/13-wallet-revocation.md` (Wallet Instance Self-locking) and
 * AD-16 / AD-17.
 */
interface PushNotificationsInteractor {
    /**
     * Registers the given FCM token with the push notifications backend.
     *
     * @param token The FCM registration token to register.
     * @return Success or a [PushNotificationsErrorResponse] on failure.
     */
    suspend fun registerFcmToken(token: String): ApiResult<Unit, PushNotificationsErrorResponse>

    /**
     * Deletes the current push notifications registration from the backend.
     *
     * @return Success or a [PushNotificationsErrorResponse] on failure.
     */
    suspend fun deleteRegistration(): ApiResult<Unit, PushNotificationsErrorResponse>

    /**
     * Handles a wallet-revocation push notification by performing an authenticated MDVM
     * renewal (bypassing the cached token). The `RevocationHandlingMdvmInteractor` decorator
     * on [MdvmInteractor] self-locks the wallet *only* if the backend returns an explicit
     * `ACCOUNT_REVOKED` error; this interactor does not perform self-lock itself.
     *
     * If the first renewal still succeeds (a race window where revocation has not yet
     * propagated to the MDVM), a single retry is performed after
     * [REVOCATION_RACE_RETRY_MS]. A second success means the wallet is not revoked, and the
     * wallet is left untouched. A second failure is left to the decorator: if it is
     * `ACCOUNT_REVOKED` the decorator self-locks; transient errors do nothing.
     *
     * This is a suspending function: callers must invoke it from a coroutine scope (e.g. the
     * FCM service's [Dispatchers.IO] scope).
     */
    suspend fun handleRevocationPush()
}

private typealias PushNotificationsResult = ApiResult<Unit, PushNotificationsErrorResponse>

private typealias PushNotificationApiClientBlock =
        suspend SigningPushNotificationsApiClient.(authChallenge: String, mdvmToken: String) -> PushNotificationsResult

class PushNotificationsInteractorImpl(
    private val pushNotificationsApiClient: PushNotificationsApiClient,
    private val mdvmAuthContextProvider: MdvmAuthContextProvider,
    private val mdvmInteractor: MdvmInteractor,
    private val logController: LogController,
) : PushNotificationsInteractor {

    override suspend fun registerFcmToken(token: String): PushNotificationsResult =
        apiClientCall { authChallenge, mdvmToken ->
            register(
                mppRegistrationToken = token,
                authChallenge = authChallenge,
                mdvmToken = mdvmToken,
            ).also {
                when (it) {
                    is ApiResult.Success -> logController.d(TAG) { "FCM token registered with push notifications backend" }
                    is ApiResult.Failure -> logController.e(TAG) { "Push notifications registration failed: ${it.error.errorCode}" }
                }
            }
        }

    override suspend fun deleteRegistration(): PushNotificationsResult =
        apiClientCall { authChallenge, mdvmToken ->
            delete(
                authChallenge = authChallenge,
                mdvmToken = mdvmToken,
            ).also {
                when (it) {
                    is ApiResult.Success -> logController.d(TAG) { "Push notifications registration deleted" }
                    is ApiResult.Failure -> logController.e(TAG) { "Push notifications deletion failed: ${it.error.errorCode}" }
                }
            }
        }

    private suspend fun apiClientCall(block: PushNotificationApiClientBlock): PushNotificationsResult {
        val authContext = when (val result = mdvmAuthContextProvider.getMdvmAuthContext()) {
            is ApiResult.Failure -> {
                logController.e(TAG) { "Failed to get MDVM auth context: ${result.error.code}" }
                return ApiResult.Failure(
                    PushNotificationsErrorResponse(errorCode = result.error.code)
                )
            }

            is ApiResult.Success -> result.response
        }

        val challenge = when (val challengeResult = pushNotificationsApiClient.getChallenge()) {
            is ApiResult.Failure -> {
                logController.e(TAG) { "Failed to get push notifications challenge: ${challengeResult.error.errorCode}" }
                return ApiResult.Failure(challengeResult.error)
            }

            is ApiResult.Success -> challengeResult.response.authChallenge
        }

        val signingClient = pushNotificationsApiClient.signingApi(authContext.mdvmAuthPrvk)
        return signingClient.block(challenge, authContext.mdvmToken)
    }

    override suspend fun handleRevocationPush() {
        logController.d(TAG) { "Revocation push received — performing authenticated MDVM renewal" }
        // The RevocationHandlingMdvmInteractor decorator self-locks on ACCOUNT_REVOKED.
        when (val firstResult = mdvmInteractor.mdvmRegistration(forceRenewal = true)) {
            is ApiResult.Success -> {
                // Race window: revocation may not yet have propagated to the MDVM. Retry once.
                logController.d(TAG) { "Renewal still succeeds; retrying after race-window delay" }
                delay(REVOCATION_RACE_RETRY_MS.milliseconds)
                mdvmInteractor.mdvmRegistration(forceRenewal = true)
                // The decorator self-locks if this retry returns ACCOUNT_REVOKED.
            }
            is ApiResult.Failure -> {
                // The decorator already self-locked if this was ACCOUNT_REVOKED.
                // For transient errors there is nothing more to do.
                logController.d(TAG) { "Renewal error (${firstResult.error.code}); not retrying" }
            }
        }
    }

    companion object {
        private const val TAG = "PushNotifInteractor"

        /**
         * Delay before retrying the MDVM renewal after a first success, to cover the race
         * window where revocation has not yet propagated to the MDVM.
         */
        const val REVOCATION_RACE_RETRY_MS = 5_000L
    }
}