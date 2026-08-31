package eu.europa.ec.corelogic.securearea

import java.security.Signature

/**
 * Wraps the static `Signature.getInstance()` factory method, for easier mocking in unit tests.
 *
 * @see java.security.Signature
 */
class SignatureProvider {
    fun getInstance(algorithm: String): Signature {
        return Signature.getInstance(algorithm)
    }
}