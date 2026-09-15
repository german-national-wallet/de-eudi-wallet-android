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
 * See the License for the specific language
 * governing permissions and limitations under the License.
 */

package eu.europa.ec.corelogic.extension

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI
import java.time.Instant
import java.util.Locale

class EaaCardDataExtensionsTest {

    private val locale = Locale.ENGLISH

    @Test
    fun `IssuedDocument toEaaCardData maps issuer metadata when present`() = runTest {
        val validityDate = Instant.parse("2030-05-13T14:25:00.073Z")
        val display = IssuerMetadata.Display(
            name = "University of Berlin",
            locale = locale,
            logo = IssuerMetadata.Logo(uri = URI.create("https://issuer.example/logo.png"), alternativeText = null),
            description = "University of Berlin",
            backgroundColor = "#1A237E",
            textColor = "#FFFFFF",
            backgroundImageUri = URI.create("https://issuer.example/bg.png"),
        )
        val metadata = IssuerMetadata(
            display = listOf(display),
            claims = null,
            issuerDisplay = emptyList(),
            credentialIssuerIdentifier = "https://issuer.example",
            documentConfigurationIdentifier = "config-1",
        )
        val document = mockk<IssuedDocument>(relaxed = true)
        every { document.id } returns "doc-1"
        every { document.format } returns MsoMdocFormat(docType = "org.iso.18013.5.1.mDL")
        every { document.issuerMetadata } returns metadata
        coEvery { document.getValidUntil() } returns Result.success(validityDate)

        val result = document.toEaaCardData(locale)

        assertEquals("doc-1", result.id)
        assertEquals("University of Berlin", result.description)
        assertEquals("University of Berlin", result.name)
        assertEquals("#1A237E", result.backgroundColor)
        assertEquals("https://issuer.example/bg.png", result.backgroundImageUri?.toString())
        assertEquals("https://issuer.example/logo.png", result.logoUri?.toString())
        assertEquals(validityDate, result.validityDate)
    }

    @Test
    fun `IssuedDocument toEaaCardData falls back to docType when no metadata`() = runTest {
        val validityDate = Instant.parse("2030-05-13T14:25:00.073Z")
        val document = mockk<IssuedDocument>(relaxed = true)
        every { document.id } returns "doc-2"
        every { document.format } returns MsoMdocFormat(docType = "org.iso.18013.5.1.mDL")
        every { document.issuerMetadata } returns null
        coEvery { document.getValidUntil() } returns Result.success(validityDate)

        val result = document.toEaaCardData(locale)

        assertEquals("doc-2", result.id)
        assertEquals("org.iso.18013.5.1.mDL", result.description)
        assertEquals("org.iso.18013.5.1.mDL", result.name)
        assertNull(result.backgroundColor)
        assertNull(result.backgroundImageUri)
        assertNull(result.logoUri)
        assertEquals(validityDate, result.validityDate)
    }

    @Test
    fun `IssuedDocument toEaaCardData has null validityDate when getValidUntil returns failure`() = runTest {
        val document = mockk<IssuedDocument>(relaxed = true)
        every { document.id } returns "doc-3"
        every { document.format } returns MsoMdocFormat(docType = "org.iso.18013.5.1.mDL")
        every { document.issuerMetadata } returns null
        coEvery { document.getValidUntil() } returns Result.failure(IllegalStateException("no expiry"))

        val result = document.toEaaCardData(locale)

        assertEquals("doc-3", result.id)
        assertNull(result.validityDate)
    }

    @Test
    fun `IssuedDocument toEaaCardData drops far-future sentinel validityDate`() = runTest {
        val sentinelDate = Instant.parse("9999-12-31T23:59:59Z")
        val document = mockk<IssuedDocument>(relaxed = true)
        every { document.id } returns "doc-4"
        every { document.format } returns MsoMdocFormat(docType = "org.iso.18013.5.1.mDL")
        every { document.issuerMetadata } returns null
        coEvery { document.getValidUntil() } returns Result.success(sentinelDate)

        val result = document.toEaaCardData(locale)

        assertEquals("doc-4", result.id)
        assertNull(result.validityDate)
    }
}