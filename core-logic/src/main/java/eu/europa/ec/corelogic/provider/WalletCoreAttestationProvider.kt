/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.provider

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationGenerationResult
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.securearea.RwscaKeyInfo
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.eudi.wallet.provider.WalletAttestationsProvider
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import kotlinx.coroutines.flow.first
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.toEcPublicKey
import org.multipaz.securearea.AndroidKeystoreKeyInfo
import org.multipaz.securearea.KeyInfo
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import org.sprind.wallet.businesslogic.extensions.runSuspendCatching

interface WalletCoreAttestationProvider : WalletAttestationsProvider

class WalletCoreAttestationProviderImpl(
    private val walletCoreConfig: WalletCoreConfig,
    private val walletAttestationRepository: WalletAttestationRepository,
    private val appAttestationController: AppAttestationController,
    private val ecKeyPairController: EcKeyPairController,
) : WalletCoreAttestationProvider {

    // EUDI-changed: the WIA comes from our wallet backend instead of the EU reference wallet
    // provider. wallet-core creates the WIA PoP key through SecureAreaWalletKeyManager over
    // AndroidKeystoreSecureArea, which stores it in Android KeyStore under keyInfo.alias. That
    // lets the double-signed backend request be signed with the same key wallet-core later
    // signs the PoP with. This relies on multipaz using keyInfo.alias as the KeyStore alias
    // (checked against multipaz 0.99.0).
    // Passing the alias also keeps EAA issuance off KEYSTORE_ALIAS_WALLET_INSTANCE_ATTESTATION_KEYS,
    // which the no-alias path deletes and regenerates, so it cannot replace the PID WIA key.
    override suspend fun getWalletAttestation(
        keyInfo: KeyInfo
    ): Result<String> {
        if (keyInfo !is AndroidKeystoreKeyInfo) {
            return Result.failure(
                IllegalStateException("WIA PoP key must be an Android KeyStore key, got ${keyInfo::class.simpleName}")
            )
        }
        // The backend request and its PoP are signed as ES256 by JwtBuilder. Check the key's own
        // curve: algorithm.curve is null for the COSE identifiers such as ES256.
        if (keyInfo.publicKey.curve != EcCurve.P256) {
            return Result.failure(
                IllegalStateException("WIA PoP key must be a P-256 key, got ${keyInfo.publicKey.curve}")
            )
        }
        // JwtBuilder signs the backend request with this key without opening an authentication
        // window, so a key that needs one would only fail after the challenge round trip.
        if (keyInfo.isUserAuthenticationRequired) {
            return Result.failure(
                IllegalStateException("WIA PoP key must not require user authentication")
            )
        }
        return runSuspendCatching {
            // Fail here rather than at the issuer, after the backend round trip, if the KeyStore
            // entry under the alias is not the key wallet-core signs the PoP with. Compare the
            // curve points, not the encodings, which depend on the security provider.
            val keystorePublicKey = ecKeyPairController
                .getEcKeyPair(keystoreAlias = keyInfo.alias)
                .public
                .toEcPublicKey(curve = EcCurve.P256)
            check(keystorePublicKey == keyInfo.publicKey) {
                "Android KeyStore key under the WIA PoP key alias does not match the wallet-core key"
            }
            val wiaResult = appAttestationController
                .generateAttestation(popKeystoreAlias = keyInfo.alias)
                .first()
            when (wiaResult) {
                is WalletAttestationGenerationResult.Success ->
                    wiaResult.walletInstanceAttestationSpec.wbWiaJwt.serialize()

                is WalletAttestationGenerationResult.Failure ->
                    error("Wallet backend WIA generation failed: ${wiaResult.errorCode} (traceId=${wiaResult.traceId})")
            }
        }
    }

    override suspend fun getKeyAttestation(
        keys: List<KeyInfo>,
        nonce: Nonce?
    ): Result<String> {
        // For rWSCA keys the `nonce` parameter (the credential-proof callback c_nonce
        // handed back at credential-request time) is intentionally not consulted. The WTE
        // was bound during Create Key to the earlier create-key c_nonce, so validation runs
        // against that stored nonce (see validateNonce) rather than this later one. Do not
        // wire `nonce` into the rWSCA branch without re-checking that binding.
        val rwscaKeys = keys.filterIsInstance<RwscaKeyInfo>()
        if (rwscaKeys.isNotEmpty()) {
            if (rwscaKeys.size != keys.size) {
                return Result.failure(
                    IllegalStateException("Cannot create key attestations for mixed rWSCA and non-rWSCA keys: rWSCA=${rwscaKeys.size}, non-rWSCA=${keys.size - rwscaKeys.size}")
                )
            }
            // Distinguish a missing WTE (all keys null) from differing WTEs so the
            // failure diagnostic reflects the real cause. Both fail closed.
            val distinctWte = rwscaKeys.map { it.walletTrustEvidence }.distinct()
            if (distinctWte.size > 1) {
                return Result.failure(
                    IllegalStateException("rWSCA keys do not share a single WTE")
                )
            }
            val walletTrustEvidence = distinctWte.singleOrNull()
                ?: return Result.failure(
                    IllegalStateException("rWSCA keys are missing the WTE")
                )

            // A differing stored nonce is treated like a differing WTE rather than
            // silently skipping validation. A single shared null nonce is allowed and
            // means no create-key nonce metadata was available to validate against.
            val distinctNonce = rwscaKeys.map { it.walletTrustEvidenceNonce }.distinct()
            if (distinctNonce.size > 1) {
                return Result.failure(
                    IllegalStateException("rWSCA keys do not share a single WTE nonce: $distinctNonce ")
                )
            }
            val walletTrustEvidenceNonce = distinctNonce.single()
            // PID issuance architecture step 061 sends the rWSCA WTE obtained during
            // Create Key as the credential request attestation proof.
            return validateNonce(
                walletTrustEvidence,
                walletTrustEvidenceNonce
            ).map { walletTrustEvidence }
        }

        return walletAttestationRepository.getKeyAttestation(
            // EUDI-changed
            // baseUrl = walletCoreConfig.walletProviderHost,
            baseUrl = EAA_WALLET_PROVIDER_HOST,
            keys = keys.map { it.publicKey.toJwk() },
            nonce = nonce?.value
        )
    }

    private fun validateNonce(walletTrustEvidence: String, nonce: String?): Result<Unit> =
        runCatching {
            val requestedNonce = nonce ?: return@runCatching
            val wteSignedJwt = SignedJWT.parse(walletTrustEvidence)
            val attestedNonce = wteSignedJwt.jwtClaimsSet.getStringClaim(WTE_NONCE_CLAIM_KEY_NAME)
            check(attestedNonce == requestedNonce) {
                "rWSCA WTE nonce does not match create-key c_nonce : $attestedNonce != $requestedNonce"
            }
        }
}

// EUDI-added
private const val EAA_WALLET_PROVIDER_HOST = "https://wallet-provider.eudiw.dev";

// The rWSCA WTE (a "key-attestation+jwt") encodes the Issuer-provided create-key
// c_nonce under the "nonce" claim. This couples the app to that WTE JWT shape: if the
// c_nonce ever moves to a different claim, getStringClaim returns null and PID issuance
// fails closed (see validateNonce). Spec — Remote WSCA §5.6 Wallet Trust Evidence:
// https://bmi.usercontent.opencode.de/eudi-wallet/wallet-development-documentation-public/latest/architecture-concept/05-remote-wsca/01-remote-wsca.html#56-wallet-trust-evidence
private const val WTE_NONCE_CLAIM_KEY_NAME = "nonce"
