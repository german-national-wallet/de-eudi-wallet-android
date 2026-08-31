package eu.europa.ec.authenticationlogic.jwt

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.model.JwtKeyPair
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Represents a signed JWT + corresponding signing key. This class is open to
 * allow for use-case specific subclasses. Those subclasses can then be used
 * as stronger types (prevent mixing up with other kinds of JwtSigningSpec,
 * enforced by the type system),and to document what kind of JWT is being
 * signed (e.g. a Proof of Possesion).
 *
 * @param signedJwt a signed JWT
 * @param signingKey the key used for signing [signedJwt]
 * @param publicKey publicKey the public key corresponding to [signingKey], when available.
 */
open class JwtSigningSpec(
    val signedJwt: SignedJWT,
    val signingKey: PrivateKey,
    val publicKey: PublicKey?,
) {
    val serializedJwt: String by lazy {
        signedJwt.serialize()
    }
    
    val keyPair: JwtKeyPair by lazy {
        JwtKeyPair(public = signedJwt.header.jwk, private = signingKey)
    }
}
