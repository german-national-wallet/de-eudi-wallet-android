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

package org.sprind.wallet.businesslogic.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun `parse accepts valid semantic versions`() {
        assertEquals(
            SemanticVersion(
                major = 1,
                minor = 2,
                patch = 3,
                prereleaseIdentifiers = listOf("dev", "1")
            ),
            SemanticVersion.parse("1.2.3-dev.1+45")
        )
    }

    @Test
    fun `parse rejects invalid semantic versions`() {
        assertNull(SemanticVersion.parse("1.2"))
        assertNull(SemanticVersion.parse("01.2.3"))
        assertNull(SemanticVersion.parse("1.2.3-"))
    }

    @Test
    fun `compare uses semantic version precedence`() {
        assertTrue(SemanticVersion.parse("1.2.3")!! < SemanticVersion.parse("1.2.4")!!)
        assertTrue(SemanticVersion.parse("1.2.3-dev")!! < SemanticVersion.parse("1.2.3")!!)
        assertTrue(SemanticVersion.parse("1.2.3-dev.1")!! < SemanticVersion.parse("1.2.3-dev.2")!!)
        assertTrue(SemanticVersion.parse("1.2.3-1")!! < SemanticVersion.parse("1.2.3-dev")!!)
        assertFalse(SemanticVersion.parse("1.2.3+1")!! < SemanticVersion.parse("1.2.3+2")!!)
    }
}
