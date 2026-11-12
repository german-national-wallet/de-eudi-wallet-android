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

import eu.europa.ec.businesslogic.config.LogConfigImpl
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the legal documents the backend config points the settings menu and the setup steps at:
 * published, served over https (the only scheme the app's web view loads) and available per
 * language. Asserts on the shape of the urls only, so no hostname reaches the published tests.
 */
class WalletBackendEnvironmentConfigTest {

    private val subject = EnvironmentConfigImpl(logConfig = LogConfigImpl())

    private val legalDocuments
        get() = with(subject.settingsLinks) { listOf(privacyPolicy, termsAndConditions) }

    @Test
    fun `the legal documents are published over https`() {
        legalDocuments.forEach {
            assertTrue(it.en, it.en.startsWith("https://"))
            assertTrue(it.de, it.de.startsWith("https://"))
        }
    }

    @Test
    fun `the legal documents have a page per language`() {
        legalDocuments.forEach {
            assertNotEquals(it.en, it.de)
            assertTrue(it.en, it.en.contains("/en/"))
            assertTrue(it.de, it.de.contains("/de/"))
        }
    }
}
