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

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.uilogic.component.SuccessContentScreen

/**
 * The wallet code is set. One statement centred under the success animation; see
 * [SuccessContentScreen].
 */
@Composable
fun WalletPinSetSuccessView(
    isLoading: Boolean,
    title: String = stringResource(R.string.pid_issuance_wallet_pin_reenter_succes),
) {
    SuccessContentScreen(
        title = title,
        isLoading = isLoading,
    )
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetSuccessPreview() {
    PreviewTheme {
        WalletPinSetSuccessView(false)
    }
}
