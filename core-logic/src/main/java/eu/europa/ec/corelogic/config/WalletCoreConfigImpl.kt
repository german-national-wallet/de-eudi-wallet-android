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
                        withIssuerUrl(issuerUrl = configLogic.environmentConfig.pidIssuerURL)
                        withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(BuildConfig.VCI_ISSUER_CLIENT_ID))
                        withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                        withParUsage(OpenId4VciManager.Config.ParUsage.REQUIRED)
                        withDPopConfig(keyAttestedDPopConfig())
                        withIssuanceMetadataStorage(storage)
                        // EUDI added
                        withAuthorizationHandler(ausweisSdkAuthorizationHandler)
                    }

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
                    )
                }
            }
            return _config!!
        }

    override val vciConfig: List<OpenId4VciManager.Config>
        get() = listOf(
            OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = configLogic.environmentConfig.pidIssuerURL)
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(BuildConfig.VCI_ISSUER_CLIENT_ID))
                .withAuthFlowRedirectionURI(DEFAULT_AUTH_FLOW_REDIRECTION_URI)
                .withParUsage(OpenId4VciManager.Config.ParUsage.REQUIRED)
                .withDPopConfig(keyAttestedDPopConfig())
                .withIssuanceMetadataStorage(storage)
                .withAuthorizationHandler(authorizationHandler = ausweisSdkAuthorizationHandler)
                .build(),
            OpenId4VciManager.Config.Builder()
                .withIssuerUrl(EUDIW_ISSUER_URL)
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
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
