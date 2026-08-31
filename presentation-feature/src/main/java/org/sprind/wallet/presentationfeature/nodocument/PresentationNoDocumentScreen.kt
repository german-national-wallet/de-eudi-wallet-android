package org.sprind.wallet.presentationfeature.nodocument

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute

@Composable
fun PresentationNoDocumentScreen(
    navController: NavController,
) {
    ContentScreen(
        topBar = {
            CancellableTopAppBar(
                onCloseClick = { navController.navigateToDashboard() }
            )
        },
        stickyBottom = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PaddingValues(
                            bottom = paddingValues.calculateBottomPadding(),
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {

                WrapButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_MEDIUM.dp),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.PRIMARY,
                        onClick = { navController.navigateToAddDocument() }
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.eaa_presentation_credential_not_found_prim_button)
                    )
                }
            }
        }
    ) { paddingValues ->
        PresentationNoDocumentContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun PresentationNoDocumentContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                R.string.eaa_presentation_credential_not_found_title
            ),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        )

        Spacer(modifier = Modifier.height(52.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            WrapImage(
                iconData = AppIcons.NoDocumentAvailable,
                modifier = Modifier.size(width = 149.dp, height = 125.dp)
            )
        }

        Spacer(modifier = Modifier.height(SPACING_EXTRA_LARGE.dp))

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(SPACING_SMALL.dp)
                )
                .padding(SPACING_SMALL.dp),
            text = stringResource(R.string.eaa_presentation_credential_not_found_paragraph),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            )
        )
    }
}

private fun NavController.navigateToDashboard() {
    navigate(DashboardScreens.Dashboard.screenRoute) {
        popUpTo(ModuleRoute.PresentationModule.route) {
            inclusive = true
        }
    }
}

private fun NavController.navigateToAddDocument() {
    navigate(IssuanceScreens.AddDocument.screenRoute) {
        popUpTo(ModuleRoute.PresentationModule.route) {
            inclusive = true
        }
    }
}
