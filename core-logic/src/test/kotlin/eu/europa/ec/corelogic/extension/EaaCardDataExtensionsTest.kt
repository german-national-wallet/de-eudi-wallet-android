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

import eu.europa.ec.eudi.openid4vci.CredentialConfiguration
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialMetadata
import eu.europa.ec.eudi.openid4vci.Display
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
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

    /**
     * The card draws [EaaCardData.description] as its title and [EaaCardData.name] underneath, so
     * the two have to stay distinct. The offer used to put the display name in both, which made a
     * credential read as "FitLife Membership / FitLife Membership" while being offered and as
     * "Health Club Membership Card / FitLife Membership" once it was in the wallet.
     */
    @Test
    fun `OfferedDocument toEaaCardData keeps the description apart from the name`() {
        val offered = offeredDocument(
            Display(
                name = "FitLife Membership",
                locale = locale,
                logo = Display.Logo(
                    uri = URI.create("https://issuer.example/logo.png"),
                    alternativeText = null,
                ),
                description = "Health Club Membership Card",
                backgroundColor = "#5B5B70",
                backgroundImage = URI.create("https://issuer.example/bg.png"),
            )
        )

        val result = offered.toEaaCardData(locale)

        assertEquals("Health Club Membership Card", result.description)
        assertEquals("FitLife Membership", result.name)
        assertEquals("#5B5B70", result.backgroundColor)
        assertEquals("https://issuer.example/bg.png", result.backgroundImageUri?.toString())
        assertEquals("https://issuer.example/logo.png", result.logoUri?.toString())
        assertNull(result.validityDate)
    }

    @Test
    fun `OfferedDocument toEaaCardData falls back to the credential type without a description`() {
        val offered = offeredDocument(
            Display(name = "FitLife Membership", locale = locale, description = null)
        )

        val result = offered.toEaaCardData(locale)

        assertEquals(VCT, result.description)
        assertEquals("FitLife Membership", result.name)
    }

    @Test
    fun `OfferedDocument toEaaCardData falls back to the credential type without metadata`() {
        val offered = offeredDocument(display = null)

        val result = offered.toEaaCardData(locale)

        assertEquals(VCT, result.description)
        assertEquals(VCT, result.name)
    }

    private fun offeredDocument(display: Display?): Offer.OfferedDocument {
        val configuration = mockk<CredentialConfiguration>(relaxed = true)
        every { configuration.credentialMetadata } returns display?.let {
            CredentialMetadata(display = listOf(it))
        }
        val offered = mockk<Offer.OfferedDocument>(relaxed = true)
        every { offered.configuration } returns configuration
        every { offered.configurationIdentifier } returns
            CredentialConfigurationIdentifier("config-1")
        every { offered.documentFormat } returns SdJwtVcFormat(vct = VCT)
        return offered
    }

    private companion object {
        const val VCT = "urn:health-club:membership"
    }
}
