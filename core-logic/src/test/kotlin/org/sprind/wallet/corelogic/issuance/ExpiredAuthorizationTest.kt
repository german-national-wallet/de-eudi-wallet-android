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

import eu.europa.ec.eudi.openid4vci.CredentialIssuanceError.AccessTokenRequestFailed
import eu.europa.ec.eudi.openid4vci.CredentialIssuanceError.PushedAuthorizationRequestFailed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiredAuthorizationTest {

    @Test
    fun `an expiry error in the redirect after the eID step is an expired authorization`() {
        listOf("invalid_request", "invalid_request_uri", "expired_token").forEach { error ->
            assertTrue(error, AuthorizationRedirectException(error, null).isExpiredAuthorization())
        }
    }

    @Test
    fun `an expiry error from the token endpoint is an expired authorization`() {
        assertTrue(
            AccessTokenRequestFailed("invalid_request", "authorization code expired")
                .isExpiredAuthorization()
        )
    }

    @Test
    fun `a wrapped expired authorization is still detected`() {
        // How the authorization handler hands a failed redirect to wallet-core.
        val error = Exception("Error occurred", AuthorizationRedirectException("invalid_request", null))

        assertTrue(error.isExpiredAuthorization())
    }

    @Test
    fun `a grant rejection is not an expired authorization`() {
        assertFalse(AccessTokenRequestFailed("invalid_grant", null).isExpiredAuthorization())
        // EUDIPLO nests invalid_grant in an invalid_request for a wrong tx_code.
        assertFalse(
            AccessTokenRequestFailed("invalid_request", "{\"error\": \"invalid_grant\"}")
                .isExpiredAuthorization()
        )
    }

    @Test
    fun `other errors are not an expired authorization`() {
        assertFalse(AuthorizationRedirectException("access_denied", "user cancelled").isExpiredAuthorization())
        assertFalse(AccessTokenRequestFailed("invalid_client", null).isExpiredAuthorization())
        // The PAR is made before the eID step, so its rejection is not an expiry.
        assertFalse(PushedAuthorizationRequestFailed("invalid_request", null).isExpiredAuthorization())
        assertFalse(IllegalStateException("invalid_request").isExpiredAuthorization())
    }
}
