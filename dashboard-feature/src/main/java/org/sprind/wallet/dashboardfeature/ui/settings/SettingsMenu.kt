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

package org.sprind.wallet.dashboardfeature.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ClickableArea
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_28
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.TextConfig
import org.sprind.wallet.businesslogic.config.debugConfigAvailable

/**
 * A row of the settings menu.
 *
 * @property labelRes the row's visible label.
 * @property leadingIcon the icon in front of the label, or null for the rows the design leaves
 *   unmarked.
 * @property trailingIconthe icon at the end of the row
 */
enum class SettingsMenuEntry(
    @StringRes val labelRes: Int,
    val leadingIcon: IconData? = null,
    val trailingIcon: IconData = AppIcons.ArrowRightSettings,
) {
    FAQ_AND_SUPPORT(
        labelRes = R.string.settings_overview_list_support,
        leadingIcon = AppIcons.HelpSettings,
    ),
    WALLET_REVOCATION(
        labelRes = R.string.settings_overview_list_revocation,
        leadingIcon = AppIcons.KeySettings,
    ),
    NOTIFICATIONS(
        labelRes = R.string.settings_overview_list_notification,
        leadingIcon = AppIcons.Notification,
        trailingIcon = AppIcons.OpenNewSettings,
    ),
    ABOUT(labelRes = R.string.settings_overview_list_about_EUDI),
    ACCESSIBILITY_DECLARATION(labelRes = R.string.settings_overview_list_accessibility),
    PRIVACY_POLICY(labelRes = R.string.settings_overview_list_privacy_policy),
    TERMS_AND_CONDITIONS(labelRes = R.string.settings_overview_list_terms_and_conditions),
    IMPRINT(labelRes = R.string.settings_overview_list_impress),
    EXPORT_LOGS(
        labelRes = R.string.dashboard_drawer_log_export,
        leadingIcon = AppIcons.Edit,
    ),
    DEBUG_CONFIG(
        labelRes = R.string.settings_debug_config_title,
        leadingIcon = AppIcons.Sign,
    ),
}

/**
 * A group of [SettingsMenuEntry] rows under a shared heading.
 *
 * @property titleRes the heading, or null for the lead group the design leaves unheaded.
 * @property entries the rows, in the order they are shown.
 */
data class SettingsMenuGroup(
    @StringRes val titleRes: Int?,
    val entries: List<SettingsMenuEntry>,
)

fun settingsMenuGroups(isLogExportAvailable: Boolean): List<SettingsMenuGroup> = buildList {
    add(
        SettingsMenuGroup(
            titleRes = null,
            entries = listOf(SettingsMenuEntry.FAQ_AND_SUPPORT),
        )
    )
    add(
        SettingsMenuGroup(
            titleRes = R.string.settings_overview_headline_settings,
            entries = listOf(
                SettingsMenuEntry.WALLET_REVOCATION,
                SettingsMenuEntry.NOTIFICATIONS,
            ),
        )
    )
    add(
        SettingsMenuGroup(
            titleRes = R.string.settings_overview_headline_legal,
            entries = listOf(
                SettingsMenuEntry.ABOUT,
                SettingsMenuEntry.ACCESSIBILITY_DECLARATION,
                SettingsMenuEntry.PRIVACY_POLICY,
                SettingsMenuEntry.TERMS_AND_CONDITIONS,
                SettingsMenuEntry.IMPRINT,
            ),
        )
    )
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    if (isLogExportAvailable || debugConfigAvailable) {
        add(
            SettingsMenuGroup(
                titleRes = R.string.dashboard_drawer_title,
                entries = buildList {
                    if (isLogExportAvailable) add(SettingsMenuEntry.EXPORT_LOGS)
                    if (debugConfigAvailable) add(SettingsMenuEntry.DEBUG_CONFIG)
                },
            )
        )
    }
}

/**
 * One [SettingsMenuGroup]: its heading and its rows.
 *
 * @param group the heading and rows to render.
 * @param onEntryClick called with the tapped entry.
 * @param modifier applied to the group.
 */
@Composable
fun SettingsMenuSection(
    group: SettingsMenuGroup,
    onEntryClick: (SettingsMenuEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        group.titleRes?.let { titleRes ->
            SectionTitle(
                modifier = Modifier.semantics { heading() },
                text = stringResource(titleRes),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    collectionInfo = CollectionInfo(
                        rowCount = group.entries.size,
                        columnCount = 1,
                    )
                },
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
        ) {
            group.entries.forEachIndexed { index, entry ->
                ListItem(
                    item = ListItemData(
                        itemId = entry.name,
                        mainContentData = ListItemMainContentData.Text(
                            text = stringResource(entry.labelRes),
                        ),
                        leadingContentData = entry.leadingIcon?.let { icon ->
                            ListItemLeadingContentData.Icon(
                                size = ICON_SIZE_28,
                                iconData = icon,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        },
                        trailingContentData = ListItemTrailingContentData.Icon(
                            iconData = entry.trailingIcon,
                            size = ICON_SIZE_28,
                        ),
                    ),
                    onItemClick = { onEntryClick(entry) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SIZE_EXTRA_LARGE.dp)
                        .semantics {
                            collectionItemInfo = CollectionItemInfo(
                                rowIndex = index,
                                rowSpan = 1,
                                columnIndex = 0,
                                columnSpan = 1,
                            )
                        },
                    leadingModifier = Modifier.clearAndSetSemantics { },
                    trailingModifier = Modifier.clearAndSetSemantics { },
                    clickableAreas = listOf(ClickableArea.ENTIRE_ROW),
                    mainContentTextStyle = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SettingsMenuSectionPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        ) {
            settingsMenuGroups(isLogExportAvailable = true).forEach { group ->
                SettingsMenuSection(group = group, onEntryClick = {})
            }
        }
    }
}