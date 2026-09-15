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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM

/**
 * The steps the PID issuance announces on its overview screen, which the header then counts off.
 *
 * Shared because the journey crosses features: the card reader walks the first three and closes the
 * fourth, and the wallet code screens that follow show it closed.
 */
enum class IssuanceJourneyStep {
    DATA_RELEASE,
    CARD_PIN,
    CARD_SCAN,
    CREDENTIAL,
    ;

    /** Position in the announced journey, counting from 1. */
    val number: Int get() = ordinal + 1

    companion object {
        val TOTAL: Int = entries.size
    }
}

/**
 * How far along the announced journey the flow is, for the header of a screen that is part of it.
 *
 * @param step the step the screen belongs to.
 * @param stepCompleted whether [step] is finished rather than being worked through; an unfinished
 *   step is drawn half filled. See [ContentStepProgressIndicator].
 */
@Composable
fun IssuanceJourneyProgress(
    step: IssuanceJourneyStep,
    modifier: Modifier = Modifier,
    stepCompleted: Boolean = false,
) {
    ContentStepProgressIndicator(
        modifier = modifier.padding(horizontal = SPACING_MEDIUM.dp),
        currentStep = step.number,
        totalSteps = IssuanceJourneyStep.TOTAL,
        currentStepCompleted = stepCompleted,
        contentDescription = stringResource(
            R.string.content_description_step_progress,
            step.number,
            IssuanceJourneyStep.TOTAL,
        ),
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IssuanceJourneyProgressPreview() {
    PreviewTheme {
        IssuanceJourneyProgress(
            modifier = Modifier.padding(vertical = SPACING_MEDIUM.dp),
            step = IssuanceJourneyStep.CARD_SCAN,
        )
    }
}