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

package org.sprind.wallet.commonfeature.ui.error

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import org.sprind.wallet.networklogic.common.NetworkFailure
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * The dialog for a [NetworkFailure], the same in every flow (WD-4529, Figma section 254:14649):
 * - [NetworkFailure.Timeout]: "That took too long"; the button calls [onRetry].
 * - [NetworkFailure.RateLimited]: the button ("Retry later") calls [onDismiss], as retrying right
 *   away would be rejected again. The body gives the time to retry at. When the response did not
 *   say, it falls back to the generic error dialog, whose button calls [onRetry].
 *
 * Neither shows an error code or trace ID, as in the design: the request may never have reached
 * the backend.
 *
 * @param onDismiss Also invoked when the dialog is dismissed without pressing the button.
 * @param zone The time zone the retry time is shown in.
 * @param locale The locale the retry time is formatted for.
 */
fun NetworkFailure.toErrorDialogConfig(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): GenericErrorDialogConfig = when (this) {
    NetworkFailure.Timeout -> GenericErrorDialogConfig(
        titleRes = R.string.global_timeout_slow_internet_error_title,
        bodyTextRes = R.string.global_timeout_slow_internet_error_paragraph,
        primaryButtonTextRes = R.string.global_timeout_slow_internet_error_prim_button,
        onPrimaryButtonClick = onRetry,
        onDismiss = onDismiss,
    )

    // Retry-After is optional on a 429: without it there is no time to wait for, so the generic
    // error lets the user retry rather than leaving them a button that only dismisses.
    is NetworkFailure.RateLimited -> when (val retryAt = retryAt) {
        null -> GenericErrorDialogConfig(
            titleRes = R.string.global_error_title,
            bodyTextRes = R.string.global_error_paragraph,
            primaryButtonTextRes = R.string.global_error_prim_button,
            onPrimaryButtonClick = onRetry,
            onDismiss = onDismiss,
        )

        else -> GenericErrorDialogConfig(
            titleRes = R.string.global_timeout_too_many_requests_title,
            bodyTextRes = R.string.global_timeout_too_many_requests_paragraph,
            // Formatted once, here: a per-app language change while the dialog is up keeps the
            // previous locale's time format, which is accepted for so short-lived a dialog.
            bodyTextArgs = listOf(retryAt.formatAsRetryTime(zone, locale)),
            primaryButtonTextRes = R.string.global_timeout_too_many_requests_prim_button,
            onPrimaryButtonClick = onDismiss,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The time to retry at, as shown in the rate-limit dialog: the time of day only, as `Retry-After`
 * is capped at an hour, so the date adds nothing.
 */
fun Instant.formatAsRetryTime(zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(toJavaInstant())
