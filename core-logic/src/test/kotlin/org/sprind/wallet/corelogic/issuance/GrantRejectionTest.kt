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

class GrantRejectionTest {

    @Test
    fun `invalid_grant from the token endpoint is a grant rejection`() {
        assertTrue(AccessTokenRequestFailed("invalid_grant", null).isGrantRejection())
    }

    @Test
    fun `invalid_grant nested in an invalid_request description is a grant rejection`() {
        // What EUDIPLO answers to a wrong tx_code.
        val error = AccessTokenRequestFailed(
            error = "invalid_request",
            errorDescription = "Invalid 'tx_code' provided\n{\n  \"error\": \"invalid_grant\"\n}",
        )

        assertTrue(error.isGrantRejection())
    }

    @Test
    fun `a wrapped grant rejection is still detected`() {
        val error = IllegalStateException(AccessTokenRequestFailed("invalid_grant", null))

        assertTrue(error.isGrantRejection())
    }

    @Test
    fun `other token errors are not grant rejections`() {
        assertFalse(AccessTokenRequestFailed("invalid_client", "unknown client").isGrantRejection())
        assertFalse(AccessTokenRequestFailed("Token request failed with 500", "N/A").isGrantRejection())
    }

    @Test
    fun `non token errors are not grant rejections`() {
        assertFalse(PushedAuthorizationRequestFailed("invalid_grant", null).isGrantRejection())
        assertFalse(IllegalStateException("invalid_grant").isGrantRejection())
    }
}
