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

/**
 * The dialog for a PID issuance whose authorization session expired after the eID step
 * (Figma section 254:14649, "PID issuance timeout (PAR request expired)").
 *
 * It cannot be dismissed by tapping outside or pressing back: the issuance cannot continue, so
 * the user has to choose one of the buttons.
 *
 * @param onStartAgain Restarts the PID issuance.
 * @param onCancel Abandons the issuance for the wallet overview.
 */
fun authorizationExpiredDialogConfig(
    onStartAgain: () -> Unit,
    onCancel: () -> Unit,
): GenericErrorDialogConfig = GenericErrorDialogConfig(
    titleRes = R.string.pid_issuance_timeout_par_request_expired_title,
    bodyTextRes = R.string.pid_issuance_timeout_par_request_expired_paragraph,
    dismissable = false,
    primaryButtonTextRes = R.string.pid_issuance_timeout_par_request_expired_prim_button,
    onPrimaryButtonClick = onStartAgain,
    secondaryButtonTextRes = R.string.pid_issuance_timeout_par_request_expired_sec_button,
    onSecondaryButtonClick = onCancel,
)

/**
 * [toErrorDialogConfig] for PID issuance: a timeout also offers "Cancel process", as in the
 * issuance timeout design, next to "Retry".
 *
 * Like [authorizationExpiredDialogConfig], it cannot be dismissed by tapping outside or pressing
 * back, so the user has to choose one of the buttons.
 *
 * @param onCancel Abandons the issuance for the wallet overview.
 * @param onDismiss Called by the rate limit's "Retry later" button, which must leave the failed
 * step, as the issuance cannot continue.
 */
fun NetworkFailure.toPidIssuanceErrorDialogConfig(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
): GenericErrorDialogConfig {
    val config = toErrorDialogConfig(onRetry = onRetry, onDismiss = onDismiss).copy(dismissable = false)
    return when (this) {
        NetworkFailure.Timeout -> config.copy(
            // The only "Cancel process" text there is. It comes from the PAR-expired section of the
            // generated string_figma.xml, so renaming that section in Figma changes it here too.
            secondaryButtonTextRes = R.string.pid_issuance_timeout_par_request_expired_sec_button,
            onSecondaryButtonClick = onCancel,
        )

        is NetworkFailure.RateLimited -> config
    }
}
