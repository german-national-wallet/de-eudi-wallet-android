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

package org.sprind.wallet.networklogic.rwsca.model.error

import org.sprind.wallet.networklogic.common.NetEntity

/**
 * The error codes that might be returned from rWSCA endpoints.
 *
 * Unknown server codes are represented as instances with the original code string preserved,
 * rather than collapsed into a single sentinel value.
 *
 * Instances are obtained via the companion object constants (for known types) or via
 * [RwscaErrorResponse.toRwscaErrorType] (for arbitrary server-sent codes, including unknown ones).
 * Note that app must support unknown future error codes that are introduced after
 * app compile time.
 */
class RwscaErrorType internal constructor(
    val code: String,
    val errorSource: NetEntity = NetEntity.BACKEND_RWSCA,
) {
    override fun equals(other: Any?) = other is RwscaErrorType && code == other.code
    override fun hashCode() = code.hashCode()
    override fun toString() = "RwscaErrorType(code=$code, errorSource=$errorSource)"

    companion object {
        // Server-side errors
        val ACCOUNT_LOCKED = RwscaErrorType("ACCOUNT_LOCKED")
        val ACCOUNT_NOT_FOUND = RwscaErrorType("ACCOUNT_NOT_FOUND")
        val BAD_REQUEST = RwscaErrorType("BAD_REQUEST")
        val CHALLENGE_VERIFICATION_FAILURE = RwscaErrorType("CHALLENGE_VERIFICATION_FAILURE")
        val DB_UNAVAILABLE = RwscaErrorType("DB_UNAVAILABLE")
        val HSM_UNAVAILABLE = RwscaErrorType("HSM_UNAVAILABLE")
        val INTERNAL_SERVER_ERROR = RwscaErrorType("INTERNAL_SERVER_ERROR")
        val MALFORMED_DATA_HASH = RwscaErrorType("MALFORMED_DATA_HASH")
        val MALFORMED_PIN_PUB_KEY = RwscaErrorType("MALFORMED_PIN_PUB_KEY")
        val MDVM_TOKEN_VERIFICATION_FAILURE = RwscaErrorType("MDVM_TOKEN_VERIFICATION_FAILURE")
        val PIN_ALREADY_INITIALIZED = RwscaErrorType("PIN_ALREADY_INITIALIZED")
        val PIN_NOT_INITIALIZED = RwscaErrorType("PIN_NOT_INITIALIZED")
        val PIN_RETRY_BLOCKED = RwscaErrorType("PIN_RETRY_BLOCKED")
        val PIN_SESSION_TOKEN_VERIFICATION_FAILURE = RwscaErrorType("PIN_SESSION_TOKEN_VERIFICATION_FAILURE")
        val PIN_VERIFICATION_FAILED = RwscaErrorType("PIN_VERIFICATION_FAILED")
        val SIGNATURE_VERIFICATION_FAILURE = RwscaErrorType("SIGNATURE_VERIFICATION_FAILURE")
        val WRAPPED_PRVK_VERIFICATION_FAILURE = RwscaErrorType("WRAPPED_PRVK_VERIFICATION_FAILURE")

        // Client-side errors
        val ACCOUNT_NOT_FOUND_LOCALLY = RwscaErrorType("ACCOUNT_NOT_FOUND_LOCALLY", errorSource = NetEntity.CLIENT)
    }
}

/** Returns the [RwscaErrorType] for this server-sent error response. */
fun RwscaErrorResponse.toRwscaErrorType(): RwscaErrorType = RwscaErrorType(code)

enum class RwscaEndpoint(vararg val errorCodes: RwscaErrorType) {
    CHALLENGE(
        RwscaErrorType.INTERNAL_SERVER_ERROR,
    ),
    CREATE_KEYS(
        RwscaErrorType.ACCOUNT_LOCKED,
        RwscaErrorType.ACCOUNT_NOT_FOUND,
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.HSM_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
    ),
    DELETE_ACCOUNT(
        RwscaErrorType.ACCOUNT_NOT_FOUND,
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.HSM_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
    ),
    INITIALIZE_PIN_AND_START_PIN_SESSION(
        RwscaErrorType.ACCOUNT_LOCKED,
        RwscaErrorType.ACCOUNT_NOT_FOUND,
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MALFORMED_PIN_PUB_KEY,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.PIN_ALREADY_INITIALIZED,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
    ),
    REGISTER(
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
    ),
    SIGN_DATA(
        RwscaErrorType.ACCOUNT_LOCKED,
        RwscaErrorType.ACCOUNT_NOT_FOUND,
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.HSM_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MALFORMED_DATA_HASH,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.PIN_SESSION_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
        RwscaErrorType.WRAPPED_PRVK_VERIFICATION_FAILURE,
    ),
    START_PIN_SESSION(
        RwscaErrorType.ACCOUNT_LOCKED,
        RwscaErrorType.ACCOUNT_NOT_FOUND,
        RwscaErrorType.BAD_REQUEST,
        RwscaErrorType.CHALLENGE_VERIFICATION_FAILURE,
        RwscaErrorType.DB_UNAVAILABLE,
        RwscaErrorType.INTERNAL_SERVER_ERROR,
        RwscaErrorType.MDVM_TOKEN_VERIFICATION_FAILURE,
        RwscaErrorType.PIN_NOT_INITIALIZED,
        RwscaErrorType.PIN_RETRY_BLOCKED,
        RwscaErrorType.PIN_VERIFICATION_FAILED,
        RwscaErrorType.SIGNATURE_VERIFICATION_FAILURE,
    ),
}
