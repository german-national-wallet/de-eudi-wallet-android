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

package org.sprind.wallet.corelogic.issuance

import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError.InvalidSignedMetadata
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError.MissingSignedMetadata
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError.UnableToFetchCredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestError.UnableToResolveCredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class IssuerMetadataVerificationTest {

    @Test
    fun `missing signed metadata is a verification failure`() {
        assertTrue(MissingSignedMetadata().isIssuerMetadataVerificationFailure())
    }

    @Test
    fun `untrusted or invalid signed metadata is a verification failure`() {
        val error = InvalidSignedMetadata(IllegalStateException("untrusted chain"))

        assertTrue(error.isIssuerMetadataVerificationFailure())
    }

    @Test
    fun `a verification failure inside an offer error is detected`() {
        val error = CredentialOfferRequestException(
            UnableToResolveCredentialIssuerMetadata(MissingSignedMetadata())
        )

        assertTrue(error.isIssuerMetadataVerificationFailure())
    }

    @Test
    fun `a wrapped verification failure is detected`() {
        val error = IllegalStateException(InvalidSignedMetadata(IllegalStateException("bad signature")))

        assertTrue(error.isIssuerMetadataVerificationFailure())
    }

    @Test
    fun `metadata that cannot be fetched is not a verification failure`() {
        val error = CredentialOfferRequestException(
            UnableToResolveCredentialIssuerMetadata(UnableToFetchCredentialIssuerMetadata(IOException("offline")))
        )

        assertFalse(error.isIssuerMetadataVerificationFailure())
    }

    @Test
    fun `unrelated errors are not verification failures`() {
        assertFalse(IllegalStateException("boom").isIssuerMetadataVerificationFailure())
    }
}
