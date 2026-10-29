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

package org.sprind.wallet.uilogic.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.designsystem.typography.CustomTypography

/**
 * The shared success screen: a success animation with one statement centred under it, on a screen
 * with an empty top bar and no actions. The caller decides when to move on.
 *
 * Screen reader order is the title (a heading) first, then the animation, then the optional
 * [announcement].
 *
 * @param title the statement shown and read as the heading.
 * @param isLoading shows the loading state of [ContentScreen].
 * @param imageDescription the description of the animation, announced with the `Image` role, or
 *   `null` when the animation is decorative (the title already says it all).
 * @param announcement a message spoken once it is non-null, e.g. before the screen is left. It is a
 *   polite live region, so it does not interrupt what is being read.
 * @param onBack invoked by system back, or `null` for the default behaviour.
 */
@Composable
fun SuccessContentScreen(
    title: String,
    isLoading: Boolean = false,
    imageDescription: String? = null,
    announcement: String? = null,
    onBack: (() -> Unit)? = null,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = onBack,
        isLoading = isLoading,
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(centerContent = true),
            title = {},
            illustration = {
                Box(
                    modifier = Modifier.semantics { isTraversalGroup = true },
                    contentAlignment = Alignment.Center,
                ) {
                    SuccessAnimation(imageDescription = imageDescription)
                    SuccessTitle(title = title)
                    announcement?.let { RedirectAnnouncement(text = it) }
                }
            },
        )
    }
}

@Composable
private fun SuccessAnimation(imageDescription: String?) {
    WrapLottieAnimation(
        animation = R.raw.success_inline,
        modifier = Modifier
            .padding(start = CHECKMARK_ALIGNMENT_START_PADDING)
            .size(SUCCESS_ANIMATION_SIZE)
            .imageSemantics(imageDescription),
        iterations = 1,
    )
}

/** The animation is an image when it has a [description], otherwise decoration. */
private fun Modifier.imageSemantics(description: String?): Modifier =
    if (description != null) {
        semantics {
            role = Role.Image
            contentDescription = description
            traversalIndex = IMAGE_TRAVERSAL_INDEX
        }
    } else {
        clearAndSetSemantics { }
    }

@Composable
private fun BoxScope.SuccessTitle(title: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .padding(top = SUCCESS_TITLE_OFFSET)
            .padding(horizontal = SPACING_MEDIUM.dp)
            .semantics {
                heading()
                traversalIndex = TITLE_TRAVERSAL_INDEX
            },
        text = title,
        textAlign = TextAlign.Center,
        style = CustomTypography.titleMedium,
    )
}

/**
 * Spoken when it enters the composition. It needs a size to stay in the accessibility tree, so it is
 * as small as possible and carries no visible content.
 */
@Composable
private fun RedirectAnnouncement(text: String) {
    Box(
        modifier = Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
                traversalIndex = ANNOUNCEMENT_TRAVERSAL_INDEX
            },
    )
}

private val SUCCESS_ANIMATION_SIZE = 354.dp

// Shifts the animation so its checkmark lines up with the centered title.
private val CHECKMARK_ALIGNMENT_START_PADDING = 6.dp

// Where the animation leaves room for the title.
private val SUCCESS_TITLE_OFFSET = 175.dp

private const val TITLE_TRAVERSAL_INDEX = 0f
private const val IMAGE_TRAVERSAL_INDEX = 1f
private const val ANNOUNCEMENT_TRAVERSAL_INDEX = 2f

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SuccessContentScreenPreview() {
    PreviewTheme {
        SuccessContentScreen(
            title = "App set up successfully",
            imageDescription = "Successful",
        )
    }
}
