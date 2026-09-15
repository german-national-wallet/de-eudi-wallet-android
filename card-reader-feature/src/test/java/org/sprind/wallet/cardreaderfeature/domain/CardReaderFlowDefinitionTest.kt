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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardReaderFlowDefinitionTest {

    @Test
    fun `issuance flow progress matches route index`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)

        val progress = definition.progressFor(CardReaderRoute.CONSENT)

        assertEquals(4, progress.currentStep)
        assertEquals(definition.routes.size, progress.totalSteps)
        assertEquals(4f / definition.routes.size, progress.fraction)
    }

    @Test
    fun `issuance flow includes dedicated nfc can route`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)

        assertEquals(
            CardReaderRoute.NFC_SCAN_CAN,
            definition.routes[definition.routes.indexOf(CardReaderRoute.ENTER_CAN) + 1],
        )
    }

    @Test
    fun `issuance flow leads from the consent to the pin entry`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)
        val navigator = CardReaderFlowNavigator(definition)

        assertEquals(
            CardReaderRoute.ENTER_PIN,
            navigator.nextRoute(CardReaderRoute.CONSENT),
        )
        // Switching NFC on is a detour off the scan, so it is not a step of the flow at all.
        assertFalse(definition.routes.contains(CardReaderRoute.NFC_ACTIVATION))
        assertEquals(
            CardReaderBackBehavior.PREVIOUS_ROUTE,
            definition.navigationPolicyFor(CardReaderRoute.NFC_ACTIVATION).backBehavior,
        )
    }

    @Test
    fun `change pin flow progress matches route index`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.CHANGE_PIN)

        val progress = definition.progressFor(CardReaderRoute.ENTER_NEW_PIN)

        assertEquals(5, progress.currentStep)
        assertEquals(definition.routes.size, progress.totalSteps)
    }

    @Test
    fun `flow navigator returns next and previous routes`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.CHANGE_PIN)
        val navigator = CardReaderFlowNavigator(definition)

        assertEquals(
            CardReaderRoute.ENTER_TRANSPORT_PIN,
            navigator.nextRoute(CardReaderRoute.TRANSPORT_PIN_LETTER),
        )
        assertEquals(
            CardReaderRoute.ENTER_NEW_PIN,
            navigator.previousRoute(CardReaderRoute.CONFIRM_NEW_PIN),
        )
    }

    @Test
    fun `flow navigator returns null at boundaries`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)
        val navigator = CardReaderFlowNavigator(definition)

        assertNull(navigator.previousRoute(CardReaderRoute.ONBOARDING_CARD))
        assertNull(navigator.nextRoute(CardReaderRoute.COMPLETED))
        assertTrue(navigator.contains(CardReaderRoute.ENTER_CAN))
        assertFalse(navigator.contains(CardReaderRoute.ENTER_NEW_PIN))
    }

    @Test
    fun `issuance flow exposes back and close behavior per route`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)

        assertEquals(
            CardReaderBackBehavior.EXIT_TO_DASHBOARD,
            definition.navigationPolicyFor(CardReaderRoute.ONBOARDING_CARD).backBehavior,
        )
        assertEquals(
            CardReaderBackBehavior.DISABLED,
            definition.navigationPolicyFor(CardReaderRoute.ENTER_CAN_SUCCESS).backBehavior,
        )
        assertEquals(
            CardReaderCloseBehavior.NO_ACTION,
            definition.navigationPolicyFor(CardReaderRoute.COMPLETED).closeBehavior,
        )
    }

    @Test
    fun `change pin flow exposes previous route back behavior`() {
        val navigator = CardReaderFlowNavigator(
            CardReaderFlowDefinition.forType(CardReaderFlowType.CHANGE_PIN)
        )

        assertEquals(
            CardReaderBackBehavior.PREVIOUS_ROUTE,
            navigator.navigationPolicy(CardReaderRoute.ENTER_NEW_PIN).backBehavior,
        )
    }

    @Test
    fun `routes outside the flow definition still expose a safe default navigation policy`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)

        assertEquals(
            CardReaderBackBehavior.EXIT_TO_DASHBOARD,
            definition.navigationPolicyFor(CardReaderRoute.ENTER_PUK).backBehavior,
        )
        assertEquals(
            CardReaderBackBehavior.PREVIOUS_ROUTE,
            definition.navigationPolicyFor(CardReaderRoute.NFC_SCAN_PUK).backBehavior,
        )
        assertEquals(
            CardReaderCloseBehavior.CANCEL_AND_EXIT_TO_DASHBOARD,
            definition.navigationPolicyFor(CardReaderRoute.ENTER_PUK).closeBehavior,
        )
    }
}
