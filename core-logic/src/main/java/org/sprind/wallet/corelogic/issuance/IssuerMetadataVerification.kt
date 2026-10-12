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
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestError.UnableToResolveCredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestException

private const val MAX_CAUSE_DEPTH = 10

/**
 * Whether the credential issuer's signed metadata is missing, untrusted or invalid: a failure on
 * the issuer's side, not the wallet's. Failing to fetch the metadata at all does not count.
 *
 * [CredentialOfferRequestException] keeps its reason outside the cause chain, so it is followed
 * explicitly.
 */
fun Throwable.isIssuerMetadataVerificationFailure(): Boolean =
    generateSequence(this) { error ->
        when (error) {
            is CredentialOfferRequestException ->
                (error.error as? UnableToResolveCredentialIssuerMetadata)?.reason

            else -> error.cause
        }?.takeIf { it !== error }
    }.take(MAX_CAUSE_DEPTH).any { it is MissingSignedMetadata || it is InvalidSignedMetadata }