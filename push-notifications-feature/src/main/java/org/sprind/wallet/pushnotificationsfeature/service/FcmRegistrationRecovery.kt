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

package org.sprind.wallet.pushnotificationsfeature.service

import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.CancellationException
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.businesslogic.controller.storage.EncryptedPrefsUnavailableException
import org.sprind.wallet.businesslogic.controller.storage.PlainPrefsController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import org.sprind.wallet.pushnotificationsfeature.service.WalletFirebaseMessagingService.Companion.FCM_TOKEN_REGISTERED_KEY

/**
 * Registers the FCM token with the push backend whenever the registered flag says it isn't.
 *
 * [WalletFirebaseMessagingService.onNewToken] clears the flag and attempts the registration
 * itself, but it runs with no user present and the attempt can fail (offline, or the encrypted
 * preferences are locked, see
 * [eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController]). On resume this asks
 * Firebase for the current one and completes the registration. An unset flag counts as
 * "not registered", so a fresh install registers on its first resume after onboarding.
 *
 * @param tokenProvider yields the current FCM registration token (`FirebaseMessaging.token`).
 */
class FcmRegistrationRecovery(
    private val plainPrefsController: PlainPrefsController,
    private val walletRevocationStore: WalletRevocationStore,
    private val mdvmRegistrationStorageController: MdvmRegistrationStorageController,
    private val pushNotificationsInteractor: PushNotificationsInteractor,
    private val tokenProvider: suspend () -> String,
    private val logController: LogController,
) {

    suspend fun registerIfNeeded() {
        if (walletRevocationStore.isRevoked()) return
        if (plainPrefsController.getBool(FCM_TOKEN_REGISTERED_KEY, false)) return
        if (!isWalletRegistered()) return

        val token = try {
            tokenProvider()
        } catch (e: CancellationException) {
            // A cancelled Task cancels the coroutine; that is not a failure to log.
            throw e
        } catch (e: Exception) {
            logController.e(TAG, e)
            return
        }
        when (val result = pushNotificationsInteractor.registerFcmToken(token)) {
            is ApiResult.Success -> {
                plainPrefsController.setBool(FCM_TOKEN_REGISTERED_KEY, true)
                logController.d(TAG) { "FCM token registered with backend on resume" }
            }

            is ApiResult.Failure -> {
                logController.e(TAG) { "FCM token registration failed: ${result.error.errorCode}" }
            }
        }
    }

    /**
     * Whether the MDVM registration exists. Reads the encrypted preferences; a failure to read
     * them is logged and counts as "not registered", so the attempt is simply postponed to the
     * next resume instead of failing the caller.
     */
    private fun isWalletRegistered(): Boolean =
        try {
            mdvmRegistrationStorageController.getMdvmRegistration() != null
        } catch (e: EncryptedPrefsUnavailableException) {
            logController.e(TAG, e)
            false
        }

    companion object {
        private const val TAG = "FcmRegistrationRecovery"
    }
}
