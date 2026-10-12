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

private const val INVALID_GRANT = "invalid_grant"

/**
 * Whether the token endpoint rejected the pre-authorized grant, which OpenID4VCI signals with
 * `invalid_grant` when the transaction code is wrong. Some issuers nest it in the
 * description of an `invalid_request`.
 */
fun Throwable.isGrantRejection(): Boolean =
    generateSequence(this) { it.cause }.any { error ->
        error is AccessTokenRequestFailed &&
            (error.error == INVALID_GRANT || error.errorDescription?.contains(INVALID_GRANT) == true)
    }
