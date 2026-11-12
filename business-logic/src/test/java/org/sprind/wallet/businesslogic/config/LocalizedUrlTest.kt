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
import java.util.Locale

class LocalizedUrlTest {

    private val subject = LocalizedUrl(en = EN, de = DE)

    @Test
    fun `given a german locale then the german page is picked`() {
        assertEquals(DE, subject.forLocale(Locale.GERMANY))
    }

    @Test
    fun `given a regional german locale then the german page is picked`() {
        assertEquals(DE, subject.forLocale(Locale("de", "AT")))
        assertEquals(DE, subject.forLocale(Locale("de", "CH")))
    }

    @Test
    fun `given an english locale then the english page is picked`() {
        assertEquals(EN, subject.forLocale(Locale.UK))
        assertEquals(EN, subject.forLocale(Locale.US))
    }

    @Test
    fun `given an unsupported language then it falls back to the english page`() {
        assertEquals(EN, subject.forLocale(Locale.FRENCH))
        assertEquals(EN, subject.forLocale(Locale.ROOT))
    }

    @Test
    fun `given no german page then a german locale gets the english one`() {
        val englishOnly = LocalizedUrl(en = EN)

        assertEquals(EN, englishOnly.forLocale(Locale.GERMANY))
    }

    private companion object {
        const val EN = "https://example.org/en/index.html"
        const val DE = "https://example.org/de/index.html"
    }
}
