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
 * Ordered route list for one card reader journey.
 *
 * The definition becomes the single source of truth for route ordering and
 * progress calculation, while route-skipping decisions can stay in the
 * ViewModel for now.
 */
data class CardReaderFlowDefinition(
    val type: CardReaderFlowType,
    val steps: List<CardReaderFlowStep>,
) {
    val routes: List<CardReaderRoute> = steps.map { it.route }

    /**
     * Derives a progress model from the position of [route] within [routes].
     */
    fun progressFor(route: CardReaderRoute): CardReaderProgress {
        val routeIndex = routes.indexOf(route)
        val currentStep = if (routeIndex >= 0) routeIndex + 1 else 0
        return CardReaderProgress(
            currentStep = currentStep,
            totalSteps = routes.size,
        )
    }

    /**
     * Returns the configured policy for [route], or a safe default when the
     * route has not yet been added to the ordered flow definition.
     */
    fun navigationPolicyFor(route: CardReaderRoute): CardReaderNavigationPolicy =
        steps.firstOrNull { it.route == route }?.navigationPolicy
            ?: route.defaultNavigationPolicy()

    companion object {
        /**
         * Both flows are static, so the definitions are cached and reused rather than rebuilt (and
         * their [routes] re-mapped) on every flow transition.
         */
        fun forType(type: CardReaderFlowType): CardReaderFlowDefinition = when (type) {
            CardReaderFlowType.ISSUANCE -> issuance
            CardReaderFlowType.CHANGE_PIN -> changePin
        }

        private val issuanceRoutes = listOf(
            CardReaderFlowStep(
                route = CardReaderRoute.ONBOARDING_CARD,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.EXIT_TO_DASHBOARD,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ONBOARDING_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.PROGRESS_STEPS,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.CONSENT,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.NFC_SCAN_EID_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.PIN_BLOCKED_ERROR,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_CAN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.NFC_SCAN_CAN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_CAN_SUCCESS,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.DISABLED,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.COMPLETED,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.DISABLED,
                    closeBehavior = CardReaderCloseBehavior.NO_ACTION,
                ),
            ),
        )

        private val changePinRoutes = listOf(
            CardReaderFlowStep(
                route = CardReaderRoute.TRANSPORT_PIN_LETTER,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_TRANSPORT_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.NFC_SCAN_TRANSPORT_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.ENTER_NEW_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.CONFIRM_NEW_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.NFC_SCAN_NEW_PIN,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.PREVIOUS_ROUTE,
                ),
            ),
            CardReaderFlowStep(
                route = CardReaderRoute.COMPLETED,
                navigationPolicy = CardReaderNavigationPolicy(
                    backBehavior = CardReaderBackBehavior.DISABLED,
                    closeBehavior = CardReaderCloseBehavior.NO_ACTION,
                ),
            ),
        )

        // Declared after the step lists so they are initialized once the lists exist.
        private val issuance = CardReaderFlowDefinition(CardReaderFlowType.ISSUANCE, issuanceRoutes)
        private val changePin =
            CardReaderFlowDefinition(CardReaderFlowType.CHANGE_PIN, changePinRoutes)
    }
}
