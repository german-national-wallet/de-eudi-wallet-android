package org.sprind.wallet.corelogic.securearea

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.CreateKeySettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * RWSCA-specific key settings carrying the credential issuer c_nonce (pp_c_nonce)
 * needed by the backend key-creation endpoint.
 */
class RwscaCreateKeySettings(
    val ppCNonce: String,
    algorithm: Algorithm = Algorithm.ESP256,
    nonce: ByteString = ByteString(),
    userAuthenticationRequired: Boolean = false,
    userAuthenticationTimeout: Duration = 0.seconds,
    validFrom: Instant? = null,
    validUntil: Instant? = null,
) : CreateKeySettings(
    algorithm = algorithm,
    nonce = nonce,
    userAuthenticationRequired = userAuthenticationRequired,
    userAuthenticationTimeout = userAuthenticationTimeout,
    validFrom = validFrom,
    validUntil = validUntil,
)
