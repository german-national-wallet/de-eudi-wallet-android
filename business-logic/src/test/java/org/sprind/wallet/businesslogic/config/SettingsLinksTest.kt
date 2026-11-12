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

package org.sprind.wallet.businesslogic.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the defaults: every page stays blank until the config points it at a published document,
 * so a flavor that overrides nothing opens empty pages rather than navigating anywhere.
 */
class SettingsLinksTest {

    private val subject = SettingsLinks()

    @Test
    fun `every page defaults to blank in both languages`() {
        val blank = LocalizedUrl(en = SettingsLinks.BLANK_PAGE, de = SettingsLinks.BLANK_PAGE)

        assertEquals(blank, subject.faqAndSupport)
        assertEquals(blank, subject.accessibilityDeclaration)
        assertEquals(blank, subject.about)
        assertEquals(blank, subject.imprint)
        assertEquals(blank, subject.privacyPolicy)
        assertEquals(blank, subject.pidPrivacyPolicy)
        assertEquals(blank, subject.termsAndConditions)
    }
}
