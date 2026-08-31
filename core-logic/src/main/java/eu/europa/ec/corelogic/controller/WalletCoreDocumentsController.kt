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

package eu.europa.ec.corelogic.controller


import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationGenerationResult
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController.Companion.PIDS_ALL_CONFIGURATION_IDS
import eu.europa.ec.corelogic.extension.getLocalizedDisplayName
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.corelogic.model.DeferredDocumentDataDomain
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.corelogic.model.ScopedDocumentDomain
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.MsoMdocCredential
import eu.europa.ec.eudi.openid4vci.SdJwtVcCredential
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DeferredDocument
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.issue.openid4vci.DeferredIssueResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.UserAuthenticationType
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState
import org.sprind.wallet.corelogic.securearea.RwscaSecureArea
import java.net.URLDecoder
import java.util.Locale
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.corelogic.securearea.RwscaCreateKeySettings
import io.opentelemetry.api.common.AttributeKey
import org.sprind.wallet.businesslogic.util.SpanAttributes
import kotlin.time.Duration.Companion.seconds

private const val REISSUE_DOCUMENT_LOG_TAG = "reissueDocument: "

enum class IssuanceMethod {
    OPENID4VCI
}

sealed class IssueDocumentPartialState {
    data class Success(val documentIds: List<String>) : IssueDocumentPartialState()
    data class DeferredSuccess(val deferredDocuments: Map<String, String>) :
        IssueDocumentPartialState()

    data class Failure(val errorMessage: String) : IssueDocumentPartialState()
    data class UserAuthRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult,
    ) : IssueDocumentPartialState()

    data object InProgress : IssueDocumentPartialState()
}

sealed class AttestationState {
    data class Success(val attestationSpec: WalletInstanceAttestationSpec) : AttestationState()
    data class Failure(val errorCode: ErrorCode, val traceId: String? = null) : AttestationState()
    enum class ErrorCode {
        WB_ACCOUNT_UNKNOWN,
        WB_AUTH_VERIFICATION_FAILED,
        PAR_FAILED,
        ATTESTATION_NOT_FOUND,
        WIA_NOT_FOUND,
        UNKNOWN
    }
}

sealed class IssueDocumentsPartialState {
    data class Success(val documentIds: List<DocumentId>) : IssueDocumentsPartialState()
    data class DeferredSuccess(val deferredDocuments: Map<DocumentId, FormatType>) :
        IssueDocumentsPartialState()

    data class PartialSuccess(
        val documentIds: List<DocumentId>,
        val nonIssuedDocuments: Map<String, String>,
    ) : IssueDocumentsPartialState()

    data class Failure(val errorMessage: String) : IssueDocumentsPartialState()
    data class UserAuthRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult,
    ) : IssueDocumentsPartialState()

    data class RefreshTokenReceived(val refreshToken: String) : IssueDocumentsPartialState()
}

sealed class DeleteDocumentPartialState {
    data object Success : DeleteDocumentPartialState()
    data class Failure(val errorMessage: String) : DeleteDocumentPartialState()
}

sealed class DeleteAllDocumentsPartialState {
    data object Success : DeleteAllDocumentsPartialState()
    data class Failure(val errorMessage: String) : DeleteAllDocumentsPartialState()
}

sealed class ResolveDocumentOfferPartialState {
    data class Success(val offer: Offer) : ResolveDocumentOfferPartialState()
    data class Failure(val errorMessage: String) : ResolveDocumentOfferPartialState()
}

sealed class FetchScopedDocumentsPartialState {
    data class Success(val documents: List<ScopedDocumentDomain>) :
        FetchScopedDocumentsPartialState()

    data class Failure(val errorMessage: String) : FetchScopedDocumentsPartialState()
}

sealed class ResolvePreferredPidConfigurationsPartialState {
    data class Success(val configurationIds: Set<CredentialConfigurationIdentifier>) :
        ResolvePreferredPidConfigurationsPartialState()

    /** The PID issuer advertised no configuration ID that the wallet recognizes. */
    data object NoPidConfigurationsAdvertised : ResolvePreferredPidConfigurationsPartialState()

    data class Failure(val errorMessage: String) : ResolvePreferredPidConfigurationsPartialState()
}

sealed class IssueDeferredDocumentPartialState {
    data class Issued(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : IssueDeferredDocumentPartialState()

    data class NotReady(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : IssueDeferredDocumentPartialState()

    data class Failed(
        val documentId: DocumentId,
        val errorMessage: String,
    ) : IssueDeferredDocumentPartialState()

    data class Expired(
        val documentId: DocumentId,
    ) : IssueDeferredDocumentPartialState()
}

/**
 * Controller for interacting with internal local storage of Core for CRUD operations on documents
 * */
interface WalletCoreDocumentsController {

    /**
     * @return All the documents from the Database.
     * */
    fun getAllDocuments(): List<Document>

    fun getAllIssuedDocuments(): List<IssuedDocument>

    fun getAllDocumentsByType(documentIdentifiers: List<DocumentIdentifier>): List<IssuedDocument>

    fun getDocumentById(documentId: DocumentId): Document?

    fun getMainPidDocument(): IssuedDocument?

    fun issueDocument(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String
    ): Flow<IssueDocumentPartialState>

    fun issueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configIds: List<String>,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ): Flow<IssueDocumentPartialState>

    fun issueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ): Flow<IssueDocumentPartialState>

    fun issueDocumentsByOfferUri(
        offerUri: String,
        txCode: String? = null,
    ): Flow<IssueDocumentsPartialState>

    /**
     * Caches a resolved [Offer] keyed by its offer URI, so that a subsequent
     * [issueDocumentsByOfferUri] call can reuse it without re-fetching the
     * (potentially single-use) offer endpoint.
     *
     * Call this from the issuance-feature interactor whenever an offer has
     * been successfully resolved for the UI, before the user taps "Add".
     */
    fun cacheOffer(offerUri: String, offer: Offer)

    /**
     * Clears a cached [Offer] for the given [offerUri], if any.
     *
     * Safe to call when no cache entry exists. Intended to be invoked after
     * terminal issuance states (success or failure) to avoid stale entries.
     */
    fun clearCachedOffer(offerUri: String)

    fun deleteDocument(
        documentId: String,
    ): Flow<DeleteDocumentPartialState>

    fun deleteAllDocuments(): Flow<DeleteAllDocumentsPartialState>

    fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferPartialState>

    fun issueDeferredDocument(docId: DocumentId): Flow<IssueDeferredDocumentPartialState>

    // EUDI-added
    fun reissueDocument(
        documentId: DocumentId
    ): Flow<ReissueDocumentPartialState>

    // END EUDI-added

    /**
     * Shared flow of issuance completion events that survives ViewModel lifecycle.
     *
     * Used by issuance screens to observe terminal states (Success/Failure/DeferredSuccess)
     * after resuming from a dynamic presentation, where the original per-ViewModel
     * [callbackFlow] channel may have been closed.
     */
    val issuanceState: SharedFlow<IssueDocumentsPartialState>

    fun resumeOpenId4VciWithAuthorization(uri: String)

    /**
     * Clears in-memory OpenID4VCI state for the PID issuer.
     *
     * Call this after terminal PID deletion, once the rWSCA account and local PID documents are
     * gone. PID deletion invalidates the backing account used by key-attested DPoP, so the next
     * PID issuance must create a fresh manager instead of reusing any cached issuer/signing state.
     */
    fun resetPidIssuanceState()

    suspend fun getScopedDocuments(locale: Locale): FetchScopedDocumentsPartialState

    /**
     * Resolves the preferred PID configuration IDs to issue, based on what the
     * PID issuer actually advertises. Preference order is defined by
     * [WalletCoreDocumentsController.PID_FORMAT_FAMILIES] (beta before stable).
     *
     * Returns the non-empty set of preferred configuration IDs, or
     * [ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised]
     * if the issuer advertises no recognized PID configuration at all.
     */
    suspend fun resolvePreferredPidConfigurations():
        ResolvePreferredPidConfigurationsPartialState

    companion object {
        // The configuration IDs that we expect PID Provider to use for mdoc/SD-JWT versions of
        // PID - we decided in WD-2263 to recognize PIDs from these rather than from their
        // respective doctype (mdoc) or VCT (SD-JWT). These values may change over time.
        // TODO: Consider turning these values into Feature Flags?
        val PID_CONFIGURATION_ID_MDOC = CredentialConfigurationIdentifier("pid-mso-mdoc")
        val PID_CONFIGURATION_ID_MDOC_2_BETA = CredentialConfigurationIdentifier("pid-mso-mdoc_2-beta")
        val PID_CONFIGURATION_ID_SD_JWT = CredentialConfigurationIdentifier("pid-sd-jwt")
        val PID_CONFIGURATION_ID_SD_JWT_2_BETA = CredentialConfigurationIdentifier("pid-sd-jwt_2-beta")

        /**
         * A PID format family (e.g. mso-mdoc, sd-jwt) with an ordered list of known
         * configuration IDs. Order = preference: the resolver picks the first one
         * the issuer actually advertises. To add a new beta release, prepend its
         * ID to [configurationIds] — no other code changes required.
         */
        internal data class PidFormatFamily(val configurationIds: List<CredentialConfigurationIdentifier>)

        internal val PID_MSO_MDOC_FAMILY = PidFormatFamily(
            listOf(
                PID_CONFIGURATION_ID_MDOC_2_BETA,   // prefer beta v2
                PID_CONFIGURATION_ID_MDOC,          // fall back to stable
            )
        )
        internal val PID_SD_JWT_FAMILY = PidFormatFamily(
            listOf(
                PID_CONFIGURATION_ID_SD_JWT_2_BETA,
                PID_CONFIGURATION_ID_SD_JWT,
            )
        )
        internal val PID_FORMAT_FAMILIES = listOf(PID_MSO_MDOC_FAMILY, PID_SD_JWT_FAMILY)

        /** All known PID configuration IDs (stable + beta). Used for *detection* only
         *  (e.g. isPid checks, scoped-documents listing), NOT for choosing what to issue. */
        val PIDS_ALL_CONFIGURATION_IDS: Set<CredentialConfigurationIdentifier> =
            PID_FORMAT_FAMILIES.flatMap { it.configurationIds }.toSet()

        /**
         * Picks, for each PID format family, the highest-preference configuration ID
         * that the issuer actually advertises in [advertised]. Returns the union of
         * the picks. Ordering inside [PidFormatFamily.configurationIds] = preference
         * order (newest/beta first). If the issuer advertises nothing for a family,
         * that family contributes nothing.
         */
        internal fun resolvePreferredPidConfigurationIds(
            advertised: Set<CredentialConfigurationIdentifier>,
        ): Set<CredentialConfigurationIdentifier> =
            PID_FORMAT_FAMILIES
                .mapNotNull { family -> family.configurationIds.firstOrNull { it in advertised } }
                .toSet()
    }
}

class WalletCoreDocumentsControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val eudiWallet: EudiWallet,
    private val walletCoreConfig: WalletCoreConfig,
    private val pidIssuerUrl: String,
    private val authorizationHandler: AusweisSdkAuthorizationHandler,
    private val hardwareKeyStorageController: HardwareKeyStorageController,
    private val appAttestationController: AppAttestationController,
    private val logController: LogController,
    private val ktorHttpClientFactory: (() -> HttpClient)?,
    private val telemetry: Telemetry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalletCoreDocumentsController {
    /**
     * Tracks the issuer currently waiting for an OpenID4VCI authorization callback.
     *
     * Why this is needed:
     * - The redirect URI callback does not include the issuer identifier.
     * - We may have multiple OpenId4VciManager instances (different issuers/handlers).
     * - At resume time we must route the callback to the same manager that started the flow.
     *
     * Lifecycle:
     * - Set right before starting an interactive authorization flow (by config or by offer).
     * - Read in [resumeOpenId4VciWithAuthorization].
     * - Cleared after resume attempt (success or failure) to avoid stale routing.
     *
     * `@Volatile` ensures visibility if write/read happen from different threads.
     */
    @Volatile
    private var pendingAuthorizationIssuerUrl: String? = null

    /**
     * In-memory cache of resolved [Offer]s keyed by offer URI.
     *
     * Why this exists: some issuers (e.g. the `eudiplo.eudi-wallet.org`
     * playground) treat credential-offer URIs as single-use — a second GET
     * to the same offer URI after the first successful resolution returns
     * HTTP 404. Without this cache, the "Add" / "Send" tap would re-resolve
     * the offer by URI and hit that 404, aborting issuance.
     *
     * Lifecycle:
     * - Populated by [cacheOffer], called from the issuance-feature
     *   interactor on successful offer resolution (screen open).
     * - Read by [issueDocumentsByOfferUri] before re-resolving.
     * - Cleared by [clearCachedOffer] after terminal issuance states.
     *
     * Single-entry: only one issuance flow is active at a time. A new
     * `cacheOffer` overwrites the previous entry.
     */
    @Volatile
    private var cachedOffer: Pair<String, Offer>? = null

    private val _issuanceState = MutableSharedFlow<IssueDocumentsPartialState>(replay = 1)
    override val issuanceState: SharedFlow<IssueDocumentsPartialState> = _issuanceState

    private val genericErrorMessage
        get() = resourceProvider.genericErrorMessage()

    private val documentErrorMessage
        get() = resourceProvider.getString(R.string.issuance_generic_error)

    private val openId4VciManagersDelegate = lazy {
        walletCoreConfig.vciConfig.associateTo(mutableMapOf()) { config ->
            config.issuerUrl to eudiWallet.createOpenId4VciManager(
                config = configForIssuer(
                    configs = walletCoreConfig.vciConfig,
                    httpsUrl = config.issuerUrl
                )
            )
        }
    }
    private val openId4VciManagers by openId4VciManagersDelegate

    override fun getAllDocuments(): List<Document> =
        eudiWallet.getDocuments { it is IssuedDocument || it is DeferredDocument }

    override fun getAllIssuedDocuments(): List<IssuedDocument> =
        eudiWallet.getDocuments().filterIsInstance<IssuedDocument>()

    override suspend fun getScopedDocuments(locale: Locale): FetchScopedDocumentsPartialState {
        return withContext(dispatcher) {
            runCatching {

                val metadata: Map<String, CredentialIssuerMetadata> =
                    openId4VciManagers.mapValues { (_, manager) ->
                        manager.getIssuerMetadata().getOrThrow()
                    }

                val documents: List<ScopedDocumentDomain> =
                    metadata.flatMap { (issuer, meta) ->
                        meta.credentialConfigurationsSupported.map { (id, config) ->

                            val name: String = config.credentialMetadata.getLocalizedDisplayName(
                                userLocale = locale,
                                fallback = id.value
                            )

                            val isPid = PIDS_ALL_CONFIGURATION_IDS.contains(id)

                            val formatType = when (config) {
                                is MsoMdocCredential -> config.docType
                                is SdJwtVcCredential -> config.type
                                else -> null
                            }

                            ScopedDocumentDomain(
                                name = name,
                                configurationId = id.value,
                                credentialIssuerId = issuer,
                                formatType = formatType,
                                isPid = isPid
                            )
                        }
                    }

                if (documents.isNotEmpty()) {
                    FetchScopedDocumentsPartialState.Success(documents = documents)
                } else {
                    FetchScopedDocumentsPartialState.Failure(errorMessage = genericErrorMessage)
                }
            }
        }.getOrElse {
            FetchScopedDocumentsPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }
    }

    override suspend fun resolvePreferredPidConfigurations():
        ResolvePreferredPidConfigurationsPartialState =
        withContext(dispatcher) {
            runCatching {
                val manager = getManagerForIssuer(pidIssuerUrl)
                val metadata = manager.getIssuerMetadata().getOrThrow()
                val advertised = metadata.credentialConfigurationsSupported.keys
                val preferred =
                    WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(advertised)
                logController.d("WalletCoreDocumentsController") {
                    "PID config preference: advertised=${advertised.sortedBy { it.value }}, " +
                        "selected=${preferred.sortedBy { it.value }}"
                }
                if (preferred.isEmpty()) {
                    ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised
                } else {
                    ResolvePreferredPidConfigurationsPartialState.Success(preferred)
                }
            }.getOrElse {
                ResolvePreferredPidConfigurationsPartialState.Failure(
                    it.localizedMessage ?: genericErrorMessage
                )
            }
        }

    override fun getAllDocumentsByType(documentIdentifiers: List<DocumentIdentifier>): List<IssuedDocument> =
        getAllDocuments()
            .filterIsInstance<IssuedDocument>()
            .filter {
                when (it.format) {
                    is MsoMdocFormat -> documentIdentifiers.any { id ->
                        id.formatType == (it.format as MsoMdocFormat).docType
                    }

                    is SdJwtVcFormat -> documentIdentifiers.any { id ->
                        id.formatType == (it.format as SdJwtVcFormat).vct
                    }
                }
            }

    override fun getDocumentById(documentId: DocumentId): Document? {
        return eudiWallet.getDocumentById(documentId = documentId)
    }

    override fun getMainPidDocument(): IssuedDocument? =
        getAllDocumentsByType(
            documentIdentifiers = listOf(
                DocumentIdentifier.MdocPid,
                DocumentIdentifier.SdJwtPid
            )
        ).minByOrNull { it.createdAt }

    override fun issueDocument(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String
    ): Flow<IssueDocumentPartialState> = flow {
        when (issuanceMethod) {
            IssuanceMethod.OPENID4VCI -> {
                issueDocumentWithOpenId4VCI(configId, issuerId).collect { response ->
                    when (response) {
                        is IssueDocumentsPartialState.Failure -> emit(
                            IssueDocumentPartialState.Failure(
                                errorMessage = documentErrorMessage
                            )
                        )

                        is IssueDocumentsPartialState.Success -> emit(
                            IssueDocumentPartialState.Success(
                                response.documentIds
                            )
                        )

                        is IssueDocumentsPartialState.UserAuthRequired -> emit(
                            IssueDocumentPartialState.UserAuthRequired(
                                crypto = response.crypto,
                                resultHandler = response.resultHandler
                            )
                        )

                        is IssueDocumentsPartialState.PartialSuccess -> emit(
                            IssueDocumentPartialState.Success(
                                response.documentIds
                            )
                        )

                        is IssueDocumentsPartialState.DeferredSuccess -> emit(
                            IssueDocumentPartialState.DeferredSuccess(
                                response.deferredDocuments
                            )
                        )

                        is IssueDocumentsPartialState.RefreshTokenReceived -> {
                            hardwareKeyStorageController.saveRefreshToken(response.refreshToken)
                        }
                    }
                }
            }
        }
    }.safeAsync {
        IssueDocumentPartialState.Failure(errorMessage = documentErrorMessage)
    }


    override fun issueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configIds: List<String>,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ): Flow<IssueDocumentPartialState> = flow {
        when (issuanceMethod) {
            IssuanceMethod.OPENID4VCI -> {
                issueDocumentWithOpenId4VCIAttested(
                    configIds = configIds,
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = walletInstanceAttestationSpec
                ).collect { response ->
                    when (response) {
                        is IssueDocumentsPartialState.Failure -> emit(
                            IssueDocumentPartialState.Failure(
                                errorMessage = documentErrorMessage
                            )
                        )

                        is IssueDocumentsPartialState.Success -> {
                            // It's impossible to have empty response.documentsIds in this case
                            require(response.documentIds.isNotEmpty())
                            emit(
                                IssueDocumentPartialState.Success(
                                    documentIds = response.documentIds
                                )
                            )
                        }

                        is IssueDocumentsPartialState.UserAuthRequired -> emit(
                            IssueDocumentPartialState.UserAuthRequired(
                                crypto = response.crypto,
                                resultHandler = response.resultHandler
                            )
                        )

                        // A partial success (issuance less format than required) is considered
                        // a failure.
                        is IssueDocumentsPartialState.PartialSuccess -> {
                            // This may contain docType value for MDOC, vct value for SD-JWT, or both.
                            // However, the possible values are not limited to these two.
                            val nonIssuedDocumentTypes = response.nonIssuedDocuments.keys
                            emit(
                                IssueDocumentPartialState.Failure(
                                    errorMessage = "Failed to issue ${
                                        nonIssuedDocumentTypes.joinToString(
                                            ", "
                                        )
                                    }"
                                )
                            )
                        }

                        is IssueDocumentsPartialState.DeferredSuccess -> emit(
                            IssueDocumentPartialState.DeferredSuccess(
                                response.deferredDocuments
                            )
                        )

                        is IssueDocumentsPartialState.RefreshTokenReceived -> {
                            hardwareKeyStorageController.saveRefreshToken(response.refreshToken)
                        }
                    }
                }
            }
        }
    }.safeAsync {
        IssueDocumentPartialState.Failure(errorMessage = documentErrorMessage)
    }

    override fun issueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ): Flow<IssueDocumentPartialState> =
        issueDocumentAttested(
            issuanceMethod = issuanceMethod,
            configIds = listOf(configId),
            issuerId = issuerId,
            walletInstanceAttestationSpec = walletInstanceAttestationSpec
        )

    override fun cacheOffer(offerUri: String, offer: Offer) {
        cachedOffer = offerUri to offer
    }

    override fun clearCachedOffer(offerUri: String) {
        val current = cachedOffer
        if (current != null && current.first == offerUri) {
            cachedOffer = null
        }
    }

    override fun issueDocumentsByOfferUri(
        offerUri: String,
        txCode: String?,
    ): Flow<IssueDocumentsPartialState> =
        callbackFlow {
            _issuanceState.resetReplayCache()
            // Fast path: reuse the offer cached at resolve time.
            val cached = cachedOffer?.takeIf { it.first == offerUri }?.second
            if (cached != null) {
                launchIssueByOffer(cached, txCode)
            } else {
                // Fallback: cache miss (e.g. process death wiped the
                // in-memory cache). Re-resolve the offer by URI, then
                // issue. This preserves robustness at the cost of a
                // potential second fetch on issuers that don't support it.
                resolveDocumentOffer(offerUri).collect {
                    when (it) {
                        is ResolveDocumentOfferPartialState.Failure -> {
                            trySendBlocking(
                                IssueDocumentsPartialState.Failure(
                                    errorMessage = it.errorMessage
                                )
                            )
                        }

                        is ResolveDocumentOfferPartialState.Success -> {
                            launchIssueByOffer(it.offer, txCode)
                        }
                    }
                }
            }
            awaitClose()
        }.safeAsync {
            IssueDocumentsPartialState.Failure(
                errorMessage = documentErrorMessage
            )
        }

    /**
     * Shared inner launcher for [issueDocumentsByOfferUri]: resolves the
     * issuer manager from the offer and kicks off the issuance.
     */
    private fun ProducerScope<IssueDocumentsPartialState>.launchIssueByOffer(
        offer: Offer,
        txCode: String?,
    ) {
        val issuerId = offer
            .credentialOffer
            .credentialIssuerIdentifier
            .toString()

        val manager = getManagerForIssuer(issuerId)
        pendingAuthorizationIssuerUrl = issuerId

        manager.issueDocumentByOffer(
            offer = offer,
            onIssueEvent = issuanceCallback(),
            txCode = txCode,
        )
    }

    override fun deleteDocument(documentId: String): Flow<DeleteDocumentPartialState> = flow {
        eudiWallet.deleteDocumentById(documentId = documentId)
            .kotlinResult
            .onSuccess {
                //revokedDocumentDao.delete(documentId)
                emit(DeleteDocumentPartialState.Success)
            }
            .onFailure {
                emit(
                    DeleteDocumentPartialState.Failure(
                        errorMessage = it.localizedMessage
                            ?: genericErrorMessage
                    )
                )
            }
    }.safeAsync {
        DeleteDocumentPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMessage
        )
    }

    override fun deleteAllDocuments(): Flow<DeleteAllDocumentsPartialState> =
        flow {

            val allDocuments = getAllDocuments()
            val mainPidDocument = getMainPidDocument()

            mainPidDocument?.let { safeMainPidDocument ->

                val restOfDocuments = allDocuments.filterNot { doc ->
                    doc.id == safeMainPidDocument.id
                }

                var restOfAllDocsDeleted = true
                var restOfAllDocsDeletedFailureReason = ""

                restOfDocuments.forEach { document ->

                    deleteDocument(
                        documentId = document.id
                    ).collect { deleteDocumentPartialState ->
                        when (deleteDocumentPartialState) {
                            is DeleteDocumentPartialState.Failure -> {
                                restOfAllDocsDeleted = false
                                restOfAllDocsDeletedFailureReason =
                                    deleteDocumentPartialState.errorMessage
                            }

                            is DeleteDocumentPartialState.Success -> {}
                        }
                    }
                }

                if (restOfAllDocsDeleted) {
                    deleteDocument(
                        documentId = safeMainPidDocument.id
                    ).collect { deleteMainPidDocumentPartialState ->
                        when (deleteMainPidDocumentPartialState) {
                            is DeleteDocumentPartialState.Failure -> emit(
                                DeleteAllDocumentsPartialState.Failure(
                                    errorMessage = deleteMainPidDocumentPartialState.errorMessage
                                )
                            )

                            is DeleteDocumentPartialState.Success -> {
                                hardwareKeyStorageController.removeWalletRegistrationIds()
                                emit(DeleteAllDocumentsPartialState.Success)
                            }
                        }
                    }
                } else {
                    emit(DeleteAllDocumentsPartialState.Failure(errorMessage = restOfAllDocsDeletedFailureReason))
                }
            } ?: emit(
                DeleteAllDocumentsPartialState.Failure(
                    errorMessage = genericErrorMessage
                )
            )
        }.safeAsync {
            DeleteAllDocumentsPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferPartialState> =
        callbackFlow {

            val issuerId = extractCredentialIssuerFromOfferUri(offerUri).getOrNull()

            val manager = issuerId?.let(::getManagerForIssuer)
                ?: openId4VciManagers.values.firstOrNull()

            require(manager != null) { genericErrorMessage }

            manager.resolveDocumentOffer(offerUri) { result ->
                when (result) {
                    is OfferResult.Failure -> {
                        trySendBlocking(
                            ResolveDocumentOfferPartialState.Failure(
                                result.cause.localizedMessage ?: genericErrorMessage
                            )
                        )
                    }

                    is OfferResult.Success -> {
                        trySendBlocking(
                            ResolveDocumentOfferPartialState.Success(
                                offer = result.offer
                            )
                        )
                    }
                }
            }
            awaitClose()
        }.safeAsync {
            ResolveDocumentOfferPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun issueDeferredDocument(docId: DocumentId): Flow<IssueDeferredDocumentPartialState> =
        callbackFlow {
            (getDocumentById(docId) as? DeferredDocument)?.let { deferredDoc ->

                val manager = deferredDoc.issuerMetadata?.credentialIssuerIdentifier
                    ?.let(::getManagerForIssuer)
                    ?: openId4VciManagers.values.firstOrNull()

                require(manager != null) { documentErrorMessage }

                manager.issueDeferredDocument(
                    deferredDocument = deferredDoc,
                    executor = null,
                    onIssueResult = { deferredIssuanceResult ->
                        when (deferredIssuanceResult) {
                            is DeferredIssueResult.DocumentFailed -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Failed(
                                        documentId = deferredIssuanceResult.documentId,
                                        errorMessage = deferredIssuanceResult.cause.localizedMessage
                                            ?: documentErrorMessage
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentIssued -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Issued(
                                        DeferredDocumentDataDomain(
                                            documentId = deferredIssuanceResult.documentId,
                                            formatType = deferredIssuanceResult.docType,
                                            docName = deferredIssuanceResult.name
                                        )
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentNotReady -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.NotReady(
                                        DeferredDocumentDataDomain(
                                            documentId = deferredIssuanceResult.documentId,
                                            formatType = deferredIssuanceResult.docType,
                                            docName = deferredIssuanceResult.name
                                        )
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentExpired -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Expired(
                                        documentId = deferredIssuanceResult.documentId
                                    )
                                )
                            }
                        }
                    }
                )
            } ?: trySendBlocking(
                IssueDeferredDocumentPartialState.Failed(
                    documentId = docId,
                    errorMessage = documentErrorMessage
                )
            )

            awaitClose()
        }.safeAsync {
            IssueDeferredDocumentPartialState.Failed(
                documentId = docId,
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    // EUDI-added
    override fun reissueDocument(
        documentId: DocumentId,
    ): Flow<ReissueDocumentPartialState> = callbackFlow {
        val document = getDocumentById(documentId) as? IssuedDocument
        if (document == null) {
            logController.d(REISSUE_DOCUMENT_LOG_TAG) { "Document not found for reissue: $documentId" }

            trySendBlocking(
                ReissueDocumentPartialState.Failure(
                    errorMessage = resourceProvider.getString(R.string.issuance_generic_error)
                )
            )
            close()
            return@callbackFlow
        }

        val issuerId = document.issuerMetadata?.credentialIssuerIdentifier
        val manager = issuerId?.let(::getManagerForIssuer)
            ?: openId4VciManagers.values.firstOrNull()

        if (manager == null) {
            logController.d(REISSUE_DOCUMENT_LOG_TAG) { "No OpenID4VCI manager available for reissue" }
            trySendBlocking(
                ReissueDocumentPartialState.Failure(
                    errorMessage = resourceProvider.getString(R.string.issuance_generic_error)
                )
            )
            close()
            return@callbackFlow
        }

        pendingAuthorizationIssuerUrl = issuerId ?: pendingAuthorizationIssuerUrl
        trySendBlocking(ReissueDocumentPartialState.InProgress)

        val onIssueEvent = OpenId4VciManager.OnIssueEvent { event ->
            when (event) {
                is IssueEvent.DocumentRequiresCreateSettings -> {
                    launch {
                        handleDocumentRequiresCreateSettings(event)
                    }
                }

                is IssueEvent.DocumentRequiresUserAuth -> {
                    launch {
                        handleDocumentRequiresUserAuth(event) { crypto, resultHandler ->
                            trySendBlocking(
                                ReissueDocumentPartialState.UserAuthRequired(
                                    crypto = crypto,
                                    resultHandler = resultHandler,
                                )
                            )
                        }
                    }
                }

                is IssueEvent.Failure -> {
                    trySendBlocking(
                        ReissueDocumentPartialState.Failure(
                            errorMessage = event.cause.message.toString()
                        )
                    )
                    close()
                }

                is IssueEvent.Finished -> {
                    trySendBlocking(ReissueDocumentPartialState.Success(event.issuedDocuments))
                    close()
                }

                is IssueEvent.Started,
                is IssueEvent.DocumentIssued,
                is IssueEvent.DocumentFailed,
                is IssueEvent.DocumentDeferred -> {
                    logController.d("IssueEvent:") { "$event" }
                }
            }
        }

        // Only the PID/RWSCA issuer needs a wallet instance attestation on re-issuance: its config
        // uses ClientAuthenticationType.None (credential proofs stay key-attested without WIA — see
        // WD-2143 / issue #358) yet its /token still requires OAuth client attestation. Other
        // documents authenticate through their configured client authentication, so we don't couple
        // them to the (PID-specific) attestation call and its failure mode.
        if (document.toDocumentIdentifier().isPid) {
            val attestationSpec =
                when (val result = appAttestationController.generateAttestation().first()) {
                    is WalletAttestationGenerationResult.Success -> result.walletInstanceAttestationSpec
                    is WalletAttestationGenerationResult.Failure -> {
                        logController.d(REISSUE_DOCUMENT_LOG_TAG) { "Wallet attestation failed: ${result.errorCode}" }
                        trySendBlocking(
                            ReissueDocumentPartialState.Failure(
                                errorMessage = resourceProvider.getString(R.string.issuance_generic_error)
                            )
                        )
                        close()
                        return@callbackFlow
                    }
                }

            manager.reissueDocumentAttested(
                documentId = documentId,
                walletAttestation = attestationSpec.wbWiaJwt,
                walletWiaPopPublicKey = attestationSpec.wiWiaPopKeyPair.public,
                walletWiaPopPrivateKey = attestationSpec.wiWiaPopKeyPair.private,
                onIssueEvent = onIssueEvent,
            )
        } else {
            manager.reissueDocument(
                documentId = documentId,
                onIssueEvent = onIssueEvent,
            )
        }

        awaitClose()
    }.safeAsync {
        ReissueDocumentPartialState.Failure(
            errorMessage = it.localizedMessage ?: documentErrorMessage
        )
    }
    // END EUDI-added

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        val issuerUrl = pendingAuthorizationIssuerUrl
        if (issuerUrl.isNullOrBlank()) {
            logController.e("resumeOpenId4Vci") { "No pending OpenID4VCI authorization. Ignoring resume uri: $uri" }
            recordResumeFailure("no_pending_authorization", uri)
            emitResumeFailure()
            return
        }

        try {
            getManagerForIssuer(issuerUrl).resumeWithAuthorization(uri)
        } catch (e: Exception) {
            logController.e("Failed to resume OpenID4VCI authorization for issuer: $issuerUrl", e)
            recordResumeFailure("resume_threw", uri, e)
            emitResumeFailure()
        } finally {
            pendingAuthorizationIssuerUrl = null
        }
    }

    private fun emitResumeFailure() {
        val state = IssueDocumentsPartialState.Failure(
            errorMessage = resourceProvider.getString(R.string.issuance_interrupted_error)
        )
        _issuanceState.tryEmit(state)
    }

    private fun recordResumeFailure(reason: String, uri: String, throwable: Throwable? = null) {
        val span = telemetry.startSpan(
            spanName = TelemetryConstants.ISSUANCE,
            initialAttributes = SpanAttributes.of(
                "issuance.resume.reason" to reason,
                "issuance.resume.uri" to uri.take(256).takeWhile { it != '?' },
            ),
        )
        throwable?.let { span.span.recordException(it) }
        span.span.setAttribute(AttributeKey.stringKey("issuance.resume.status"), "failed")
        span.close()
        telemetry.logEvent(
            "issuance.resume.failed",
            SpanAttributes.of(
                "reason" to reason,
            )
        )
    }

    override fun resetPidIssuanceState() {
        if (pendingAuthorizationIssuerUrl == pidIssuerUrl) {
            pendingAuthorizationIssuerUrl = null
        }

        if (openId4VciManagersDelegate.isInitialized()) {
            openId4VciManagers.remove(pidIssuerUrl)
        }
    }

    private fun issueDocumentWithOpenId4VCI(
        configId: String,
        issuerId: String
    ): Flow<IssueDocumentsPartialState> =
        callbackFlow {
            _issuanceState.resetReplayCache()

            val manager = getManagerForIssuer(issuerId)
            pendingAuthorizationIssuerUrl = issuerId

            manager.issueDocumentByConfigurationIdentifier(
                credentialConfigurationId = configId,
                onIssueEvent = issuanceCallback()
            )

            awaitClose()

        }.safeAsync {
            IssueDocumentsPartialState.Failure(
                errorMessage = documentErrorMessage
            )
        }

    private fun issueDocumentWithOpenId4VCIAttested(
        configIds: List<String>,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ): Flow<IssueDocumentsPartialState> =
        callbackFlow {
            _issuanceState.resetReplayCache()

            val manager = getManagerForIssuer(issuerId)
            pendingAuthorizationIssuerUrl = issuerId

            manager.issueDocumentByConfigurationIdentifiersAttested(
                credentialConfigurationIds = configIds,
                walletAttestation = walletInstanceAttestationSpec.wbWiaJwt,
                walletWiaPopPublicKey = walletInstanceAttestationSpec.wiWiaPopKeyPair.public,
                walletWiaPopPrivateKey = walletInstanceAttestationSpec.wiWiaPopKeyPair.private,
                onIssueEvent = issuanceCallback()
            )

            awaitClose()
        }.safeAsync {
            IssueDocumentsPartialState.Failure(
                errorMessage = documentErrorMessage
            )
        }

    private fun ProducerScope<IssueDocumentsPartialState>.issuanceCallback(): OpenId4VciManager.OnIssueEvent {

        var totalDocumentsToBeIssued = 0
        val nonIssuedDocuments: MutableMap<FormatType, String> = mutableMapOf()
        val deferredDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()
        val issuedDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()

        val listener = OpenId4VciManager.OnIssueEvent { event ->
            when (event) {
                is IssueEvent.DocumentFailed -> {
                    nonIssuedDocuments[event.docType] = event.name
                }

                is IssueEvent.DocumentRequiresCreateSettings -> {
                    launch {
                        // BEGIN EUDI-changed
                        handleDocumentRequiresCreateSettings(event)
                        // END EUDI-changed
                    }
                }

                is IssueEvent.DocumentRequiresUserAuth -> {
                    launch {
                        // BEGIN EUDI-changed
                        handleDocumentRequiresUserAuth(event) { crypto, resultHandler ->
                            trySendBlocking(
                                IssueDocumentsPartialState.UserAuthRequired(
                                    crypto = crypto,
                                    resultHandler = resultHandler,
                                )
                            )
                        }
                        // END EUDI-changed
                    }
                }

                is IssueEvent.Failure -> {
                    val state = IssueDocumentsPartialState.Failure(
                        errorMessage = documentErrorMessage
                    )
                    trySendBlocking(state)
                    _issuanceState.tryEmit(state)
                }

                is IssueEvent.Finished -> {

                    if (deferredDocuments.isNotEmpty()) {
                        val state = IssueDocumentsPartialState.DeferredSuccess(deferredDocuments)
                        trySendBlocking(state)
                        _issuanceState.tryEmit(state)
                        return@OnIssueEvent
                    }

                    if (event.issuedDocuments.isEmpty()) {
                        val state = IssueDocumentsPartialState.Failure(
                            errorMessage = documentErrorMessage
                        )
                        trySendBlocking(state)
                        _issuanceState.tryEmit(state)
                        return@OnIssueEvent
                    }

                    if (event.issuedDocuments.size == totalDocumentsToBeIssued) {
                        val state = IssueDocumentsPartialState.Success(
                            documentIds = event.issuedDocuments
                        )
                        trySendBlocking(state)
                        _issuanceState.tryEmit(state)
                        return@OnIssueEvent
                    }

                    val state = IssueDocumentsPartialState.PartialSuccess(
                        documentIds = event.issuedDocuments,
                        nonIssuedDocuments = nonIssuedDocuments
                    )
                    trySendBlocking(state)
                    _issuanceState.tryEmit(state)
                }

                is IssueEvent.DocumentIssued -> {
                    issuedDocuments[event.documentId] = event.docType
                }

                is IssueEvent.Started -> {
                    totalDocumentsToBeIssued = event.total
                }

                is IssueEvent.DocumentDeferred -> {
                    deferredDocuments[event.documentId] = event.docType
                }

            }
        }

        return listener
    }

    private suspend fun handleDocumentRequiresCreateSettings(
        event: IssueEvent.DocumentRequiresCreateSettings,
    ) {
        val configurationIdentifier = event.offeredDocument.configurationIdentifier
        val createDocumentSettings =
            if (PIDS_ALL_CONFIGURATION_IDS.contains(configurationIdentifier)) {
                val ppCNonce = fetchIssuerCNonce(
                    event.offeredDocument.offer.credentialOffer.credentialIssuerMetadata
                )
                // For PID, use the Rwsc*SecureArea, generate a batch of one-time-use credentials.
                CreateDocumentSettings.invoke(
                    secureAreaIdentifier = RwscaSecureArea.IDENTIFIER,
                    numberOfCredentials = 5, // Batch size of 5 per format [WD-2142]
                    credentialPolicy = CreateDocumentSettings.CredentialPolicy.OneTimeUse,
                    createKeySettings = RwscaCreateKeySettings(ppCNonce = ppCNonce),
                )
            } else {
                // This is a policy in progress, current verifier/issuer of EAAs do not have a policy yet,
                // currently, the assumption is that batchCredentialIssuanceSize > 1 we do OneTimeUse, so
                // credentials are deleted after an operation is done and we can test features like refresh token
                val credentialPolicy =
                    if (event.offeredDocument.batchCredentialIssuanceSize > 1) {
                        CreateDocumentSettings.CredentialPolicy.OneTimeUse
                    } else {
                        CreateDocumentSettings.CredentialPolicy.RotateUse
                    }

                eudiWallet.getDefaultCreateDocumentSettings(
                    offeredDocument = event.offeredDocument,
                    numberOfCredentials = event.offeredDocument.batchCredentialIssuanceSize,
                    credentialPolicy = credentialPolicy,
                    configure = { applyWalletDocumentKeyConfig() },
                )
            }

        logController.d("CreateDocumentSettings: $createDocumentSettings") {
            "SecureAreaIdentifier: ${createDocumentSettings.secureAreaIdentifier}"
        }

        val walletSecureArea =
            eudiWallet.secureAreaRepository.getImplementation(createDocumentSettings.secureAreaIdentifier)
        requireNotNull(walletSecureArea)

        event.resume(createDocumentSettings)
    }

    private suspend fun fetchIssuerCNonce(issuerMetadata: CredentialIssuerMetadata): String {
        val nonceEndpoint = issuerMetadata.nonceEndpoint
            ?: error("PID issuance requires a nonce endpoint from issuer metadata")

        val client = ktorHttpClientFactory?.invoke()
            ?: error("PID issuance requires ktorHttpClientFactory to fetch c_nonce")

        val response = client.post(nonceEndpoint.toString())
        val cNonce = JSONObject(response.bodyAsText()).optString("c_nonce")
        check(cNonce.isNotBlank()) {
            "Issuer nonce endpoint did not return a valid c_nonce"
        }
        return cNonce
    }

    private suspend fun handleDocumentRequiresUserAuth(
        event: IssueEvent.DocumentRequiresUserAuth,
        emitUserAuthRequired: (BiometricCrypto, DeviceAuthenticationResult) -> Unit,
    ) {
        val keyUnlockDataMap =
            event.keysRequireAuth.mapValues { (keyAlias, secureArea) ->
                getDefaultKeyUnlockData(secureArea, keyAlias)
            }

        val keyUnlockData =
            keyUnlockDataMap.values.first() // TODO: Revisit this once Core adds support.
        val cryptoObject = keyUnlockData?.getCryptoObjectForSigning()

        emitUserAuthRequired(
            BiometricCrypto(cryptoObject),
            DeviceAuthenticationResult(
                onAuthenticationSuccess = { event.resume(keyUnlockDataMap) },
                onAuthenticationError = { event.cancel(null) }
            )
        )
    }

    private fun AndroidKeystoreCreateKeySettings.Builder.applyWalletDocumentKeyConfig() {
        // Biometrics are only used for EAA, thus we enable them iff we should enable
        // them for EAA
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        if (!BuildConfig.ENABLE_EAA_BIOMETRICS && BuildConfig.FLAVOR == BuildConfig.FLAVOR_NAME_DEV)
            return

        setUseStrongBox(eudiWallet.config.useStrongBoxForKeys)
        setUserAuthenticationRequired(
            required = true,
            timeout = 30.seconds,
            userAuthenticationTypes = setOf(
                UserAuthenticationType.LSKF,
                UserAuthenticationType.BIOMETRIC
            )
        )
    }

    /**
     * Extracts the credential issuer identifier from the `credential_offer` query parameter.
     */
    private fun extractCredentialIssuerFromOfferUri(offerUri: String): Result<String> =
        runCatching {
            val credentialOffer = offerUri.toUri().getQueryParameter("credential_offer")
            val decoded = URLDecoder.decode(credentialOffer, "UTF-8")
            val json = JSONObject(decoded)
            json.getString("credential_issuer")
        }

    /**
     * Returns the cached [OpenId4VciManager] for [httpsUrl], or creates and caches one on demand.
     *
     * The manager configuration is always derived through [configForIssuer] so issuer-specific
     * overrides stay in one place.
     */
    private fun getManagerForIssuer(httpsUrl: String): OpenId4VciManager {
        openId4VciManagers[httpsUrl]?.let { return it }

        val config = configForIssuer(walletCoreConfig.vciConfig, httpsUrl)

        return openId4VciManagers.getOrPut(httpsUrl) {
            eudiWallet.createOpenId4VciManager(
                config = config,
                ktorHttpClientFactory = ktorHttpClientFactory,
            )
        }
    }

    /**
     * Resolves the [OpenId4VciManager.Config] to use for [httpsUrl].
     *
     * It first looks for an exact issuer match. If none exists, it falls back to a configuration
     * without client authentication and adapts it to the requested issuer. PID issuer keep the
     * [authorizationHandler], while non-PID issuers clear it.
     *
     * @throws IllegalArgumentException when no exact or fallback issuer configuration is available.
     */
    private fun configForIssuer(
        configs: List<OpenId4VciManager.Config>,
        httpsUrl: String
    ): OpenId4VciManager.Config {
        val issuerConfig = configs.firstOrNull { it.issuerUrl == httpsUrl }
            ?: configs
                .firstOrNull { it.clientAuthenticationType is OpenId4VciManager.ClientAuthenticationType.None }
                ?.copy(
                    issuerUrl = httpsUrl,
                    authFlowRedirectionURI = BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK,
                    parUsage = OpenId4VciManager.Config.ParUsage.IF_SUPPORTED,
                )
            ?: throw IllegalArgumentException(
                "No OpenID4VCI configuration found for issuer '$httpsUrl' and no fallback issuer without client authentication is configured."
            )

        return if (issuerConfig.issuerUrl == pidIssuerUrl) {
            issuerConfig.copy(authorizationHandler = authorizationHandler)
        } else {
            // Non-PID issuers must not use the PID's KeyAttested DPoP config — that flow
            // swaps the provisional key for a nonce-bound attested key after the token
            // endpoint, which breaks the standard browser flow (the PAR and token
            // requests must use the same DPoP key). Fall back to the default Android
            // Keystore DPoP config which uses a single key for the entire flow.
            issuerConfig.copy(
                authorizationHandler = null,
                dpopConfig = walletCoreConfig.defaultDPopConfig,
            )
        }
    }
}
