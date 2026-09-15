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

import org.sprind.wallet.uilogic.component.IssuanceJourneyStep

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardReaderRouteTest {

    @Test
    fun `the opening card question hides the close action`() {
        assertFalse(CardReaderRoute.ONBOARDING_CARD.showsCloseAction)
    }

    @Test
    fun `only the routes that carry their own way out hide the close action`() {
        assertEquals(
            listOf(
                CardReaderRoute.ONBOARDING_CARD,
                CardReaderRoute.NO_PIN_LETTER_INFO,
            ),
            CardReaderRoute.entries.filterNot { it.showsCloseAction },
        )
    }

    @Test
    fun `the steps overview sits between the card pin question and the consent`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)
        val navigator = CardReaderFlowNavigator(definition)

        assertEquals(
            CardReaderRoute.PROGRESS_STEPS,
            navigator.nextRoute(CardReaderRoute.ONBOARDING_PIN),
        )
        assertEquals(
            CardReaderRoute.CONSENT,
            navigator.nextRoute(CardReaderRoute.PROGRESS_STEPS),
        )
    }

    @Test
    fun `the citizen office dead end has no next route in the flow`() {
        val definition = CardReaderFlowDefinition.forType(CardReaderFlowType.ISSUANCE)

        // It is a detour, so it stays out of the ordered flow and back resolves through the return
        // target instead of through a neighbouring route.
        assertFalse(definition.routes.contains(CardReaderRoute.NO_PIN_LETTER_INFO))
        assertEquals(
            CardReaderBackBehavior.PREVIOUS_ROUTE,
            definition.navigationPolicyFor(CardReaderRoute.NO_PIN_LETTER_INFO).backBehavior,
        )
        assertEquals(
            CardReaderRoute.PROGRESS_STEPS,
            CardReaderFlowNavigator(definition).previousRoute(CardReaderRoute.CONSENT),
        )
    }

    @Test
    fun `the announced journey counts off the four steps in order`() {
        assertEquals(1, CardReaderRoute.CONSENT.journeyStep?.number)
        assertEquals(2, CardReaderRoute.ENTER_PIN.journeyStep?.number)
        assertEquals(3, CardReaderRoute.NFC_SCAN_EID_PIN.journeyStep?.number)
        assertEquals(4, CardReaderRoute.COMPLETED.journeyStep?.number)
        assertEquals(4, IssuanceJourneyStep.TOTAL)
    }

    @Test
    fun `the routes before and off the journey have no step`() {
        val withoutStep = CardReaderRoute.entries.filter { it.journeyStep == null }

        assertTrue(withoutStep.containsAll(
            listOf(
                CardReaderRoute.ONBOARDING_CARD,
                CardReaderRoute.ONBOARDING_PIN,
                CardReaderRoute.PROGRESS_STEPS,
                CardReaderRoute.NO_PIN_LETTER_INFO,
            )
        ))
        // The card PIN journey walks its own steps, so it stays out until its design lands.
        assertTrue(withoutStep.contains(CardReaderRoute.ENTER_TRANSPORT_PIN))
    }

    @Test
    fun `the opening card question offers help`() {
        assertTrue(CardReaderRoute.ONBOARDING_CARD.hasHelpSheet)
    }

    @Test
    fun `routes without an explanation sheet hide the help action`() {
        assertEquals(
            listOf(
                CardReaderRoute.NO_PIN_LETTER_INFO,
                CardReaderRoute.NFC_ACTIVATION,
                CardReaderRoute.ENTER_PUK,
                CardReaderRoute.COMPLETED,
            ),
            CardReaderRoute.entries.filterNot { it.hasHelpSheet },
        )
    }
}