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

package org.sprind.wallet.uilogic.navigation.helper

import eu.europa.ec.uilogic.BuildConfig
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepLinkTypeTest {

    @Test
    fun `given a scheme the wallet owns, when parsed, then the type is accepted from other apps`() {
        listOf(
            BuildConfig.OPENID4VP_SCHEME to DeepLinkType.OPENID4VP,
            BuildConfig.EUDI_OPENID4VP_SCHEME to DeepLinkType.OPENID4VP,
            BuildConfig.MDOC_OPENID4VP_SCHEME to DeepLinkType.OPENID4VP,
            BuildConfig.HAIP_OPENID4VP_SCHEME to DeepLinkType.OPENID4VP,
            BuildConfig.CREDENTIAL_OFFER_SCHEME to DeepLinkType.CREDENTIAL_OFFER,
            BuildConfig.CREDENTIAL_OFFER_HAIP_SCHEME to DeepLinkType.CREDENTIAL_OFFER,
        ).forEach { (scheme, expected) ->
            val type = DeepLinkType.parse(scheme)
            assertEquals(expected, type, "unexpected type for $scheme")
            assertTrue(type.isAcceptedFromOtherApps, "expected $scheme to be accepted")
        }
    }

    @Test
    fun `given the issuance redirect, when parsed, then only the wallet host is accepted`() {
        val onHost = DeepLinkType.parse(
            BuildConfig.ISSUE_AUTHORIZATION_SCHEME,
            BuildConfig.ISSUE_AUTHORIZATION_HOST
        )
        assertEquals(DeepLinkType.ISSUANCE, onHost)
        assertTrue(onHost.isAcceptedFromOtherApps)

        val onAnotherHost =
            DeepLinkType.parse(BuildConfig.ISSUE_AUTHORIZATION_SCHEME, "somewhere-else")
        assertEquals(DeepLinkType.EXTERNAL, onAnotherHost)
        assertFalse(onAnotherHost.isAcceptedFromOtherApps)
    }

    @Test
    fun `given a scheme the wallet does not own, when parsed, then the type is not accepted`() {
        listOf(
            "tel",
            "https",
            "http",
            "intent",
            "content",
            "file",
            "market",
            "unknown-scheme",
        ).forEach { scheme ->
            val type = DeepLinkType.parse(scheme)
            assertEquals(DeepLinkType.EXTERNAL, type, "unexpected type for $scheme")
            assertFalse(type.isAcceptedFromOtherApps, "expected $scheme to be rejected")
        }
    }

    @Test
    fun `given a type the wallet derives itself, then it is not accepted from other apps`() {
        assertFalse(DeepLinkType.EXTERNAL.isAcceptedFromOtherApps)
        assertFalse(DeepLinkType.DYNAMIC_PRESENTATION.isAcceptedFromOtherApps)
    }
}