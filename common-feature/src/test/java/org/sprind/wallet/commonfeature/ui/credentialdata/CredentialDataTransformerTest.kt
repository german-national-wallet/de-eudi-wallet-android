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

package org.sprind.wallet.commonfeature.ui.credentialdata

import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.sprind.wallet.commonfeature.ui.credentialdata.CredentialDataTransformer.Companion.MISSING_VALUE
import java.util.Locale

private const val PID_NAMESPACE = "eu.europa.ec.eudi.pid.1"
private const val PID_DE_NAMESPACE = "eu.europa.ec.eudi.pid.de.1"
private const val MDL_NAMESPACE = "org.iso.18013.5.1"
private const val ADDRESS_TITLE = "Adresse"
private const val AGE_TITLE = "Alter"

/** Metadata excerpts follow the PID Provider's `pid-sd-jwt_3` and `pid-mso-mdoc_3` configurations. */
class CredentialDataTransformerTest {

    private val locale = Locale.GERMANY

    private val resourceProvider = mock<ResourceProvider> {
        on { getLocale() } doReturn locale
        on { getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_yes) } doReturn "Ja"
        on { getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_no) } doReturn "Nein"
        on { getString(R.string.eaa_inspection_eaa_detailed_data_label_adress) } doReturn ADDRESS_TITLE
        on { getString(R.string.eaa_inspection_eaa_detailed_data_label_age) } doReturn AGE_TITLE
    }

    private val transformer = CredentialDataTransformer(resourceProvider)

    @Test
    fun `SD-JWT claims missing from the old static schema are shown with their metadata label`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim("family_name", "MUSTERMANN"),
                claim("birth_name", "GABLER"),
                claim("title", "DR."),
                claim("also_known_as", "SCHWESTER AGNES"),
            ),
            metadataClaims = listOf(
                metadata(listOf("family_name"), "Familienname"),
                metadata(listOf("birth_name"), "Geburtsname"),
                metadata(listOf("title"), "Doktorgrad"),
                metadata(listOf("also_known_as"), "Ordens-/Künstlername"),
            ),
        )

        assertEquals(
            listOf(
                item("family_name", "Familienname", "MUSTERMANN"),
                item("birth_name", "Geburtsname", "GABLER"),
                item("title", "Doktorgrad", "DR."),
                item("also_known_as", "Ordens-/Künstlername", "SCHWESTER AGNES"),
            ),
            items,
        )
    }

    @Test
    fun `a PID claim the metadata lists but the credential lacks is shown with a dash`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(claim("family_name", "MUSTERMANN"), claim("given_name", "ERIKA")),
            metadataClaims = listOf(
                metadata(listOf("family_name"), "Familienname"),
                metadata(listOf("given_name"), "Vorname(n)"),
                metadata(listOf("title"), "Doktorgrad"),
                metadata(listOf("also_known_as"), "Ordens-/Künstlername"),
                metadata(listOf("place_of_birth", "locality"), "Geburtsort"),
            ),
        )

        assertEquals(
            listOf(
                item("family_name", "Familienname", "MUSTERMANN"),
                item("given_name", "Vorname(n)", "ERIKA"),
                missing("title", "Doktorgrad"),
                missing("also_known_as", "Ordens-/Künstlername"),
                missing("place_of_birth.locality", "Geburtsort"),
            ),
            items,
        )
    }

    @Test
    fun `an EAA claim without a value is left out`() {
        val items = transformer.transform(
            isPid = false,
            claims = listOf(claim("degree", "MSc"), claim("minor", "")),
            metadataClaims = listOf(
                metadata(listOf("degree"), "Abschluss"),
                metadata(listOf("minor"), "Nebenfach"),
                metadata(listOf("grade"), "Note"),
            ),
        )

        assertEquals(listOf(item("degree", "Abschluss", "MSc")), items)
    }

    @Test
    fun `claims follow the metadata order and untranslated ones come last under their own name`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim("custom_claim", "X"),
                claim("given_name", "ERIKA"),
                claim("family_name", "MUSTERMANN"),
            ),
            metadataClaims = listOf(
                metadata(listOf("family_name"), "Familienname"),
                metadata(listOf("given_name"), "Vorname(n)"),
            ),
        )

        assertEquals(listOf("family_name", "given_name", "custom_claim"), items.map { it.id })
        assertEquals(item("custom_claim", "custom_claim", "X"), items.last())
    }

    @Test
    fun `a translated SD-JWT object gets a heading followed by its claims in metadata order`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim(
                    "address",
                    children = listOf(
                        claim("locality", "KÖLN"),
                        claim("street_address", "HEIDESTRASSE 17"),
                        claim("country", "DE"),
                    ),
                ),
                claim("nationalities", value = listOf("DE"), children = listOf(element(0, "DE"))),
            ),
            metadataClaims = listOf(
                metadata(listOf("address"), "Adresse"),
                metadata(listOf("address", "street_address"), "Straße"),
                metadata(listOf("address", "locality"), "Stadt"),
                metadata(listOf("address", "region"), "Staat"),
                metadata(listOf("address", "country"), "Land"),
                metadata(listOf("nationalities"), "Staatsangehörigkeit"),
            ),
        )

        assertEquals(
            listOf(
                CredentialDataItem.Heading("address", "Adresse"),
                item("address.street_address", "Straße", "HEIDESTRASSE 17"),
                item("address.locality", "Stadt", "KÖLN"),
                missing("address.region", "Staat"),
                item("address.country", "Land", "DEUTSCHLAND"),
                item("nationalities", "Staatsangehörigkeit", "DEUTSCHLAND"),
            ),
            items,
        )
    }

    @Test
    fun `a PID SD-JWT address and age without a metadata translation get the app's headings`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim("address", children = listOf(claim("locality", "KÖLN"))),
                claim("age_equal_or_over", children = listOf(claim("18", true))),
            ),
            metadataClaims = listOf(
                metadata(listOf("address", "locality"), "Stadt des Wohnsitzes"),
                metadata(listOf("age_equal_or_over", "18"), "Älter als 18"),
            ),
        )

        assertEquals(
            listOf(
                CredentialDataItem.Heading("address", ADDRESS_TITLE),
                item("address.locality", "Stadt des Wohnsitzes", "KÖLN"),
                CredentialDataItem.Heading("age_equal_or_over", AGE_TITLE),
                item("age_equal_or_over.18", "Älter als 18", "Ja"),
            ),
            items,
        )
    }

    @Test
    fun `an EAA mdoc stays one flat list without headings`() {
        val items = transformer.transform(
            isPid = false,
            claims = listOf(
                mdocClaim(MDL_NAMESPACE, "resident_city", "KÖLN"),
                mdocClaim(MDL_NAMESPACE, "age_over_18", true),
            ),
            metadataClaims = emptyList(),
        )

        assertEquals(
            listOf(
                item("$MDL_NAMESPACE.resident_city", "resident_city", "KÖLN"),
                item("$MDL_NAMESPACE.age_over_18", "age_over_18", "Ja"),
            ),
            items,
        )
    }

    @Test
    fun `an SD-JWT object without a translated path lists its children in place`() {
        val items = transformer.transform(
            isPid = false,
            claims = listOf(claim("place_of_birth", children = listOf(claim("locality", "BERLIN")))),
            metadataClaims = listOf(metadata(listOf("place_of_birth", "locality"), "Geburtsort")),
        )

        assertEquals(listOf(item("place_of_birth.locality", "Geburtsort", "BERLIN")), items)
    }

    @Test
    fun `booleans, country code arrays and dates are formatted for display`() {
        val items = transformer.transform(
            isPid = false,
            claims = listOf(
                claim("age_equal_or_over", children = listOf(claim("18", true), claim("65", false))),
                claim(
                    "nationalities",
                    value = listOf("DE", "FR"),
                    children = listOf(element(0, "DE"), element(1, "FR")),
                ),
                claim("birthdate", "1964-08-12"),
            ),
            metadataClaims = listOf(
                metadata(listOf("age_equal_or_over"), "Alter"),
                metadata(listOf("age_equal_or_over", "18"), "Älter als 18"),
                metadata(listOf("age_equal_or_over", "65"), "Älter als 65"),
                metadata(listOf("nationalities"), "Staatsangehörigkeit"),
                metadata(listOf("birthdate"), "Geburtsdatum"),
            ),
        )

        assertEquals(
            listOf(
                CredentialDataItem.Heading("age_equal_or_over", "Alter"),
                item("age_equal_or_over.18", "Älter als 18", "Ja"),
                item("age_equal_or_over.65", "Älter als 65", "Nein"),
                item("nationalities", "Staatsangehörigkeit", "DEUTSCHLAND\nFRANKREICH"),
                item("birthdate", "Geburtsdatum", "1964-08-12".toDateFormatted(locale.toLanguageTag())!!),
            ),
            items,
        )
    }

    @Test
    fun `SD-JWT format claims and PID validity dates are left out`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim("iat", 1_700_000_000),
                claim("exp", 1_800_000_000),
                claim("family_name", "MUSTERMANN"),
                claim("date_of_expiry", "2029-05-13"),
            ),
            metadataClaims = listOf(metadata(listOf("date_of_expiry"), "Gültig bis")),
        )

        assertEquals(listOf("family_name"), items.map { it.id })
    }

    @Test
    fun `EAA validity dates listed as attributes are shown, SD-JWT format claims are not`() {
        val sdJwt = transformer.transform(
            isPid = false,
            claims = listOf(claim("iat", 1_700_000_000), claim("date_of_expiry", "2029-05-13")),
            metadataClaims = emptyList(),
        )
        val mdoc = transformer.transform(
            isPid = false,
            claims = listOf(mdocClaim(MDL_NAMESPACE, "expiry_date", "2029-05-13")),
            metadataClaims = emptyList(),
        )

        assertEquals(listOf("date_of_expiry"), sdJwt.map { it.id })
        assertEquals(listOf("$MDL_NAMESPACE.expiry_date"), mdoc.map { it.id })
    }

    @Test
    fun `the metadata label follows the user's language`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(claim("title", "DR.")),
            metadataClaims = listOf(
                IssuerMetadata.Claim(
                    path = listOf("title"),
                    display = listOf(
                        IssuerMetadata.Claim.Display(name = "Doctoral degree", locale = Locale.ENGLISH),
                        IssuerMetadata.Claim.Display(name = "Doktorgrad", locale = Locale.GERMAN),
                    ),
                ),
            ),
        )

        assertEquals(listOf(item("title", "Doktorgrad", "DR.")), items)
    }

    @Test
    fun `PID mdoc data elements follow the metadata order under address and age headings`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                mdocClaim(PID_NAMESPACE, "expiry_date", "2029-05-13"),
                mdocClaim(PID_NAMESPACE, "portrait", byteArrayOf(1, 2, 3)),
                mdocClaim(PID_NAMESPACE, "resident_city", "KÖLN"),
                mdocClaim(PID_NAMESPACE, "place_of_birth", mapOf("locality" to "BERLIN")),
                mdocClaim(PID_NAMESPACE, "family_name", "MUSTERMANN"),
                mdocClaim(PID_DE_NAMESPACE, "age_over_18", true),
            ),
            metadataClaims = listOf(
                metadata(listOf(PID_NAMESPACE, "family_name"), "Familienname"),
                metadata(listOf(PID_DE_NAMESPACE, "academic_title"), "Doktorgrad"),
                // Listed twice on purpose: the dash is shown once.
                metadata(listOf(PID_DE_NAMESPACE, "academic_title"), "Doktorgrad"),
                metadata(listOf(PID_NAMESPACE, "resident_city"), "Stadt"),
                metadata(listOf(PID_NAMESPACE, "place_of_birth", "locality"), "Geburtsort"),
                metadata(listOf(PID_DE_NAMESPACE, "age_over_18"), "Älter als 18"),
                metadata(listOf(PID_NAMESPACE, "expiry_date"), "Gültig bis"),
            ),
        )

        assertEquals(
            listOf(
                item("$PID_NAMESPACE.family_name", "Familienname", "MUSTERMANN"),
                missing("$PID_DE_NAMESPACE.academic_title", "Doktorgrad"),
                CredentialDataItem.Heading("#address", ADDRESS_TITLE),
                item("$PID_NAMESPACE.resident_city", "Stadt", "KÖLN"),
                item("$PID_NAMESPACE.place_of_birth.locality", "Geburtsort", "BERLIN"),
                CredentialDataItem.Heading("#age", AGE_TITLE),
                item("$PID_DE_NAMESPACE.age_over_18", "Älter als 18", "Ja"),
            ),
            items,
        )
    }

    @Test
    fun `images are left out, whether bytes, a data URI or a known image claim`() {
        val items = transformer.transform(
            isPid = true,
            claims = listOf(
                claim("family_name", "MUSTERMANN"),
                claim("photo", "data:image/jpeg;base64,/9j/4AAQ"),
                claim("picture", "/9j/4AAQSkZJRgABAQ"),
            ),
            metadataClaims = listOf(
                metadata(listOf("family_name"), "Familienname"),
                metadata(listOf("portrait"), "Lichtbild"),
            ),
        )

        assertEquals(listOf(item("family_name", "Familienname", "MUSTERMANN")), items)
    }

    @Test
    fun `a lowercase country code is spelled out like an uppercase one`() {
        val items = transformer.transform(
            isPid = false,
            claims = listOf(claim("address", children = listOf(claim("country", "de")))),
            metadataClaims = listOf(metadata(listOf("address", "country"), "Land")),
        )

        assertEquals(listOf(item("address.country", "Land", "DEUTSCHLAND")), items)
    }

    @Test
    fun `an array element path arrives as its array's path and adds no row of its own`() {
        // wallet-core keeps only the claim names, so ["nationalities", null] becomes ["nationalities"].
        val items = transformer.transform(
            isPid = true,
            claims = listOf(claim("family_name", "MUSTERMANN")),
            metadataClaims = listOf(
                metadata(listOf("nationalities"), "Staatsangehörigkeit"),
                metadata(listOf("family_name"), "Familienname"),
                metadata(listOf("nationalities"), "Staatsangehörigkeit"),
            ),
        )

        assertEquals(
            listOf(
                missing("nationalities", "Staatsangehörigkeit"),
                item("family_name", "Familienname", "MUSTERMANN"),
            ),
            items,
        )
    }

    private fun claim(
        name: String,
        value: Any? = null,
        children: List<SdJwtVcClaim> = emptyList(),
    ) = SdJwtVcClaim(
        pathElement = ClaimPathElement.Claim(name),
        value = value ?: children.associate { it.claimName to it.value },
        rawValue = value.toString(),
        issuerMetadata = null,
        selectivelyDisclosable = true,
        children = children,
    )

    private fun element(index: Int, value: String) = SdJwtVcClaim(
        pathElement = ClaimPathElement.ArrayElement(index),
        value = value,
        rawValue = value,
        issuerMetadata = null,
        selectivelyDisclosable = true,
        children = emptyList(),
    )

    private fun mdocClaim(nameSpace: String, element: String, value: Any) = MsoMdocClaim(
        nameSpace = nameSpace,
        dataElementName = element,
        value = value,
        rawValue = byteArrayOf(),
        issuerMetadata = null,
    )

    private fun metadata(path: List<String>, germanName: String) = IssuerMetadata.Claim(
        path = path,
        display = listOf(IssuerMetadata.Claim.Display(name = germanName, locale = Locale.GERMAN)),
    )

    private fun item(id: String, label: String, value: String) =
        CredentialDataItem.Attribute(id = id, label = label, value = value)

    private fun missing(id: String, label: String) =
        CredentialDataItem.Attribute(id = id, label = label, value = MISSING_VALUE, isMissing = true)
}