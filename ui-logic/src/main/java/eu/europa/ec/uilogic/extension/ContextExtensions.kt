/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.extension

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import eu.europa.ec.uilogic.container.EudiComponentActivity
import org.sprind.wallet.uilogic.extension.isSafeRedirectLink
import timber.log.Timber

/**
 * Opens the redirect that closes an issuance or presentation flow.
 *
 * The redirect is chosen by the issuer or the relying party, so schemes that would do more than
 * hand control back to their app are dropped.
 *
 * @param deepLink the redirect to open.
 */
fun Context.openDeepLink(deepLink: Uri) {
    if (!deepLink.isSafeRedirectLink()) {
        Timber.w("Dropped redirect with rejected scheme: %s", deepLink.scheme)
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = deepLink
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No app to open redirect with scheme: %s", deepLink.scheme)
    } catch (e: SecurityException) {
        Timber.e(e, "Not allowed to open redirect with scheme: %s", deepLink.scheme)
    }
}

fun Context.getPendingDeepLink(): Uri? {
    return (this as? EudiComponentActivity)?.pendingDeepLink?.let { deepLink ->
        clearPendingDeepLink()
        deepLink
    }
}

fun Context.cacheDeepLink(uri: Uri) {
    val intent = Intent().apply {
        data = uri
    }
    (this as? EudiComponentActivity)?.cacheDeepLink(intent)
}

fun Context.finish() {
    (this as? EudiComponentActivity)?.finish()
}

fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No Activity found.")
}

private fun Context.clearPendingDeepLink() {
    (this as? EudiComponentActivity)?.pendingDeepLink = null
}

/**
 * Sends an Action View Intent for a link the wallet does not handle itself.
 *
 * Reached by the links a flow caches on its way out - an issuer or relying party redirect - and
 * held to the same policy as [openDeepLink]. Links another app sends in never reach here: they are
 * dropped on intake by `EudiComponentActivity`.
 *
 * @param uri the url to open.
 */
fun Context.openUrl(uri: Uri) {
    if (!uri.isSafeRedirectLink()) {
        Timber.w("Dropped link with rejected scheme: %s", uri.scheme)
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No app to open link with scheme: %s", uri.scheme)
    } catch (e: SecurityException) {
        Timber.e(e, "Not allowed to open link with scheme: %s", uri.scheme)
    }
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri = Uri.fromParts("package", packageName, null)
    intent.data = uri
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.openBleSettings() {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.openIntentChooser(intent: Intent, title: String? = null) {
    try {
        startActivity(Intent.createChooser(intent, title))
    } catch (_: Exception) {
    }
}

fun Context.shareLogs(fileUris: ArrayList<Uri>) {
    if (fileUris.isNotEmpty()) {
        val shareIntent =
            Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
                    type = "text/plain"
                },
                null,
            )
        openIntentChooser(shareIntent)
    }
}