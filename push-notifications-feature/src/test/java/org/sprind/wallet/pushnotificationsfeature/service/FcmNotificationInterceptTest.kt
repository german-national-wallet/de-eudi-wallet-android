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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmNotificationInterceptTest {

    private val receive = FcmNotificationIntercept.ACTION_RECEIVE

    @Test
    fun `intercepts a notification message so the app handles it in the background`() {
        assertTrue(
            FcmNotificationIntercept.shouldIntercept(
                action = receive,
                messageType = "gcm",
                extraKeys = setOf("gcm.n.e", "gcm.n.title", "action", "google.message_id"),
            )
        )
    }

    @Test
    fun `intercepts the legacy notification key prefix too`() {
        assertTrue(
            FcmNotificationIntercept.shouldIntercept(
                action = receive,
                messageType = null,
                extraKeys = setOf("gcm.notification.title", "action"),
            )
        )
    }

    @Test
    fun `leaves pure data messages to the SDK path`() {
        assertFalse(
            FcmNotificationIntercept.shouldIntercept(
                action = receive,
                messageType = "gcm",
                extraKeys = setOf("action", "google.message_id", "from"),
            )
        )
    }

    @Test
    fun `leaves token refreshes and other actions to the SDK`() {
        assertFalse(
            FcmNotificationIntercept.shouldIntercept(
                action = "com.google.firebase.messaging.NEW_TOKEN",
                messageType = null,
                extraKeys = setOf("gcm.n.e"),
            )
        )
    }

    @Test
    fun `leaves deleted-message and send-event signals to the SDK`() {
        assertFalse(
            FcmNotificationIntercept.shouldIntercept(
                action = receive,
                messageType = "deleted_messages",
                extraKeys = setOf("gcm.n.e"),
            )
        )
    }
}
