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

package org.sprind.wallet.corelogic.model

import eu.europa.ec.eudi.iso18013.transfer.response.WrpRegistrationInfo
import eu.europa.ec.eudi.openid4vp.AuthorizationPolicyValidationError
import eu.europa.ec.eudi.openid4vp.RequestValidationError
import eu.europa.ec.eudi.openid4vp.asException
import eu.europa.ec.eudi.wallet.registration.ClaimPathElement
import eu.europa.ec.eudi.wallet.registration.CredentialMeta
import eu.europa.ec.eudi.wallet.registration.LocalizedText
import eu.europa.ec.eudi.wallet.registration.OverAskedClaim
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificate
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificateResult
import eu.europa.ec.eudi.wallet.registration.RegistrationFailureReason
import eu.europa.ec.eudi.wallet.registration.RegistrationIdentifier
import eu.europa.ec.eudi.wallet.registration.SupervisoryAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RelyingPartyRegistrationStatusTest {

    @Test
    fun `Given no registration info, When mapped, Then it is NotEvaluated and the request proceeds`() {
        val status = (null as WrpRegistrationInfo?).toRelyingPartyRegistrationStatus()

        assertEquals(RelyingPartyRegistrationStatus.NotEvaluated, status)
        assertFalse(
            "WrpRegistrationPolicy.Disabled must not reject anything - it is today's behaviour",
            status.rejectsRequest,
        )
    }

    @Test
    fun `Given a verified certificate with no over-asking, When mapped, Then the request proceeds`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(),
            overAskedClaims = emptyList(),
        ).toRelyingPartyRegistrationStatus()

        assertEquals(RelyingPartyRegistrationStatus.WithinRegisteredScope(), status)
        assertFalse(status.rejectsRequest)
    }

    @Test
    fun `Given a verified in-scope certificate, When mapped, Then its purpose and authority are carried`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(
                supervisoryAuthority = SupervisoryAuthority(uri = AUTHORITY_URI),
                purpose = listOf(LocalizedText(language = "de", value = "Altersprüfung")),
            ),
            overAskedClaims = emptyList(),
        ).toRelyingPartyRegistrationStatus()

        assertEquals(
            RelyingPartyRegistrationStatus.WithinRegisteredScope(
                purpose = listOf(LocalizedTextDomain(language = "de", value = "Altersprüfung")),
                supervisoryAuthorityUri = AUTHORITY_URI,
            ),
            status,
        )
        assertEquals(AUTHORITY_URI, status.reportUri)
    }

    @Test
    fun `Given texts in several languages, When one is picked for a locale, Then the closest match wins`() {
        val texts = listOf(
            LocalizedTextDomain(language = "fr", value = "français"),
            LocalizedTextDomain(language = "en", value = "English"),
            LocalizedTextDomain(language = "de-DE", value = "Deutsch"),
        )

        assertEquals("Deutsch", texts.forLocale(Locale.GERMANY))
        assertEquals("Deutsch", texts.forLocale(Locale.GERMAN))
        assertEquals("English", texts.forLocale(Locale.ITALIAN))
        assertEquals("français", texts.take(1).forLocale(Locale.ITALIAN))
        assertEquals(null, emptyList<LocalizedTextDomain>().forLocale(Locale.GERMAN))
    }

    @Test
    fun `Given a certificate that failed validation, When a report target is asked for, Then there is none`() {
        assertEquals(
            null,
            RegistrationCertificateResult.Failed(RegistrationFailureReason.entries.first())
                .toRelyingPartyRegistrationStatus()
                .reportUri,
        )
    }

    @Test
    fun `Given a verified certificate with over-asked claims, When mapped, Then the request is rejected`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(),
            overAskedClaims = listOf(
                overAskedClaim(ClaimPathElement.Claim("family_name")),
                overAskedClaim(
                    ClaimPathElement.Claim("nationalities"),
                    ClaimPathElement.ArrayElement(1),
                ),
            ),
        ).toRelyingPartyRegistrationStatus()

        assertTrue("Over-asking must reject the request, got $status", status.rejectsRequest)
        val overAsking = status as RelyingPartyRegistrationStatus.OverAsking
        assertEquals(
            listOf("family_name", "nationalities/1"),
            overAsking.overAskedClaims.map { it.path },
        )
        assertEquals(listOf(SD_JWT_VC, SD_JWT_VC), overAsking.overAskedClaims.map { it.format })
    }

    @Test
    fun `Given a wildcard claim path, When mapped, Then it renders the way the certificate spells it`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(),
            overAskedClaims = listOf(
                overAskedClaim(
                    ClaimPathElement.Claim("nationalities"),
                    ClaimPathElement.AllArrayElements,
                ),
            ),
        ).toRelyingPartyRegistrationStatus()

        // The wildcard prints as "null", its JSON spelling.
        assertEquals(
            "nationalities/null",
            (status as RelyingPartyRegistrationStatus.OverAsking)
                .overAskedClaims.single().path,
        )
    }

    @Test
    fun `Given an over-asking certificate naming a supervisory authority, When mapped, Then its uri is carried`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(
                supervisoryAuthority = SupervisoryAuthority(
                    name = "Example Data Protection Authority",
                    uri = AUTHORITY_URI,
                ),
            ),
            overAskedClaims = listOf(overAskedClaim(ClaimPathElement.Claim("family_name"))),
        ).toRelyingPartyRegistrationStatus()

        assertEquals(
            AUTHORITY_URI,
            (status as RelyingPartyRegistrationStatus.OverAsking).supervisoryAuthorityUri,
        )
    }

    @Test
    fun `Given an over-asking certificate with a blank authority uri, When mapped, Then no uri is carried`() {
        val status = RegistrationCertificateResult.Verified(
            registration = registration(
                supervisoryAuthority = SupervisoryAuthority(name = "No Contact Uri", uri = "  "),
            ),
            overAskedClaims = listOf(overAskedClaim(ClaimPathElement.Claim("family_name"))),
        ).toRelyingPartyRegistrationStatus()

        assertEquals(
            null,
            (status as RelyingPartyRegistrationStatus.OverAsking).supervisoryAuthorityUri,
        )
    }

    @Test
    fun `Given every failure reason, When mapped, Then each is NotRegistered and rejects the request`() {
        RegistrationFailureReason.entries.forEach { reason ->
            val status = RegistrationCertificateResult.Failed(reason)
                .toRelyingPartyRegistrationStatus()

            assertEquals(
                "$reason must map to NotRegistered",
                RelyingPartyRegistrationStatus.NotRegistered(reason),
                status,
            )
            assertTrue("$reason must reject the request", status.rejectsRequest)
        }
    }

    @Test
    fun `Given openid4vp's registration certificate errors, When mapped, Then each is NotRegistered`() {
        val expected = mapOf(
            AuthorizationPolicyValidationError.MissingRequiredRegistrationCertificate to
                RegistrationFailureReason.CERTIFICATE_ABSENT,
            AuthorizationPolicyValidationError.MultipleRegistrationCertificates to
                RegistrationFailureReason.MALFORMED,
            AuthorizationPolicyValidationError.MalformedRegistrationCertificate("bad") to
                RegistrationFailureReason.MALFORMED,
        )

        expected.forEach { (error, reason) ->
            assertEquals(
                "$error must map to $reason",
                RelyingPartyRegistrationStatus.NotRegistered(reason),
                error.asException().toNotRegisteredOrNull(),
            )
        }
    }

    @Test
    fun `Given any other error, When mapped, Then it is not a registration rejection`() {
        assertNull(
            RequestValidationError.InvalidJarJwt("Untrusted x5c").asException()
                .toNotRegisteredOrNull()
        )
        assertNull(IllegalStateException("boom").toNotRegisteredOrNull())
    }

    @Test
    fun `Given a registration info of an unknown type, When mapped, Then it is NotRegistered`() {
        val status = object : WrpRegistrationInfo {}.toRelyingPartyRegistrationStatus()

        assertEquals(
            RelyingPartyRegistrationStatus.NotRegistered(RegistrationFailureReason.MALFORMED),
            status,
        )
        assertTrue(status.rejectsRequest)
    }

    private fun registration(
        supervisoryAuthority: SupervisoryAuthority? = null,
        purpose: List<LocalizedText> = emptyList(),
    ): RegistrationCertificate = RegistrationCertificate(
        identifiers = listOf(RegistrationIdentifier(value = RELYING_PARTY_ID)),
        name = "Example Relying Party",
        supervisoryAuthority = supervisoryAuthority,
        purpose = purpose,
    )

    private fun overAskedClaim(vararg path: ClaimPathElement) = OverAskedClaim(
        format = SD_JWT_VC,
        meta = CredentialMeta(vctValues = listOf(PID_VCT)),
        path = path.toList(),
    )

    private companion object {
        const val SD_JWT_VC = "dc+sd-jwt"
        const val PID_VCT = "urn:eudi:pid:1"
        const val RELYING_PARTY_ID = "https://relying-party.example.invalid"
        const val AUTHORITY_URI = "https://authority.example.invalid/complaint"
    }
}
