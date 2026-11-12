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

/**
 * The OAuth error codes taken to mean that the authorization session lapsed: the pushed
 * authorization request (and the `request_uri` it returned) or the authorization code expired.
 * `invalid_grant` is not among them, as it means a wrong transaction code (see [isGrantRejection]).
 */
private val EXPIRED_AUTHORIZATION_ERRORS = setOf("invalid_request", "invalid_request_uri", "expired_token")

/**
 * The authorization server redirected back after the eID step with an OAuth error instead of an
 * authorization code (RFC 6749, section 4.1.2.1).
 */
class AuthorizationRedirectException(
    val error: String,
    val errorDescription: String?,
) : IllegalStateException("Authorization failed: $error")

/**
 * Whether the authorization session expired before issuance could complete, e.g. because the user
 * took too long reading the ID card or entering the PIN. Only errors raised after the eID step
 * count: the error in the redirect from the authorization server, or the token endpoint rejecting
 * the authorization code.
 */
fun Throwable.isExpiredAuthorization(): Boolean =
    !isGrantRejection() && generateSequence(this) { it.cause }.any { error ->
        when (error) {
            is AuthorizationRedirectException -> error.error in EXPIRED_AUTHORIZATION_ERRORS
            is AccessTokenRequestFailed -> error.error in EXPIRED_AUTHORIZATION_ERRORS
            else -> false
        }
    }
