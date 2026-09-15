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

package org.sprind.wallet.corelogic.revocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider

/**
 * Posts the wallet self-lock notification on a high-importance channel.
 *
 * A revocation push is a pure data message, so nothing is shown unless the app posts a
 * notification itself; without one, a backgrounded user learns about the lock only on the next
 * app open. Delivery is best-effort — on API 33+ the user may have denied `POST_NOTIFICATIONS`,
 * and the blocking screen remains the authoritative surface.
 */
class RevocationNotificationManager(
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
) {

    private val context = resourceProvider.provideContext()

    private val notificationManager = context.getSystemService<NotificationManager>()

    /** Posts the revocation notification, creating the channel on first use. */
    fun postRevocationNotification() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(resourceProvider.getString(R.string.notification_revoked_title))
            .setContentText(resourceProvider.getString(R.string.notification_revoked_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createMainActivityPendingIntent())
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
        logController.d(TAG) { "Posted revocation notification" }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            resourceProvider.getString(R.string.notification_channel_revocation_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        notificationManager?.createNotificationChannel(channel)
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply { setPackage(context.packageName) }
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "RevocationNotifMgr"
        private const val CHANNEL_ID = "wallet_revocation"
        private const val NOTIFICATION_ID = 1
    }
}
