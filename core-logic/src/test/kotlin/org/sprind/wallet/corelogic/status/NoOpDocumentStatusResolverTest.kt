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

package org.sprind.wallet.corelogic.status

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NoOpDocumentStatusResolverTest {

    @Test
    fun `resolveStatus returns a failure`() = runTest {
        val document = mockk<IssuedDocument>()

        val result = NoOpDocumentStatusResolver.resolveStatus(document)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun `resolveStatus does not touch the document`() = runTest {
        val document = mockk<IssuedDocument>()

        NoOpDocumentStatusResolver.resolveStatus(document)

        confirmVerified(document)
    }
}
