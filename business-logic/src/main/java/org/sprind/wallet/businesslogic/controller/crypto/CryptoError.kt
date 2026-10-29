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

package org.sprind.wallet.businesslogic.controller.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import java.security.InvalidAlgorithmParameterException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/** Why a cryptographic operation failed; carried as the failure of a [Result]. */
sealed class CryptoError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class KeyNotFound(cause: Throwable? = null) : CryptoError("Key not found", cause)
    class InvalidParameters(cause: Throwable? = null) : CryptoError("Invalid cipher parameters", cause)
    class UserNotAuthenticated(cause: Throwable) : CryptoError("User authentication required", cause)
    class KeyInvalidated(cause: Throwable) : CryptoError("Key permanently invalidated", cause)
    class AuthenticationFailed(cause: Throwable) : CryptoError("Ciphertext authentication failed", cause)
    class Failure(cause: Throwable) : CryptoError("Cryptographic operation failed", cause)
}

fun Throwable.toCryptoError(): CryptoError = when (this) {
    is CryptoError -> this
    is KeyPermanentlyInvalidatedException -> CryptoError.KeyInvalidated(this)
    is UserNotAuthenticatedException -> CryptoError.UserNotAuthenticated(this)
    is AEADBadTagException -> CryptoError.AuthenticationFailed(this)
    is UnrecoverableKeyException -> CryptoError.KeyNotFound(this)
    is InvalidAlgorithmParameterException, is IllegalArgumentException -> CryptoError.InvalidParameters(this)
    else -> CryptoError.Failure(this)
}
