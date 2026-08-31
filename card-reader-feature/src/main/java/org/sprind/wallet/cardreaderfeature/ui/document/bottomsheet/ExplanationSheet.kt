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

package org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/**
 * The mark and the question at the top of an [ExplanationSheet].
 *
 * @property icon the mark above the title.
 * @property title the question the sheet answers.
 * @property tintIcon whether [icon] is a monochrome glyph to be tinted with the sheet's content
 *   color (a help mark, for instance). Set it to `false` for a multi-color logo, which has to keep
 *   its own colors — the eID logo's gradient would otherwise be flattened to a silhouette.
 * @property markSize how big the mark is drawn. The designs give a logo the full size and a plain
 *   glyph, such as the help mark, half of it.
 */
@Immutable
data class ExplanationSheetHeader(
    val icon: IconData,
    val title: String,
    val tintIcon: Boolean = true,
    val markSize: Dp = SIZE_EXTRA_LARGE.dp,
)

/**
 * The shape every card-reader explanation sheet shares: a mark, the question it answers, the answer
 * itself as a stack of [org.sprind.wallet.uilogic.component.InfoCard]s, and the action that closes the sheet.
 *
 * Accessibility:
 * - the title is exposed as a heading, as is every [org.sprind.wallet.uilogic.component.InfoCard] headline, so a screen reader can jump
 *   between the questions instead of having to read the sheet from the top;
 * - the mark is decorative and hidden, since the title right below it says what the sheet is about;
 * - the answer scrolls under the closing action rather than being clipped, so the way out of the
 *   sheet stays reachable at large font scales.
 *
 * @param header the mark and the question, see [ExplanationSheetHeader].
 * @param onCloseClick dismisses the sheet; wired to the "close hint" action the designs put below
 *   the explanation.
 * @param content the answer, as one [org.sprind.wallet.uilogic.component.InfoCard] per question.
 */
@Composable
fun ExplanationSheet(
    header: ExplanationSheetHeader,
    onCloseClick: () -> Unit,
    primaryAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                // fill = false keeps the sheet as tall as its content, and gives the explanation the
                // remaining space to scroll in only once it outgrows the sheet.
                .weight(WEIGHT_1, fill = false)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
                .padding(top = SPACING_LARGE_32.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetMark(header = header)
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                text = header.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            content()
        }

        // The actions stay put while the explanation scrolls under them, which is what the design's
        // overflow shadow shows.
        primaryAction?.let { action ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_MEDIUM.dp)
                    .padding(top = SPACING_SMALL.dp),
            ) {
                action()
            }
        }

        WrapButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
                .padding(top = SPACING_SMALL.dp, bottom = SPACING_LARGE_32.dp),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = onCloseClick,
            ),
        ) {
            WrapText(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.global_close_hint_button),
                textConfig = TextConfig(
                    style = ThemeTextStyles.onSecondaryButton,
                    color = ThemeColors.onSecondaryButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                ),
            )
        }
    }
}

/**
 * The header's mark. Decorative either way: the title right below it names what it stands for, so
 * announcing it would say the same thing twice.
 */
@Composable
private fun SheetMark(header: ExplanationSheetHeader) {
    val markModifier = Modifier
        .size(header.markSize)
        .clearAndSetSemantics { }

    if (header.tintIcon) {
        WrapIcon(
            iconData = header.icon,
            modifier = markModifier,
            customTint = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        WrapImage(
            iconData = header.icon,
            modifier = markModifier,
            contentScale = ContentScale.Fit,
        )
    }
}
