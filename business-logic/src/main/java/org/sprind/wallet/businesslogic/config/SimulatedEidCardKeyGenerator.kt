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
package org.sprind.wallet.businesslogic.config

import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Generates the private key the simulated eID card derives its pseudonym from.
 *
 * The PID provider allows only two PIDs per eID card and identifies the card by this pseudonym.
 * Every simulated card would share the AusweisApp default key, so [UserRuntimeConfig] makes a
 * new key each time the simulated card is switched on: one switch-on is one card.
 * The key is a PKCS#8 DER encoding with explicit brainpoolP256r1 parameters and no public key,
 * so only the private scalar changes between the fixed prefix and suffix.
 */
class SimulatedEidCardKeyGenerator(
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
) {

    /** @throws SimulatedEidCardKeyException if no valid key could be made. */
    fun generatePseudonymKeyHex(): String {
        repeat(MAX_ATTEMPTS) {
            val scalar = randomBytes(SCALAR_LENGTH)
            if (scalar.size != SCALAR_LENGTH) {
                throw SimulatedEidCardKeyException(
                    "Random source returned ${scalar.size} bytes instead of $SCALAR_LENGTH"
                )
            }
            if (isValidScalar(scalar)) {
                return (DER_PREFIX + scalar + DER_SUFFIX).toHexString()
            }
        }
        throw SimulatedEidCardKeyException("No valid brainpoolP256r1 scalar in $MAX_ATTEMPTS attempts")
    }

    companion object {
        /** Key 1 checks the blacklist, key 2 calculates the pseudonym for the service provider. */
        const val PSEUDONYM_KEY_ID = 2
        const val SCALAR_LENGTH = 32
        private const val MAX_ATTEMPTS = 16

        /** brainpoolP256r1 as explicit ECParameters. */
        private val EC_PARAMETERS = (
            "3081e0020101302c06072a8648ce3d0101022100a9fb57dba1eea9bc3e660a909d838d726e3bf623d52620282013481d1f6e5377304404207d5a0975" +
                "fc2c3057eef67530417affe7fb8055c126dc5c6ce94a4b44f330b5d9042026dc5c6ce94a4b44f330b5d9bbd77cbf958416295cf7e1ce6bccdc18ff8c" +
                "07b60441048bd2aeb9cb7e57cb2c4b482ffc81b7afb9de27e1e3bd23c23a4453bd9ace3262547ef835c3dac4fd97f8461a14611dc9c27745132ded8e" +
                "545c1d54c72f046997022100a9fb57dba1eea9bc3e660a909d838d718c397aa3b561a6f7901e0e82974856a7"
            ).hexToByteArray()

        /** PrivateKeyInfo and AlgorithmIdentifier headers, then ECPrivateKey up to the scalar. */
        private val DER_PREFIX = "308202050201003081ec06072a8648ce3d0201".hexToByteArray() +
            EC_PARAMETERS +
            "0201010482010f3082010b0201010420".hexToByteArray()

        /** The `[0]` parameters of ECPrivateKey, closing with the cofactor. */
        private val DER_SUFFIX =
            "a081e3".hexToByteArray() + EC_PARAMETERS + "020101".hexToByteArray()

        private val BRAINPOOL_P256R1_ORDER =
            "a9fb57dba1eea9bc3e660a909d838d718c397aa3b561a6f7901e0e82974856a7".hexToByteArray()

        /** A private scalar is valid in the range [1, n - 1] of the curve order n. */
        fun isValidScalar(scalar: ByteArray): Boolean =
            scalar.any { it != 0.toByte() } && compareUnsigned(scalar, BRAINPOOL_P256R1_ORDER) < 0

        private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val diff = a[i].toUByte().compareTo(b[i].toUByte())
                if (diff != 0) return diff
            }
            return a.size.compareTo(b.size)
        }

        private fun secureRandomBytes(count: Int): ByteArray =
            ByteArray(count).also { SecureRandom().nextBytes(it) }
    }
}

class SimulatedEidCardKeyException(message: String) : GeneralSecurityException(message)