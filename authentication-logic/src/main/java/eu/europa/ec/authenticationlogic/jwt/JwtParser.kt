package eu.europa.ec.authenticationlogic.jwt

import com.nimbusds.jwt.SignedJWT
import java.text.ParseException

interface JwtParser {
    /**
     * Parses a serialized JWT string to a SignedJWT object.
     *
     * @param serializedJwt the JWT string to parse.
     * @return SignedJWT object if parsing succeeds; null otherwise.
     * @throws ParseException if the input string is not a valid JWT format.
     */
    fun parseJwt(serializedJwt: String): SignedJWT
}


internal class DefaultJwtParser: JwtParser {
    @Throws(ParseException::class)
    override fun parseJwt(serializedJwt: String): SignedJWT {
        return SignedJWT.parse(serializedJwt)
    }
}