package eu.europa.ec.commonfeature.ui.request.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep
import eu.europa.ec.commonfeature.ui.request.State
import eu.europa.ec.commonfeature.ui.request.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.BUTTON_HEIGHT
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_72
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapText

/**
 * Credentials to present content section
 */
@Composable
fun CredentialDetailsView(
    modifier: Modifier = Modifier,
    state: State,
    shape: Shape = CardDefaults.shape,
    elevation: Dp = 0.dp,
    border: BorderStroke? = BorderStroke(
        width = BORDER_STROKE_1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    ),
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = modifier.padding(bottom = SPACING_EXTRA_LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_LARGE.dp)
    ) {
        val requestDocuments: List<RequestDocumentItemUi?> = state.items.ifEmpty { listOf(null) }
        requestDocuments.forEach { requestDocument ->
            // claims for the given document
            val claimItems = requestDocument
                ?.expandedUiItems
                ?.firstOrNull()
                ?.domainPayload
                ?.docClaimsDomain
                ?: state.claimItems

            val claimItemLabels =  claimItems.map { it.readableName }.distinct()

            CredentialDetailsCard(
                modifier = Modifier.fillMaxWidth(),
                data = CredentialDetailsCardData(
                    claimItems = claimItems,
                    claimItemLabels = claimItemLabels,
                    requestedClaimCount = requestDocument?.requestedClaimsCount ?: claimItems.size,
                    totalClaimCount = requestDocument?.totalClaimsCount ?: claimItems.size,
                    credentialName = requestDocument?.credentialName()
                        ?: stringResource(R.string.global_pid_credential_name),
                    showDataDetails = state.toShowDataDetails,
                ),
                style = CredentialDetailsCardStyle(
                    shape = shape,
                    elevation = elevation,
                    border = border,
                ),
                onInfoClick = { state.headerConfig.importantInformationAction() },
                onToggleDataDetails = {
                    if (state.toShowDataDetails) {
                        onEventSend(Event.HideDataDetailsButtonPressed)
                    } else {
                        onEventSend(Event.ShowDataDetailsButtonPressed)
                    }
                }
            )
        }
    }
}

/**
 * Resolves the credential name for PID and EAAs
 * TODO resolve with docName instead, at the moment the docName is "German PID" we either need a sync with PIDP or update in our mapping
 */
@Composable
private fun RequestDocumentItemUi.credentialName(): String {
    val docType = expandedUiItems.firstOrNull()
        ?.domainPayload
        ?.documentType

    return if (docType == DocumentType.PID) {
        stringResource(R.string.global_pid_credential_name)
    } else {
        expandedUiItems.firstOrNull()?.domainPayload?.docName ?: ""
    }
}


private data class CredentialDetailsCardData(
    val claimItems: List<RequestDocumentClaim>,
    val claimItemLabels: List<String>,
    val requestedClaimCount: Int,
    val totalClaimCount: Int,
    val credentialName: String,
    val showDataDetails: Boolean,
)

private data class CredentialDetailsCardStyle(
    val shape: Shape,
    val elevation: Dp,
    val border: BorderStroke?,
)

@Composable
private fun CredentialDetailsCard(
    modifier: Modifier = Modifier,
    data: CredentialDetailsCardData,
    style: CredentialDetailsCardStyle,
    onInfoClick: () -> Unit,
    onToggleDataDetails: () -> Unit,
) {
    Box(modifier = modifier) {
        Surface(
            modifier = modifier,
            shape = style.shape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = style.elevation,
            border = style.border,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BUTTON_HEIGHT.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(color = ThemeColors.primaryPid)
                            .fillMaxWidth()
                            .height(BUTTON_HEIGHT.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WrapText(
                            modifier = Modifier
                                .padding(start = SPACING_MEDIUM.dp),
                            text = pluralStringResource(
                                R.plurals.request_detail_data_title,
                                data.requestedClaimCount,
                                data.requestedClaimCount,
                                data.totalClaimCount,
                                data.credentialName,
                            ),
                            textConfig = TextConfig(
                                style = MaterialTheme.typography.titleMedium,
                                color = ThemeColors.surfaceBrightest
                            )
                        )
                        WrapIconButton(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            iconData = AppIcons.Info,
                            enabled = true,
                            customTint = ThemeColors.onPrimaryPid,
                            onClick = onInfoClick
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .align(Alignment.CenterEnd),
                        painter = painterResource(id = R.drawable.ic_eagle_mini_right),
                        contentDescription = "Eagle",
                        tint = ThemeColors.onPrimaryPid.copy(alpha = 0.15f)
                    )
                }

                if (data.showDataDetails) {
                    DataWithDetail(
                        modifier = Modifier.padding(
                            top = SPACING_EXTRA_MEDIUM.dp,
                            bottom = SPACING_LARGE.dp
                        ),
                        claimItems = data.claimItems
                    )
                } else {
                    DataWithoutDetail(
                        modifier = Modifier.padding(
                            start = SPACING_MEDIUM.dp,
                            end = SPACING_MEDIUM.dp,
                            top = SPACING_MEDIUM.dp,
                            bottom = SPACING_LARGE_32.dp
                        ),
                        labels = data.claimItemLabels
                    )
                }
            }
        }

        Button(
            colors = ButtonDefaults.buttonColors()
                .copy(containerColor = MaterialTheme.colorScheme.onSecondaryContainer),
            onClick = onToggleDataDetails,
            modifier = Modifier
                .height(BUTTON_HEIGHT.dp)
                .align(Alignment.BottomCenter)
                .offset(y = SPACING_LARGE.dp)
        ) {

            val icon = if (data.showDataDetails) AppIcons.VisibilityOff else AppIcons.Visibility

            WrapIcon(
                iconData = icon,
                customTint = MaterialTheme.colorScheme.secondaryContainer,
            )

            val buttonText = if (data.showDataDetails) {
                stringResource(R.string.pid_presentation_data_consent_toggle_unvisible_button)
            } else {
                stringResource(R.string.pid_presentation_data_consent_toggle_visible_button)
            }

            Spacer(modifier = Modifier.width(SPACING_SMALL.dp))

            WrapText(
                text = buttonText,
                textConfig = TextConfig(
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
            )
        }
    }
}

@Composable
private fun DataWithDetail(
    claimItems: List<RequestDocumentClaim>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = SPACING_MEDIUM.dp)
    ) {
        claimItems.forEachIndexed { index, requestDocumentClaim ->
            ListItem(
                onItemClick = null,
                item = ListItemData(
                    itemId = index.toString(),
                    overlineText = requestDocumentClaim.readableName,
                    mainContentData = ListItemMainContentData.Text(requestDocumentClaim.labelValue)
                ),
            )
        }
    }
}


@Composable
private fun DataWithoutDetail(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val columnCount = 2

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        labels.chunked(columnCount).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                rowItems.forEach { label ->
                    WrapText(
                        modifier = Modifier.weight(WEIGHT_1),
                        text = label,
                        textConfig = TextConfig(
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                repeat(columnCount - rowItems.size) {
                    Spacer(modifier = Modifier.weight(WEIGHT_1))
                }
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialDetailsPreviewWithoutDetail() {
    val claimItems = listOf(
        RequestDocumentClaim(
            elementIdentifier = "",
            value = "MUSTERMANN",
            readableName = stringResource(R.string.pid_issuance_data_consent_label_name),
            isRequired = true,
            isAvailable = true,
            withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_name),
            labelValue = "MUSTERMANN",
            path = listOf(),
        ),
        RequestDocumentClaim(
            elementIdentifier = "",
            value = "ERIKA",
            readableName = stringResource(R.string.pid_issuance_data_consent_label_first_names),
            isRequired = true,
            isAvailable = true,
            withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_first_names),
            labelValue = "ERIKA",
            path = listOf(),
        ),
        RequestDocumentClaim(
            elementIdentifier = "",
            value = "23.05.1983",
            readableName = stringResource(R.string.pid_issuance_data_consent_label_birth_date),
            isRequired = true,
            isAvailable = true,
            withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_birth_date),
            labelValue = "23.05.1983",
            path = listOf(),
        ),
    )

    val labels = claimItems.map {
        it.withoutDetailLabel
    }.distinct()


    PreviewTheme {
        CredentialDetailsView(
            state = State(
                claimItems = claimItems,
                claimItemLabels = labels,
                currentStep = RequestScreenStep.CredentialPreview,
                headerConfig = ContentHeaderConfig(
                    description = stringResource(R.string.pid_presentation_rp_info_paragraph_1)+"\n\n" +stringResource(R.string.pid_presentation_rp_info_paragraph_2) ,
                    purposeText = stringResource(R.string.request_header_main_text),
                    relyingPartyData = RelyingPartyData(
                        isVerified = true,
                        name = stringResource(R.string.request_relying_party_default_name),
                        description = stringResource(R.string.request_relying_party_description)
                    )
                ),
            ),
            onEventSend = { },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialDetailsPreviewWithDetail() {
    PreviewTheme {
        val claimItems = listOf(
            RequestDocumentClaim(
                elementIdentifier = "",
                value = "MUSTERMANN",
                readableName = stringResource(R.string.pid_issuance_data_consent_label_name),
                isRequired = true,
                isAvailable = true,
                withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_name),
                labelValue = "MUSTERMANN",
                path = listOf(),
            ),
            RequestDocumentClaim(
                elementIdentifier = "",
                value = "ERIKA",
                readableName = stringResource(R.string.pid_issuance_data_consent_label_first_names),
                isRequired = true,
                isAvailable = true,
                withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_first_names),
                labelValue = "ERIKA",
                path = listOf(),
            ),
            RequestDocumentClaim(
                elementIdentifier = "",
                value = "23.05.1983",
                readableName = stringResource(R.string.pid_issuance_data_consent_label_birth_date),
                isRequired = true,
                isAvailable = true,
                withoutDetailLabel = stringResource(R.string.pid_issuance_data_consent_label_birth_date),
                labelValue = "23.05.1983",
                path = listOf(),
            ),
        )


        CredentialDetailsView(
            state = State(
                toShowDataDetails = true,
                claimItems = claimItems,
                currentStep = RequestScreenStep.CredentialPreview,
                headerConfig = ContentHeaderConfig(
                    description = stringResource(R.string.pid_presentation_rp_info_paragraph_1)+"\n\n" +stringResource(R.string.pid_presentation_rp_info_paragraph_2) ,
                    purposeText = stringResource(R.string.request_header_main_text),
                    relyingPartyData = RelyingPartyData(
                        isVerified = true,
                        name = stringResource(R.string.request_relying_party_default_name),
                        description = stringResource(R.string.request_relying_party_description)
                    )
                ),
            ),
            onEventSend = { },
        )
    }
}
