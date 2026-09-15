package eu.europa.ec.authenticationlogic.controller.appattestation.jwt

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.jwt.JwtParser

internal class FakeJwtParser: JwtParser {
    override fun parseJwt(serializedJwt: String): SignedJWT {
        return SignedJWT.parse(serializedJwt)
    }
}