@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.commonfeature.ui.request

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.commonfeature.ui.request.views.AccountLockedView
import eu.europa.ec.commonfeature.ui.request.views.CredentialDetailsView
import eu.europa.ec.commonfeature.ui.request.views.IdDeletionConfirmationDialog
import eu.europa.ec.commonfeature.ui.request.views.TemporaryPinBlockView
import eu.europa.ec.commonfeature.ui.request.views.footer.AccountLockedFooter
import eu.europa.ec.commonfeature.ui.request.views.footer.DefaultFooter
import eu.europa.ec.commonfeature.ui.request.views.footer.EnterPinFooter
import eu.europa.ec.commonfeature.ui.request.views.footer.TemporaryPinBlockFooter
import eu.europa.ec.commonfeature.ui.success.SuccessView
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.StickyButtonAction
import eu.europa.ec.uilogic.component.wrap.WrapStickyButton
import eu.europa.ec.uilogic.component.wrap.stickyBottomInsets
import org.sprind.wallet.uilogic.component.CodeEntryField
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.codeEntryStateForPreview
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.openDeepLink
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialog
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.uilogic.component.OrganisationRowDefaults
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.OrganisationRow
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogConfig
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogLink
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialog
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialogConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestScreen(
    navController: NavController,
    viewModel: RequestViewModel,
    fallbackReportUrl: String,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    // The report target may come from a certificate, so it is confirmed before leaving the app.
    var pendingReportLink by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    Content(
        state = state,
        onEventSend = { viewModel.setEvent(it) },
        bottomSheetState = bottomSheetState
    )

    if (state.shouldDisplayConfirmCancelDialog) {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.pid_presentation_dialog_rp_rejection_title),
                body = stringResource(R.string.pid_presentation_dialog_rp_rejection_paragraph),
                confirmText = stringResource(R.string.pid_presentation_dialog_rp_rejection_prim_button),
                dismissText = stringResource(R.string.pid_presentation_dialog_rp_rejection_sec_button_2),
                onConfirm = {
                    viewModel.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
                },
                onDismiss = {
                    viewModel.setEvent(Event.DismissClickOnCancelConfirmationDialog)
                },
                link = ConfirmationDialogLink(
                    text = stringResource(R.string.pid_presentation_dialog_rp_rejection_sec_button),
                    onClick = { pendingReportLink = state.reportUrl ?: fallbackReportUrl },
                ),
            )
        )
    }

    pendingReportLink?.let { link ->
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = link.toUri(),
                onConfirm = {
                    pendingReportLink = null
                    context.openUrl(link.toUri())
                },
                onDismiss = { pendingReportLink = null },
            )
        )
    }

    state.pendingReportIssueUrl?.let { link ->
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = link.toUri(),
                onConfirm = {
                    viewModel.setEvent(Event.ReportIssueRedirectHandled)
                    context.openUrl(link.toUri())
                },
                onDismiss = { viewModel.setEvent(Event.ReportIssueRedirectHandled) },
            )
        )
    }

    if (state.shouldDisplayCloseConfirmationDialog) {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.pid_presentation_dialog_cancel_title),
                body = stringResource(R.string.pid_presentation_dialog_cancel_paragraph),
                confirmText = stringResource(R.string.pid_presentation_dialog_cancel_prim_button),
                dismissText = stringResource(R.string.pid_presentation_dialog_cancel_sec_button),
                onConfirm = {
                    viewModel.setEvent(Event.ConfirmClickOnCloseConfirmationDialog)
                },
                onDismiss = {
                    viewModel.setEvent(Event.DismissClickOnCloseConfirmationDialog)
                },
            )
        )
    }

    if (state.shouldDisplayPidDeletionConfirmDialog) {
        IdDeletionConfirmationDialog(
            onDismiss = {
                viewModel.setEvent(Event.IdDeletionDialogDismissed)
            },
            onConfirm = {
                viewModel.setEvent(Event.DeletePidDocumentsRequested)
            }
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.DoWork)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(context, effect, navController)
                is Effect.CloseBottomSheet -> {
                    scope.launch {
                        bottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                            viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }

                Effect.HideKeyboard -> {
                    keyboardController?.hide()
                }
            }
        }.collect()
    }
}

private fun onNavigationRequested(
    context: Context,
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {

        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute)
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }

        is Effect.Navigation.PopTo -> {
            navController.popBackStack(
                route = navigationEffect.screenRoute,
                inclusive = false
            )
        }

        is Effect.Navigation.DeepLink -> {
            context.openDeepLink(navigationEffect.link)
        }
    }
}

@Composable
private fun Content(
    state: State,
    onEventSend: (Event) -> Unit,
    bottomSheetState: SheetState,
) {

    val navigatableAction =
        if (state.currentStep == RequestScreenStep.Initial ||
            state.currentStep is RequestScreenStep.TemporaryPinBlocked ||
            state.currentStep is RequestScreenStep.DocumentDeletedConfirmation ||
            state.currentStep is RequestScreenStep.AccountLocked
        ) {
            ScreenNavigateAction.NONE
        } else ScreenNavigateAction.BACKABLE

    val onHelp: () -> Unit = { onEventSend(Event.HelpPressed) }
    val onClose: () -> Unit = { onEventSend(Event.CloseButtonPressed) }

    val toolbar: (@Composable () -> Unit)? = when {
        state.currentStep == RequestScreenStep.Initial -> {
            {
                CancellableTopAppBar(
                    onHelpClick = onHelp.takeUnless { state.isLoading },
                    onCloseClick = onClose.takeUnless { state.isLoading },
                )
            }
        }

        state.isLoading ||
            state.currentStep is RequestScreenStep.AccountLocked ||
            state.currentStep is RequestScreenStep.TemporaryPinBlocked ||
            state.currentStep is RequestScreenStep.DocumentDeletedConfirmation -> {
            {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }

        //if it is null, navigatableAction.Backable it self creates the toolbar with back arrow
        else -> null
    }

    ContentScreen(
        navigatableAction = navigatableAction,
        genericErrorDialogConfig = state.errorDialog,
        topBar = toolbar,
        isLoading = state.isLoading,
        loadingText = stringResource(R.string.pid_presentation_loading_title_2),
        onBack = {
            onEventSend(Event.OnBackPressed)
        },
        stickyBottom = { paddingValues ->
            if (state.currentStep == RequestScreenStep.Initial) {
                RpInfoFooter(
                    paddingValues = paddingValues,
                    purpose = state.purpose,
                    onRejectPressed = { onEventSend(Event.CancelButtonPressed) },
                    onContinuePressed = { state.onContinueAction() },
                )
            } else {
                StepFooter(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(bottom = paddingValues.calculateBottomPadding()),
                    currentStep = state.currentStep,
                    pinState = state.pinState,
                    onCancelPressed = { onEventSend(Event.CancelButtonPressed) },
                    onContinuePressed = { state.onContinueAction() },
                    securityInfoClicked = { state.onSecurityInfoClicked() },
                    onEventSend = onEventSend
                )
            }
        },
        contentErrorConfig = state.error
    ) { paddingValues ->

        if (state.currentStep == RequestScreenStep.Initial) {
            RpInfoBody(
                modifier = Modifier.padding(paddingValues),
                title = state.headerConfig.title.orEmpty(),
                serviceName = state.verifierName,
            )
        } else {
            // Scrollable main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = paddingValues.calculateLeftPadding(LayoutDirection.Ltr))
                    .then(
                        other = if (state.noItems) Modifier else Modifier.verticalScroll(
                            rememberScrollState()
                        )
                    )
            ) {
                ContentHeader(
                    modifier = Modifier.fillMaxWidth(),
                    config = state.headerConfig,
                )

                when (state.currentStep) {

                    // Drawn by RpInfoBody above, which this branch is never reached for.
                    RequestScreenStep.Initial -> {
                    }

                    RequestScreenStep.CredentialPreview -> {
                        CredentialDetailsView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = SPACING_MEDIUM.dp),
                            state = state,
                            onEventSend = onEventSend
                        )
                    }

                    RequestScreenStep.EnterPin -> {
                        //TODO to add logo

                        CodeEntryField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SPACING_EXTRA_MEDIUM.dp),
                            state = state.pinState,
                            onCodeChange = { onEventSend(Event.OnPinEntered) },
                        )
                    }

                    is RequestScreenStep.TemporaryPinBlocked -> {
                        TemporaryPinBlockView(
                            modifier = Modifier.fillMaxSize(),
                            remainingTime = state.unBlockRemainingTime
                        )
                    }

                    RequestScreenStep.AccountLocked -> {
                        AccountLockedView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = SPACING_MEDIUM.dp)
                        )
                    }

                    RequestScreenStep.Completed,
                        -> {/* Navigate or show success */
                    }

                    RequestScreenStep.DocumentDeletedConfirmation -> {
                        SuccessView(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (state.isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = {
                    onEventSend(
                        Event.BottomSheet.UpdateBottomSheetState(
                            isOpen = false
                        )
                    )
                },
                sheetState = bottomSheetState
            ) {
                SheetContent(
                    sheetContent = state.sheetContent,
                )
            }
        }
    }
}

@Composable
private fun RpInfoBody(
    title: String,
    serviceName: String?,
    modifier: Modifier = Modifier,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = CustomTypography.titleMedium,
        )
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(verticalSpacing = TITLE_TO_SERVICE_GAP),
            title = { Text(title) },
            extraContent = {
                OrganisationRow(
                    name = serviceName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.generic_default_relying_party_name),
                    icon = AppIcons.Transfer,
                    style = OrganisationRowDefaults.service,
                    trailingIcon = AppIcons.Info,
                )
            },
        )
    }
}

private val TITLE_TO_SERVICE_GAP = 36.dp

@Composable
private fun RpInfoFooter(
    paddingValues: PaddingValues,
    purpose: String?,
    onRejectPressed: () -> Unit,
    onContinuePressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.stickyBottomInsets(paddingValues),
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
    ) {
        ContentNotice {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                Text(
                    text = stringResource(R.string.pid_presentation_rp_info_paragraph_1),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = purpose ?: stringResource(R.string.pid_presentation_rp_info_paragraph_2),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WrapStickyButton(
                action = StickyButtonAction(
                    text = stringResource(R.string.pid_presentation_rp_info_sec_button),
                    onClick = onRejectPressed,
                ),
                type = ButtonType.SECONDARY,
                modifier = Modifier.weight(WEIGHT_1),
            )
            WrapStickyButton(
                action = StickyButtonAction(
                    text = stringResource(R.string.pid_presentation_rp_info_prim_button),
                    onClick = onContinuePressed,
                    trailingIcon = AppIcons.ArrowRightLong,
                ),
                type = ButtonType.PRIMARY,
                modifier = Modifier.weight(WEIGHT_1),
            )
        }
    }
}

@Composable
private fun StepFooter(
    modifier: Modifier,
    currentStep: RequestScreenStep,
    pinState: CodeEntryState,
    onCancelPressed: () -> Unit,
    onContinuePressed: () -> Unit,
    securityInfoClicked: () -> Unit,
    onEventSend: (Event) -> Unit,
) {


    when (currentStep) {
        RequestScreenStep.EnterPin -> EnterPinFooter(
            modifier = modifier,
            isPinValid = pinState.isValid,
            securityInfoClicked = securityInfoClicked,
            onContinuePressed = onContinuePressed
        )

        RequestScreenStep.AccountLocked -> AccountLockedFooter(
            modifier = modifier,
            onPidDeletionButtonClicked = { onEventSend(Event.PidDeletionRequested) },
            onGoToDashboardButtonPressed = { onEventSend(Event.GoToDashboardButtonPressed) }
        )

        is RequestScreenStep.TemporaryPinBlocked -> TemporaryPinBlockFooter(
            modifier = modifier,
            isLastTry = currentStep.isLastTry,
            bottomSheetButtonPressed = {
                onEventSend(Event.TemporaryPinBlockedBottomSheetButtonPressed)
                onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
            }
        )

        is RequestScreenStep.DocumentDeletedConfirmation -> {
            //Show nothing
        }

        else -> DefaultFooter(
            modifier = modifier,
            onCancelPressed = onCancelPressed,
            onContinuePressed = onContinuePressed
        )
    }

}

@Composable
private fun SheetContent(
    sheetContent: RequestBottomSheetContent,
) {
    GenericBottomSheet(
        titleContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sheetContent.isInfo) {
                    WrapIcon(
                        iconData = AppIcons.Info,
                        customTint = ThemeColors.onSecondaryButton
                    )
                    HSpacer.Medium()
                }
                Text(
                    text = sheetContent.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                )
            }
        },
        bodyContent = {
            Column(
                modifier = Modifier.padding(top = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                Text(
                    text = sheetContent.description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                sheetContent.buttonText?.let { text ->
                    WrapButton(
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            onClick = { sheetContent.onButtonClick() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SPACING_SMALL.dp),
                    ) {
                        WrapText(
                            textConfig = TextConfig(
                                style = ThemeTextStyles.onSecondaryButton,
                                color = ThemeColors.onSecondaryButton,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SPACING_MEDIUM.dp),
                            text = text,
                        )
                    }
                }
            }
        }
    )
}


@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentPreviewRpInfo() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_rp_info_title),
                    relyingPartyData = null,
                ),
                verifierName = "BVG Mobility",
                purpose = stringResource(R.string.__variable_text_rp_deinebank_var_purpose_1),
                currentStep = RequestScreenStep.Initial
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.request_relying_party_title),
                    purposeText = stringResource(R.string.request_header_main_text),
                    relyingPartyData = RelyingPartyData(
                        isVerified = true,
                        name = stringResource(R.string.request_relying_party_default_name),
                        description = stringResource(R.string.request_relying_party_description)
                    )
                ),
                currentStep = RequestScreenStep.EnterPin
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun SheetContentWarningPreview() {
    PreviewTheme {
        SheetContent(
            sheetContent = RequestBottomSheetContent(
                "Why my data is not fully displayed at this moment in time?",
                "Here your description"
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun SheetContentWithButtonPreview() {
    PreviewTheme {
        SheetContent(
            sheetContent = RequestBottomSheetContent(
                "Why my data is not fully displayed at this moment in time?",
                "Here your description",
                buttonText = "Button"
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TemporaryPinBlockPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_retry_counter_counter_title),
                ),
                currentStep = RequestScreenStep.TemporaryPinBlocked(
                    isLastTry = false,
                ),
                unBlockRemainingTime = "10:00"
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TemporaryPinBlockLastTryPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_retry_counter_counter_title),
                ),
                currentStep = RequestScreenStep.TemporaryPinBlocked(
                    isLastTry = true,
                ),
                unBlockRemainingTime = "07:10:00"
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun AccountLockedPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_retry_counter_locked_title),
                ),
                currentStep = RequestScreenStep.AccountLocked
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterPinPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_wallet_pin_entry_title),
                ),
                currentStep = RequestScreenStep.EnterPin
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterPinWrongPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_wallet_pin_entry_title),
                ),
                currentStep = RequestScreenStep.EnterPin,
                pinState = codeEntryStateForPreview(
                    capacity = CodeLength.WALLET_PIN,
                    supportingText = stringResource(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin),
                )
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DeleteDocumentSuccessPreview() {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                headerConfig = ContentHeaderConfig(
                    title = stringResource(R.string.pid_presentation_retry_counter_reset_success),
                ),
                currentStep = RequestScreenStep.DocumentDeletedConfirmation
            ),
            onEventSend = {},
            bottomSheetState = bottomSheetState
        )
    }
}
