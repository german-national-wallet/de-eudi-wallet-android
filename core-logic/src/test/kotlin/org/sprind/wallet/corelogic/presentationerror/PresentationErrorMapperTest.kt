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

package org.sprind.wallet.corelogic.presentationerror

import eu.europa.ec.eudi.openid4vp.AuthorizationPolicyValidationError
import eu.europa.ec.eudi.openid4vp.AuthorizationRequestException
import eu.europa.ec.eudi.openid4vp.HttpError
import eu.europa.ec.eudi.openid4vp.RequestValidationError
import eu.europa.ec.eudi.openid4vp.ResolutionError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class PresentationErrorMapperTest {

    private val traceId = "test-trace-id"
    private val genericMessage = "generic error message"

    @Test
    fun `RequestValidationError maps to RelyingPartySide INVALID_REQUEST`() {
        val niscyError = RequestValidationError.MissingClientId
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_INVALID_REQUEST", result.errorCode)
        assertEquals(traceId, result.traceId)
        assertEquals(PresentationError.RecommendedAction.CONTACT_RELYING_PARTY, result.recommendedAction)
    }

    @Test
    fun `AuthorizationPolicyValidationError maps to RelyingPartySide TRUST_FAILURE`() {
        val niscyError = AuthorizationPolicyValidationError.MissingRequiredRegistrationCertificate
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.TRUST_FAILURE,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_TRUST_FAILURE", result.errorCode)
        assertEquals(PresentationError.RecommendedAction.CONTACT_RELYING_PARTY, result.recommendedAction)
    }

    @Test
    fun `ResolutionError ClientVpFormatsNotSupportedFromWallet maps to RelyingPartySide UNSUPPORTED_FORMATS`() {
        val niscyError = ResolutionError.ClientVpFormatsNotSupportedFromWallet
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.UNSUPPORTED_FORMATS,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_UNSUPPORTED_FORMATS", result.errorCode)
    }

    @Test
    fun `ResolutionError ClientMetadataJwksUnparsable maps to RelyingPartySide INVALID_REQUEST`() {
        val niscyError = ResolutionError.ClientMetadataJwksUnparsable(RuntimeException("bad jwks"))
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_INVALID_REQUEST", result.errorCode)
    }

    @Test
    fun `ResolutionError UnableToFetchRequestObject with IOException maps to Intermittent`() {
        val ioException = SocketTimeoutException("timeout")
        val niscyError = ResolutionError.UnableToFetchRequestObject(ioException)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Intermittent)
        assertEquals("INTERMITTENT_NETWORK", result.errorCode)
        assertEquals(PresentationError.RecommendedAction.RETRY, result.recommendedAction)
    }

    @Test
    fun `ResolutionError UnableToFetchRequestObject with non-IO cause maps to RelyingPartySide UNREACHABLE`() {
        val nonIoCause = RuntimeException("server returned 500")
        val niscyError = ResolutionError.UnableToFetchRequestObject(nonIoCause)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_UNREACHABLE", result.errorCode)
    }

    @Test
    fun `HttpError with SocketTimeoutException maps to Intermittent`() {
        val ioException = SocketTimeoutException("timeout")
        val niscyError = HttpError(ioException)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Intermittent)
    }

    @Test
    fun `HttpError with ConnectException maps to Intermittent`() {
        val ioException = ConnectException("connection refused")
        val niscyError = HttpError(ioException)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Intermittent)
    }

    @Test
    fun `HttpError with SSLException maps to Intermittent`() {
        val ioException = SSLException("SSL handshake failed")
        val niscyError = HttpError(ioException)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Intermittent)
    }

    @Test
    fun `HttpError with non-transient IOException maps to RelyingPartySide UNREACHABLE`() {
        val ioException = java.io.IOException("stream closed")
        val niscyError = HttpError(ioException)
        val throwable = AuthorizationRequestException(niscyError)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
            (result as PresentationError.RelyingPartySide).subType,
        )
    }

    @Test
    fun `IllegalStateException with 'Verifier rejected the response' maps to RelyingPartySide REJECTED`() {
        val throwable = IllegalStateException("Verifier rejected the response")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.RELYING_PARTY_REJECTED,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals(PresentationError.RecommendedAction.CONTACT_RELYING_PARTY, result.recommendedAction)
    }

    @Test
    fun `IllegalArgumentException from EncryptionPolicy maps to RelyingPartySide RESPONSE_ENCRYPTION_REQUIRED`() {
        // The message thrown by OpenId4VpConfig.EncryptionPolicy.HAIP.enforce() in the
        // wallet-core submodule: the relying party asked for direct_post instead of the
        // encrypted direct_post.jwt that HAIP requires, so the relying party is at fault.
        val throwable = IllegalArgumentException(
            "HAIP profile requires an encrypted response mode " +
                "(direct_post.jwt or dc_api.jwt); got direct_post"
        )

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.RESPONSE_ENCRYPTION_REQUIRED,
            (result as PresentationError.RelyingPartySide).subType,
        )
        assertEquals("RELYING_PARTY_RESPONSE_ENCRYPTION_REQUIRED", result.errorCode)
        assertEquals(PresentationError.RecommendedAction.CONTACT_RELYING_PARTY, result.recommendedAction)
    }

    @Test
    fun `IllegalArgumentException with an unrelated message maps to Unclassified`() {
        val throwable = IllegalArgumentException("Unsupported response mode")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Unclassified)
    }

    @Test
    fun `IllegalStateException with 'Key data missing for credential' maps to WalletSide MISSING_UNLOCK_DATA`() {
        val throwable = IllegalStateException("Key data missing for credential pid-credential")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.WalletSide)
        assertEquals(
            PresentationError.WalletSideSubType.MISSING_UNLOCK_DATA,
            (result as PresentationError.WalletSide).subType,
        )
    }

    @Test
    fun `bare IOException maps to Intermittent when transient`() {
        val throwable = SocketTimeoutException("timeout")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Intermittent)
    }

    @Test
    fun `bare IOException maps to RelyingPartySide UNREACHABLE when non-transient`() {
        val throwable = java.io.IOException("stream closed")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
            (result as PresentationError.RelyingPartySide).subType,
        )
    }

    @Test
    fun `unknown Throwable maps to Unclassified`() {
        val throwable = RuntimeException("something unexpected")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertTrue(result is PresentationError.Unclassified)
        assertEquals(PresentationError.ERROR_CODE_UNKNOWN, result.errorCode)
        assertEquals(PresentationError.RecommendedAction.DISMISS, result.recommendedAction)
        assertEquals(traceId, result.traceId)
        assertNotNull(result.cause)
    }

    @Test
    fun `displayMessage falls back to localizedMessage then genericMessage`() {
        val throwable = RuntimeException("custom message")

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        // RuntimeException's message is "custom message", which localizedMessage delegates to.
        assertEquals("custom message", result.displayMessage)
    }

    @Test
    fun `displayMessage falls back to genericMessage when message is null`() {
        val throwable = RuntimeException()

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertEquals(genericMessage, result.displayMessage)
    }

    @Test
    fun `traceId is propagated to all error types`() {
        val throwable = AuthorizationRequestException(RequestValidationError.MissingNonce)

        val result = mapToPresentationError(throwable, traceId, genericMessage)

        assertEquals(traceId, result.traceId)
    }

    @Test
    fun `null traceId is accepted`() {
        val throwable = RuntimeException("error")

        val result = mapToPresentationError(throwable, null, genericMessage)

        assertNull(result.traceId)
    }

    @Test
    fun `AuthorizationRequestException cause chain is traversed`() {
        val niscyError = RequestValidationError.MissingScope
        val innerException = AuthorizationRequestException(niscyError)
        val wrappedException = RuntimeException("wrapper", innerException)

        val result = mapToPresentationError(wrappedException, traceId, genericMessage)

        assertTrue(result is PresentationError.RelyingPartySide)
        assertEquals(
            PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            (result as PresentationError.RelyingPartySide).subType,
        )
    }
}