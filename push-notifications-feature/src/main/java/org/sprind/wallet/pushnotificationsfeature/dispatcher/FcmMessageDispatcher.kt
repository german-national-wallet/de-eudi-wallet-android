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

package org.sprind.wallet.pushnotificationsfeature.dispatcher

import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor

/**
 * Routes incoming FCM data-message actions to the right [PushNotificationsInteractor] entry point.
 *
 * Extracted from [org.sprind.wallet.pushnotificationsfeature.service.WalletFirebaseMessagingService]
 * so the dispatch table can be unit-tested without the Android `Service` / Firebase framework
 * wiring. The service delegates here with its own [CoroutineScope].
 *
 * Known actions:
 * - [ACTION_RENEW_MDVM_TOKEN] → [PushNotificationsInteractor.handleRevocationPush]: the push is only
 *   a signal — the wallet performs an authenticated MDVM renewal and self-locks *only* on an
 *   explicit `ACCOUNT_REVOKED` response. See `docs/.../03-data-flows/13-wallet-revocation.md`.
 */
class FcmMessageDispatcher(
    private val interactor: PushNotificationsInteractor,
    private val logController: LogController,
) {

    /**
     * Dispatches the given FCM [action] inside [scope]. Unknown actions are logged and ignored.
     *
     * The dispatch is launched in [scope] because the interactor entry point is suspending;
     * the caller (the FCM service) owns the scope lifecycle.
     */
    fun dispatch(action: String?, scope: CoroutineScope) {
        when (action) {
            ACTION_RENEW_MDVM_TOKEN -> {
                logController.d(TAG) { "Handling $ACTION_RENEW_MDVM_TOKEN action" }
                scope.launch { interactor.handleRevocationPush() }
            }

            else -> {
                logController.e(TAG) { "Unknown message action received $action" }
            }
        }
    }

    companion object {
        private const val TAG = "FcmDispatcher"

        /**
         * FCM `data.action` value signalling a wallet revocation. The wallet performs an
         * authenticated MDVM token renewal and self-locks only on an explicit
         * `ACCOUNT_REVOKED` response from the backend.
         */
        const val ACTION_RENEW_MDVM_TOKEN = "RENEW_MDVM_TOKEN"
    }
}