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

package org.sprind.wallet.businesslogic.util

/**
 * Header names, body fields and query parameters whose values must never be logged or exported
 * to telemetry. Shared by the network log and the telemetry interceptor so the two cannot drift.
 */
object RedactedKeys {

    const val REDACTED = "REDACTED"

    val KEYS: Set<String> = setOf(
        "X-Auth-Token",
        "auth-challenge",
        "Signature-Input",
        "Signature",
        "mdvm-token",
        "mdvm-wi-id",
        "wpb-wi-id",
        "rwsca-account-id",
        "rwsca-pin-session-token",
        "apiKey",
        "Authorization",
        "Proxy-Authorization",
        "WWW-Authenticate",
        "DPoP",
        "DPoP-Nonce",
        "OAuth-Client-Attestation",
        "OAuth-Client-Attestation-PoP",
        "Location",
        "Cookie",
        "Set-Cookie",
        "access_token",
        "refresh_token",
        "c_nonce",
        "pp_c_nonce",
        "credential",
        "credentials",
        "rwsca_pin_session_token",
        "rwsca_auth_challenge",
        "wpb_auth_challenge",
        "rwscd_key_binding_signature",
        "rwsca_wi_wrapped_prvk",
        "rwsca_wte",
        "pap_devicecheck_attestation",
        "pap_devicecheck_assertion",
        "pre-authorized_code",
        "tx_code",
        "code",
        "code_verifier",
        "mdvm_token",
        "mdvm_wi_id",
        "mdvm_auth_challenge",
        "pns_auth_challenge",
        "rwsca_account_id",
        "wpb_wia",
        "proof",
        "proofs"
    ).map { it.lowercase() }.toSet()

    fun isRedacted(key: String): Boolean = KEYS.contains(key.lowercase())
}
