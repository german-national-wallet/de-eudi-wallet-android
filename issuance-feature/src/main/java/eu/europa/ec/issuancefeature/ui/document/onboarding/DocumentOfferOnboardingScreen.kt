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

package eu.europa.ec.issuancefeature.ui.document.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.issuancefeature.ui.document.onboarding.Effect.Navigation
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * Displays the additional step screen in the EAA (Electronic Attestation of Attributes) issuance flow.
 *
 * This screen prompts users to enter a transaction code required to save the credential
 * in their wallet. It provides two options:
 * - Continue: Navigate to the document offer code entry screen
 * - Decline: Return to dashboard or close the app
 *
 * The screen shows an informational paragraph explaining the requirement, along with
 * user and card icons to illustrate the credential storage process.
 *
 * @param navController The navigation controller for handling screen transitions
 * @param viewModel The ViewModel handling business logic and navigation effects
 */
@Composable
fun DocumentOfferOnboardingScreen(
    navController: NavController,
    viewModel: DocumentOfferOnboardingViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Screen(
        onBackClick = {
            navController.popBackStack(
                route = IssuanceScreens.DocumentOffer.screenRoute,
                inclusive = false
            )
        },
        onCloseClick = {
            handleCloseOrDeclineNavigation(navController, context)
        },
        onContinueWithEntry = {
            viewModel.setEvent(Event.EnterPin())
        },
        onDecline = {
            viewModel.setEvent(Event.Decline())
        }
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach {
            when (it) {
                is Navigation.EnterPin -> {
                    navController.navigate(
                        route = "${IssuanceScreens.DocumentOfferCode.screenName}?offerCodeConfig=${it.offerSerializedConfig}"
                    )
                }
                is Navigation.Decline -> {
                    handleCloseOrDeclineNavigation(navController, context)
                }
                is Navigation.CloseApp -> {
                    context.finish()
                }
            }
        }.collect()
    }
}

private fun handleCloseOrDeclineNavigation(
    navController: NavController,
    context: android.content.Context
) {
    try {
        navController.getBackStackEntry(DashboardScreens.Dashboard.screenRoute)
        navController.popBackStack(
            route = DashboardScreens.Dashboard.screenRoute,
            inclusive = false
        )
    } catch (_: Exception) {
        context.finish()
    }
}

@Composable
private fun Screen(
    onBackClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onContinueWithEntry: () -> Unit = {},
    onDecline: () -> Unit = {},
) {
    ContentScreen(
        topBar = {
            CancellableTopAppBar(
                onBackClick = onBackClick,
                onCloseClick = onCloseClick,
            )
        },
        stickyBottom = { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues)
            ) {
                WrapButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.PRIMARY,
                        onClick = onContinueWithEntry
                    )
                ) {
                    Text(stringResource(R.string.eaa_issuance_transaction_code_intro_prim_button))
                }

                WrapButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.SECONDARY,
                        onClick = onDecline
                    )
                ) {
                    Text(stringResource(R.string.eaa_issuance_transaction_code_intro_sec_button))
                }
            }
        }
    ) { paddingValues ->
        Content(paddingValues)
    }
}

@Composable
private fun Content(
    paddingValues: PaddingValues = PaddingValues(SPACING_MEDIUM.dp)
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = CustomTypography.titleLargeBold,
        )
    ) {
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            title = {
                Text(stringResource(R.string.eaa_issuance_transaction_code_intro_title))
            },
            templateConfig = ContentTemplateConfig(
                illustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
            ),
            illustration = {
                WrapImage(
                    modifier = Modifier.fillMaxWidth(),
                    painter = painterResource(id = R.drawable.auth_code_wip),
                    contentDescription = stringResource(
                        R.string.eaa_issuance_transaction_code_intro_title
                    ),
                    contentScale = ContentScale.Fit,
                )
            },
            extraContent = {
                ContentNotice {
                    Text(stringResource(R.string.eaa_issuance_transaction_code_intro_paragraph))
                }
            },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ScreenPreview() {
    PreviewTheme {
        Screen()
    }
}
