package org.sprind.wallet.cardreaderfeature.ui.document.issuer_details

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.ui.issuer_details.IssuerDetailsContent
import eu.europa.ec.commonfeature.ui.issuer_details.IssuerDetailsViewModel

@Composable
fun IssuerDetailsScreen(
    navController: NavController,
    viewModel: IssuerDetailsViewModel
) {
    IssuerDetailsContent(
        navController = navController,
        viewModel = viewModel
    )
}