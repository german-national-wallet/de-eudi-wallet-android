/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.issuancefeature.ui.document.offer

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.ErrorInfo
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapStickyTwoButtons
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.extension.cacheDeepLink
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.EaaDocumentCard
import org.sprind.wallet.uilogic.component.OrganisationRow
import org.sprind.wallet.uilogic.component.EaaDocumentCardAction
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialog
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogConfig

private const val SHEET_CARD_HEIGHT = 78

/** The details sheet never takes more than this share of the window's height. */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.8f

/** Height and strength of the shadow hinting that the claim list scrolls further. */
private const val SCROLL_SHADOW_HEIGHT = SPACING_MEDIUM
private const val SCROLL_SHADOW_ALPHA = 0.12f

@Composable
fun DocumentOfferScreen(
    navController: NavController,
    viewModel: DocumentOfferViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.showRejectConfirmationDialog) {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.eaa_issuance_dialog_issuer_rejection_title),
                body = stringResource(R.string.eaa_issuance_dialog_issuer_rejection_paragraph),
                confirmText = stringResource(R.string.eaa_issuance_dialog_issuer_rejection_prim_button),
                dismissText = stringResource(R.string.eaa_issuance_dialog_issuer_rejection_sec_button),
                onConfirm = {
                    viewModel.setEvent(Event.RejectConfirmed)
                },
                onDismiss = {
                    viewModel.setEvent(Event.DismissRejectConfirmationDialog)
                },
            )
        )
    }

    DocumentOfferLayout(
        state = state,
        onBack = { viewModel.setEvent(Event.OnReject) },
        onHelpClicked = { viewModel.setEvent(Event.HelpPressed) },
        onAddClicked = { viewModel.setEvent(Event.StickyButtonPressed(context)) },
        viewModel = viewModel
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(context, effect, navController)
            }
        }.collect()
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init(context.getPendingDeepLink()))
    }

    SystemBroadcastReceiver(
        actions = listOf(
            CoreActions.VCI_RESUME_ACTION,
            CoreActions.VCI_DYNAMIC_PRESENTATION
        )
    ) {
        when (it?.action) {
            CoreActions.VCI_RESUME_ACTION -> it.extras?.getString("uri")?.let { link ->
                viewModel.setEvent(Event.OnResumeIssuance(link))
            }

            CoreActions.VCI_DYNAMIC_PRESENTATION -> it.extras?.getString("uri")?.let { link ->
                viewModel.setEvent(Event.OnDynamicPresentation(link))
            }
        }
    }
}

@Composable
private fun DocumentOfferLayout(
    state: State,
    onBack: () -> Unit = {},
    onHelpClicked: () -> Unit = {},
    onAddClicked: () -> Unit = {},
    viewModel: DocumentOfferViewModel? = null,
) {
    ContentScreen(
        isLoading = state.isLoading,
        loadingText = stringResource(state.loadingText),
        hideTopBar = state.isLoading,
        contentErrorConfig = state.error,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = onBack,
        topBar = {
            CancellableTopAppBar(
                onHelpClick = onHelpClicked.takeUnless { state.isLoading },
                onCloseClick = onBack.takeUnless { state.isLoading },
            )
        },
        stickyBottom = { paddingValues ->
            val leadsToTransactionCode = state.txCodeLength != null
            WrapStickyTwoButtons(
                paddingValues = paddingValues,
                secondaryText = stringResource(R.string.eaa_issuance_eaa_info_sec_button),
                primaryText = stringResource(
                    if (leadsToTransactionCode) {
                        R.string.eaa_issuance_eaa_info_prim_button_code
                    } else {
                        R.string.eaa_issuance_eaa_info_prim_button
                    }
                ),
                onSecondaryClick = onBack,
                onPrimaryClick = onAddClicked,
                primaryTrailingIcon = AppIcons.ArrowRightLong.takeIf { leadsToTransactionCode },
            )
        }
    ) { paddingValues ->
        Content(
            state = state,
            paddingValues = paddingValues,
            viewModel = viewModel
        )
    }

}

@Composable
private fun Content(
    state: State,
    paddingValues: PaddingValues,
    viewModel: DocumentOfferViewModel? = null,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = CustomTypography.titleMedium,
        )
    ) {
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            title = { Text(state.headerConfig.title.orEmpty()) },
            extraContent = {
                if (state.noDocument) {
                    ErrorInfo(
                        modifier = Modifier.fillMaxWidth(),
                        informativeText = stringResource(id = R.string.issuance_document_offer_error_no_document)
                    )
                } else {
                    OfferedDocuments(
                        documents = state.documents,
                        eaaCardDataMap = state.eaaCardDataMap,
                        onShowDetails = { documentId ->
                            viewModel?.setEvent(Event.ShowDocumentDetails(documentId))
                        },
                    )
                }

                state.headerConfig.relyingPartyData?.let { relyingPartyData ->
                    IssuerSection(
                        relyingPartyData = relyingPartyData,
                        onViewIssuerDetails = {
                            viewModel?.setEvent(Event.ViewIssuerDetails)
                        },
                    )
                }
            },
        )
    }

    state.detailsSheetDocumentId?.let { documentId ->
        DocumentDetailsBottomSheet(
            state = state,
            documentId = documentId,
            onDismiss = { viewModel?.setEvent(Event.DismissDocumentDetails) },
        )
    }
}

/**
 * The offered credentials, each rendered as an [EaaDocumentCard] whose "view data" action opens
 * [DocumentDetailsBottomSheet] with the claims it would add to the wallet.
 *
 * A plain [Column] rather than a `LazyColumn`: an offer carries a handful of credentials at most,
 * and [ContentTemplateBody] already scrolls its body, which a nested lazy list cannot live inside.
 */
@Composable
private fun OfferedDocuments(
    documents: List<ListItemData>,
    eaaCardDataMap: Map<String, EaaCardData>,
    onShowDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
    ) {
        documents.forEach { document ->
            val documentId = document.itemId
            val cardData = eaaCardDataMap[documentId]

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                if (cardData != null) {
                    EaaDocumentCard(
                        modifier = Modifier.fillMaxWidth(),
                        data = cardData,
                        footer = {
                            EaaDocumentCardAction(
                                text = stringResource(R.string.eaa_card_view_data),
                                onClick = { onShowDetails(documentId) },
                            )
                        },
                    )
                } else {
                    // No card metadata to render: the credential still has to be nameable and its
                    // claims still have to be reachable, so the card's two parts stand on their own.
                    Text(
                        text = document.overlineText
                            ?: stringResource(R.string.generic_default_document_name),
                        style = CustomTypography.titleMediumLarge,
                        modifier = Modifier.padding(SPACING_SMALL.dp)
                    )
                    EaaDocumentCardAction(
                        modifier = Modifier.padding(horizontal = SPACING_SMALL.dp),
                        text = stringResource(R.string.eaa_card_view_data),
                        onClick = { onShowDetails(documentId) },
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet listing the claims an offered credential would add to the wallet, headed by the
 * credential's own [EaaDocumentCard] so the sheet reads as a continuation of the card that opened it.
 *
 * Closing through the button animates the sheet away before the dismissal reaches the view model,
 * the same way the request screen closes its sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailsBottomSheet(
    state: State,
    documentId: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // No half-open state: the sheet cannot be dragged, so a half-open sheet could never be pulled
    // up, and its sticky close button would sit below the screen edge.
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cardData = state.eaaCardDataMap[documentId]
    val fallbackTitle = state.documents.firstOrNull { it.itemId == documentId }?.overlineText
        ?: stringResource(R.string.generic_default_document_name)
    val labels = state.documentClaimLabels[documentId].orEmpty()

    WrapModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {},
        sheetGesturesEnabled = false,
    ) {
        DocumentDetailsSheetContent(
            cardData = cardData,
            fallbackTitle = fallbackTitle,
            labels = labels,
            onClose = {
                scope.launch {
                    bottomSheetState.hide()
                }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

@Composable
private fun DocumentDetailsSheetContent(
    cardData: EaaCardData?,
    fallbackTitle: String,
    labels: List<String>,
    onClose: () -> Unit,
) {
    val maxSheetHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * SHEET_MAX_HEIGHT_FRACTION
    }

    // The height cap is what makes the header and the close button sticky: once the content no
    // longer fits, only the claim list in between shrinks and scrolls. GenericBottomSheet does not
    // pad for the navigation bar, so that stays out here. Its own padding is zeroed so the card
    // runs edge to edge, and its color matches the sheet's.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .navigationBarsPadding()
    ) {
        GenericBottomSheet(
            sheetBackgroundColor = MaterialTheme.colorScheme.surface,
            sheetPadding = PaddingValues(),
            titleContent = {
                DocumentDetailsSheetHeader(cardData = cardData, fallbackTitle = fallbackTitle)
            },
            bodyContent = {
                DocumentDetailsSheetBody(labels = labels, onClose = onClose)
            },
            spacer = { VSpacer.Empty() }
        )
    }
}

@Composable
private fun DocumentDetailsSheetHeader(
    cardData: EaaCardData?,
    fallbackTitle: String,
) {
    if (cardData != null) {
        // The card draws itself as a cut-off strip, so its bounds, its outline and what is
        // visible are one and the same box. It is SHEET_CARD_HEIGHT tall while its text fits and
        // grows with the text at larger font sizes, so the issuer line stays readable.
        // The card stretches to whatever height it is offered (it pins its optional footer to
        // the bottom), and the sheet offers its whole height here, so it is measured unbounded to
        // keep it at its own height.
        EaaDocumentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .semantics { heading() },
            data = cardData,
            minHeight = SHEET_CARD_HEIGHT.dp,
            bottomCutOff = true,
        )
    } else {
        // Same fallback as the offer list: without card metadata the credential is named by
        // its document name alone.
        Text(
            text = fallbackTitle,
            style = CustomTypography.titleMediumLarge,
            modifier = Modifier
                .padding(horizontal = SPACING_LARGE.dp)
                .padding(top = SPACING_LARGE.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun DocumentDetailsSheetBody(
    labels: List<String>,
    onClose: () -> Unit,
) {
    // GenericBottomSheet hands the body whatever height the header leaves, so `fill = false` lets
    // the claim list take only what it needs on a short list and the rest of that on a long one,
    // always leaving room for the button below it.
    Column(modifier = Modifier.fillMaxWidth()) {
        ScrollableClaimLabels(
            labels = labels,
            modifier = Modifier.weight(1f, fill = false),
        )

        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = onClose,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
                .padding(top = SPACING_MEDIUM.dp, bottom = SPACING_LARGE.dp),
        ) {
            WrapText(
                textConfig = TextConfig(
                    style = ThemeTextStyles.onSecondaryButton,
                    color = ThemeColors.onSecondaryButton,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.global_close_button),
            )
        }
    }
}

/**
 * The claim labels, scrolling on their own, with a soft shadow along the bottom edge while more of
 * the list is hidden below. The shadow fades out once the list reaches its end, and never shows on
 * a list that fits.
 */
@Composable
private fun ScrollableClaimLabels(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val shadowAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        label = "claimListShadowAlpha",
    )
    val shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCROLL_SHADOW_ALPHA)
    val shadowBrush = remember(shadowColor) {
        Brush.verticalGradient(listOf(Color.Transparent, shadowColor))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // No top padding: GenericBottomSheet already puts a medium spacer below the header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = SPACING_MEDIUM.dp)
                .padding(vertical = SPACING_EXTRA_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_MEDIUM.dp)
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index < labels.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.tertiaryContainer)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SCROLL_SHADOW_HEIGHT.dp)
                .graphicsLayer { alpha = shadowAlpha }
                .background(shadowBrush)
        )
    }
}

/**
 * The "who is issuing this" footer: a heading over an [OrganisationRow] that opens the issuer
 * details screen.
 */
@Composable
private fun IssuerSection(
    relyingPartyData: RelyingPartyData,
    onViewIssuerDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SPACING_SMALL.dp)
    ) {
        Text(
            stringResource(R.string.pid_inspection_pid_issuer_title),
            style = ThemeTextStyles.titleMediumLarge,
            color = ThemeColors.onBackgroundVariant,
        )

        OrganisationRow(
            name = relyingPartyData.name,
            icon = AppIcons.Building,
            trailingIcon = AppIcons.Info,
            onClick = onViewIssuerDetails,
        )
    }
}

private fun handleNavigationEffect(
    context: Context,
    navigationEffect: Effect.Navigation,
    navController: NavController
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                if (navigationEffect.shouldPopToSelf) {
                    popUpTo(IssuanceScreens.DocumentOffer.screenRoute) {
                        inclusive = true
                    }
                }
            }
        }

        is Effect.Navigation.PopBackStackUpTo -> {
            navController.popBackStack(
                route = navigationEffect.screenRoute,
                inclusive = navigationEffect.inclusive
            )
        }

        is Effect.Navigation.DeepLink -> {
            navigationEffect.routeToPop?.let {
                context.cacheDeepLink(navigationEffect.link)
                navController.popBackStack(
                    route = it,
                    inclusive = false
                )
            } ?: handleDeepLinkAction(navController, navigationEffect.link)
        }

        is Effect.Navigation.Pop -> navController.popBackStack()

        is Effect.Navigation.NavigateToIssuerDetails -> {
            navController.navigate(navigationEffect.issuerInfo)
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ScreenPreview() {
    PreviewTheme {
        val previewState = State(
            isLoading = false,
            error = null,
            isInitialised = true,
            documents = listOf(
                ListItemData(
                    itemId = "doc_1",
                    mainContentData = ListItemMainContentData.Text(text = "PID")
                )
            ),
            eaaCardDataMap = mapOf(
                "doc_1" to EaaCardData(
                    id = "doc_1",
                    description = "Mitgliedskarte",
                    name = "VERS",
                    backgroundColor = "#FFC400",
                    backgroundImageUri = null,
                    logoUri = null,
                    validityDate = null,
                )
            ),
            noDocument = false,
            headerConfig = ContentHeaderConfig(
                title = stringResource(R.string.eaa_issuance_eaa_info_title),
                relyingPartyData = RelyingPartyData(
                    isVerified = true,
                    name = stringResource(R.string.issuance_document_offer_relying_party_default_name),
                    description = stringResource(R.string.issuance_document_offer_relying_party_description)
                )
            ),
            offerUiConfig = OfferUiConfig(
                offerURI = "",
                onSuccessNavigation = ConfigNavigation(
                    navigationType = NavigationType.PushScreen(
                        screen = DashboardScreens.Dashboard,
                        popUpToScreen = IssuanceScreens.AddDocument
                    )
                ),
                onCancelNavigation = ConfigNavigation(
                    navigationType = NavigationType.Pop
                )
            )
        )

        DocumentOfferLayout(
            state = previewState,
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DocumentDetailsSheetContentPreview() {
    PreviewTheme {
        DocumentDetailsSheetContent(
            cardData = EaaCardData(
                id = "doc_1",
                description = "Mitgliedskarte",
                name = "ADAC",
                backgroundColor = "#FFC400",
                backgroundImageUri = null,
                logoUri = null,
                validityDate = null,
            ),
            fallbackTitle = "Mitgliedskarte",
            labels = listOf("Familienname", "Geburtsname", "Vorname(n)", "Geburtsdatum"),
            onClose = {},
        )
    }
}