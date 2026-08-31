@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.europa.ec.commonfeature.ui.success.SuccessView
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews

@Composable
fun WalletPinSetSuccessView(
    isLoading: Boolean,
    title: String = stringResource(R.string.pid_issuance_wallet_pin_reenter_succes),
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        isLoading = isLoading
    ) { paddingValues ->
        SuccessView(
            title = title,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetSuccessPreview() {
    WalletPinSetSuccessView(false)
}