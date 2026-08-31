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

package org.sprind.wallet.cardreaderfeature.domain

/**
 * Progress information derived from the active flow definition.
 *
 * The values are 1-based when the current route is part of the flow. When a
 * route is not found, [currentStep] falls back to 0 so callers can detect
 * that the route is outside the predefined flow definition.
 */
data class CardReaderProgress(
    val currentStep: Int,
    val totalSteps: Int,
) {
    val fraction: Float =
        if (totalSteps == 0) {
            0f
        } else {
            currentStep.toFloat() / totalSteps.toFloat()
        }
}
