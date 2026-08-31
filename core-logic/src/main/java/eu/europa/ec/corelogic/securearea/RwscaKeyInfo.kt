package eu.europa.ec.corelogic.securearea

import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopKeyAttestation
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo

class RwscaKeyInfo(
    alias: String,
    publicKey: EcPublicKey,
    attestation: KeyAttestation,
    algorithm: Algorithm,
    val walletTrustEvidence: String? = null,
    val walletTrustEvidenceNonce: String? = null,
    // EUDI verified with the Architect and iOS team , it is not needed
    //keyPurposes: Set<>
) : KeyInfo(
    alias = alias,
    algorithm = algorithm,
    publicKey= publicKey,
    attestation = attestation
), DPopKeyAttestation {

    override val dpopKeyAttestation: String?
        get() = walletTrustEvidence
}
