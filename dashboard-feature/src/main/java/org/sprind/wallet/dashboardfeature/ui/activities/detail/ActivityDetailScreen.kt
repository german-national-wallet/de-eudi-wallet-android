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

package org.sprind.wallet.dashboardfeature.ui.activities.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.ThemeShapes
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BUTTON_HEIGHT
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.openUrl
import org.sprind.wallet.dashboardfeature.ui.activities.ActivityAvatar
import org.sprind.wallet.dashboardfeature.ui.activities.ExternalLinkButton
import org.sprind.wallet.dashboardfeature.ui.activities.contentColor
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityAvatarUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityDetailUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityLinkKind
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityLinkUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.PresentationOutcomeUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.SharedCredentialUi
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.CredentialClaimList
import org.sprind.wallet.uilogic.component.CredentialClaimsCard
import org.sprind.wallet.uilogic.component.CredentialClaimsHeader
import org.sprind.wallet.uilogic.component.CredentialClaimsVariant
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.EaaDocumentCard
import org.sprind.wallet.uilogic.component.EaaDocumentCardAction
import org.sprind.wallet.uilogic.component.NoticeBanner
import org.sprind.wallet.uilogic.component.NoticeBannerType
import org.sprind.wallet.uilogic.component.PidCredentialCard
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialog
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogConfig
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialog
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialogConfig
import org.sprind.wallet.uilogic.extension.untrimmed

@Composable
fun ActivityDetailScreen(
    navHostController: NavController,
    viewModel: ActivityDetailViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }

    MainScreen(state = state, onEventSend = viewModel::setEvent)

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

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ActivityDetailEffect.OpenUrl -> pendingLink = effect.uri
                is ActivityDetailEffect.OpenWebPage -> navHostController.navigate(effect.route)
                ActivityDetailEffect.Pop -> navHostController.popBackStack()
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: ActivityDetailState,
    onEventSend: (ActivityDetailEvent) -> Unit,
) {
    ContentScreen(
        isLoading = state.isLoading,
        onBack = { onEventSend(ActivityDetailEvent.BackPressed) },
        toolBarConfig = ToolbarConfig(
            actions = listOf(
                ToolbarAction(icon = AppIcons.Help, onClick = { onEventSend(ActivityDetailEvent.HelpClicked) })
            )
        ),
    ) { paddingValues ->
        val detail = state.detail ?: return@ContentScreen
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(),
            title = {
                Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
            },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                    Text(
                        text = stringResource(detail.dateLabel()),
                        style = MaterialTheme.typography.labelLarge.untrimmed(),
                        color = ThemeColors.onBackgroundVariant,
                    )
                    Text(
                        text = detail.date,
                        style = MaterialTheme.typography.bodyLarge.untrimmed(),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    (detail as? ActivityDetailUi.Presentation)?.outcome?.bannerText()?.let { text ->
                        NoticeBanner(body = stringResource(text), type = NoticeBannerType.WARNING)
                    }
                }
            },
            extraContent = {
                when (detail) {
                    is ActivityDetailUi.Presentation -> PresentationContent(detail)
                    is ActivityDetailUi.CredentialEvent -> CredentialEventContent(
                        detail = detail,
                        onViewCredential = { onEventSend(ActivityDetailEvent.ViewCredentialClicked) },
                    )
                }

                if (detail.links.isNotEmpty()) {
                    Section(
                        title = stringResource(
                            when (detail) {
                                is ActivityDetailUi.Presentation -> R.string.activities_detail_label_3
                                is ActivityDetailUi.CredentialEvent -> R.string.activities_detail_label_3_issuance
                            }
                        )
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp)) {
                            detail.links.forEach { link ->
                                LinkRow(link = link, onClick = { onEventSend(ActivityDetailEvent.LinkClicked(link.uri)) })
                            }
                        }
                    }
                }

            },
            bottomContent = {
                BottomActions(detail = detail, onEventSend = onEventSend)
            },
        )
    }

    val credentialEvent = state.detail as? ActivityDetailUi.CredentialEvent
    if (state.showCredentialSheet && credentialEvent != null) {
        CredentialSheet(
            credential = credentialEvent.credential,
            onDismiss = { onEventSend(ActivityDetailEvent.CredentialSheetDismissed) },
        )
    }

    if (state.showDeleteDialog) {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.activities_panel_delete_entries_dialogue_single),
                confirmText = stringResource(R.string.eaa_inspection_dialog_delete_eaa_prim_button),
                dismissText = stringResource(R.string.eaa_inspection_dialog_delete_eaa_sec_button),
                onConfirm = { onEventSend(ActivityDetailEvent.DeleteConfirmed) },
                onDismiss = { onEventSend(ActivityDetailEvent.DeleteDismissed) },
                icon = AppIcons.Warning,
                iconTint = MaterialTheme.colorScheme.error,
            )
        )
    }
}

@Composable
private fun BottomActions(
    detail: ActivityDetailUi,
    onEventSend: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (detail.reportUri != null) {
            ExternalLinkButton(
                text = stringResource(R.string.activities_detail_sec_button),
                onClick = { onEventSend(ActivityDetailEvent.ReportClicked) },
            )
        }
        TextButton(
            modifier = Modifier.heightIn(min = BUTTON_HEIGHT.dp),
            onClick = { onEventSend(ActivityDetailEvent.DeleteClicked) },
        ) {
            Text(
                text = stringResource(R.string.activities_panel_delete_entries_button_3),
                style = MaterialTheme.typography.labelLarge,
                color = ThemeColors.errorOutline,
            )
        }
    }
}

private fun ActivityDetailUi.dateLabel(): Int = when (this) {
    is ActivityDetailUi.Presentation -> if (completed) {
        R.string.activities_detail_label_1
    } else {
        R.string.activities_detail_label_1_rejected
    }

    is ActivityDetailUi.CredentialEvent -> when {
        deleted && isPid -> R.string.activities_detail_label_pid_issuance_deleted
        deleted -> R.string.activities_detail_label_eaa_issuance_deleted
        isPid -> R.string.activities_detail_label_pid_issuance
        else -> R.string.activities_detail_label_issuance
    }
}

private fun PresentationOutcomeUi.bannerText(): Int? = when (this) {
    PresentationOutcomeUi.COMPLETED,
    PresentationOutcomeUi.NOT_COMPLETED,
        -> null

    PresentationOutcomeUi.REJECTED_BY_USER -> R.string.activities_detail_banner_rejection
    PresentationOutcomeUi.OVERASKING -> R.string.activities_detail_banner_reject_overasking
    PresentationOutcomeUi.UNKNOWN_SERVICE -> R.string.activities_detail_banner_reject_unkown
    PresentationOutcomeUi.REJECTED_BY_SERVICE -> R.string.activities_detail_banner_service_rejection
}

@Composable
private fun PresentationContent(detail: ActivityDetailUi.Presentation) {
    detail.purpose?.let { purpose ->
        Section(title = stringResource(R.string.pid_presentation_rp_info_label)) {
            Text(
                text = purpose,
                style = MaterialTheme.typography.bodyLarge.untrimmed(),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
    if (detail.credentials.isNotEmpty()) {
        // The service rejected what the wallet had already sent, so the data was shared.
        val shared = detail.completed || detail.outcome == PresentationOutcomeUi.REJECTED_BY_SERVICE
        val title = when {
            !shared -> R.string.activities_detail_label_2_rejected
            detail.credentials.size == 1 -> R.string.activities_detail_label_2_single
            else -> R.string.activities_detail_label_2
        }
        Section(title = stringResource(title)) {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)) {
                detail.credentials.forEach { SharedCredentialCard(it, struckThrough = !shared) }
            }
        }
    }
}

@Composable
private fun CredentialEventContent(
    detail: ActivityDetailUi.CredentialEvent,
    onViewCredential: () -> Unit,
) {
    val title = when {
        detail.deleted && detail.isPid -> R.string.activities_detail_label_pid_issuance_credential_deleted
        detail.deleted -> R.string.activities_detail_label_issuance_credential_deleted
        detail.isPid -> R.string.activities_detail_label_pid_issuance_credential
        else -> R.string.activities_detail_label_issuance_credential
    }
    val viewDetails = stringResource(R.string.generic_view_details)
    Section(title = stringResource(title)) {
        if (detail.isPid) {
            PidCredentialCard(
                modifier = Modifier.fillMaxWidth(),
                actionLabel = viewDetails,
                onAction = onViewCredential,
            )
        } else {
            EaaDocumentCard(
                modifier = Modifier.fillMaxWidth(),
                data = detail.card,
                minHeight = ISSUED_CARD_MIN_HEIGHT,
                footer = { EaaDocumentCardAction(text = viewDetails, onClick = onViewCredential) },
            )
        }
    }
}

/** The credential and the claims it held, opened from "view details". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialSheet(
    credential: SharedCredentialUi,
    onDismiss: () -> Unit,
) {
    WrapModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = ThemeShapes.EXTRA_LARGE.dp, topEnd = ThemeShapes.EXTRA_LARGE.dp),
        dragHandle = {},
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val maxSheetHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp() * SHEET_MAX_HEIGHT_FRACTION
        }
        // Header and close button stay put; only the claims scroll once the sheet is full.
        Column(
            modifier = Modifier
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            CredentialClaimsHeader(
                name = credential.name,
                issuer = credential.issuer,
                logo = { CredentialLogoPanel(avatar = credential.avatar) },
                variant = CredentialClaimsVariant.SHEET,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CredentialClaimList(
                variant = CredentialClaimsVariant.SHEET,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = SPACING_EXTRA_MEDIUM.dp, top = SPACING_SMALL.dp, end = SPACING_EXTRA_MEDIUM.dp),
                labels = credential.claimLabels,
            )
            WrapButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SPACING_MEDIUM.dp,
                        top = SPACING_LARGE.dp,
                        end = SPACING_MEDIUM.dp,
                        bottom = SPACING_EXTRA_MEDIUM.dp,
                    ),
                buttonConfig = ButtonConfig(type = ButtonType.SECONDARY, onClick = onDismiss),
            ) {
                Text(text = stringResource(R.string.global_close_button))
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.untrimmed(),
            color = ThemeColors.onBackgroundVariant,
        )
        content()
    }
}

@Composable
private fun SharedCredentialCard(credential: SharedCredentialUi, struckThrough: Boolean) {
    CredentialClaimsCard(
        name = credential.name,
        issuer = credential.issuer,
        claimLabels = credential.claimLabels,
        logo = { CredentialLogoPanel(avatar = credential.avatar) },
        struckThrough = struckThrough,
    )
}

/** The issuer's logo on its background color, as the credential cards draw it at their trailing edge. */
@Composable
private fun CredentialLogoPanel(avatar: ActivityAvatarUi) {
    val initials = when (avatar) {
        is ActivityAvatarUi.Initials -> avatar
        is ActivityAvatarUi.Logo -> avatar.fallback
        ActivityAvatarUi.PidEagle -> {
            PidEaglePanel()
            return
        }
    }
    val background = initials.backgroundColor?.let { parseCssColor(it, Color.Transparent) }
    if (background == null && avatar is ActivityAvatarUi.Initials) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = SPACING_MEDIUM.dp),
            contentAlignment = Alignment.Center,
        ) {
            ActivityAvatar(avatar = avatar)
        }
        return
    }
    var failed by remember(avatar) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(dimensionResource(R.dimen.eaa_colored_panel_width))
            .background(background ?: Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        when {
            avatar is ActivityAvatarUi.Logo && !failed -> WrapAsyncImage(
                source = avatar.uri,
                modifier = Modifier.sizeIn(
                    maxWidth = dimensionResource(R.dimen.eaa_logo_max_width),
                    maxHeight = dimensionResource(R.dimen.eaa_logo_max_height),
                ),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )

            background != null -> Text(
                text = initials.text,
                style = MaterialTheme.typography.headlineSmall,
                color = background.contentColor(),
            )

            else -> ActivityAvatar(avatar = initials)
        }
    }
}

@Composable
private fun PidEaglePanel() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(dimensionResource(R.dimen.eaa_colored_panel_width)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.pid_card_eagle),
            contentDescription = null,
            modifier = Modifier.sizeIn(
                maxWidth = dimensionResource(R.dimen.eaa_logo_max_width),
                maxHeight = dimensionResource(R.dimen.eaa_logo_max_height),
            ),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun LinkRow(
    link: ActivityLinkUi,
    onClick: () -> Unit,
) {
    val (icon, label) = when (link.kind) {
        ActivityLinkKind.SUPPORT -> AppIcons.Message to R.string.settings_overview_list_support
        ActivityLinkKind.PRIVACY_POLICY -> AppIcons.Shield to R.string.activities_detail_label_5
        ActivityLinkKind.WEBSITE -> AppIcons.Globe to R.string.activities_detail_label_6
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LINK_ROW_MIN_HEIGHT)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelLarge.untrimmed(),
                color = ThemeColors.onBackgroundVariant,
            )
            Text(
                text = link.displayText,
                style = MaterialTheme.typography.bodyLarge.untrimmed(),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        WrapIcon(iconData = AppIcons.OpenNew, customTint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LinkIcon(icon: IconData) {
    Box(
        modifier = Modifier
            .size(LINK_ICON_BACKGROUND_SIZE)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        WrapIcon(
            iconData = icon,
            modifier = Modifier.size(LINK_ICON_SIZE),
            customTint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ISSUED_CARD_MIN_HEIGHT = 120.dp
private val LINK_ROW_MIN_HEIGHT = 60.dp
private const val SHEET_MAX_HEIGHT_FRACTION = 0.8f
private val LINK_ICON_BACKGROUND_SIZE = 40.dp
private val LINK_ICON_SIZE = 20.dp

private val previewPresentation = ActivityDetailUi.Presentation(
    id = "1",
    title = "DeineBank.de",
    date = "12.03.2026 · 12:46 PM",
    links = listOf(
        ActivityLinkUi(ActivityLinkKind.SUPPORT, "https://deinebank.de/de/support"),
        ActivityLinkUi(ActivityLinkKind.PRIVACY_POLICY, "https://deinebank.de/de/datenschutz"),
        ActivityLinkUi(ActivityLinkKind.WEBSITE, "https://deinebank.de/de"),
    ),
    reportUri = "https://bfdi.bund.de",
    outcome = PresentationOutcomeUi.COMPLETED,
    purpose = "Altersverifizierung, um Alkohol zu kaufen.",
    credentials = listOf(
        SharedCredentialUi(
            name = "Digitaler Ausweis",
            issuer = "Bundesrepublik Deutschland",
            avatar = ActivityAvatarUi.Initials("DI"),
            claimLabels = listOf("Familienname", "Geburtsname", "Vorname(n)"),
        )
    ),
)

private val previewCredentialEvent = ActivityDetailUi.CredentialEvent(
    id = "2",
    title = "SozialPass added",
    date = "12.03.2026 · 12:46 PM",
    links = listOf(ActivityLinkUi(ActivityLinkKind.WEBSITE, "https://stadt.koeln")),
    reportUri = null,
    deleted = false,
    isPid = false,
    card = EaaCardData(
        id = "document",
        description = "Sozialpass",
        name = "Stadt Köln",
        backgroundColor = "#FF5722",
        backgroundImageUri = null,
        logoUri = null,
        validityDate = null,
    ),
    credential = SharedCredentialUi(
        name = "Sozialpass",
        issuer = "Stadt Köln",
        avatar = ActivityAvatarUi.Initials("SO", "#FF5722"),
        claimLabels = listOf("Familienname", "Vorname(n)"),
    ),
)

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun PresentationDetailPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivityDetailState(isLoading = false, detail = previewPresentation),
            onEventSend = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RejectedPresentationDetailPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivityDetailState(
                isLoading = false,
                detail = previewPresentation.copy(outcome = PresentationOutcomeUi.OVERASKING),
            ),
            onEventSend = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun CredentialEventDetailPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivityDetailState(isLoading = false, detail = previewCredentialEvent),
            onEventSend = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun DeletedPidDetailPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivityDetailState(
                isLoading = false,
                detail = previewCredentialEvent.copy(title = "Digital ID deleted", deleted = true, isPid = true),
                showCredentialSheet = true,
            ),
            onEventSend = {},
        )
    }
}
