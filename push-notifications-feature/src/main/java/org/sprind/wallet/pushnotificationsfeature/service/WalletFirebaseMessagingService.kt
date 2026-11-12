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

import android.content.Intent
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.sprind.wallet.businesslogic.controller.storage.PlainPrefsController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.pushnotificationsfeature.dispatcher.FcmMessageDispatcher
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor

/**
 * FCM service that handles token refresh, push notification registration with the push
 * notifications backend, and incoming FCM data messages. Message dispatch (the single
 * `RENEW_MDVM_TOKEN` action that drives the authenticated-pull-then-self-lock revocation
 * flow) is delegated to [FcmMessageDispatcher].
 *
 * The service runs with no user present, so it only touches [PlainPrefsController], see
 * [eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController]. The token itself is
 * not stored; when the registration attempt here fails, [FcmRegistrationRecovery] completes it on
 * the next resume.
 */
class WalletFirebaseMessagingService : FirebaseMessagingService() {

    private val plainPrefsController: PlainPrefsController by inject()
    private val logController: LogController by inject()
    private val pushNotificationsInteractor: PushNotificationsInteractor by inject()
    private val fcmMessageDispatcher: FcmMessageDispatcher by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handleIntent(intent: Intent) {
        val extras = intent.extras ?: Bundle()
        val intercept = FcmNotificationIntercept.shouldIntercept(
            action = intent.action,
            messageType = extras.getString(FcmNotificationIntercept.KEY_MESSAGE_TYPE),
            extraKeys = extras.keySet(),
        )
        if (intercept) {
            logController.d(TAG) { "Intercepting FCM notification message for in-app handling" }
            onMessageReceived(RemoteMessage(extras))
        } else {
            super.handleIntent(intent)
        }
    }

    override fun onNewToken(token: String) {
        logController.d(TAG) { "FCM registration token refreshed" }
        // Cleared, and on disk, before the attempt: if the process dies or the attempt fails,
        // the next resume fetches the current token and registers it.
        plainPrefsController.setBoolSync(FCM_TOKEN_REGISTERED_KEY, false)
        serviceScope.launch {
            try {
                attemptRegistration(token)
            } catch (e: Exception) {
                logController.e(TAG, e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        logController.d(TAG) { "FCM message received from: ${remoteMessage.from}" }

        fcmMessageDispatcher.dispatch(
            action = remoteMessage.data[KEY_ACTION],
            scope = serviceScope,
        )
    }

    private suspend fun attemptRegistration(token: String) {
        when (val result = pushNotificationsInteractor.registerFcmToken(token)) {
            is ApiResult.Success -> {
                plainPrefsController.setBool(FCM_TOKEN_REGISTERED_KEY, true)
                logController.d(TAG) { "FCM token successfully registered with backend" }
            }

            is ApiResult.Failure -> {
                logController.e(TAG) { "FCM token registration failed: ${result.error.errorCode}" }
            }
        }
    }

    companion object {
        private const val TAG = "WalletFCM"

        /**
         * [PlainPrefsController] key tracking whether the current FCM token has been registered
         * with the backend. Unset counts as not registered.
         */
        const val FCM_TOKEN_REGISTERED_KEY = "FCM_TOKEN_REGISTERED"

        const val KEY_ACTION = "action"
    }
}
