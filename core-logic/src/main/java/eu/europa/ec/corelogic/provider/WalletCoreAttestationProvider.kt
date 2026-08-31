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
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.securearea.RwscaKeyInfo
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.eudi.wallet.provider.WalletAttestationsProvider
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import org.multipaz.securearea.KeyInfo

interface WalletCoreAttestationProvider : WalletAttestationsProvider

class WalletCoreAttestationProviderImpl(
    private val walletCoreConfig: WalletCoreConfig,
    private val walletAttestationRepository: WalletAttestationRepository
) : WalletCoreAttestationProvider {

    override suspend fun getWalletAttestation(
        keyInfo: KeyInfo
    ): Result<String> = walletAttestationRepository.getWalletAttestation(
        // EUDI-changed
        // baseUrl = walletCoreConfig.walletProviderHost,
        baseUrl = EAA_WALLET_PROVIDER_HOST,
        keyInfo = keyInfo.publicKey.toJwk()
    )

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
