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

package org.sprind.wallet.corelogic.controller

import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import eu.europa.ec.eudi.openid4vci.CIAuthorizationServerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialConfiguration
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerId
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOffer
import eu.europa.ec.eudi.openid4vci.Grants
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

/**
 * Builds a mock [Offer] from [issuerUrl] with no credential configurations.
 * [authorizationServerMetadata] and [grants] are only stubbed when given.
 */
internal fun buildOffer(
    issuerUrl: String,
    authorizationServerMetadata: CIAuthorizationServerMetadata? = null,
    grants: Grants? = null,
): Offer {
    // CredentialIssuerId is a value class — construct it directly
    // instead of mocking (Mockito can't mock inline classes).
    val credentialIssuerId = CredentialIssuerId.invoke(issuerUrl).getOrThrow()
    val credentialIssuerMetadata = mock(CredentialIssuerMetadata::class.java)
    whenever(credentialIssuerMetadata.credentialConfigurationsSupported)
        .thenReturn(emptyMap<CredentialConfigurationIdentifier, CredentialConfiguration>())
    val credentialOffer = mock(CredentialOffer::class.java)
    whenever(credentialOffer.credentialIssuerIdentifier).thenReturn(credentialIssuerId)
    whenever(credentialOffer.credentialIssuerMetadata).thenReturn(credentialIssuerMetadata)
    if (authorizationServerMetadata != null) {
        whenever(credentialOffer.authorizationServerMetadata).thenReturn(authorizationServerMetadata)
    }
    if (grants != null) {
        whenever(credentialOffer.grants).thenReturn(grants)
    }
    return Offer(credentialOffer)
}

/**
 * Builds a mock [CIAuthorizationServerMetadata] whose
 * `token_endpoint_auth_methods_supported` contains [methods].
 */
internal fun buildAsMetadata(
    methods: List<ClientAuthenticationMethod>,
): CIAuthorizationServerMetadata {
    val metadata = mock(CIAuthorizationServerMetadata::class.java)
    whenever(metadata.tokenEndpointAuthMethods).thenReturn(methods)
    return metadata
}
