/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.eudi.wallet.registration.relyingparty.WrpRegistrationPolicy
import eu.europa.ec.eudi.iso18013.transfer.readerauth.RevocationPolicy
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.EncryptionAlgorithm
import eu.europa.ec.eudi.wallet.transfer.openId4vp.EncryptionMethod
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.corelogic.securearea.RwscaCreateKeySettings
import kotlinx.io.bytestring.ByteString
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds

internal class WalletCoreConfigImpl(
    private val context: Context,
    // EUDI-added hardware key to sign the pop with the same key we used when issued  for the first time
    // private val hardwareKeyStorageController: HardwareKeyStorageController,
    private val configLogic: ConfigLogic,
    private val ausweisSdkAuthorizationHandler: AuthorizationHandler,
    private val rwscSecureArea: SecureArea,
    private val androidKeystoreSecureArea: SecureArea,
    private val storage: Storage,
) : WalletCoreConfig {

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = false,
                        userAuthenticationTimeout = DOCUMENT_USER_AUTHENTICATION_TIMEOUT,
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withEncryptionAlgorithms(listOf(EncryptionAlgorithm.ECDH_ES))
                        withEncryptionMethods(
                            listOf(
                                EncryptionMethod.A128CBC_HS256,
                                EncryptionMethod.A256GCM
                            )
                        )

                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.X509Hash)
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME,
                                BuildConfig.HAIP_OPENID4VP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc.ES256, Format.SdJwtVc.ES256
                        )
                    }

                    configureOpenId4Vci {
                        withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(BuildConfig.VCI_ISSUER_CLIENT_ID))
                        withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                        withParUsage(OpenId4VciManager.Config.ParUsage.REQUIRED)
                        withDPopConfig(keyAttestedDPopConfig())
                        withIssuanceMetadataStorage(storage)
                        // EUDI added
                        withAuthorizationHandler(ausweisSdkAuthorizationHandler)
                    }

                    // The revocation policy is passed explicitly because core-lib 0.30 flipped
                    // the default from RevocationPolicy.NoCheck to RevocationPolicy.HardFail,
                    // which fails reader authentication whenever a CRL cannot be retrieved -
                    // several of the development CAs below publish none.
                    configureReaderTrustStore(
                        context,
                        R.raw.pidissuerca02_cz,
                        R.raw.pidissuerca02_ee,
                        R.raw.pidissuerca02_eu,
                        R.raw.pidissuerca02_lu,
                        R.raw.pidissuerca02_nl,
                        R.raw.pidissuerca02_pt,
                        R.raw.pidissuerca02_ut,
                        // EUDI-added
                        R.raw.dc4eu,
                        R.raw.german_pg_cert,
                        R.raw.wrpac_ca,
                        R.raw.wrprc_ca,
                        revocationPolicy = RevocationPolicy.NoCheck,
                    )
                    // core-lib 0.30 introduced relying party registration certificates and
                    // defaults the policy to Enabled. With it enabled, the OpenID4VP library
                    // rejects *every* request from an `x509_hash` verifier that carries no
                    // registration certificate, before the wallet's own (non-blocking) evaluation
                    // runs - see RegistrationCertificatePolicyEvaluator, which fails such a
                    // request with MissingRequiredRegistrationCertificate. The openid4vp version
                    // core-lib 0.28.1 used to had no registration certificate concept at all, so
                    // leaving the new default in place would reject verifiers the wallet accepted
                    // before the upgrade. Disabled restores that behaviour. Turning the mechanism
                    // on needs the ETSI Trusted Lists (`configureEtsiTrust`, `wrprcProviders`).
                    configureWrpRegistrationPolicy(WrpRegistrationPolicy.Disabled)
                }
            }
            return _config!!
        }

    override val vciConfig: Map<String, OpenId4VciManager.Config>
        get() = mapOf(
            configLogic.environmentConfig.pidIssuerURL to OpenId4VciManager.Config.Builder()
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(BuildConfig.VCI_ISSUER_CLIENT_ID))
                .withAuthFlowRedirectionURI(DEFAULT_AUTH_FLOW_REDIRECTION_URI)
                .withParUsage(OpenId4VciManager.Config.ParUsage.REQUIRED)
                .withDPopConfig(keyAttestedDPopConfig())
                .withIssuanceMetadataStorage(storage)
                .withAuthorizationHandler(authorizationHandler = ausweisSdkAuthorizationHandler)
                .build(),
            EUDIW_ISSUER_URL to OpenId4VciManager.Config.Builder()
                // ClientAuthenticationType.AttestationBased now requires an explicit client id;
                // until v0.29.0 openid4vci derived it from the wallet attestation's `sub`. Per
                // OAuth attestation-based client authentication the request `client_id` must equal
                // that `sub`, so the value passed here is not what authenticates the authorization
                // request: the core fork derives that from the attestation itself, on both the
                // normal and the out-of-band attested path ("derive `client_id` from the
                // attestation subject" in wiki/forks.md - the rebased stack rewrites hashes, so
                // the wiki is the stable reference). What is left of this value is offer
                // resolution: OfferResolver needs some client id to resolve issuer metadata and
                // credential offers before an attestation is fetched.
                .withClientAuthenticationType(
                    OpenId4VciManager.ClientAuthenticationType.AttestationBased(
                        BuildConfig.VCI_ISSUER_CLIENT_ID
                    )
                )
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                .withDPopConfig(defaultDPopConfig)
                .withIssuanceMetadataStorage(storage)
                .build(),
        )

    override val storageToBeUsed: Storage get() = storage

    override val defaultDPopConfig: DPopConfig.Custom = DPopConfig.Custom(
        secureArea = androidKeystoreSecureArea,
        createKeySettingsBuilder = { algorithms ->
            val algorithm = algorithms.firstOrNull { it.isSigning }
                ?: throw IllegalStateException("No suitable signing algorithm found for DPoP")
            val challenge = ByteArray(16).also { SecureRandom().nextBytes(it) }
            AndroidKeystoreCreateKeySettings.Builder(ByteString(challenge))
                .setAlgorithm(algorithm)
                .setUseStrongBox(AndroidKeystoreSecureArea.Capabilities().strongBoxSupported)
                .build()
        },
    )

    private fun keyAttestedDPopConfig() = DPopConfig.KeyAttested(
        secureArea = rwscSecureArea,
        attestedCreateKeySettingsBuilder = { algorithms, dpopNonce ->
            val algorithm = algorithms.firstOrNull { it.isSigning }
                ?: throw IllegalStateException("No suitable signing algorithm found for DPoP")
            // PID issuance uses the token endpoint DPoP nonce to bind the RWSCA WTE to the refresh-token key.
            RwscaCreateKeySettings(
                ppCNonce = dpopNonce.value,
                algorithm = algorithm,
            )
        },
        provisionalConfig = defaultDPopConfig,
    )

    companion object {
        const val DEFAULT_AUTH_FLOW_REDIRECTION_URI = "https://uri.example.com"

        const val EUDIW_ISSUER_URL = "https://issuer.eudiw.dev"

        val DOCUMENT_USER_AUTHENTICATION_TIMEOUT = 5.seconds

    }
}
