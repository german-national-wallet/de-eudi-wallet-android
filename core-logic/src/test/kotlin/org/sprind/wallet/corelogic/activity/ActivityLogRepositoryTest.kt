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

package org.sprind.wallet.corelogic.activity

import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.multipaz.storage.ephemeral.EphemeralStorage

class ActivityLogRepositoryTest {

    private val storage = EphemeralStorage()
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun repository() = ActivityLogRepositoryImpl(
        storage = storage,
        logController = mock<LogController>(),
        scope = testScope,
    )

    @Test
    fun `entries are listed newest first`() = testScope.runTest {
        val repository = repository()

        repository.record(entry("older", timestampMillis = 1_000))
        repository.record(entry("newer", timestampMillis = 2_000))

        assertEquals(listOf("newer", "older"), repository.entries.first().map { it.id })
    }

    @Test
    fun `entries survive a new repository instance`() = testScope.runTest {
        repository().record(entry("kept", timestampMillis = 1_000))

        assertEquals(listOf("kept"), repository().entries.first().map { it.id })
    }

    @Test
    fun `delete removes only the given entry`() = testScope.runTest {
        val repository = repository()
        repository.record(entry("gone", timestampMillis = 1_000))
        repository.record(entry("kept", timestampMillis = 2_000))

        repository.delete("gone")

        assertEquals(listOf("kept"), repository.entries.first().map { it.id })
        assertEquals(listOf("kept"), repository().entries.first().map { it.id })
    }

    @Test
    fun `clear removes every entry from storage`() = testScope.runTest {
        val repository = repository()
        repository.record(entry("gone", timestampMillis = 1_000))

        repository.clear()

        assertEquals(emptyList<ActivityLogEntry>(), repository.entries.first())
        assertEquals(emptyList<ActivityLogEntry>(), repository().entries.first())
    }

    private fun entry(id: String, timestampMillis: Long) = ActivityLogEntry(
        id = id,
        timestampMillis = timestampMillis,
        type = ActivityType.PRESENTATION,
        result = ActivityResult.COMPLETED,
        interactingParty = InteractingParty(name = "Verifier", isTrusted = true),
        credentials = listOf(
            ActivityCredential(
                documentId = "document",
                credentialIdentifier = "urn:eudi:pid:de:1",
                presentedClaims = listOf(listOf("family_name")),
            )
        ),
    )
}
