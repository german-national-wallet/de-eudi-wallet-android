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

package org.sprind.wallet.presentationfeature.rejected

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.presentationfeature.interactor.RequestRejectionReason
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.extension.openDeepLink
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.uilogic.component.ArtworkCloseAction
import org.sprind.wallet.uilogic.component.ArtworkTopBar
import org.sprind.wallet.uilogic.component.ExternalLinkButton
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialog
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialogConfig
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig

object PresentationRequestRejectedTestTags {
    const val OVER_ASKING = "presentation_request_rejected_over_asking"
    const val NOT_REGISTERED = "presentation_request_rejected_not_registered"
    const val CLOSE = "presentation_request_rejected_close"
}

/** Shown after the wallet refused a presentation request on the user's behalf. */
@Composable
fun PresentationRequestRejectedScreen(
    navController: NavController,
    reason: RequestRejectionReason,
    fallbackReportUrl: String,
    reportUri: String? = null,
    redirectUri: String? = null,
) {
    val context = LocalContext.current

    // The report target may come from a certificate, so it is confirmed before leaving the app.
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }

    val backToService = redirectUri?.let { uri ->
        {
            context.openDeepLink(uri.toUri())
            navController.navigateToDashboard()
        }
    }

    Screen(
        reason = reason,
        onCloseClick = backToService ?: { navController.navigateToDashboard() },
        onOverviewClick = { navController.navigateToDashboard() },
        onReportClick = { pendingLink = reportUri ?: fallbackReportUrl },
        onBackToServiceClick = backToService,
    )

    pendingLink?.let { link ->
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = link.toUri(),
                onConfirm = {
                    pendingLink = null
                    context.openUrl(link.toUri())
                },
                onDismiss = { pendingLink = null },
            )
        )
    }
}

@Composable
private fun Screen(
    reason: RequestRejectionReason,
    onCloseClick: () -> Unit = {},
    onOverviewClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onBackToServiceClick: (() -> Unit)? = null,
) {
    val copy = reason.rejectionCopy()

    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            ArtworkTopBar(artwork = AppIcons.PhoneSecurityShield) {
                ArtworkCloseAction(
                    onClick = onCloseClick,
                    modifier = Modifier.testTag(PresentationRequestRejectedTestTags.CLOSE),
                )
            }
        },
        stickyBottom = { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                when (copy.primaryTarget) {
                    PrimaryTarget.REPORT -> ExternalLinkButton(
                        type = ButtonType.PRIMARY,
                        textRes = copy.primaryButton,
                        onClick = onReportClick,
                        modifier = Modifier.padding(bottom = SPACING_SMALL.dp),
                    )

                    PrimaryTarget.OVERVIEW -> WrapButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SPACING_SMALL.dp),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onOverviewClick,
                        ),
                    ) {
                        Text(stringResource(copy.primaryButton))
                    }
                }
                onBackToServiceClick?.let {
                    ExternalLinkButton(
                        type = ButtonType.SECONDARY,
                        textRes = copy.secondaryButton,
                        onClick = it,
                    )
                }
            }
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier
                .padding(top = SPACING_MEDIUM.dp)
                .testTag(copy.testTag),
            screenPadding = paddingValues,
            templateConfig = ContentTemplateConfig(showScrollFade = true),
            title = {
                Text(
                    text = stringResource(copy.title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            body = { Text(stringResource(copy.paragraph)) },
        )
    }
}

private enum class PrimaryTarget { REPORT, OVERVIEW }

private data class RejectionCopy(
    @StringRes val title: Int,
    @StringRes val paragraph: Int,
    @StringRes val primaryButton: Int,
    val primaryTarget: PrimaryTarget,
    @StringRes val secondaryButton: Int,
    val testTag: String,
)

private fun RequestRejectionReason.rejectionCopy(): RejectionCopy = when (this) {
    RequestRejectionReason.OVER_ASKING -> RejectionCopy(
        title = R.string.pid_presentation_dialog_rp_overasking_title,
        paragraph = R.string.pid_presentation_dialog_rp_overasking_paragraph_overasking,
        primaryButton = R.string.pid_presentation_dialog_rp_overasking_prim_button,
        primaryTarget = PrimaryTarget.REPORT,
        secondaryButton = R.string.pid_presentation_dialog_rp_overasking_sec_button,
        testTag = PresentationRequestRejectedTestTags.OVER_ASKING,
    )

    // An unvalidated certificate names no authority to report to.
    RequestRejectionReason.NOT_REGISTERED -> RejectionCopy(
        title = R.string.pid_presentation_dialog_rp_unkown_title,
        paragraph = R.string.pid_presentation_dialog_rp_unkown_paragraph_cert_failed,
        primaryButton = R.string.global_to_overview_button,
        primaryTarget = PrimaryTarget.OVERVIEW,
        secondaryButton = R.string.pid_presentation_dialog_rp_unkown_sec_button,
        testTag = PresentationRequestRejectedTestTags.NOT_REGISTERED,
    )
}

private fun NavController.navigateToDashboard() {
    navigate(DashboardScreens.Dashboard.screenRoute) {
        popUpTo(ModuleRoute.PresentationModule.route) {
            inclusive = true
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationRequestRejectedOverAskingPreview() {
    PreviewTheme {
        Screen(reason = RequestRejectionReason.OVER_ASKING, onBackToServiceClick = {})
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationRequestRejectedNotRegisteredPreview() {
    PreviewTheme {
        Screen(reason = RequestRejectionReason.NOT_REGISTERED, onBackToServiceClick = {})
    }
}
