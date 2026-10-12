package eu.europa.ec.commonfeature.ui.issuer_details

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ClickableArea
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.openUrl
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuerDetailsContent(
    navController: NavController,
    viewModel: IssuerDetailsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    ContentScreen(
        toolBarConfig = ToolbarConfig(
            actions = listOf(
                ToolbarAction(icon = AppIcons.Help, onClick = { viewModel.setEvent(Event.HelpPressed) })
            )
        ),
        onBack = { viewModel.setEvent(Event.Pop) },
        stickyBottom = { padding ->
            IssuerDetailsStickyButton(
                modifier = Modifier.padding(padding),
                documentType = state.issuerData.documentType,
                onClick = { viewModel.setEvent(Event.Report) }
            )
        },
    ) { paddingValues ->
        IssuerDetailsBody(
            issuerData = state.issuerData,
            onEvent = viewModel::setEvent,
            modifier = Modifier.padding(paddingValues)
        )
        IssuerDetailsNavigationEffect(
            navController = navController,
            viewModel = viewModel
        )
    }
}

@Composable
private fun IssuerDetailsStickyButton(
    modifier: Modifier = Modifier,
    documentType: DocumentType,
    onClick: () -> Unit,
) {
    WrapButton(
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = onClick
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        TextAndIcon(
            textValue = stringResource(documentType.issuerDetailsStrings().secButton),
            textConfig = TextConfig(
                style = ThemeTextStyles.onSecondaryButton,
                color = ThemeColors.onSecondaryButton,
            ),
            rightIconData = AppIcons.OpenNew,
            iconModifier = Modifier
                .padding(start = SPACING_SMALL.dp)
                .size(18.dp),
            customTint = ThemeColors.onSecondaryButton
        )
    }
}

@Composable
private fun IssuerDetailsBody(
    issuerData: IssuerInfo,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
        horizontalAlignment = Alignment.Start
    ) {
        WrapText(
            text = stringResource(issuerData.documentType.issuerDetailsStrings().title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        IssuerLogo(issuerData)
        IssuerDetailsItems(issuerData, onEvent)
    }
}

@Composable
private fun IssuerLogo(issuerData: IssuerInfo) {
    val logoModifier = Modifier
        .size(56.dp)
        .clip(CircleShape)

    when {
        issuerData.logoUri != null -> {
            WrapAsyncImage(
                contentDescription = issuerData.issuerName,
                modifier = logoModifier,
                source = issuerData.logoUri
            )
        }

        issuerData.imageRes != null -> {
            WrapImage(
                contentDescription = issuerData.issuerName,
                modifier = logoModifier,
                painter = painterResource(issuerData.imageRes)
            )
        }
    }
}

@Composable
private fun IssuerDetailsItems(issuerData: IssuerInfo, onEvent: (Event) -> Unit) {
    IssuerDetailItem(
        leadingIcon = AppIcons.Building,
        label = stringResource(issuerData.documentType.issuerDetailsStrings().labelName),
        value = issuerData.issuerName
    )

    issuerData.optionalDetails().forEach { item ->
        IssuerDetailItem(
            leadingIcon = item.leadingIcon,
            label = stringResource(item.labelRes),
            value = item.value,
            trailingIcon = item.trailingIcon,
            onClick = item.clickEvent
                ?.takeIf { item.value.isNotBlank() }
                ?.let { event -> { onEvent(event) } },
        )
    }
}

@Composable
private fun IssuerDetailsNavigationEffect(
    navController: NavController,
    viewModel: IssuerDetailsViewModel,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                Effect.Navigation.Pop -> navController.popBackStack()
                is Effect.Navigation.SwitchScreen -> Unit
                is Effect.Navigation.OpenLink -> context.openUrl(effect.uri)
            }
        }
    }
}

@Composable
private fun IssuerDetailItem(
    leadingIcon: IconData,
    label: String,
    value: String,
    trailingIcon: IconData? = null,
    onClick: (() -> Unit)? = null,
) {

    ListItem(
        item = ListItemData(
            itemId = "",
            leadingContentData = ListItemLeadingContentData.Icon(
                size = 20,
                iconData = leadingIcon,
                tint = MaterialTheme.colorScheme.onSurface
            ),
            overlineText = label,
            mainContentData = ListItemMainContentData.Text(value.ifBlank { "-" }),
            trailingContentData = trailingIcon?.takeIf { value.isNotBlank() }?.let {
                ListItemTrailingContentData.Icon(
                    size = 20,
                    iconData = it,
                )
            },
        ),
        overlineTextStyle = MaterialTheme.typography.labelLarge.copy(
            color = ThemeColors.onBackgroundVariant
        ),
        onItemClick = onClick?.let { { _ -> it() } },
        clickableAreas = listOf(ClickableArea.ENTIRE_ROW, ClickableArea.TRAILING_CONTENT),
    )
}

private data class IssuerDetailUiItem(
    val leadingIcon: IconData,
    val labelRes: Int,
    val value: String,
    val trailingIcon: IconData? = null,
    /** Sent when the row is tapped; `null` for rows that are not actionable. */
    val clickEvent: Event? = null,
)

/** The always-present strings that differ between the PID and EAA flows. */
private data class IssuerDetailsStrings(
    val title: Int,
    val secButton: Int,
    val labelName: Int,
)

private fun DocumentType.issuerDetailsStrings(): IssuerDetailsStrings = when (this) {
    DocumentType.PID -> IssuerDetailsStrings(
        title = R.string.pid_issuance_data_consent_issuer_title,
        secButton = R.string.pid_issuance_data_consent_issuer_sec_button,
        labelName = R.string.pid_issuance_data_consent_issuer_label_name,
    )

    DocumentType.EAA -> IssuerDetailsStrings(
        title = R.string.eaa_issuance_eaa_info_issuer_title,
        secButton = R.string.eaa_issuance_eaa_info_issuer_sec_button,
        labelName = R.string.eaa_issuance_eaa_info_issuer_label_name,
    )
}

/** The rows shown below "Name"; PID and EAA show different sets of fields, not just different labels. */
private fun IssuerInfo.optionalDetails(): List<IssuerDetailUiItem> =
    when (documentType) {
        DocumentType.PID -> listOf(
            IssuerDetailUiItem(
                leadingIcon = AppIcons.Flag,
                labelRes = R.string.pid_issuance_data_consent_issuer_label_address,
                value = address
            ),
            IssuerDetailUiItem(
                leadingIcon = AppIcons.EmailGlobe,
                labelRes = R.string.pid_issuance_data_consent_issuer_label_email,
                value = email
            ),
            IssuerDetailUiItem(
                leadingIcon = AppIcons.Shield,
                labelRes = R.string.pid_issuance_data_consent_issuer_label_privacy,
                value = privacyPolicy,
                trailingIcon = AppIcons.OpenNew,
                clickEvent = Event.PrivacyPolicyPressed,
            ),
            IssuerDetailUiItem(
                leadingIcon = AppIcons.Store,
                labelRes = R.string.pid_issuance_data_consent_issuer_label_certifacte_valid,
                value = certificateValidUntil
            ),
        )

        DocumentType.EAA -> listOf(
            IssuerDetailUiItem(
                leadingIcon = AppIcons.Flag,
                labelRes = R.string.eaa_inspection_eaa_issuer_label_country,
                value = country
            ),
            IssuerDetailUiItem(
                leadingIcon = AppIcons.Shield,
                labelRes = R.string.eaa_issuance_eaa_info_issuer_label_privacy,
                value = privacyPolicy,
                trailingIcon = AppIcons.OpenNew,
                clickEvent = Event.PrivacyPolicyPressed,
            ),
            IssuerDetailUiItem(
                leadingIcon = AppIcons.CharacterWholeNumber,
                labelRes = R.string.eaa_inspection_eaa_issuer_label_id,
                value = identificationNumber
            ),
        )
    }

@SuppressLint("ViewModelConstructorInComposable")
@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IssuerDetailScreenPidPreview() {
    val vm = IssuerDetailsViewModel(
        issuerInfo = IssuerInfo(
            address = "Kommandantenstraße 18 10969 Berlin",
            issuerName = "Bundesdruckerei",
            email = "info@bdr.de",
            privacyPolicy = "bundesdruckerei.de/de/datenschutz",
            certificateValidUntil = "23.05.2030",
            imageRes = R.drawable.bundesdruckerei_logo_squared,
            documentType = DocumentType.PID,
        )
    )
    PreviewTheme {
        IssuerDetailsContent(
            navController = NavController(LocalContext.current),
            viewModel = vm,
        )
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IssuerDetailScreenEaaPreview() {
    val vm = IssuerDetailsViewModel(
        issuerInfo = IssuerInfo(
            issuerName = "University XY",
            country = "Germany",
            privacyPolicy = "university-xy.de/privacy",
            identificationNumber = "1234675690878ioi8uw",
            documentType = DocumentType.EAA,
        )
    )
    PreviewTheme {
        IssuerDetailsContent(
            navController = NavController(LocalContext.current),
            viewModel = vm,
        )
    }
}
