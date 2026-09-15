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

object FcmNotificationIntercept {

    const val ACTION_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"
    const val KEY_MESSAGE_TYPE = "message_type"
    const val MESSAGE_TYPE_GCM = "gcm"
    private const val NOTIFICATION_PREFIX = "gcm.n."
    private const val NOTIFICATION_PREFIX_LEGACY = "gcm.notification."

    fun shouldIntercept(action: String?, messageType: String?, extraKeys: Set<String>): Boolean {
        if (action != ACTION_RECEIVE) return false
        if (messageType != null && messageType != MESSAGE_TYPE_GCM) return false
        return extraKeys.any {
            it.startsWith(NOTIFICATION_PREFIX) || it.startsWith(NOTIFICATION_PREFIX_LEGACY)
        }
    }
}
