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
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuerDetailsContent(
    navController: NavController,
    viewModel: IssuerDetailsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    ContentScreen(
        toolBarConfig = ToolbarConfig(),
        onBack = { viewModel.setEvent(Event.Pop) },
        stickyBottom = { padding ->
            IssuerDetailsStickyButton(
                modifier = Modifier.padding(padding),
                onClick = state.primaryButtonAction
            )
        },
    ) { paddingValues ->
        IssuerDetailsBody(
            issuerData = state.issuerData,
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
    onClick: () -> Unit,
) {
    WrapButton(
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = onClick
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        WrapText(
            text = stringResource(R.string.pid_issuance_data_consent_issuer_sec_button),
            textConfig = TextConfig(
                style = ThemeTextStyles.onSecondaryButton,
                color = ThemeColors.onSecondaryButton,
            )
        )
    }
}

@Composable
private fun IssuerDetailsBody(
    issuerData: IssuerInfo,
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
            modifier = Modifier.padding(bottom = SPACING_LARGE.dp),
            text = stringResource(R.string.pid_issuance_data_consent_issuer_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        IssuerLogo(issuerData)
        IssuerDetailsItems(issuerData)
    }
}

@Composable
private fun IssuerLogo(issuerData: IssuerInfo) {
    val logoModifier = Modifier
        .size(70.dp)
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
private fun IssuerDetailsItems(issuerData: IssuerInfo) {
    IssuerDetailItem(
        icon = AppIcons.Balance,
        label = stringResource(R.string.pid_issuance_data_consent_issuer_label_name),
        value = issuerData.issuerName
    )

    issuerData.optionalDetails().forEach { item ->
        IssuerDetailItem(
            icon = item.icon,
            label = stringResource(item.labelRes),
            value = item.value
        )
    }
}

@Composable
private fun IssuerDetailsNavigationEffect(
    navController: NavController,
    viewModel: IssuerDetailsViewModel,
) {
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                Effect.Navigation.Pop -> navController.popBackStack()
                is Effect.Navigation.SwitchScreen -> Unit
            }
        }
    }
}

@Composable
private fun IssuerDetailItem(
    icon: IconData,
    label: String,
    value: String,
) {

    ListItem(
        item = ListItemData(
            itemId = "",
            leadingContentData = ListItemLeadingContentData.Icon(
                iconData = icon,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            overlineText = label,
            mainContentData = ListItemMainContentData.Text(value),
        ),
        onItemClick = {},
    )
}

private data class IssuerDetailUiItem(
    val icon: IconData,
    val labelRes: Int,
    val value: String,
)

private fun IssuerInfo.optionalDetails(): List<IssuerDetailUiItem> =
    listOf(
        IssuerDetailUiItem(
            icon = AppIcons.Location,
            labelRes = R.string.pid_issuance_data_consent_issuer_label_address,
            value = address
        ),
        IssuerDetailUiItem(
            icon = AppIcons.EmailGlobe,
            labelRes = R.string.pid_issuance_data_consent_issuer_label_email,
            value = email
        ),
        IssuerDetailUiItem(
            icon = AppIcons.Shield,
            labelRes = R.string.pid_issuance_data_consent_issuer_label_privacy,
            value = privacyPolicy
        ),
        IssuerDetailUiItem(
            icon = AppIcons.Store,
            labelRes = R.string.pid_issuance_data_consent_issuer_label_certifacte_valid,
            value = certificateValidUntil
        )
    ).filter { it.value.isNotEmpty() }

@SuppressLint("ViewModelConstructorInComposable")
@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IssuerDetailScreenPreview() {
    val vm = IssuerDetailsViewModel(
        issuerInfo = IssuerInfo(
            address = "Kommandantenstraße 18 10969 Berlin",
            issuerName = "Bundesdruckerei",
            email = "info@bdr.de",
            privacyPolicy = "bundesdruckerei.de/de/datenschutz",
            certificateValidUntil = "23.05.2030",
            imageRes = R.drawable.bundesdruckerei_logo_squared
        )
    )
    PreviewTheme {
        IssuerDetailsContent(
            navController = NavController(LocalContext.current),
            viewModel = vm,
        )
    }
}
