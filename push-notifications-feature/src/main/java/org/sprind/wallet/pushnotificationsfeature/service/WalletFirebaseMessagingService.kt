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

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.pushnotificationsfeature.dispatcher.FcmMessageDispatcher
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor

/**
 * FCM service that handles token refresh, push notification registration with the push
 * notifications backend, and incoming FCM data messages. Message dispatch (the single
 * `RENEW_MDVM_TOKEN` action that drives the authenticated-pull-then-self-lock revocation
 * flow) is delegated to [FcmMessageDispatcher].
 */
class WalletFirebaseMessagingService : FirebaseMessagingService() {

    private val prefsController: PrefsController by inject()
    private val logController: LogController by inject()
    private val pushNotificationsInteractor: PushNotificationsInteractor by inject()
    private val fcmMessageDispatcher: FcmMessageDispatcher by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        logController.d(TAG) { "FCM registration token refreshed: $token" }
        prefsController.setString(FCM_REGISTRATION_ID_KEY, token)
        prefsController.setBool(FCM_TOKEN_REGISTERED_KEY, false)
        serviceScope.launch { attemptRegistration(token) }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        logController.d(TAG) { "FCM message received from: ${remoteMessage.from}" }
        remoteMessage.data.forEach { (key, value) ->
            logController.d(TAG) { "FCM data — $key: $value" }
        }

        fcmMessageDispatcher.dispatch(
            action = remoteMessage.data[KEY_ACTION],
            scope = serviceScope,
        )
    }

    private suspend fun attemptRegistration(token: String) {
        when (val result = pushNotificationsInteractor.registerFcmToken(token)) {
            is ApiResult.Success -> {
                prefsController.setBool(FCM_TOKEN_REGISTERED_KEY, true)
                logController.d(TAG) { "FCM token successfully registered with backend" }
            }

            is ApiResult.Failure -> {
                prefsController.setBool(FCM_TOKEN_REGISTERED_KEY, false)
                logController.e(TAG) { "FCM token registration failed: ${result.error.errorCode}" }
            }
        }
    }

    companion object {
        private const val TAG = "WalletFCM"

        /** Preference key storing the current FCM registration token. */
        const val FCM_REGISTRATION_ID_KEY = "FCM_REGISTRATION_TOKEN"

        /** Preference key tracking whether the FCM token has been successfully registered with the backend. */
        const val FCM_TOKEN_REGISTERED_KEY = "FCM_TOKEN_REGISTERED"

        private const val KEY_ACTION = "action"
    }
}
