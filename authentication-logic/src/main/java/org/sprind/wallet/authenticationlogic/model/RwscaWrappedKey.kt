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

package org.sprind.wallet.authenticationlogic.model

import android.security.keystore.KeyProperties.KEY_ALGORITHM_EC
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcCurve.P256
import org.multipaz.crypto.EcCurve.P384
import org.multipaz.crypto.EcCurve.P521
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.toEcPublicKey
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class RwscaWrappedKey(val encodedPublicKey: String, val wrappedPrivateKey: String) {
    fun toEcPublicKey(): EcPublicKey = decodeBase64EcPublicKey(encodedPublicKey)
}

/**
 * Decodes a base64-encoded X.509 EC public key to a Multipaz [EcPublicKey].
 */
fun decodeBase64EcPublicKey(base64EncodedPublicKey: String): EcPublicKey {
    // Note: These first four lines correspond to KeyConverterImpl.convertToECPublicKey(String).
    val keyBytes = Base64.getDecoder().decode(base64EncodedPublicKey)
    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_EC)
    val ecPublicKey = keyFactory.generatePublic(keySpec) as ECPublicKey

    // Convert java.security.interfaces.ECPublicKey -> org.multipaz.crypto.EcPublicKey.
    val curve = getCurve(ecPublicKey)
    return ecPublicKey.toEcPublicKey(curve)
}

// See https://github.com/openwallet-foundation/multipaz/blob/49ff6c0b99afa6ed546ecbf692598e958b158887/multipaz/src/commonMain/kotlin/org/multipaz/crypto/EcPublicKey.kt#L116
// and https://datatracker.ietf.org/doc/html/rfc5480#section-2.1.1
private const val OID_P256 = "1.2.840.10045.3.1.7"
private const val OID_P384 = "1.3.132.0.34"
private const val OID_P521 = "1.3.132.0.35"
private const val PRIME256V1 = "prime256v1"

/**
 * Identifies the Elliptic Curve from its  name obtained via the key. This is onerous and
 * fragile because the curve name varies with crypto provider, but it's perhaps better
 * than going by its # bits alone, which we used to do.
 */
private fun getCurve(ecPublicKey: ECPublicKey): EcCurve {
    val algorithmParameters = AlgorithmParameters.getInstance(KEY_ALGORITHM_EC)
    algorithmParameters.init(ecPublicKey.params)
    val curveName = algorithmParameters.getParameterSpec(ECGenParameterSpec::class.java).name
    val curve = when (curveName) {
        // The curve name depends on the crypto provider. The same P-256 curve may be
        // reported as secp256r1, its OID, or the OpenSSL/Conscrypt alias prime256v1.
        P256.SECGName, OID_P256, PRIME256V1 -> P256   // secp256r1 / prime256v1
        P384.SECGName, OID_P384 -> P384               // secp384r1
        P521.SECGName, OID_P521 -> P521               // secp521r1
        else -> throw IllegalArgumentException("Unsupported EC curve: $curveName")
    }
    return curve
}
