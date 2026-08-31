package eu.europa.ec.authenticationlogic.model

import com.nimbusds.jwt.SignedJWT

/**
 * Combines the backend-provided Wallet Instance Attestation and the client-side key that was
 * used for the client-side wi_wia_pop. These together must be used for authentication towards
 * the issuer.
 * 
 * @param wbWiaJwt: Wallet backend provided WIA in serialized JWT format (serialized name: wb_wia)
 * @param wiWiaPopKeyPair: The key with which the wallet client had signed the request that
 *        resulted in the wallet backend sending us [wbWiaJwt] (serialized name: wi_wia_pop).
 */
data class WalletInstanceAttestationSpec(val wbWiaJwt: SignedJWT, val wiWiaPopKeyPair: JwtKeyPair) {
}