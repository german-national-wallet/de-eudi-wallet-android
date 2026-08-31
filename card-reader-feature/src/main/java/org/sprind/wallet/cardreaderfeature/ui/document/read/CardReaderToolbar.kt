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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.cardreaderfeature.ui.document.read

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.wrap.WrapImage
import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.cardreaderfeature.domain.CardReaderJourneyStep
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.hasHelpSheet
import org.sprind.wallet.cardreaderfeature.domain.journeyStep
import org.sprind.wallet.cardreaderfeature.domain.journeyStepCompleted
import org.sprind.wallet.cardreaderfeature.domain.showsCloseAction
import org.sprind.wallet.uilogic.component.ContentStepProgressIndicator

@Composable
internal fun CardReaderToolbar(
    state: State,
    onBackEvent: () -> Unit,
    onHelpEvent: () -> Unit,
    onCloseEvent: () -> Unit,
) {
    if (state.scanStatus != null) return

    if (state.isLoading || state.currentRoute == CardReaderRoute.COMPLETED) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
            // The journey ends on the success screen, so that is where its last step is shown; a
            // loader is not a step of its own.
            if (!state.isLoading) {
                JourneyProgress(
                    route = state.currentRoute,
                    modifier = Modifier.padding(bottom = SPACING_SMALL.dp),
                )
            }
        }
        return
    }

    // Some routes replace the toolbar with their own artwork, which the designs draw edge to edge
    // with the back action floating on it, the same shape as ContentScreenWithPidTopBar.
    state.currentRoute.topBarArtwork?.let { artwork ->
        ArtworkTopBar(artwork = artwork, onBackClick = onBackEvent)
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CancellableTopAppBar(
            onBackClick = onBackEvent,
            onHelpClick = onHelpEvent.takeIf { state.currentRoute.hasHelpSheet },
            onCloseClick = onCloseEvent.takeIf { state.currentRoute.showsCloseAction }
        )
        JourneyProgress(
            route = state.currentRoute,
            modifier = Modifier.padding(bottom = SPACING_SMALL.dp),
        )
    }
}

/** How far along the announced journey the flow is, for the routes that are part of it. */
@Composable
private fun JourneyProgress(
    route: CardReaderRoute,
    modifier: Modifier = Modifier,
) {
    val step = route.journeyStep ?: return

    ContentStepProgressIndicator(
        modifier = modifier.padding(horizontal = SPACING_MEDIUM.dp),
        currentStep = step.number,
        totalSteps = CardReaderJourneyStep.TOTAL,
        currentStepCompleted = route.journeyStepCompleted,
        contentDescription = stringResource(
            R.string.content_description_step_progress,
            step.number,
            CardReaderJourneyStep.TOTAL,
        ),
    )
}

/**
 * The artwork a route draws in place of the toolbar, or `null` for the routes that show the usual
 * one. Artwork belongs to the screen it introduces, so the mapping lives here rather than in the
 * route model, which knows nothing about drawables.
 */
private val CardReaderRoute.topBarArtwork: IconData?
    get() = when (this) {
        CardReaderRoute.NO_PIN_LETTER_INFO -> AppIcons.CitizenOffice
        else -> null
    }

/**
 * A route's artwork as its toolbar: a square band across the top of the screen, reaching behind the
 * status bar, with the back action on top of it.
 *
 * Accessibility:
 * - the artwork is decorative, and the screen's title and paragraph say what it pictures, so it is
 *   kept out of the accessibility tree;
 * - the back action stays an [IconButton], so it keeps its label and its full touch target even
 *   though it is drawn as a small circle on the artwork.
 */
@Composable
private fun ArtworkTopBar(
    artwork: IconData,
    onBackClick: () -> Unit,
) {
    val configuration = LocalConfiguration.current

    Box(modifier = Modifier.fillMaxWidth()) {
        WrapImage(
            iconData = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight(configuration))
                .clearAndSetSemantics { },
            contentScale = ContentScale.Crop,
            // A band shorter than the artwork is square gives up its sky, not the ground it stands
            // on, which is what carries into the text below it.
            alignment = Alignment.BottomCenter,
        )
        IconButton(
            modifier = Modifier
                .statusBarsPadding()
                .padding(SPACING_SMALL.dp),
            onClick = onBackClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_description_arrow_back_icon),
            )
        }
    }
}

/**
 * How tall an artwork band is: square, as the designs draw it, unless that would take more of the
 * screen than the designs give it — on a short screen the square would leave the title, the
 * paragraph and the action fighting over what is left, so the band is cropped to a share of the
 * screen instead.
 */
private fun artworkHeight(configuration: Configuration): Dp = min(
    configuration.screenWidthDp.dp,
    configuration.screenHeightDp.dp * ARTWORK_MAX_SCREEN_FRACTION,
)

/** Just over the share the designs give the band (360dp of a 740dp screen). */
private const val ARTWORK_MAX_SCREEN_FRACTION = 0.5f
