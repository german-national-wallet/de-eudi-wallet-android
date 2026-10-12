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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import androidx.compose.ui.Alignment
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.hasHelpSheet
import org.sprind.wallet.cardreaderfeature.domain.journeyStep
import org.sprind.wallet.cardreaderfeature.domain.journeyStepCompleted
import org.sprind.wallet.cardreaderfeature.domain.showsCloseAction
import org.sprind.wallet.uilogic.component.ArtworkTopBar
import org.sprind.wallet.uilogic.component.IssuanceJourneyProgress

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
        ArtworkTopBar(artwork = artwork) { ArtworkBackAction(onBackClick = onBackEvent) }
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

    IssuanceJourneyProgress(
        modifier = modifier,
        step = step,
        stepCompleted = route.journeyStepCompleted,
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

@Composable
private fun BoxScope.ArtworkBackAction(onBackClick: () -> Unit) {
    IconButton(
        modifier = Modifier
            .align(Alignment.TopStart)
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
