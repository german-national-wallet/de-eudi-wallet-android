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

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificate
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificateResult
import eu.europa.ec.eudi.wallet.registration.RegistrationFailureReason
import eu.europa.ec.eudi.wallet.registration.SupervisoryAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class ActivityLogEntriesTest {

    private val certificate = RegistrationCertificate(
        name = "Stadt Köln",
        privacyPolicyUri = "https://stadt.koeln/datenschutz",
        supportUri = "https://stadt.koeln/support",
        infoUri = "https://stadt.koeln",
        supervisoryAuthority = SupervisoryAuthority(uri = "https://www.ldi.nrw.de"),
    )

    @Test
    fun `an issuer with a validated registration certificate carries its links`() {
        val party = issuer(listOf(mock<IssuedDocument>()), RegistrationCertificateResult.Verified(certificate))

        assertEquals("Stadt Köln", party.name)
        assertEquals("https://stadt.koeln/datenschutz", party.privacyPolicyUri)
        assertEquals("https://stadt.koeln/support", party.supportUri)
        assertEquals("https://stadt.koeln", party.infoUri)
        assertEquals("https://www.ldi.nrw.de", party.supervisoryAuthorityUri)
    }

    @Test
    fun `an issuer whose certificate failed validation carries none of its links`() {
        val failed = RegistrationCertificateResult.Failed(
            reason = RegistrationFailureReason.SIGNATURE_INVALID,
            registration = certificate,
        )

        val party = issuer(listOf(mock<IssuedDocument>()), failed)

        assertNull(party.privacyPolicyUri)
        assertNull(party.supervisoryAuthorityUri)
    }

    private fun claim(vararg path: String) = IssuerMetadata.Claim(path = path.toList())

    private val metadata = IssuerMetadata(
        documentConfigurationIdentifier = "diploma",
        display = listOf(IssuerMetadata.Display(name = "University Diploma")),
        claims = listOf(claim("given_name"), claim("family_name"), claim("degree"), claim("grade")),
        credentialIssuerIdentifier = "https://issuer.example",
        issuerDisplay = null,
    )

    @Test
    fun `the snapshot keeps the labels of only the claims the activity involved`() {
        val snapshot = metadata.snapshot(involvedClaims = listOf(listOf("given_name"), listOf("ns", "degree")))

        assertEquals(listOf(listOf("given_name"), listOf("degree")), snapshot.claims?.map { it.path })
    }
}
