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

package org.sprind.wallet.uilogic.component.dialog

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig

/**
 * The "You're offline" dialog shown before a request when the OS reports no internet (WD-4685).
 * Figma "Internet Error Case": Retry, Open internet settings, Close. No error code or trace ID,
 * since no request was made.
 *
 * @param onOpenSettings opens the system Internet panel; the dialog stays open behind it so
 *   the user can retry on return.
 */
fun offlineErrorDialogConfig(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
): GenericErrorDialogConfig = GenericErrorDialogConfig(
    titleRes = R.string.global_no_internet_error_title,
    bodyTextRes = R.string.global_no_internet_error_paragraph,
    primaryButtonTextRes = R.string.global_no_internet_error_prim_button,
    onPrimaryButtonClick = onRetry,
    secondaryButtonTextRes = R.string.global_no_internet_error_sec_button,
    onSecondaryButtonClick = onOpenSettings,
    tertiaryButtonTextRes = R.string.global_close_button,
    onTertiaryButtonClick = onClose,
    onDismiss = onClose,
)
