package eu.europa.ec.authenticationlogic.model

import com.nimbusds.jose.jwk.JWK
import java.security.PrivateKey

/**
 * @param public: The public key for verifying a [SignedJwt]
 * @param private: The private key used for signing that same [SignedJwt]
 */
data class JwtKeyPair(val public: JWK, val private: PrivateKey) {
}