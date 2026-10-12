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

package eu.europa.ec.resourceslogic.provider

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import eu.europa.ec.resourceslogic.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StyledStringTest {

    private val subject = ResourceProviderImpl(ApplicationProvider.getApplicationContext())

    @Test
    fun `markdown bold is removed from the text and styled`() {
        val result = "Yes, I've **set my card PIN** and know it".markdownToAnnotatedString()

        assertEquals("Yes, I've set my card PIN and know it", result.text)
        assertEquals(listOf("set my card PIN"), result.boldRanges())
    }

    @Test
    fun `markdown without markers is returned unchanged`() {
        val result = "Plain text".markdownToAnnotatedString()

        assertEquals("Plain text", result.text)
        assertEquals(emptyList<String>(), result.boldRanges())
    }

    @Test
    fun `markdown supports several bold parts`() {
        val result = "**One** and **two**".markdownToAnnotatedString()

        assertEquals("One and two", result.text)
        assertEquals(listOf("One", "two"), result.boldRanges())
    }

    @Test
    fun `markdown bold in a resource keeps its styling`() {
        val result = subject.getMarkdownString(R.string.pid_issuance_onboarding_eid_prim_button)

        assertEquals("Yes, I've set my card PIN and know it", result.text)
        assertEquals(listOf("set my card PIN"), result.boldRanges())
    }

    @Test
    fun `a resource without styling is returned as plain text`() {
        val result = subject.getMarkdownString(R.string.generic_ok)

        assertEquals("OK", result.text)
        assertEquals(emptyList<String>(), result.boldRanges())
    }

    private fun AnnotatedString.boldRanges(): List<String> =
        spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .sortedBy { it.start }
            .map { text.substring(it.start, it.end) }
}