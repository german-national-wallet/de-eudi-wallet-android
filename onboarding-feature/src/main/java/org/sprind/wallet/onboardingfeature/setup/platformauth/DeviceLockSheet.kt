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

package org.sprind.wallet.onboardingfeature.setup.platformauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.StickyButtonAction
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapStickyButton
import kotlinx.coroutines.launch

/**
 * The sheet asking the user to acknowledge that removing every screen lock resets the app and
 * deletes their data.
 *
 * Only the primary action confirms. Back, a tap outside and a swipe down only close the sheet
 * ([onDismiss]); they never confirm.
 *
 * Accessibility: the sheet is a modal window, so screen reader and keyboard focus move into it and
 * stay there while it is open. As it has no visible close button, closing is exposed as the
 * "Close" accessibility action. The title is a heading and the icon is decorative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceLockSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeLabel = stringResource(R.string.content_description_close_icon)

    WrapModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SHEET_SHAPE,
        dragHandle = {},
    ) {
        DeviceLockSheetContent(
            modifier = Modifier.semantics {
                dismiss(label = closeLabel) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    true
                }
            },
            onConfirm = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceLockSheetContent(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // GenericBottomSheet takes no modifier and does not pad for the navigation bar, so the
    // accessibility action, the inset and the scrolling stay out here.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        GenericBottomSheet(
            // Matches the modal sheet's own container, so the sheet keeps its color.
            sheetBackgroundColor = BottomSheetDefaults.ContainerColor,
            sheetPadding = PaddingValues(
                start = SHEET_HORIZONTAL_PADDING,
                end = SHEET_HORIZONTAL_PADDING,
                top = SPACING_LARGE_32.dp,
                bottom = SHEET_BOTTOM_PADDING,
            ),
            spacer = { Spacer(modifier = Modifier.height(SHEET_SECTION_SPACING)) },
            titleContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                ) {
                    WrapIcon(
                        iconData = AppIcons.Info,
                        modifier = Modifier
                            .size(ICON_SIZE)
                            .clearAndSetSemantics { },
                        customTint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        modifier = Modifier.semantics { heading() },
                        text = stringResource(R.string.app_onboarding_pa_set_success_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            bodyContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SHEET_SECTION_SPACING),
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.app_onboarding_pa_set_success_sheet_paragraph),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    WrapStickyButton(
                        // The designs' bottom navigation area around the button.
                        modifier = Modifier.padding(top = SPACING_SMALL.dp, bottom = SPACING_LARGE_32.dp),
                        action = StickyButtonAction(
                            text = stringResource(R.string.app_onboarding_pa_set_success_sheet_prim_button),
                            onClick = onConfirm,
                        ),
                        type = ButtonType.PRIMARY,
                    )
                }
            },
        )
    }
}

private val SHEET_SHAPE = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
private val SHEET_HORIZONTAL_PADDING = 20.dp
private val SHEET_BOTTOM_PADDING = 20.dp
private val SHEET_SECTION_SPACING = 20.dp
private val ICON_SIZE = 24.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DeviceLockSheetContentPreview() {
    PreviewTheme {
        DeviceLockSheetContent(onConfirm = {})
    }
}
