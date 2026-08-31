package eu.europa.ec.authenticationlogic.jwt

import android.security.keystore.KeyProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimNames
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder.Companion.JwtTypes.OAUTH_CLIENT_ATTESTATION_POP_JWT_TYPE
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder.Companion.JwtTypes.WALLET_INSTANCE_POP_JWT_TYPE
import eu.europa.ec.authenticationlogic.model.WalletWiaPopSpec
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.util.RandomUUIDGenerator
import org.sprind.wallet.authenticationlogic.jwt.JwtSigner
import org.sprind.wallet.authenticationlogic.jwt.JwtSpec
import org.sprind.wallet.businesslogic.controller.storage.KEYSTORE_ALIAS_WALLET_INSTANCE_ATTESTATION_KEYS
import org.sprind.wallet.businesslogic.controller.storage.KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
import java.security.interfaces.ECPublicKey
import java.time.Duration


/**
 * Generates specific kinds of JSON Web Tokens (JWTs) for various use cases needed across the
 * app.
 */
class JwtBuilder(
    private val ecKeyPairController: EcKeyPairController,
    private val randomUUIDGenerator: RandomUUIDGenerator,
    private val logController: LogController,
    private val jwtParser: JwtParser,
    private val jwtSigner: JwtSigner,
) {
    private val logTag = javaClass.simpleName

    private companion object {
        /**
         * A set of claim names used in the JWT payload.
         */
        object ClaimNames {
            const val WB_AUTH_CHALLENGE = "wb_auth_challenge"
        }

        /**
         * A set of Jwt Types
         */
        object JwtTypes {
            const val WALLET_INSTANCE_POP_JWT_TYPE = "wi-wb-auth-pop+jwt"
            const val OAUTH_CLIENT_ATTESTATION_POP_JWT_TYPE = "oauth-client-attestation-pop+jwt"
        }

        const val JWT_EXPIRY_TIME_IN_MIN: Long = 60
    }

    /**
     * Creates a new JWT of type "oauth-client-attestation-pop" with a specified nonce value and subject
     * provided that will be saved as "iss"
     * Retrieves the stored EC P-256 key pair (private and public) from the device, and constructs a signed JWT with claims
     * like issued-at, expiration-time, audience, subject and nonce.
     *
     * @param nonce a unique identifier to include as a claim in the JWT for security purposes.
     * @return A serialized JWT string with the specified claims and headers.
     */
    fun createOAuthClientAttestationJwt(
        nonce: String,
    ): WalletWiaPopSpec {
        val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_ATTESTATION_KEYS
        ecKeyPairController.generateEcKeyPair(
            keystoreAlias = keystoreAlias,
            purposes = KeyProperties.PURPOSE_SIGN
        )

        val extraClaims = mapOf(
            // corresponds to JWTClaimsSet.Builder.jwtID()
            JWTClaimNames.JWT_ID to randomUUIDGenerator.generate(),
            ClaimNames.WB_AUTH_CHALLENGE to nonce,
        )

        val spec = JwtSpec(
            type = OAUTH_CLIENT_ATTESTATION_POP_JWT_TYPE,
            algorithm = JWSAlgorithm.ES256,
            expireAfter = Duration.ofMinutes(JWT_EXPIRY_TIME_IN_MIN),
            requiredClaims = setOf(
                JWTClaimNames.ISSUED_AT,
                JWTClaimNames.EXPIRATION_TIME,
                JWTClaimNames.JWT_ID,
                ClaimNames.WB_AUTH_CHALLENGE
            )
        )

        val signingSpec = createSigningSpecFromKeystoreKey(
            spec = spec,
            extraClaims = extraClaims,
            keystoreAlias = keystoreAlias,
        )
        val serializedJwt = signingSpec.serializedJwt
        log(OAUTH_CLIENT_ATTESTATION_POP_JWT_TYPE, serializedJwt)

        return WalletWiaPopSpec.from(signingSpec)
    }

    /**
     * Creates a new JWT of type "wi-wb-auth-pop+jwt" with a specified nonce value.
     * Generates an EC P-256 key pair, stores the key data, and constructs a signed JWT with claims
     * like issued-at, expiration-time, and wb_auth_challenge.
     *
     * @param nonce a unique identifier to include as a claim in the JWT for security purposes.
     * @return A serialized JWT string with the specified claims and headers.
     */
    fun createInstancePopJwt(
        nonce: String,
    ): String {
        val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
        ecKeyPairController.generateEcKeyPair(
            keystoreAlias = keystoreAlias,
            purposes = KeyProperties.PURPOSE_SIGN
        )

        val spec = JwtSpec(
            type = WALLET_INSTANCE_POP_JWT_TYPE,
            algorithm = JWSAlgorithm.ES256,
            expireAfter = Duration.ofMinutes(JWT_EXPIRY_TIME_IN_MIN),
            requiredClaims = setOf(
                JWTClaimNames.ISSUED_AT,
                JWTClaimNames.EXPIRATION_TIME,
                ClaimNames.WB_AUTH_CHALLENGE,
            ),
        )

        val extraClaims = mapOf(
            ClaimNames.WB_AUTH_CHALLENGE to nonce,
        )

        val signingSpec =
            createSigningSpecFromKeystoreKey(
                spec,
                keystoreAlias = keystoreAlias,
                extraClaims = extraClaims,
            )

        val serializedJwt = signingSpec.serializedJwt
        log("$WALLET_INSTANCE_POP_JWT_TYPE for registration", serializedJwt)
        return serializedJwt
    }

    /**
     * Creates a new JWT of type "wi-wb-auth-pop+jwt" with a specified nonce value.
     * Used the stored private key, and constructs a signed JWT with claims
     * like issued-at, expiration-time, and wb_auth_challenge.
     * Does not include the headerJwk
     *
     * @param nonce a unique identifier to include as a claim in the JWT for security purposes.
     * @return A serialized JWT string with the specified claims and headers.
     */
    fun createInstancePopJwtWithSavedKeys(nonce: String): String? {
        val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
        if (!ecKeyPairController.hasEcKeyPair(keystoreAlias)) {
            logController.e(
                logTag,
                message = { "wi_wb_auth_keys is missing from KeyStore" })
            return null
        }

        val spec = JwtSpec(
            type = WALLET_INSTANCE_POP_JWT_TYPE,
            algorithm = JWSAlgorithm.ES256,
            expireAfter = Duration.ofMinutes(JWT_EXPIRY_TIME_IN_MIN),
            requiredClaims = setOf(
                JWTClaimNames.ISSUED_AT,
                JWTClaimNames.EXPIRATION_TIME,
                ClaimNames.WB_AUTH_CHALLENGE,
            ),
        )

        val extraClaims = mapOf(
            ClaimNames.WB_AUTH_CHALLENGE to nonce,
        )

        val signingSpec =
            createSigningSpecFromKeystoreKey(
                spec,
                extraClaims = extraClaims,
                keystoreAlias = keystoreAlias,
                includeHeaderJwk = false,
            )

        val serializedJwt = signingSpec.serializedJwt
        log("$WALLET_INSTANCE_POP_JWT_TYPE for attestation", serializedJwt)
        return serializedJwt
    }

    /**
     * logs the jwt with header and claims
     */
    private fun log(type: String, serializedJwt: String, extra: Map<String, String> = emptyMap()) {
        val parsedJwt = jwtParser.parseJwt(serializedJwt)
        logController.d(
            logTag,
            message = {
                "$type  header: ${parsedJwt.header?.toJSONObject()}  " +
                        "claims: ${parsedJwt.jwtClaimsSet?.toJSONObject()} " + if (extra.isNotEmpty()) "extra: $extra" else ""
            })
    }

    private fun createSigningSpecFromKeystoreKey(
        spec: JwtSpec,
        extraClaims: Map<String, Any>,
        keystoreAlias: String,
        includeHeaderJwk: Boolean = true,
    ): JwtSigningSpec {
        val keyPair = ecKeyPairController.getEcKeyPair(keystoreAlias)
        val headerKey = if (includeHeaderJwk) keyPair.public as ECPublicKey else null
        return jwtSigner.sign(spec, extraClaims,keyPair.private, headerKey)
    }

}