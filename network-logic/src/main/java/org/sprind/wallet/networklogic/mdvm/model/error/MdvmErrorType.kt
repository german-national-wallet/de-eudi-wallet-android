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

package org.sprind.wallet.networklogic.mdvm.model.error

import org.sprind.wallet.networklogic.common.NetEntity

/**
 * The error codes that might be returned from MDVM endpoints. Equality / hashCode is
 * based on the `code` attribute. This is important because we must support preserve
 * error types that may be introduced after app compile time. For this reason,
 * instances do *not* need to be interned.
 *
 * Instances are obtained via the companion object constants (for known types) or via
 * [MdvmErrorResponse.toMdvmErrorType] (for server-sent codes).
 */
class MdvmErrorType internal constructor(
    val code: String,
    val errorSource: NetEntity = NetEntity.BACKEND_MDVM,
) {
    override fun equals(other: Any?) = other is MdvmErrorType && code == other.code
    override fun hashCode() = code.hashCode()
    override fun toString() = "MdvmErrorType(code=$code, errorSource=$errorSource)"

    companion object {
        // Server-side errors
        val ACCOUNT_REVOKED = MdvmErrorType("ACCOUNT_REVOKED")
        val ANDROID_ATTESTATION_FAILURE = MdvmErrorType("ANDROID_ATTESTATION_FAILURE")
        val BAD_REQUEST = MdvmErrorType("BAD_REQUEST")
        val CHALLENGE_VERIFICATION_FAILURE = MdvmErrorType("CHALLENGE_VERIFICATION_FAILURE")
        val DB_UNAVAILABLE = MdvmErrorType("DB_UNAVAILABLE")
        val INTERNAL_SERVER_ERROR = MdvmErrorType("INTERNAL_SERVER_ERROR")
        val MALFORMED_KEY = MdvmErrorType("MALFORMED_KEY")
        val SIGNATURE_VERIFICATION_FAILURE = MdvmErrorType("SIGNATURE_VERIFICATION_FAILURE")
        val SKIP_INTEGRITY_CHECKS_NOT_ALLOWED = MdvmErrorType("SKIP_INTEGRITY_CHECKS_NOT_ALLOWED")
        val UNKNOWN_ACCOUNT_ID = MdvmErrorType("UNKNOWN_ACCOUNT_ID")
        val WRONG_CONTENT_DIGEST = MdvmErrorType("WRONG_CONTENT_DIGEST")

        // Client-side errors
        val MDVM_KEY_NOT_FOUND = MdvmErrorType("MDVM_KEY_NOT_FOUND", errorSource = NetEntity.CLIENT)
    }
}

/** Returns the [MdvmErrorType] for this server-sent error response. */
fun MdvmErrorResponse.toMdvmErrorType(): MdvmErrorType = MdvmErrorType(code)

enum class MdvmEndpoint(vararg val errorCodes: MdvmErrorType) {
    CHALLENGE(
        MdvmErrorType.INTERNAL_SERVER_ERROR,
    ),
    REGISTER(
        MdvmErrorType.ANDROID_ATTESTATION_FAILURE,
        MdvmErrorType.BAD_REQUEST,
        MdvmErrorType.CHALLENGE_VERIFICATION_FAILURE,
        MdvmErrorType.DB_UNAVAILABLE,
        MdvmErrorType.INTERNAL_SERVER_ERROR,
        MdvmErrorType.MALFORMED_KEY,
        MdvmErrorType.SIGNATURE_VERIFICATION_FAILURE,
        MdvmErrorType.SKIP_INTEGRITY_CHECKS_NOT_ALLOWED,
        MdvmErrorType.WRONG_CONTENT_DIGEST,
    ),
    RENEWAL(
        MdvmErrorType.ACCOUNT_REVOKED,
        MdvmErrorType.ANDROID_ATTESTATION_FAILURE,
        MdvmErrorType.BAD_REQUEST,
        MdvmErrorType.CHALLENGE_VERIFICATION_FAILURE,
        MdvmErrorType.DB_UNAVAILABLE,
        MdvmErrorType.INTERNAL_SERVER_ERROR,
        MdvmErrorType.SIGNATURE_VERIFICATION_FAILURE,
        MdvmErrorType.SKIP_INTEGRITY_CHECKS_NOT_ALLOWED,
        MdvmErrorType.UNKNOWN_ACCOUNT_ID,
        MdvmErrorType.WRONG_CONTENT_DIGEST,
    ),
}
