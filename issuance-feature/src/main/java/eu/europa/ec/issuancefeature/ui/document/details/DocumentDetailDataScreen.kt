package eu.europa.ec.issuancefeature.ui.document.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACER_SIZE_1
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.commonfeature.ui.credentialdata.CredentialDataItem
import org.sprind.wallet.uilogic.component.NoticeBanner

@Composable
fun DocumentDetailDataScreen(
    navController: NavController,
    viewModel: DocumentDetailsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }

    MainScreen(
        state = state,
        onEventSend = viewModel::setEvent
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            if (effect is Effect.Navigation) {
                handleNavigationEffect(effect, navController)
            }
        }.collect()
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }
    }
}

@Composable
private fun MainScreen(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    ContentScreen(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        onBack = { onEventSend(Event.OnBackPressed) },
    ) { paddingValues ->
        CredentialDataList(
            items = state.credentialData,
            isPid = state.isPid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        )
    }
}

object CredentialDataListTestTags {
    const val LIST = "credential_data_list"
}

/**
 * The screen title and privacy banner, worded for the PID or an EAA, followed by the credential's
 * attributes as issued, labelled and ordered by the issuer metadata. Each attribute is read out
 * as one node of label and value and a heading is announced as such; a claim the credential has no
 * value for shows a dash and is read out as "No value given". Screen readers start with the
 * title, then the toolbar, then the banner and attributes in order.
 */
@Composable
fun CredentialDataList(
    items: List<CredentialDataItem>,
    isPid: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = modifier
            .testTag(CredentialDataListTestTags.LIST)
            .semantics {
                collectionInfo = CollectionInfo(rowCount = items.size, columnCount = 1)
                // A lazy list is a traversal group of its own, which would keep the title behind the toolbar.
                isTraversalGroup = false
            },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
    ) {
        item(key = TITLE_KEY) {
            WrapText(
                modifier = Modifier.semantics {
                    heading()
                    traversalIndex = TITLE_TRAVERSAL_INDEX
                },
                text = stringResource(
                    if (isPid) R.string.pid_inspection_pid_personal_data_title else R.string.eaa_inspection_eaa_detailed_data_title
                ),
                textConfig = TextConfig(
                    maxLines = Int.MAX_VALUE,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
        item(key = BANNER_KEY) {
            NoticeBanner(
                body = stringResource(
                    if (isPid) R.string.pid_inspection_pid_personal_data_banner else R.string.eaa_inspection_eaa_detailed_data_banner
                )
            )
        }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val itemModifier = Modifier
                .fillMaxWidth()
                .semantics {
                    collectionItemInfo = CollectionItemInfo(
                        rowIndex = index,
                        rowSpan = 1,
                        columnIndex = 0,
                        columnSpan = 1
                    )
                }
            when (item) {
                is CredentialDataItem.Attribute -> DetailItem(
                    modifier = itemModifier,
                    label = item.label,
                    value = item.value,
                    isMissing = item.isMissing,
                )

                is CredentialDataItem.Heading -> WrapText(
                    modifier = itemModifier
                        .padding(top = SPACING_MEDIUM.dp)
                        .semantics { heading() },
                    text = item.title,
                    textConfig = TextConfig(
                        maxLines = Int.MAX_VALUE,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/** Read before the toolbar, whose app bar is a traversal group at the default index of 0. */
private const val TITLE_TRAVERSAL_INDEX = -1f
private const val TITLE_KEY = "credential_data_title"
private const val BANNER_KEY = "credential_data_banner"

@Composable
private fun DetailItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isMissing: Boolean = false,
) {
    val noValue = stringResource(R.string.__variable_text_credential_data_var_label_empty_value_a11y)
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        WrapText(
            text = label,
            textConfig = TextConfig(
                maxLines = Int.MAX_VALUE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        WrapText(
            modifier = Modifier
                .padding(top = SPACER_SIZE_1.dp)
                .then(if (isMissing) Modifier.clearAndSetSemantics { text = AnnotatedString(noValue) } else Modifier),
            text = value,
            textConfig = TextConfig(
                maxLines = Int.MAX_VALUE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun DocumentDetailDataScreenPreview() {
    val credentialData = listOf(
        CredentialDataItem.Attribute("family_name", "Familienname", "MUSTERMANN"),
        CredentialDataItem.Attribute("birth_name", "Geburtsname", "GABLER"),
        CredentialDataItem.Attribute("given_name", "Vorname(n)", "ERIKA"),
        CredentialDataItem.Attribute("title", "Doktorgrad", "-", isMissing = true),
        CredentialDataItem.Attribute("also_known_as", "Ordens-/Künstlername", "-", isMissing = true),
        CredentialDataItem.Heading("address", "Adresse"),
        CredentialDataItem.Attribute("address.street_address", "Straße", "HEIDESTRASSE 17"),
        CredentialDataItem.Attribute("address.postal_code", "Postleitzahl", "51147"),
        CredentialDataItem.Attribute("address.locality", "Stadt", "KÖLN"),
        CredentialDataItem.Attribute("nationalities", "Staatsangehörigkeit", "DEUTSCHLAND"),
        CredentialDataItem.Attribute("birthdate", "Geburtsdatum", "12.08.1964"),
        CredentialDataItem.Heading("age_equal_or_over", "Alter"),
        CredentialDataItem.Attribute("age_equal_or_over.18", "Älter als 18", "Ja"),
        CredentialDataItem.Attribute("age_equal_or_over.65", "Älter als 65", "Nein"),
        CredentialDataItem.Attribute("issuing_country", "Ausstellender Staat", "DEUTSCHLAND"),
    )
    PreviewTheme {
        MainScreen(
            state = State(
                isLoading = false,
                credentialData = credentialData,
                isPid = true,
            ),
            onEventSend = {})
    }
}
