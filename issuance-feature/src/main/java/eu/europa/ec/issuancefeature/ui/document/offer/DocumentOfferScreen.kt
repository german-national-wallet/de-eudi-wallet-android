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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.ErrorInfo
import eu.europa.ec.uilogic.component.IconData
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
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.extension.cacheDeepLink
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.EaaDocumentCard
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

@Composable
fun DocumentOfferScreen(
    navController: NavController,
    viewModel: DocumentOfferViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DocumentOfferLayout(
        state = state,
        onBack = { viewModel.setEvent(Event.BackButtonPressed) },
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
        lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
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
    onAddClicked: () -> Unit = {},
    viewModel: DocumentOfferViewModel? = null,
) {
    ContentScreen(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = onBack,
        stickyBottom = { paddingValues ->
            WrapStickyBottomContent(
                stickyBottomModifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                stickyBottomConfig = StickyBottomConfig(
                    type = StickyBottomType.Generic
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        SPACING_MEDIUM.dp,
                        Alignment.CenterHorizontally
                    ),
                    modifier = Modifier.padding(paddingValues)
                ) {
                    WrapButton(
                        modifier = Modifier.weight(1f),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            onClick = onBack
                        )
                    ) {
                        Text(text = stringResource(R.string.eaa_issuance_eaa_info_sec_button))
                    }
                    WrapButton(
                        modifier = Modifier.weight(1f),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onAddClicked
                        )
                    ) {
                        Text(text = stringResource(R.string.eaa_issuance_eaa_info_prim_button_no_code))
                    }
                }
            }
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
    val description = state.headerConfig.description

    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        )
    ) {
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_LARGE.dp),
            title = { Text(state.headerConfig.title.orEmpty()) },
            body = if (description != null) {
                { Text(description) }
            } else {
                null
            },
            extraContent = {
                if (state.noDocument) {
                    ErrorInfo(
                        modifier = Modifier.fillMaxWidth(),
                        informativeText = stringResource(id = R.string.issuance_document_offer_error_no_document)
                    )
                } else {
                    OfferedDocuments(
                        documents = state.documents,
                        documentDetails = state.documentDetails,
                        eaaCardDataMap = state.eaaCardDataMap,
                        expandedDocumentIds = state.expandedDocumentIds,
                        onToggleExpanded = { documentId ->
                            viewModel?.setEvent(Event.ToggleDocumentExpanded(documentId))
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
}

/**
 * The offered credentials, each rendered as an [EaaDocumentCard] with an expandable list of the
 * claims it would add to the wallet.
 *
 * A plain [Column] rather than a `LazyColumn`: an offer carries a handful of credentials at most,
 * and [ContentTemplateBody] already scrolls its body, which a nested lazy list cannot live inside.
 */
@Composable
private fun OfferedDocuments(
    documents: List<ListItemData>,
    documentDetails: Map<String, List<Pair<String, String>>>,
    eaaCardDataMap: Map<String, EaaCardData>,
    expandedDocumentIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
    ) {
        documents.forEach { document ->
            val documentId = document.itemId
            val isExpanded = documentId in expandedDocumentIds
            val details = documentDetails[documentId] ?: emptyList()
            val cardData = eaaCardDataMap[documentId]

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                if (cardData != null) {
                    EaaDocumentCard(
                        modifier = Modifier.fillMaxWidth(),
                        data = cardData,
                    )
                } else {
                    Text(
                        text = document.overlineText
                            ?: stringResource(R.string.generic_default_document_name),
                        style = CustomTypography.titleMediumLarge,
                        modifier = Modifier.padding(SPACING_SMALL.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpanded(documentId) }
                        .padding(horizontal = SPACING_SMALL.dp, vertical = SPACING_MEDIUM.dp)
                        .semantics(mergeDescendants = true) { role = Role.Button },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.eaa_card_view_data),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isExpanded && details.isNotEmpty()) {
                    ClaimRows(details = details)
                }
            }
        }
    }
}

/**
 * The claim label/value pairs revealed when a credential is expanded.
 *
 * Each pair is merged for accessibility so it is announced as "<label>, <value>" instead of the
 * label and the value landing as two unrelated nodes the reader cannot associate.
 */
@Composable
private fun ClaimRows(
    details: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        details.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * The "who is issuing this" footer: a row that opens the issuer details screen.
 *
 * The row is merged and exposed as a button so it is announced as one tappable "<issuer name>"
 * item; the trailing chevron is decorative and hidden, since "arrow right" tells a screen-reader
 * user nothing the button role does not already convey.
 */
@Composable
private fun IssuerSection(
    relyingPartyData: RelyingPartyData,
    onViewIssuerDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pid_inspection_pid_issuer_title),
            style = CustomTypography.titleMediumLarge,
            modifier = Modifier.padding(bottom = SPACING_MEDIUM.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewIssuerDetails() }
                .semantics(mergeDescendants = true) { role = Role.Button }
        ) {
            WrapIcon(
                iconData = IconData(
                    resourceId = R.drawable.ic_id,
                    contentDescriptionId = R.string.content_description_info_icon
                ),
                modifier = Modifier.size(SIZE_LARGE.dp),
            )

            Text(
                text = relyingPartyData.name,
                modifier = Modifier.padding(horizontal = SPACING_SMALL.dp)
            )

            Spacer(modifier = Modifier.weight(1f))
            WrapIcon(
                iconData = IconData(
                    resourceId = R.drawable.ic_arrow_right,
                    contentDescriptionId = R.string.content_description_arrow_right_icon
                ),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
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
            noDocument = false,
            headerConfig = ContentHeaderConfig(
                title = stringResource(R.string.eaa_issuance_eaa_info_title),
                description = stringResource(R.string.issuance_document_offer_description),
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