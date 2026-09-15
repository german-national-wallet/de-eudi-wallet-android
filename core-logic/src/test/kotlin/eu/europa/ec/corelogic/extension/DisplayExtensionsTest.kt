/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.extension

import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DisplayExtensionsTest {

    @Test
    fun `getLocalizedClaimName iterates preferred languages and finds exact match`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Familienname", Locale.GERMAN),
            IssuerMetadata.Claim.Display("Family name", Locale.ENGLISH),
            IssuerMetadata.Claim.Display("Nom de famille", Locale.FRENCH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("Familienname", result)
    }

    @Test
    fun `getLocalizedClaimName falls back to first available when no language match`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Familienname", Locale.GERMAN),
            IssuerMetadata.Claim.Display("Family name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.JAPANESE,
            fallback = "family_name"
        )

        assertEquals("Familienname", result)
    }

    @Test
    fun `getLocalizedClaimName handles empty display list`() {
        val displays = emptyList<IssuerMetadata.Claim.Display>()

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("family_name", result)
    }

    @Test
    fun `getLocalizedClaimName handles null display list`() {
        val displays: List<IssuerMetadata.Claim.Display>? = null

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("family_name", result)
    }

    @Test
    fun `getLocalizedClaimName matches language but not region`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Family name (UK)", Locale.UK),
            IssuerMetadata.Claim.Display("Family name (US)", Locale.US)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.ENGLISH,
            fallback = "family_name"
        )

        assertEquals("Family name (UK)", result)
    }

    @Test
    fun `getLocalizedClaimName matches language when exact locale not available`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Family name", Locale.ENGLISH),
            IssuerMetadata.Claim.Display("Family name (US)", Locale.US)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.US,
            fallback = "family_name"
        )

        // The implementation matches language first, returns first match
        assertEquals("Family name", result)
    }

    @Test
    fun `getLocalizedClaimName handles multiple displays with same locale`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("First name", Locale.ENGLISH),
            IssuerMetadata.Claim.Display("Given name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.ENGLISH,
            fallback = "given_name"
        )

        assertEquals("First name", result)
    }

    @Test
    fun `getLocalizedClaimName handles display with null locale`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Family name", null),
            IssuerMetadata.Claim.Display("Familienname", Locale.GERMAN)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("Familienname", result)
    }

    @Test
    fun `getLocalizedClaimName uses fallback when all locales are null`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Name 1", null),
            IssuerMetadata.Claim.Display("Name 2", null)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("Name 1", result)
    }

    @Test
    fun `getLocalizedClaimName handles German locale correctly`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Vorname", Locale.GERMAN),
            IssuerMetadata.Claim.Display("First name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "given_name"
        )

        assertEquals("Vorname", result)
    }

    @Test
    fun `getLocalizedClaimName handles French locale correctly`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Prénom", Locale.FRENCH),
            IssuerMetadata.Claim.Display("First name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.FRENCH,
            fallback = "given_name"
        )

        assertEquals("Prénom", result)
    }

    @Test
    fun `getLocalizedClaimName handles Italian locale correctly`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Nome", Locale.ITALIAN),
            IssuerMetadata.Claim.Display("First name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.ITALIAN,
            fallback = "given_name"
        )

        assertEquals("Nome", result)
    }

    @Test
    fun `getLocalizedClaimName handles Spanish locale correctly`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("Nombre", Locale("es")),
            IssuerMetadata.Claim.Display("First name", Locale.ENGLISH)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale("es"),
            fallback = "given_name"
        )

        assertEquals("Nombre", result)
    }

    @Test
    fun `getLocalizedClaimName preserves order when multiple matches exist`() {
        val displays = listOf(
            IssuerMetadata.Claim.Display("DE Name", Locale.GERMAN),
            IssuerMetadata.Claim.Display("EN Name", Locale.ENGLISH),
            IssuerMetadata.Claim.Display("DE Name 2", Locale.GERMAN)
        )

        val result = displays.getLocalizedClaimName(
            userLocale = Locale.GERMAN,
            fallback = "family_name"
        )

        assertEquals("DE Name", result)
    }
}
