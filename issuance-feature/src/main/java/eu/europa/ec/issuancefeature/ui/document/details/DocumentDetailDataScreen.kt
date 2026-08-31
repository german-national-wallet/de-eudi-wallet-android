@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.issuancefeature.ui.document.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.content.ContentPidTopBarConfig
import eu.europa.ec.uilogic.component.content.ContentScreenWithPidTopBar
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACER_SIZE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

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
    ContentScreenWithPidTopBar(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        contentPidTopBarConfig = ContentPidTopBarConfig(
            title = stringResource(R.string.pid_inspection_pid_personal_data_title),
            onBackIconPress = { onEventSend(Event.OnBackPressed) },
            backgroundColor = parseCssColor(
                state.topBarBackgroundColor,
                ThemeColors.primaryPid
            ),
            backgroundImageUri = state.topBarBackgroundImageUri,
            textColor = parseCssColor(
                state.topBarTextColor,
                ThemeColors.onPrimaryPid
            )
        ),
        onBack = { onEventSend(Event.OnBackPressed) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                ),
            contentPadding = PaddingValues(
                top = SPACING_MEDIUM.dp,
                bottom = SPACING_MEDIUM.dp + paddingValues.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
        ) {
            itemsIndexed(state.documentDetails) { _, item ->

                if (item.mainContentData is ListItemMainContentData.Text) {
                    Column {
                        val overlineText = item.overlineText ?: ""
                        if (item.hasTopDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = SPACING_EXTRA_MEDIUM.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = BORDER_STROKE_1.dp
                            )
                        }

                        DetailItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = SPACING_MEDIUM.dp,
                                    end = SPACING_MEDIUM.dp
                                ),
                            label = overlineText,
                            value = (item.mainContentData as ListItemMainContentData.Text).text
                        )

                        if (item.hasBottomDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = SPACING_EXTRA_MEDIUM.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = BORDER_STROKE_1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Column(modifier = modifier) {
        WrapText(
            text = label,
            textConfig = TextConfig(
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        WrapText(
            modifier = Modifier.padding(top = SPACER_SIZE_1.dp),
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
    val documentDetails = listOf(
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_name),
            mainContentData = ListItemMainContentData.Text("MUSTERMANN"),
        ),

        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_birth_name),
            mainContentData = ListItemMainContentData.Text("GABLER"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_first_names),
            mainContentData = ListItemMainContentData.Text("ERIKA"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_address),
            mainContentData = ListItemMainContentData.Text("HEI 17\n80888 MUNICH"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_nationality),
            mainContentData = ListItemMainContentData.Text("Germany"),
            hasTopDivider = true,
            hasBottomDivider = true
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_birth_date),
            mainContentData = ListItemMainContentData.Text("23 May 1984"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_place_of_birth),
            mainContentData = ListItemMainContentData.Text("BERLIN"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_age_in_years),
            mainContentData = ListItemMainContentData.Text("40"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_age_birth_year),
            mainContentData = ListItemMainContentData.Text("1984"),
        ),

        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_age_equal_or_over),
            mainContentData = ListItemMainContentData.Text(
                "${
                    stringResource(
                        R.string.document_details_data_age_over,
                        12
                    )
                } ${stringResource(R.string.pid_issuance_data_consent_label_age_equal_or_over_yes)}\n" +
                        "${
                            stringResource(
                                R.string.document_details_data_age_over,
                                65
                            )
                        } ${stringResource(R.string.pid_issuance_data_consent_label_age_equal_or_over_no)}"
            ),
            hasBottomDivider = true
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_issuing_authority),
            mainContentData = ListItemMainContentData.Text("GERMANY"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_issuing_country),
            mainContentData = ListItemMainContentData.Text("GERMANY"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_created_at),
            mainContentData = ListItemMainContentData.Text("13 May 2025"),
        ),
        ListItemData(
            itemId = "",
            overlineText = stringResource(R.string.pid_issuance_data_consent_label_expire_date),
            mainContentData = ListItemMainContentData.Text("13 May 2029"),
        ),
    )
    PreviewTheme {
        MainScreen(
            state = State(
                isLoading = false,
                documentDetails = documentDetails
            ),
            onEventSend = {})
    }
}
