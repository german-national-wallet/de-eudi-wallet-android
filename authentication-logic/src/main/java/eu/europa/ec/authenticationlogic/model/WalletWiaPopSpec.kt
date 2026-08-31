package eu.europa.ec.authenticationlogic.model

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.jwt.JwtSigningSpec
import java.security.PrivateKey
import java.security.PublicKey

class WalletWiaPopSpec(signedJwt: SignedJWT, signingKey: PrivateKey, publicKey: PublicKey) :
    JwtSigningSpec(signedJwt, signingKey, publicKey) {

    val x509PublicKey: PublicKey
        get() = checkNotNull(publicKey)

    companion object {
        fun from(jwtSigningSpec: JwtSigningSpec) = WalletWiaPopSpec(
            signedJwt = jwtSigningSpec.signedJwt,
            signingKey = jwtSigningSpec.signingKey,
            publicKey = checkNotNull(jwtSigningSpec.publicKey),
        )
    }
}
