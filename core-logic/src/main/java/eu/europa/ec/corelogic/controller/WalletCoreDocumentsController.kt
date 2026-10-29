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


import androidx.annotation.StringRes
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
import eu.europa.ec.corelogic.model.ScopedDocumentDomain
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.openid4vci.AttestationBasedClientAuthenticationSpec
import eu.europa.ec.eudi.openid4vci.CIAuthorizationServerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.MsoMdocCredential
import eu.europa.ec.eudi.openid4vci.SdJwtVcCredential
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DeferredDocument
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateKeySettings
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
import eu.europa.ec.eudi.wallet.issue.openid4vci.reissue.ReissuanceAuthorizationException
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificateResult
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.multipaz.securearea.UserAuthenticationType
import org.sprind.wallet.corelogic.activity.ActivityLogRepository
import org.sprind.wallet.corelogic.activity.ActivityResult
import org.sprind.wallet.corelogic.activity.ActivityType
import org.sprind.wallet.corelogic.activity.issuer
import org.sprind.wallet.corelogic.activity.newActivityLogEntry
import org.sprind.wallet.corelogic.activity.toActivityCredential
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.businesslogic.util.SpanAttributes
import org.sprind.wallet.corelogic.config.PidConfigurationOverridesProvider
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState
import org.sprind.wallet.corelogic.issuance.isGrantRejection
import org.sprind.wallet.corelogic.issuance.isIssuerMetadataVerificationFailure
import org.sprind.wallet.corelogic.securearea.RwscaCreateKeySettings
import org.sprind.wallet.corelogic.securearea.RwscaSecureArea
import java.net.URLDecoder
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val REISSUE_DOCUMENT_LOG_TAG = "reissueDocument: "
private const val CREDENTIAL_CONFIG_ID_LOG_TAG = "CredentialConfigId"
private const val ISSUANCE_EVENT_LOG_TAG = "IssuanceEvent"

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

    data class Failure(
        val errorMessage: String,
        /** The token endpoint rejected the grant, i.e. the transaction code was wrong. */
        val isGrantRejection: Boolean = false,
        /** The issuer's signed metadata could not be verified; [errorMessage] says so. */
        val isIssuerNotVerified: Boolean = false,
    ) : IssueDocumentsPartialState()
    data class UserAuthRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult,
    ) : IssueDocumentsPartialState()

    data class RefreshTokenReceived(val refreshToken: String) : IssueDocumentsPartialState()

    /**
     * The authorization-code flow completed browser authorization and is
     * paused just before credential creation/submission, waiting for
     * [WalletCoreDocumentsController.continueIssuanceAfterConsent]. Never
     * emitted for pre-authorized offers.
     */
    data object AwaitingConsent : IssueDocumentsPartialState()
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

    /**
     * Deletes every stored document unconditionally, PID present or not, and succeeds on an
     * already-empty wallet. Used by the wallet self-lock, where [deleteAllDocuments]'s
     * requirement of a main PID does not hold (a revoked wallet may hold only EAAs, or nothing).
     * Clears the wallet registration ids once all documents are gone, like [deleteAllDocuments].
     */
    fun wipeAllDocuments(): Flow<DeleteAllDocumentsPartialState>

    fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferPartialState>

    fun issueDeferredDocument(
        document: DeferredDocument
    ): Flow<IssueDeferredDocumentPartialState>

    // EUDI-added
    fun reissueDocument(
        document: IssuedDocument
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
     * Resumes an authorization-code-flow issuance that is paused at
     * [IssueDocumentsPartialState.AwaitingConsent], after the user has
     * confirmed consent.
     *
     * No-op if no issuance is currently awaiting consent.
     */
    suspend fun continueIssuanceAfterConsent()

    /**
     * Clears in-memory OpenID4VCI state for the PID issuer.
     *
     * Call this after terminal PID deletion, once the rWSCA account and local PID documents are
     * gone. PID deletion invalidates the backing account used by key-attested DPoP, so the next
     * PID issuance must create a fresh manager instead of reusing any cached issuer/signing state.
     */
    fun resetPidIssuanceState()

    suspend fun getScopedDocuments(locale: Locale): FetchScopedDocumentsPartialState

    suspend fun resolvePreferredPidConfigurations():
        ResolvePreferredPidConfigurationsPartialState

    companion object {
        // The configuration IDs that we expect PID Provider to use for mdoc/SD-JWT versions of
        // PID - we decided in WD-2263 to recognize PIDs from these rather than from their
        // respective doctype (mdoc) or VCT (SD-JWT). These values may change over time.
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

        internal fun resolvePreferredPidConfigurationIds(
            advertised: Set<CredentialConfigurationIdentifier>,
            mdocOverride: CredentialConfigurationIdentifier? = null,
            sdJwtOverride: CredentialConfigurationIdentifier? = null,
        ): Set<CredentialConfigurationIdentifier> =
            listOf(PID_MSO_MDOC_FAMILY to mdocOverride, PID_SD_JWT_FAMILY to sdJwtOverride)
                .mapNotNull { (family, override) ->
                    override ?: family.configurationIds.firstOrNull { it in advertised }
                }
                .toSet()

        internal fun isPidOffer(
            configurationId: CredentialConfigurationIdentifier,
            formatType: FormatType?,
            issuerId: String,
            knownPidConfigurationIds: Set<CredentialConfigurationIdentifier>,
            pidIssuerUrl: String,
        ): Boolean =
            configurationId in knownPidConfigurationIds ||
                (formatType?.toDocumentIdentifier()?.isPid == true &&
                    issuerId.trimEnd('/') == pidIssuerUrl.trimEnd('/'))
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
    private val pidConfigurationOverridesProvider: PidConfigurationOverridesProvider,
    private val activityLog: ActivityLogRepository,
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

    /**
     * Holds the "resume" side of a held [IssueEvent.DocumentRequiresCreateSettings]
     * for the authorization-code flow's consent gate.
     *
     * For an offer whose grant requires browser authorization (as opposed to
     * pre-authorized issuance), the app starts issuance immediately on offer
     * resolve so the browser opens without waiting for a consent tap. Once
     * the browser returns, [issuanceCallback] intercepts the *first*
     * [IssueEvent.DocumentRequiresCreateSettings] instead of auto-resuming
     * it, stashing the resume action here and emitting
     * [IssueDocumentsPartialState.AwaitingConsent] so the UI can show consent
     * only now, after authorization. [continueIssuanceAfterConsent] invokes
     * it once the user confirms.
     *
     * Single-entry: only one issuance flow is active at a time.
     */
    @Volatile
    private var pendingConsentResume: (suspend () -> Unit)? = null

    private val _issuanceState = MutableSharedFlow<IssueDocumentsPartialState>(replay = 1)
    override val issuanceState: SharedFlow<IssueDocumentsPartialState> = _issuanceState

    /**
     * Scope for handling [IssueEvent]s that need suspending work.
     *
     * Deliberately not the issuance [callbackFlow]'s [ProducerScope]: in the
     * authorization-code flow the UI stops collecting that flow at the consent
     * gate ([IssueDocumentsPartialState.AwaitingConsent]), which cancels the
     * producer scope. Events that arrive after consent (e.g.
     * [IssueEvent.DocumentRequiresUserAuth]) would then be launched into a
     * cancelled scope and silently dropped, leaving the issuance suspended.
     *
     * Because a [SupervisorJob] does not propagate child failures, handlers must be
     * started via [launchIssuanceEventHandler], which turns exceptions into an
     * [IssueDocumentsPartialState.Failure] (as the producer scope's `safeAsync` did
     * before). The [CoroutineExceptionHandler] is only a backstop so that an
     * unexpected failure is logged instead of crashing the app.
     */
    private val issuanceEventScope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, throwable ->
            logController.e(ISSUANCE_EVENT_LOG_TAG, throwable)
        }
    )

    private val genericErrorMessage
        get() = resourceProvider.genericErrorMessage()

    private val documentErrorMessage
        get() = resourceProvider.getString(R.string.issuance_generic_error)

    private fun Throwable.issuanceErrorMessage(fallback: String): String =
        if (isIssuerMetadataVerificationFailure()) {
            resourceProvider.getString(R.string.issuance_issuer_not_verified_error)
        } else {
            fallback
        }

    private fun Throwable.toIssueDocumentsFailure(fallback: String) = IssueDocumentsPartialState.Failure(
        errorMessage = issuanceErrorMessage(fallback),
        isGrantRejection = isGrantRejection(),
        isIssuerNotVerified = isIssuerMetadataVerificationFailure(),
    )

    private fun IssueDocumentsPartialState.Failure.toIssueDocumentFailure() = IssueDocumentPartialState.Failure(
        errorMessage = if (isIssuerNotVerified) errorMessage else documentErrorMessage
    )

    private val openId4VciManagersDelegate = lazy {
        walletCoreConfig.vciConfig.keys.associateWithTo(mutableMapOf()) { issuerUrl ->
            eudiWallet.createOpenId4VciManager(
                config = configForIssuer(
                    configs = walletCoreConfig.vciConfig,
                    httpsUrl = issuerUrl
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

                val knownPidConfigurationIds = knownPidConfigurationIds()
                val metadata: Map<String, CredentialIssuerMetadata> =
                    openId4VciManagers.mapValues { (issuerUrl, manager) ->
                        manager.getIssuerMetadata(issuerUrl).getOrThrow()
                    }

                val documents: List<ScopedDocumentDomain> =
                    metadata.flatMap { (issuer, meta) ->
                        meta.credentialConfigurationsSupported.map { (id, config) ->

                            val name: String = config.credentialMetadata.getLocalizedDisplayName(
                                userLocale = locale,
                                fallback = id.value
                            )

                            val isPid = id in knownPidConfigurationIds

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
                val (mdocOverride, sdJwtOverride) = pidConfigurationOverrides()
                // Both formats pinned by FeatureHub: nothing to pick from the issuer metadata.
                val advertised = if (mdocOverride != null && sdJwtOverride != null) {
                    emptySet()
                } else {
                    getManagerForIssuer(pidIssuerUrl)
                        .getIssuerMetadata(pidIssuerUrl)
                        .getOrThrow()
                        .credentialConfigurationsSupported
                        .keys
                }
                val preferred = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(
                    advertised = advertised,
                    mdocOverride = mdocOverride,
                    sdJwtOverride = sdJwtOverride,
                )
                logController.d(CREDENTIAL_CONFIG_ID_LOG_TAG) {
                    "PID config preference: overrides=[$mdocOverride, $sdJwtOverride], " +
                        "advertised=${advertised.sortedBy { it.value }}, " +
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

    private fun pidConfigurationOverrides(): Pair<CredentialConfigurationIdentifier?, CredentialConfigurationIdentifier?> {
        val overrides = pidConfigurationOverridesProvider.overrides()
        return overrides.mdoc?.let(::CredentialConfigurationIdentifier) to
            overrides.sdJwt?.let(::CredentialConfigurationIdentifier)
    }

    private fun knownPidConfigurationIds(): Set<CredentialConfigurationIdentifier> =
        PIDS_ALL_CONFIGURATION_IDS + pidConfigurationOverrides().toList().filterNotNull()

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
                        is IssueDocumentsPartialState.Failure -> emit(response.toIssueDocumentFailure())

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

                        // Only emitted for the authorization-code flow's consent gate,
                        // which this call site never arms (requiresConsentGate = false).
                        is IssueDocumentsPartialState.AwaitingConsent -> Unit
                    }
                }
            }
        }
    }.safeAsync {
        IssueDocumentPartialState.Failure(errorMessage = it.issuanceErrorMessage(documentErrorMessage))
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
                        is IssueDocumentsPartialState.Failure -> emit(response.toIssueDocumentFailure())

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

                        // Only emitted for the authorization-code flow's consent gate,
                        // which this call site never arms (requiresConsentGate = false).
                        is IssueDocumentsPartialState.AwaitingConsent -> Unit
                    }
                }
            }
        }
    }.safeAsync {
        IssueDocumentPartialState.Failure(errorMessage = it.issuanceErrorMessage(documentErrorMessage))
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
            it.toIssueDocumentsFailure(documentErrorMessage)
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

        val manager = getManagerForIssuer(
            httpsUrl = issuerId,
            authorizationServerMetadata = offer.credentialOffer.authorizationServerMetadata,
        )
        pendingAuthorizationIssuerUrl = issuerId

        // Offers without a pre-authorized-code grant require the browser-based
        // authorization-code flow. For those, the consent gate withholds the
        // first DocumentRequiresCreateSettings resume until the user confirms
        // consent (which is now shown only after authorization completes).
        val requiresConsentGate = offer.credentialOffer.grants?.preAuthorizedCode() == null

        manager.issueDocumentByOffer(
            offer = offer,
            onIssueEvent = issuanceCallback(requiresConsentGate),
            txCode = txCode,
        )
    }

    override fun deleteDocument(documentId: String): Flow<DeleteDocumentPartialState> =
        deleteDocument(documentId = documentId, recordActivity = true)

    private fun deleteDocument(
        documentId: String,
        recordActivity: Boolean,
    ): Flow<DeleteDocumentPartialState> = flow {
        val document = eudiWallet.getDocumentById(documentId)
        eudiWallet.deleteDocumentById(documentId = documentId)
            .kotlinResult
            .onSuccess {
                //revokedDocumentDao.delete(documentId)
                if (recordActivity && document != null) recordDeletion(document)
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

    override fun wipeAllDocuments(): Flow<DeleteAllDocumentsPartialState> =
        flow {
            val allDocuments = getAllDocuments()
            val mainPidId = getMainPidDocument()?.id

            // Non-PID documents first, the PID last, so an interrupted wipe cannot leave EAAs
            // behind without the PID they were issued against.
            val orderedIds = allDocuments.map { it.id }.sortedBy { it == mainPidId }

            var failureReason: String? = null
            orderedIds.forEach { documentId ->
                // No activity: the self-lock clears the activity log too.
                deleteDocument(documentId = documentId, recordActivity = false).collect { state ->
                    if (state is DeleteDocumentPartialState.Failure) {
                        failureReason = state.errorMessage
                    }
                }
            }

            failureReason?.let {
                emit(DeleteAllDocumentsPartialState.Failure(errorMessage = it))
            } ?: run {
                hardwareKeyStorageController.removeWalletRegistrationIds()
                emit(DeleteAllDocumentsPartialState.Success)
            }
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
                                result.cause.issuanceErrorMessage(
                                    result.cause.localizedMessage ?: genericErrorMessage
                                )
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
                errorMessage = it.issuanceErrorMessage(it.localizedMessage ?: genericErrorMessage)
            )
        }

    override fun issueDeferredDocument(
        document: DeferredDocument
    ): Flow<IssueDeferredDocumentPartialState> =
        callbackFlow {
            val manager = document.issuerMetadata?.credentialIssuerIdentifier
                ?.let(::getManagerForIssuer)
                ?: openId4VciManagers.values.firstOrNull()

            require(manager != null) { documentErrorMessage }

            manager.issueDeferredDocument(
                deferredDocument = document,
                    executor = null,
                    onIssueResult = { deferredIssuanceResult ->
                        when (deferredIssuanceResult) {
                            is DeferredIssueResult.DocumentFailed -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Failed(
                                        documentId = deferredIssuanceResult.documentId,
                                        errorMessage = deferredIssuanceResult.cause.issuanceErrorMessage(
                                            deferredIssuanceResult.cause.localizedMessage ?: documentErrorMessage
                                        )
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentIssued -> {
                                recordIssuance(listOf(deferredIssuanceResult.documentId))
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
            awaitClose()
        }.safeAsync {
            IssueDeferredDocumentPartialState.Failed(
                documentId = document.id,
                errorMessage = it.issuanceErrorMessage(it.localizedMessage ?: genericErrorMessage)
            )
        }

    // EUDI-added
    private fun ProducerScope<ReissueDocumentPartialState>.sendReissueError(
        @StringRes userErrorMessageId: Int,
        logErrorMessage: () -> String
    ) {
        logController.d(REISSUE_DOCUMENT_LOG_TAG, logErrorMessage)
        trySendBlocking(ReissueDocumentPartialState.Failure(
            errorMessage = resourceProvider.getString(userErrorMessageId))
        )
        close()
    }

    // Handles OpenId4VciManager events during re-issuance
    private fun ProducerScope<ReissueDocumentPartialState>.onIssueEvent(
        documentId: DocumentId,
    ): OpenId4VciManager.OnIssueEvent = { event ->
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
                val cause = event.cause
                val errorMessage = cause.message.toString()
                logController.d(REISSUE_DOCUMENT_LOG_TAG) {
                    if (cause is ReissuanceAuthorizationException) {
                        "Re-issuance of $documentId needs user authorization: $errorMessage"
                    } else {
                        "Re-issuance of $documentId failed: $errorMessage"
                    }
                }
                trySendBlocking(
                    ReissueDocumentPartialState.Failure(errorMessage = errorMessage)
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

    override fun reissueDocument(
        document: IssuedDocument,
    ): Flow<ReissueDocumentPartialState> = callbackFlow {
        val issuerId = document.issuerMetadata?.credentialIssuerIdentifier
        val manager = issuerId?.let(::getManagerForIssuer)
            ?: openId4VciManagers.values.firstOrNull()

        if (manager == null) {
            sendReissueError(R.string.issuance_generic_error) {
                "No OpenID4VCI manager available for reissue"
            }
            return@callbackFlow
        }

        // NOTE: This must NOT set pendingAuthorizationIssuerUrl. Re-issuance uses the
        // non-interactive refresh-token grant (allowAuthorizationFallback = false in both
        // the PID-attested and the default paths below), so no authorization callback is
        // ever expected. Claiming routing here would clobber the pending state of an
        // issuance that is genuinely waiting for its authorization deep link (seen as
        // issuance.resume.failed in the WD-3753 e2e run).
        trySendBlocking(ReissueDocumentPartialState.InProgress)

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
                        sendReissueError(R.string.issuance_generic_error) {
                            "Wallet attestation failed: ${result.errorCode}"
                        }
                        return@callbackFlow
                    }
                }

            // allowAuthorizationFallback = false: the default would start an interactive eID flow
            // on a dead refresh token, and AusweisSdkAuthorizationHandler.authorize() then suspends
            // forever because only ReadCardViewModel drives it. A failure is reported instead.
            manager.reissueDocumentAttested(
                documentId = document.id,
                walletAttestation = attestationSpec.wbWiaJwt,
                walletWiaPopPublicKey = attestationSpec.wiWiaPopKeyPair.public,
                walletWiaPopPrivateKey = attestationSpec.wiWiaPopKeyPair.private,
                allowAuthorizationFallback = false,
                onIssueEvent = onIssueEvent(document.id),
            )
        } else {
            manager.reissueDocument(
                documentId = document.id,
                allowAuthorizationFallback = false,
                onIssueEvent = onIssueEvent(document.id),
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

        // Diagnostic only: the callback does carry the issuer in the `iss` query parameter, so a
        // mismatch means pending state pointed somewhere else (interleaved flows, stale routing).
        // We still route to the pending manager unchanged — this exists to surface such bugs
        // immediately in logs rather than as a distant downstream failure.
        // Null-safe: toUri() can yield null under unit tests (isReturnDefaultValues makes
        // Uri.parse return null instead of throwing); the diagnostic must never break the resume.
        val callbackIss = try {
            uri.toUri().getQueryParameter("iss")
        } catch (_: Exception) {
            null
        }
        callbackIss?.let {
            if (issuerUrl != it) {
                logController.w("resumeOpenId4Vci") {
                    "Resume uri issuer mismatch: pending=$issuerUrl, uri=$it"
                }
            }
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

    override suspend fun continueIssuanceAfterConsent() {
        // Clears the stale AwaitingConsent value replayed by _issuanceState so
        // a caller awaiting the next (terminal) state doesn't immediately
        // re-match it instead of waiting for the real outcome.
        _issuanceState.resetReplayCache()
        val resume = pendingConsentResume
        pendingConsentResume = null
        resume?.invoke()
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

            manager.issueDocumentByConfigurationIdentifiers(
                issuerUrl = issuerId,
                credentialConfigurationIds = listOf(configId),
                onIssueEvent = issuanceCallback()
            )

            awaitClose()
        }.safeAsync {
            it.toIssueDocumentsFailure(documentErrorMessage)
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

            logController.d(CREDENTIAL_CONFIG_ID_LOG_TAG) {
                "Issuing configIds=$configIds from issuer=$issuerId"
            }
            manager.issueDocumentByConfigurationIdentifiersAttested(
                issuerUrl = issuerId,
                credentialConfigurationIds = configIds,
                walletAttestation = walletInstanceAttestationSpec.wbWiaJwt,
                walletWiaPopPublicKey = walletInstanceAttestationSpec.wiWiaPopKeyPair.public,
                walletWiaPopPrivateKey = walletInstanceAttestationSpec.wiWiaPopKeyPair.private,
                onIssueEvent = issuanceCallback()
            )

            awaitClose()
        }.safeAsync {
            it.toIssueDocumentsFailure(documentErrorMessage)
        }

    private fun ProducerScope<IssueDocumentsPartialState>.issuanceCallback(
        requiresConsentGate: Boolean = false,
    ): OpenId4VciManager.OnIssueEvent {

        var totalDocumentsToBeIssued = 0
        val nonIssuedDocuments: MutableMap<FormatType, String> = mutableMapOf()
        val deferredDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()
        val issuedDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()
        var consentGatePending = requiresConsentGate
        var issuerRegistration: RegistrationCertificateResult? = null

        val listener = OpenId4VciManager.OnIssueEvent { event ->
            when (event) {
                is IssueEvent.DocumentFailed -> {
                    nonIssuedDocuments[event.docType] = event.name
                }

                is IssueEvent.DocumentRequiresCreateSettings -> {
                    if (consentGatePending) {
                        // Authorization just completed. Withhold the resume until the
                        // user confirms consent instead of auto-resuming immediately;
                        // only the first document's event gates — subsequent ones (for
                        // multi-document offers) auto-resume normally below.
                        consentGatePending = false
                        pendingConsentResume = suspend {
                            issuerRegistration = event.offeredDocument.offer.issuerRegistration
                            // BEGIN EUDI-changed
                            handleDocumentRequiresCreateSettings(event)
                            // END EUDI-changed
                        }
                        val state = IssueDocumentsPartialState.AwaitingConsent
                        trySendBlocking(state)
                        _issuanceState.tryEmit(state)
                    } else {
                        // See issuanceEventScope: the producer scope may already be
                        // cancelled for the remaining documents of a consent-gated offer.
                        launchIssuanceEventHandler(cancelIssuance = { event.cancelIssuance() }) {
                            issuerRegistration = event.offeredDocument.offer.issuerRegistration
                            // BEGIN EUDI-changed
                            handleDocumentRequiresCreateSettings(event)
                            // END EUDI-changed
                        }
                    }
                }

                is IssueEvent.DocumentRequiresUserAuth -> {
                    // See issuanceEventScope: after the consent gate the producer scope is
                    // cancelled, so a plain launch {} here would never run.
                    launchIssuanceEventHandler(cancelIssuance = { event.cancel(null) }) {
                        // BEGIN EUDI-changed
                        handleDocumentRequiresUserAuth(event) { crypto, resultHandler ->
                            val state = IssueDocumentsPartialState.UserAuthRequired(
                                crypto = crypto,
                                resultHandler = resultHandler,
                            )
                            trySendBlocking(state)
                            // Also publish on the persistent state: the authorization-code
                            // flow awaits the post-consent outcome on issuanceState, not on
                            // this (by then closed) callbackFlow channel.
                            _issuanceState.tryEmit(state)
                        }
                        // END EUDI-changed
                    }
                }

                is IssueEvent.Failure -> {
                    val state = event.cause.toIssueDocumentsFailure(documentErrorMessage)
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

                    recordIssuance(event.issuedDocuments, issuerRegistration)

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

    /**
     * Runs [block] on [issuanceEventScope]. A failure is reported as an
     * [IssueDocumentsPartialState.Failure] on both this flow and [issuanceState]
     * (the authorization-code flow awaits the outcome there after consent), and
     * wallet-core is unblocked via [cancelIssuance] instead of staying suspended.
     */
    private fun ProducerScope<IssueDocumentsPartialState>.launchIssuanceEventHandler(
        cancelIssuance: () -> Unit,
        block: suspend () -> Unit,
    ) {
        issuanceEventScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logController.e(ISSUANCE_EVENT_LOG_TAG, e)
                val state = e.toIssueDocumentsFailure(documentErrorMessage)
                trySendBlocking(state)
                _issuanceState.tryEmit(state)
                runCatching(cancelIssuance).onFailure {
                    logController.e(ISSUANCE_EVENT_LOG_TAG, it)
                }
            }
        }
    }

    private fun IssueEvent.DocumentRequiresCreateSettings.cancelIssuance() = when (this) {
        is IssueEvent.DocumentRequiresCreateSettings.MandatoryReusePolicy -> cancel(null)
        is IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy -> cancel(null)
    }

    /** Keeps the links of the issuance entry, the issuer's registration is not stored with the credential. */
    private suspend fun recordDeletion(document: Document) {
        // The PID is one credential in two formats, so only deleting its last format is logged.
        if (document.toDocumentIdentifier().isPid && eudiWallet.getDocuments().any { it.toDocumentIdentifier().isPid }) return
        val issuance = activityLog.entries.first().firstOrNull { entry ->
            entry.type == ActivityType.ISSUANCE && entry.credentials.any { it.documentId == document.id }
        }
        activityLog.record(
            newActivityLogEntry(
                type = ActivityType.DELETION,
                result = ActivityResult.COMPLETED,
                interactingParty = issuance?.interactingParty ?: issuer(listOf(document)),
                credentials = listOf(document.toActivityCredential(withHeldClaims = true)),
            )
        )
    }

    private fun recordIssuance(
        documentIds: List<DocumentId>,
        registration: RegistrationCertificateResult? = null,
    ) {
        val documents = documentIds.mapNotNull { eudiWallet.getDocumentById(it) }
        if (documents.isEmpty()) return
        activityLog.record(
            newActivityLogEntry(
                type = ActivityType.ISSUANCE,
                result = ActivityResult.COMPLETED,
                interactingParty = issuer(documents, registration),
                credentials = documents.map { it.toActivityCredential(withHeldClaims = true) },
            )
        )
    }

    private suspend fun handleDocumentRequiresCreateSettings(
        event: IssueEvent.DocumentRequiresCreateSettings,
    ) {
        val offeredDocument = event.offeredDocument
        val formatType = when (val format = offeredDocument.documentFormat) {
            is MsoMdocFormat -> format.docType
            is SdJwtVcFormat -> format.vct
            null -> null
        }
        val issuerId = offeredDocument.offer.credentialOffer.credentialIssuerIdentifier.toString()
        val isPid = WalletCoreDocumentsController.isPidOffer(
            configurationId = offeredDocument.configurationIdentifier,
            formatType = formatType,
            issuerId = issuerId,
            knownPidConfigurationIds = knownPidConfigurationIds(),
            pidIssuerUrl = pidIssuerUrl,
        )
        logController.d(CREDENTIAL_CONFIG_ID_LOG_TAG) {
            "Creating keys for configId=${offeredDocument.configurationIdentifier}, " +
                "formatType=$formatType, issuer=$issuerId, isPid=$isPid"
        }

        // The key material settings are chosen the same way whoever decides the credential
        // policy: PID keys live in the Rwsc*SecureArea, everything else uses the wallet's
        // default Android Keystore settings.
        val (secureAreaIdentifier, createKeySettings) = if (isPid) {
            val ppCNonce = fetchIssuerCNonce(
                event.offeredDocument.offer.credentialOffer.credentialIssuerMetadata
            )
            RwscaSecureArea.IDENTIFIER to RwscaCreateKeySettings(ppCNonce = ppCNonce)
        } else {
            eudiWallet.getDefaultCreateKeySettings(
                configure = { applyWalletDocumentKeyConfig() },
            )
        }

        val walletSecureArea =
            eudiWallet.secureAreaRepository.getImplementation(secureAreaIdentifier)
        requireNotNull(walletSecureArea)

        when (event) {
            // The issuer advertises a mandatory credential reuse policy;
            // wallet-core derives the credential policy from it and we only supply the keys.
            is IssueEvent.DocumentRequiresCreateSettings.MandatoryReusePolicy -> {
                logController.d("CreateDocumentSettings: mandatory reuse policy") {
                    "SecureAreaIdentifier: $secureAreaIdentifier, " +
                        "resolvedReusePolicy: ${event.resolvedReusePolicy}"
                }
                event.resume(secureAreaIdentifier, createKeySettings)
            }

            // No issuer reuse policy, so we keep choosing the policy ourselves.
            is IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy -> {
                val credentialPolicy = when {
                    // For PID, generate a batch of one-time-use credentials.
                    // Batch size of 5 per format [WD-2142]
                    isPid -> CreateDocumentSettings.CredentialPolicy.OnceOnly(
                        numberOfCredentials = 5
                    )

                    // This is a policy in progress, current verifier/issuer of EAAs do not have a
                    // policy yet, currently, the assumption is that batchCredentialIssuanceSize > 1
                    // we do OnceOnly, so credentials are deleted after an operation is done and we
                    // can test features like refresh token
                    event.offeredDocument.batchCredentialIssuanceSize > 1 ->
                        CreateDocumentSettings.CredentialPolicy.OnceOnly(
                            numberOfCredentials = event.offeredDocument.batchCredentialIssuanceSize
                        )

                    else -> CreateDocumentSettings.CredentialPolicy.RotatingBatch()
                }

                val createDocumentSettings = CreateDocumentSettings(
                    secureAreaIdentifier = secureAreaIdentifier,
                    createKeySettings = createKeySettings,
                    credentialPolicy = credentialPolicy,
                )

                logController.d("CreateDocumentSettings: $createDocumentSettings") {
                    "SecureAreaIdentifier: ${createDocumentSettings.secureAreaIdentifier}"
                }

                event.resume(createDocumentSettings)
            }
        }
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
     * Resolves the [OpenId4VciManager] for [httpsUrl], using the authorization
     * server metadata from the credential offer to decide whether Wallet
     * Instance Attestation (WIA) client authentication is required.
     *
     * When the AS advertises `attest_jwt_client_auth` in
     * `token_endpoint_auth_methods_supported`, an `AttestationBased`
     * configuration is used; otherwise a `None` configuration is used.
     *
     * The manager configuration is always derived through [configForIssuer] so
     * issuer-specific overrides stay in one place.
     */
    private fun getManagerForIssuer(
        httpsUrl: String,
        authorizationServerMetadata: CIAuthorizationServerMetadata? = null,
    ): OpenId4VciManager {
        openId4VciManagers[httpsUrl]?.let { return it }

        val config = configForIssuer(
            configs = walletCoreConfig.vciConfig,
            httpsUrl = httpsUrl,
            authorizationServerMetadata = authorizationServerMetadata,
        )

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
     * It first looks for an exact issuer match. If none exists, it falls back
     * to a configuration selected by [fallbackConfig] — which inspects the
     * authorization server metadata (when available) to decide whether Wallet
     * Instance Attestation client authentication is required. PID issuers keep
     * the [authorizationHandler], while non-PID issuers clear it.
     *
     * @throws IllegalArgumentException when no exact or fallback issuer
     *   configuration is available.
     */
    private fun configForIssuer(
        configs: Map<String, OpenId4VciManager.Config>,
        httpsUrl: String,
        authorizationServerMetadata: CIAuthorizationServerMetadata? = null,
    ): OpenId4VciManager.Config {
        val issuerConfig = configs[httpsUrl]
            ?: fallbackConfig(configs, httpsUrl, authorizationServerMetadata)

        return if (httpsUrl == pidIssuerUrl) {
            issuerConfig.copy(authorizationHandler = authorizationHandler)
        } else {
            // Non-PID issuers must not use the PID's KeyAttested DPoP config — that flow
            // swaps the provisional key for a nonce-bound attested key after the token
            // endpoint, which breaks the standard browser flow (the PAR and token
            // requests must use the same DPoP key). Fall back to the default Android
            // Keystore DPoP config which uses a single key for the entire flow.
            // Non-PID issuers get plain JWT proofs when they advertise a `jwt` proof type
            // without `key_attestations_required`. Per the "key attestations for EAA issuance"
            // decision the wallet sends no key attestation at the credential endpoint for EAAs:
            // their credential keys live in the same local key store as the keys already covered
            // by the wallet attestation, so a key attestation adds no information. Only the PID
            // issuer, whose keys are rWSCA-backed and carry a real Wallet Trust Evidence, keeps
            // the attestation proof - which is why this is set here and not globally.
            issuerConfig.copy(
                authorizationHandler = null,
                dpopConfig = walletCoreConfig.defaultDPopConfig,
                proofTypes = issuerConfig.proofTypes.copy(
                    allowJwtProofWithoutKeyAttestation = true
                ),
            )
        }
    }

    /**
     * Selects a fallback [OpenId4VciManager.Config] for an issuer that has no
     * exact match in [configs].
     *
     * When [authorizationServerMetadata] advertises
     * `attest_jwt_client_auth` in `token_endpoint_auth_methods_supported`, an
     * `AttestationBased` configuration is used as the template (so a WIA is
     * sent at the PAR and token endpoints). Otherwise a `None` configuration
     * is used.
     *
     * @throws IllegalArgumentException when no suitable template configuration
     *   is available.
     */
    private fun fallbackConfig(
        configs: Map<String, OpenId4VciManager.Config>,
        httpsUrl: String,
        authorizationServerMetadata: CIAuthorizationServerMetadata?,
    ): OpenId4VciManager.Config {
        val requiresWia = authorizationServerMetadata?.tokenEndpointAuthMethods
            ?.any { it.value == AttestationBasedClientAuthenticationSpec.ATTESTATION_JWT_CLIENT_AUTHENTICATION_METHOD }
            ?: false
        val template = if (requiresWia) {
            configs.values.firstOrNull {
                it.clientAuthenticationType is OpenId4VciManager.ClientAuthenticationType.AttestationBased
            }
        } else {
            configs.values.firstOrNull {
                it.clientAuthenticationType is OpenId4VciManager.ClientAuthenticationType.None
            }
        } ?: throw IllegalArgumentException(
            "No OpenID4VCI configuration found for issuer '$httpsUrl' " +
                "and no suitable fallback config is available."
        )
        return template.copy(
            authFlowRedirectionURI = BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK,
            parUsage = OpenId4VciManager.Config.ParUsage.IF_SUPPORTED,
        )
    }
}
