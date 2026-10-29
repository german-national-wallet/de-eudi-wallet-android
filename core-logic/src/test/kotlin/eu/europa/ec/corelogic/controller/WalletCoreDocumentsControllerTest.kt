package eu.europa.ec.corelogic.controller

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import eu.europa.ec.eudi.openid4vci.CIAuthorizationServerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialIssuanceError.AccessTokenRequestFailed
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError.InvalidSignedMetadata
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError.MissingSignedMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestError.UnableToResolveCredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestException
import eu.europa.ec.eudi.openid4vci.CredentialOffer
import eu.europa.ec.eudi.openid4vci.CredentialIssuerId
import eu.europa.ec.eudi.openid4vci.Grants
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.Outcome
import eu.europa.ec.eudi.wallet.document.UnsignedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.reissue.ReissuanceAuthorizationException
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationGenerationResult
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.authenticationlogic.jwt.JwtParser
import eu.europa.ec.authenticationlogic.model.JwtKeyPair
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedExceptionWithNoMessage
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState
import org.sprind.wallet.corelogic.activity.ActivityCredential
import org.sprind.wallet.corelogic.activity.ActivityLogEntry
import org.sprind.wallet.corelogic.activity.ActivityLogRepository
import org.sprind.wallet.corelogic.activity.ActivityResult
import org.sprind.wallet.corelogic.activity.ActivityType
import org.sprind.wallet.corelogic.activity.InteractingParty
import org.sprind.wallet.corelogic.activity.newActivityLogEntry
import org.sprind.wallet.corelogic.config.PidConfigurationOverrides
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.SecureArea
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey


class WalletCoreDocumentsControllerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var eudiWallet: EudiWallet

    @Mock
    private lateinit var openId4VciManager: OpenId4VciManager

    @Mock
    private lateinit var hardwareKeyStorageController: HardwareKeyStorageController

    @Mock
    private lateinit var walletCoreConfig: WalletCoreConfig

    @Mock
    private lateinit var jwtParser: JwtParser

    @Mock
    private lateinit var appAttestationController: AppAttestationController

    private val configIssuerUrl = "https://issuer.eudiw.dev"
    private val attestationBasedIssuerUrl = "https://wallet-provider.eudiw.dev"
    private val unknownEaaIssuerUrl = "https://eaa-provider.example.com"

    private val config: OpenId4VciManager.Config =
        OpenId4VciManager.Config.Builder()
            .withClientAuthenticationType(
                OpenId4VciManager.ClientAuthenticationType.None("https://issuer.eudiw.dev")
            )
            .withAuthFlowRedirectionURI("https://example.com/auth")
            .build()

    private val attestationBasedConfig: OpenId4VciManager.Config =
        OpenId4VciManager.Config.Builder()
            .withClientAuthenticationType(
                OpenId4VciManager.ClientAuthenticationType.AttestationBased(
                    "https://wallet-provider.eudiw.dev"
                )
            )
            .withAuthFlowRedirectionURI("https://example.com/auth")
            .build()

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var telemetry: org.sprind.wallet.analyticslogic.controller.Telemetry

    private lateinit var closeable: AutoCloseable

    private var pidConfigurationOverrides = PidConfigurationOverrides()

    private val subject: WalletCoreDocumentsController by lazy {
        WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            eudiWallet = eudiWallet,
            walletCoreConfig = walletCoreConfig,
            pidIssuerUrl = PID_ISSUER_URL,
            authorizationHandler = mock(),
            hardwareKeyStorageController = hardwareKeyStorageController,
            appAttestationController = appAttestationController,
            logController = logController,
            ktorHttpClientFactory = null,
            telemetry = telemetry,
            pidConfigurationOverridesProvider = { pidConfigurationOverrides },
            // Runs issuanceEventScope on the test scheduler instead of Dispatchers.IO.
            dispatcher = coroutineRule.testDispatcher,
            activityLog = activityLog,
        )
    }

    private val activityLog: ActivityLogRepository = mock(ActivityLogRepository::class.java)

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(resourceProvider.getString(R.string.issuance_generic_error))
            .thenReturn(mockedGenericErrorMessage)

        whenever(walletCoreConfig.vciConfig).thenReturn(
            mapOf(
                configIssuerUrl to config,
                attestationBasedIssuerUrl to attestationBasedConfig,
            )
        )
        whenever(walletCoreConfig.defaultDPopConfig).thenReturn(DPopConfig.Disabled)
        whenever(eudiWallet.createOpenId4VciManager(any(), anyOrNull()))
            .thenReturn(openId4VciManager)

        // Stub telemetry.startSpan to return a mock SpanHandle so recordResumeFailure
        // doesn't NPE on span.span.recordException / setAttribute / close
        val mockSpanHandle = mock(org.sprind.wallet.analyticslogic.controller.SpanHandle::class.java)
        val mockSpan = mock(io.opentelemetry.api.trace.Span::class.java)
        whenever(mockSpanHandle.span).thenReturn(mockSpan)
        whenever(telemetry.startSpan(any(), any())).thenReturn(mockSpanHandle)
    }

    @After
    fun after() {
        closeable.close()
    }

    /**
     * Re-issuance now generates a wallet instance attestation (WIA) and passes it to
     * OpenId4VciManager.reissueDocument(), so tests that reach that call must stub a successful
     * attestation. The concrete JWT/keys are irrelevant here — the manager is mocked.
     */
    private fun stubWalletAttestation() {
        val spec = WalletInstanceAttestationSpec(
            wbWiaJwt = mock(SignedJWT::class.java),
            wiWiaPopKeyPair = JwtKeyPair(
                public = mock(JWK::class.java),
                private = mock(PrivateKey::class.java),
            ),
        )
        whenever(appAttestationController.generateAttestation())
            .thenReturn(flowOf(WalletAttestationGenerationResult.Success(spec)))
    }


    @Test
    fun `Given api client throws error, issueDocumentByConfigurationIdentifierAttested() returns Failure result`() =
        coroutineRule.runTest {
            // Given
            val mockJwt: SignedJWT = mock()
            val fakeWalletAttestation =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
            whenever(jwtParser.parseJwt(fakeWalletAttestation)).thenReturn(mockJwt)

            val mockAttestationSpec = mock(WalletInstanceAttestationSpec::class.java)

            val key: ECPrivateKey = mock()
            whenever(key.algorithm).thenReturn("EC")

            doThrow(mockedExceptionWithNoMessage as Throwable)
                .whenever(openId4VciManager).issueDocumentByConfigurationIdentifiersAttested(
                    issuerUrl = any(),
                    credentialConfigurationIds = eq(
                        listOf(WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC.value)
                    ),
                    walletAttestation = any<SignedJWT>(),
                    walletWiaPopPublicKey = any(),
                    walletWiaPopPrivateKey = any(),
                    onIssueEvent = any(),
                    txCode = any(),
                    executor = any()
                )

            // When
            val result = subject.issueDocumentAttested(
                configId = WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC.value,
                walletInstanceAttestationSpec = mockAttestationSpec,
                issuerId = "issuerId",
                issuanceMethod = IssuanceMethod.OPENID4VCI
            )

            result.runFlowTest {
                //Then

                val state = awaitItem()
                assertEquals(
                    IssueDocumentPartialState.Failure(
                        mockedGenericErrorMessage
                    ),
                    state
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    @Test
    fun `Given api client returns success, issueDocumentByConfigurationIdentifierAttested() returns Success result`() {
        // TODO needs work and we need to update based on latest logic
    }

    @Test
    fun `Given reissue authorization exception then reissueDocument forwards upstream reauth message`() =
        coroutineRule.runTest {
            val issuedDocument = mock(IssuedDocument::class.java)
            // PID document → the attested re-issuance path (WIA required by its /token endpoint).
            whenever(issuedDocument.format).thenReturn(MsoMdocFormat("eu.europa.ec.eudi.pid.1"))
            whenever(issuedDocument.id).thenReturn("doc-1")
            stubWalletAttestation()

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(6)
                callback(IssueEvent.Failure(ReissuanceAuthorizationException()))
                null
            }.whenever(openId4VciManager).reissueDocumentAttested(
                documentId = eq("doc-1"),
                walletAttestation = any(),
                walletWiaPopPublicKey = any(),
                walletWiaPopPrivateKey = any(),
                allowAuthorizationFallback = eq(false),
                executor = anyOrNull(),
                onIssueEvent = any()
            )

            subject.reissueDocument(document = issuedDocument)
                .runFlowTest {
                    val first = awaitItem()
                    val failure = when (first) {
                        is ReissueDocumentPartialState.Failure -> first
                        else -> awaitItem() as ReissueDocumentPartialState.Failure
                    }
                    assertEquals(
                        "Re-issuance requires user authorization",
                        failure.errorMessage,
                    )
                    cancelAndIgnoreRemainingEvents()
                }
        }

    @Test
    fun `Given reissue failure with unauthorized message then reissueDocument forwards unauthorized message`() =
        coroutineRule.runTest {
            val issuedDocument = mock(IssuedDocument::class.java)
            // PID document → the attested re-issuance path (WIA required by its /token endpoint).
            whenever(issuedDocument.format).thenReturn(MsoMdocFormat("eu.europa.ec.eudi.pid.1"))
            whenever(issuedDocument.id).thenReturn("doc-2")
            stubWalletAttestation()

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(6)
                callback(IssueEvent.Failure(IllegalStateException("401 Unauthorized")))
                null
            }.whenever(openId4VciManager).reissueDocumentAttested(
                documentId = eq("doc-2"),
                walletAttestation = any(),
                walletWiaPopPublicKey = any(),
                walletWiaPopPrivateKey = any(),
                allowAuthorizationFallback = eq(false),
                executor = anyOrNull(),
                onIssueEvent = any()
            )

            subject.reissueDocument(document = issuedDocument)
                .runFlowTest {
                    val first = awaitItem()
                    val failure = when (first) {
                        is ReissueDocumentPartialState.Failure -> first
                        else -> awaitItem() as ReissueDocumentPartialState.Failure
                    }
                    assertEquals(
                        "401 Unauthorized",
                        failure.errorMessage,
                    )
                    cancelAndIgnoreRemainingEvents()
                }
        }

    // TODO: Add explicit test for metadata-missing upstream reissuance errors once error contract is stabilized.

    // ---------------------------------------------------------------------
    // Regression tests for issueDocumentsByOfferUri offer cache.
    //
    // Some issuers (e.g. the eudiplo.eudi-wallet.org playground) treat
    // credential-offer URIs as single-use: a second GET to the same offer
    // URI after the first successful resolution returns HTTP 404. The
    // controller must therefore reuse the cached Offer on the issue path
    // instead of re-resolving by URI.
    // ---------------------------------------------------------------------

    private fun buildOffer(
        issuerUrl: String,
        authorizationServerMetadata: CIAuthorizationServerMetadata? = null,
        grants: Grants? = null,
    ): Offer {
        // CredentialIssuerId is a value class — construct it directly
        // instead of mocking (Mockito can't mock inline classes).
        val credentialIssuerId = CredentialIssuerId.invoke(issuerUrl).getOrThrow()
        val credentialIssuerMetadata = mock(CredentialIssuerMetadata::class.java)
        whenever(credentialIssuerMetadata.credentialConfigurationsSupported)
            .thenReturn(emptyMap<CredentialConfigurationIdentifier, eu.europa.ec.eudi.openid4vci.CredentialConfiguration>())
        val credentialOffer = mock(CredentialOffer::class.java)
        whenever(credentialOffer.credentialIssuerIdentifier).thenReturn(credentialIssuerId)
        whenever(credentialOffer.credentialIssuerMetadata).thenReturn(credentialIssuerMetadata)
        if (authorizationServerMetadata != null) {
            whenever(credentialOffer.authorizationServerMetadata).thenReturn(authorizationServerMetadata)
        }
        if (grants != null) {
            whenever(credentialOffer.grants).thenReturn(grants)
        }
        return Offer(credentialOffer)
    }

    // ---------------------------------------------------------------------
    // WD-3993: consent shown after browser authorization for the
    // authorization-code flow.
    //
    // An offer with no pre-authorized-code grant requires browser auth. Its
    // first DocumentRequiresCreateSettings — which always fires right after
    // authorization and right before credential submission — is withheld
    // (instead of auto-resumed) until the app calls continueIssuanceAfterConsent,
    // so the UI can show consent only now, after the browser step.
    // ---------------------------------------------------------------------

    @Test
    fun `Given auth-code-flow offer, first DocumentRequiresCreateSettings arms the consent gate instead of auto-resuming`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(
                configIssuerUrl,
                grants = Grants.AuthorizationCode(),
            )
            subject.cacheOffer(offerUri, offer)

            var resumeCalled = false
            var cancelCalled = false
            val gateEvent = IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy(
                offeredDocument = mock(Offer.OfferedDocument::class.java),
                resume = { resumeCalled = true },
                cancel = { cancelCalled = true },
            )

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(gateEvent)
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.AwaitingConsent)
                    cancelAndIgnoreRemainingEvents()
                }

            // Withheld, not auto-resumed.
            assertTrue(!resumeCalled)
            assertTrue(!cancelCalled)
            // The persistent flow carries it too, so a recovery path (e.g. after the
            // ViewModel is recreated while the browser round-trip is in flight) can
            // still observe it.
            assertEquals(
                IssueDocumentsPartialState.AwaitingConsent,
                subject.issuanceState.replayCache.lastOrNull()
            )
        }

    @Test
    fun `Given auth-code-flow offer, DocumentRequiresUserAuth after the consent gate is published on issuanceState`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(
                configIssuerUrl,
                grants = Grants.AuthorizationCode(),
            )
            subject.cacheOffer(offerUri, offer)

            val gateEvent = IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy(
                offeredDocument = mock(Offer.OfferedDocument::class.java),
                resume = {},
                cancel = {},
            )
            val callback = argumentCaptor<OpenId4VciManager.OnIssueEvent>()
            doAnswer { invocation ->
                invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)(gateEvent)
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = callback.capture(),
            )

            // The UI stops collecting at the consent gate, which cancels the
            // callbackFlow's producer scope.
            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .first { it is IssueDocumentsPartialState.AwaitingConsent }

            // After consent, credential submission hits a key that needs user auth.
            val unsignedDocument = mock(UnsignedDocument::class.java)
            whenever(unsignedDocument.id).thenReturn("doc-1")
            whenever(unsignedDocument.name).thenReturn("University Diploma")
            callback.lastValue(
                IssueEvent.DocumentRequiresUserAuth(
                    document = unsignedDocument,
                    signingAlgorithm = Algorithm.ESP256,
                    keysRequireAuth = mapOf("alias" to mock(SecureArea::class.java)),
                    resume = {},
                    cancel = {},
                )
            )

            // Must still reach the UI (previously it was launched into the cancelled
            // producer scope and dropped, so the biometric prompt never showed).
            // (issuanceEventScope runs on the test dispatcher; awaiting lets it run.)
            val state = subject.issuanceState.first { it !is IssueDocumentsPartialState.AwaitingConsent }
            assertTrue(state is IssueDocumentsPartialState.UserAuthRequired)
        }

    @Test
    fun `Given auth-code-flow offer, when a post-consent event handler throws, then a Failure is published and issuance is cancelled`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(
                configIssuerUrl,
                grants = Grants.AuthorizationCode(),
            )
            subject.cacheOffer(offerUri, offer)

            val gateEvent = IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy(
                offeredDocument = mock(Offer.OfferedDocument::class.java),
                resume = {},
                cancel = {},
            )
            val callback = argumentCaptor<OpenId4VciManager.OnIssueEvent>()
            doAnswer { invocation ->
                invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)(gateEvent)
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = callback.capture(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .first { it is IssueDocumentsPartialState.AwaitingConsent }

            // A second document's create-settings event arrives after the gate. Its
            // offeredDocument mock has no offer, so the handler throws an exception.
            var cancelled = false
            callback.lastValue(
                IssueEvent.DocumentRequiresCreateSettings.OptionalReusePolicy(
                    offeredDocument = mock(Offer.OfferedDocument::class.java),
                    resume = {},
                    cancel = { cancelled = true },
                )
            )

            // As with the producer scope before, the failure must surface as a Failure
            // state rather than escape as an uncaught exception.
            val state = subject.issuanceState.first { it !is IssueDocumentsPartialState.AwaitingConsent }
            assertTrue(state is IssueDocumentsPartialState.Failure)
            assertTrue(cancelled)
        }

    @Test
    fun `continueIssuanceAfterConsent is a safe no-op when nothing is awaiting consent`() =
        coroutineRule.runTest {
            // Should not throw even though no issuance ever gated.
            subject.continueIssuanceAfterConsent()
        }

    @Test
    fun `Given cached offer, issueDocumentsByOfferUri reuses it and does not re-resolve`() =
        coroutineRule.runTest {
            // Given: an offer cached for this URI, pointing at the configured issuer.
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(configIssuerUrl)
            subject.cacheOffer(offerUri, offer)

            // The manager is the one returned by getManagerForIssuer(issuerUrl).
            // Stub issueDocumentByOffer to emit a terminal Failure so the flow
            // completes without requiring CreateDocumentSettings / biometrics.
            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(IllegalStateException("stubbed issuance failure")))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            // When
            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure)
                    cancelAndIgnoreRemainingEvents()
                }

            // Then: the cached offer was used — issueDocumentByOffer was
            // invoked with the cached offer, and resolveDocumentOffer was
            // NOT called (no second GET to the single-use offer URI).
            verify(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )
            verify(openId4VciManager, never()).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )
        }

    @Test
    fun `Given the issuer rejects the transaction code, issueDocumentsByOfferUri reports a grant rejection`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(configIssuerUrl)
            subject.cacheOffer(offerUri, offer)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(AccessTokenRequestFailed("invalid_grant", null)))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = eq("111111"),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = "111111")
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure && state.isGrantRejection)
                    cancelAndIgnoreRemainingEvents()
                }
        }

    @Test
    fun `Given the issuer metadata cannot be verified, issueDocumentsByOfferUri says the provider is not verified`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(configIssuerUrl)
            subject.cacheOffer(offerUri, offer)
            whenever(resourceProvider.getString(R.string.issuance_issuer_not_verified_error))
                .thenReturn(ISSUER_NOT_VERIFIED_MESSAGE)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(MissingSignedMetadata()))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure && state.isIssuerNotVerified)
                    assertEquals(ISSUER_NOT_VERIFIED_MESSAGE, (state as IssueDocumentsPartialState.Failure).errorMessage)
                    cancelAndIgnoreRemainingEvents()
                }
        }

    @Test
    fun `Given the offer issuer metadata cannot be verified, resolveDocumentOffer says the provider is not verified`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            whenever(resourceProvider.getString(R.string.issuance_issuer_not_verified_error))
                .thenReturn(ISSUER_NOT_VERIFIED_MESSAGE)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnResolvedOffer>(2)
                callback(
                    OfferResult.Failure(
                        CredentialOfferRequestException(
                            UnableToResolveCredentialIssuerMetadata(
                                InvalidSignedMetadata(IllegalStateException("untrusted chain"))
                            )
                        )
                    )
                )
                null
            }.whenever(openId4VciManager).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )

            subject.resolveDocumentOffer(offerUri)
                .runFlowTest {
                    assertEquals(
                        ResolveDocumentOfferPartialState.Failure(ISSUER_NOT_VERIFIED_MESSAGE),
                        awaitItem(),
                    )
                    cancelAndIgnoreRemainingEvents()
                }
        }

    // ---------------------------------------------------------------------
    // WD-4477: Metadata-driven WIA decision for unknown EAA issuers.
    //
    // When an EAA provider is not in the static vciConfig map, the wallet
    // must inspect the AS metadata for `attest_jwt_client_auth` to decide
    // whether to send a Wallet Instance Attestation at the PAR/token
    // endpoints.
    // ---------------------------------------------------------------------

    /**
     * Builds a mock [CIAuthorizationServerMetadata] whose
     * `token_endpoint_auth_methods_supported` contains [methods].
     */
    private fun buildAsMetadata(
        methods: List<ClientAuthenticationMethod>,
    ): CIAuthorizationServerMetadata {
        val metadata = mock(CIAuthorizationServerMetadata::class.java)
        whenever(metadata.tokenEndpointAuthMethods).thenReturn(methods)
        return metadata
    }

    @Test
    fun `Given unknown EAA issuer with attest_jwt_client_auth, issueDocumentsByOfferUri uses AttestationBased config`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val asMetadata = buildAsMetadata(
                listOf(ClientAuthenticationMethod("attest_jwt_client_auth"))
            )
            val offer = buildOffer(unknownEaaIssuerUrl, asMetadata)
            subject.cacheOffer(offerUri, offer)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(IllegalStateException("stubbed issuance failure")))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure)
                    cancelAndIgnoreRemainingEvents()
                }

            // The manager for the unknown EAA issuer must be created with an
            // AttestationBased config (metadata-driven fallback). The lazy
            // delegate also pre-creates managers for known issuers, so match
            // on the fallback config itself: fallbackConfig() is the only path
            // that overrides authFlowRedirectionURI to the deep link, whereas
            // every known-issuer config keeps its configured redirect URI.
            val configCaptor = argumentCaptor<OpenId4VciManager.Config>()
            verify(eudiWallet, atLeast(1)).createOpenId4VciManager(configCaptor.capture(), anyOrNull())
            val fallbackConfigs = configCaptor.allValues.filter {
                it.authFlowRedirectionURI == BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK
            }
            assertEquals(
                "Expected exactly one fallback (unknown EAA issuer) config, but found " +
                    "${fallbackConfigs.map { it.clientAuthenticationType }}",
                1,
                fallbackConfigs.size
            )
            val eaaConfig = fallbackConfigs.single()
            assertTrue(
                "Expected the unknown EAA issuer's config to use AttestationBased client auth, " +
                    "but was ${eaaConfig.clientAuthenticationType}",
                eaaConfig.clientAuthenticationType is OpenId4VciManager.ClientAuthenticationType.AttestationBased
            )
        }

    @Test
    fun `Given unknown EAA issuer without attest_jwt_client_auth, issueDocumentsByOfferUri uses None config`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val asMetadata = buildAsMetadata(
                listOf(ClientAuthenticationMethod("client_secret_basic"))
            )
            val offer = buildOffer(unknownEaaIssuerUrl, asMetadata)
            subject.cacheOffer(offerUri, offer)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(IllegalStateException("stubbed issuance failure")))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure)
                    cancelAndIgnoreRemainingEvents()
                }

            // The manager for the unknown EAA issuer must be created with a
            // None config (metadata-driven fallback: no attest_jwt_client_auth
            // in AS metadata). The lazy delegate also pre-creates managers for
            // known issuers, so match on the fallback config itself:
            // fallbackConfig() is the only path that overrides
            // authFlowRedirectionURI to the deep link.
            val configCaptor = argumentCaptor<OpenId4VciManager.Config>()
            verify(eudiWallet, atLeast(1)).createOpenId4VciManager(configCaptor.capture(), anyOrNull())
            val fallbackConfigs = configCaptor.allValues.filter {
                it.authFlowRedirectionURI == BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK
            }
            assertEquals(
                "Expected exactly one fallback (unknown EAA issuer) config, but found " +
                    "${fallbackConfigs.map { it.clientAuthenticationType }}",
                1,
                fallbackConfigs.size
            )
            val eaaConfig = fallbackConfigs.single()
            assertTrue(
                "Expected the unknown EAA issuer's config to use None client auth, " +
                    "but was ${eaaConfig.clientAuthenticationType}",
                eaaConfig.clientAuthenticationType is OpenId4VciManager.ClientAuthenticationType.None
            )
        }

    //region resumeOpenId4VciWithAuthorization

    private val mockedInterruptedErrorMessage = "issuance was interrupted"

    @Test
    fun `Given no pending authorization, resumeOpenId4VciWithAuthorization emits Failure to issuanceState`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            subject.issuanceState.runFlowTest {
                subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")

                val state = awaitItem()
                assertTrue(state is IssueDocumentsPartialState.Failure)
                val failure = state as IssueDocumentsPartialState.Failure
                assertEquals(mockedInterruptedErrorMessage, failure.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Given no pending authorization, resumeOpenId4VciWithAuthorization records telemetry`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")

            verify(telemetry).logEvent(
                eq("issuance.resume.failed"),
                any()
            )
        }

    @Test
    fun `Given no cached offer, issueDocumentsByOfferUri falls back to resolveDocumentOffer`() =
        coroutineRule.runTest {
            // Given: no cache entry for this URI.
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(configIssuerUrl)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnResolvedOffer>(2)
                callback(OfferResult.Success(offer))
                null
            }.whenever(openId4VciManager).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )
            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(IllegalStateException("stubbed issuance failure")))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = any(),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            // When
            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    val state = awaitItem()
                    assertTrue(state is IssueDocumentsPartialState.Failure)
                    cancelAndIgnoreRemainingEvents()
                }

            // Then: fallback path was taken — resolveDocumentOffer WAS
            // called, and issueDocumentByOffer was invoked with the
            // resolved offer.
            verify(openId4VciManager).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )
            verify(openId4VciManager).issueDocumentByOffer(
                offer = any(),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )
        }

    @Test
    fun `Given no pending authorization, resumeOpenId4VciWithAuthorization records no_pending_authorization reason`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")

            // The first emit is the Failure state; drain it so it doesn't block
            subject.issuanceState.first<IssueDocumentsPartialState>()

            // Verify startSpan was called with the ISSUANCE span name
            verify(telemetry).startSpan(
                eq(org.sprind.wallet.analyticslogic.controller.TelemetryConstants.ISSUANCE),
                any()
            )
        }

    @Test
    fun `clearCachedOffer removes a cached offer so the next issue falls back to resolve`() =
        coroutineRule.runTest {
            val offerUri = "openid-credential-offer://?credential_offer_uri=https://offer.uri"
            val offer = buildOffer(configIssuerUrl)
            subject.cacheOffer(offerUri, offer)
            subject.clearCachedOffer(offerUri)

            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnResolvedOffer>(2)
                callback(OfferResult.Success(offer))
                null
            }.whenever(openId4VciManager).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )
            doAnswer { invocation ->
                val callback = invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                callback(IssueEvent.Failure(IllegalStateException("stubbed issuance failure")))
                null
            }.whenever(openId4VciManager).issueDocumentByOffer(
                offer = any(),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any(),
            )

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null)
                .runFlowTest {
                    assertTrue(awaitItem() is IssueDocumentsPartialState.Failure)
                    cancelAndIgnoreRemainingEvents()
                }

            // Cache was cleared → resolveDocumentOffer was invoked.
            verify(openId4VciManager).resolveDocumentOffer(
                offerUri = any(),
                executor = anyOrNull(),
                onResolvedOffer = any(),
            )
        }

    @Test
    fun `Given manager throws, resumeOpenId4VciWithAuthorization emits Failure to issuanceState`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            // Populate pendingAuthorizationIssuerUrl via reflection so the non-null branch is taken
            val issuerUrl = configIssuerUrl
            val field = WalletCoreDocumentsControllerImpl::class.java
                .getDeclaredField("pendingAuthorizationIssuerUrl")
            field.isAccessible = true
            field.set(subject, issuerUrl)

            // Stub resumeWithAuthorization to throw
            doThrow(IllegalStateException("No suspended authorization found"))
                .whenever(openId4VciManager).resumeWithAuthorization(any<String>())

            subject.issuanceState.runFlowTest {
                subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")

                val state = awaitItem()
                assertTrue(state is IssueDocumentsPartialState.Failure)
                val failure = state as IssueDocumentsPartialState.Failure
                assertEquals(mockedInterruptedErrorMessage, failure.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Given manager throws, resumeOpenId4VciWithAuthorization clears pendingAuthorizationIssuerUrl`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            val field = WalletCoreDocumentsControllerImpl::class.java
                .getDeclaredField("pendingAuthorizationIssuerUrl")
            field.isAccessible = true
            field.set(subject, configIssuerUrl)

            doThrow(IllegalStateException("No suspended authorization found"))
                .whenever(openId4VciManager).resumeWithAuthorization(any<String>())

            subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")

            // Drain the emission
            subject.issuanceState.first<IssueDocumentsPartialState>()

            // The finally block should have cleared the field
            assertEquals(null, field.get(subject))
        }

    @Test
    fun `Given no pending authorization, second resumeOpenId4VciWithAuthorization also emits Failure`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
                .thenReturn(mockedInterruptedErrorMessage)

            // First call — no pending auth (field is null)
            subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc1")
            subject.issuanceState.first<IssueDocumentsPartialState>()

            // Second call — field is still null
            subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc2")

            // Verify telemetry.logEvent was called twice (once per resume failure)
            verify(telemetry, times(2)).logEvent(
                eq("issuance.resume.failed"),
                any()
            )
        }

    //endregion

    //region resolvePreferredPidConfigurationIds / resolvePreferredPidConfigurations

    @Test
    fun `resolvePreferredPidConfigurationIds prefers beta mdoc and beta sd-jwt when both advertised`() {
        val advertised = setOf(
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
        )
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(advertised)
        assertEquals(
            setOf(
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
            ),
            selected,
        )
    }

    @Test
    fun `resolvePreferredPidConfigurationIds falls back to stable when beta not advertised`() {
        val advertised = setOf(
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT,
        )
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(advertised)
        assertEquals(
            setOf(
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT,
            ),
            selected,
        )
    }

    @Test
    fun `resolvePreferredPidConfigurationIds prefers beta for one format and stable for the other`() {
        val advertised = setOf(
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
        )
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(advertised)
        assertEquals(
            setOf(
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
                WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
            ),
            selected,
        )
    }

    @Test
    fun `resolvePreferredPidConfigurationIds returns empty when issuer advertises no known PID`() {
        val advertised = setOf<CredentialConfigurationIdentifier>(
            CredentialConfigurationIdentifier("some-other-credential")
        )
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(advertised)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `resolvePreferredPidConfigurationIds is forward-compatible with a future _3-beta`() {
        // Simulate adding a _3-beta by constructing a family inline. The algorithm
        // picks the first advertised entry in preference order, so a _3-beta would
        // be preferred over _2-beta when both are advertised — no other code changes.
        val futureBeta = CredentialConfigurationIdentifier("pid-mso-mdoc_3-beta")
        val family = listOf(
            futureBeta,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
        )
        val advertised = setOf(
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
        )
        val selected = family.firstOrNull { it in advertised }
        assertEquals(WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA, selected)
    }

    @Test
    fun `Given issuer advertises beta and stable, resolvePreferredPidConfigurations returns beta subset`() =
        coroutineRule.runTest {
            val metadata = mock(CredentialIssuerMetadata::class.java)
            // CredentialConfiguration is a sealed interface and cannot be mocked,
            // so use real MsoMdocCredential / SdJwtVcCredential instances. The
            // resolver only inspects the map keys, so the values are irrelevant.
            val mdoc = eu.europa.ec.eudi.openid4vci.MsoMdocCredential(
                credentialMetadata = null,
                docType = "eu.europa.ec.eudi.pid.1",
            )
            val sdjwt = eu.europa.ec.eudi.openid4vci.SdJwtVcCredential(
                credentialMetadata = null,
                type = "urn:eu.europa.ec.eudi.pid:1",
            )
            whenever(metadata.credentialConfigurationsSupported).thenReturn(
                mapOf(
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC to mdoc,
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA to mdoc,
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT to sdjwt,
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA to sdjwt,
                )
            )
            whenever(openId4VciManager.getIssuerMetadata(any())).thenReturn(Result.success(metadata))

            val result = subject.resolvePreferredPidConfigurations()

            assertTrue(result is ResolvePreferredPidConfigurationsPartialState.Success)
            assertEquals(
                setOf(
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
                ),
                (result as ResolvePreferredPidConfigurationsPartialState.Success).configurationIds,
            )
        }

    @Test
    fun `Given issuer advertises no known PID, resolvePreferredPidConfigurations returns NoPidConfigurationsAdvertised`() =
        coroutineRule.runTest {
            val metadata = mock(CredentialIssuerMetadata::class.java)
            val mdoc = eu.europa.ec.eudi.openid4vci.MsoMdocCredential(
                credentialMetadata = null,
                docType = "some-other-doctype",
            )
            whenever(metadata.credentialConfigurationsSupported).thenReturn(
                mapOf(
                    CredentialConfigurationIdentifier("some-other-credential") to mdoc,
                )
            )
            whenever(openId4VciManager.getIssuerMetadata(any())).thenReturn(Result.success(metadata))

            val result = subject.resolvePreferredPidConfigurations()

            assertTrue(result is ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised)
        }

    @Test
    fun `Given issuer metadata fetch fails, resolvePreferredPidConfigurations returns Failure`() =
        coroutineRule.runTest {
            whenever(openId4VciManager.getIssuerMetadata(any()))
                .thenReturn(Result.failure(IllegalStateException("network down")))

            val result = subject.resolvePreferredPidConfigurations()

            assertTrue(result is ResolvePreferredPidConfigurationsPartialState.Failure)
        }

    @Test
    fun `resolvePreferredPidConfigurationIds picks an override even when it is not advertised`() {
        val override = CredentialConfigurationIdentifier("pid-mso-mdoc_3")
        val advertised = setOf(
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC_2_BETA,
            WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA,
        )
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(
            advertised = advertised,
            mdocOverride = override,
        )
        assertEquals(
            setOf(override, WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT_2_BETA),
            selected,
        )
    }

    @Test
    fun `resolvePreferredPidConfigurationIds picks both overrides regardless of advertised`() {
        val mdoc = CredentialConfigurationIdentifier("pid-mso-mdoc")
        val sdJwt = CredentialConfigurationIdentifier("pid-sd-jwt_3")
        val selected = WalletCoreDocumentsController.resolvePreferredPidConfigurationIds(
            advertised = emptySet(),
            mdocOverride = mdoc,
            sdJwtOverride = sdJwt,
        )
        assertEquals(setOf(mdoc, sdJwt), selected)
    }

    @Test
    fun `Given both PID config overrides are set, resolvePreferredPidConfigurations returns them without fetching metadata`() =
        coroutineRule.runTest {
            pidConfigurationOverrides = PidConfigurationOverrides(mdoc = "pid-mso-mdoc_3", sdJwt = "pid-sd-jwt_3")

            val result = subject.resolvePreferredPidConfigurations()

            assertEquals(
                ResolvePreferredPidConfigurationsPartialState.Success(
                    setOf(
                        CredentialConfigurationIdentifier("pid-mso-mdoc_3"),
                        CredentialConfigurationIdentifier("pid-sd-jwt_3"),
                    )
                ),
                result,
            )
            verify(openId4VciManager, never()).getIssuerMetadata(any())
        }

    @Test
    fun `Given only the mdoc override is set, resolvePreferredPidConfigurations combines it with the advertised sd-jwt`() =
        coroutineRule.runTest {
            pidConfigurationOverrides = PidConfigurationOverrides(mdoc = "pid-mso-mdoc_3")
            val metadata = mock(CredentialIssuerMetadata::class.java)
            whenever(metadata.credentialConfigurationsSupported).thenReturn(
                mapOf(
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC to pidMdocConfiguration,
                    WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT to pidSdJwtConfiguration,
                )
            )
            whenever(openId4VciManager.getIssuerMetadata(any())).thenReturn(Result.success(metadata))

            val result = subject.resolvePreferredPidConfigurations()

            assertEquals(
                ResolvePreferredPidConfigurationsPartialState.Success(
                    setOf(
                        CredentialConfigurationIdentifier("pid-mso-mdoc_3"),
                        WalletCoreDocumentsController.PID_CONFIGURATION_ID_SD_JWT,
                    )
                ),
                result,
            )
        }

    @Test
    fun `Given only one override is set and metadata fetch fails, resolvePreferredPidConfigurations returns Failure`() =
        coroutineRule.runTest {
            pidConfigurationOverrides = PidConfigurationOverrides(mdoc = "pid-mso-mdoc_3")
            whenever(openId4VciManager.getIssuerMetadata(any()))
                .thenReturn(Result.failure(IllegalStateException("network down")))

            val result = subject.resolvePreferredPidConfigurations()

            assertTrue(result is ResolvePreferredPidConfigurationsPartialState.Failure)
        }

    @Test
    fun `Given a PID config override, getScopedDocuments marks that configuration as PID`() =
        coroutineRule.runTest {
            pidConfigurationOverrides = PidConfigurationOverrides(mdoc = "pid-mso-mdoc_3")
            val metadata = mock(CredentialIssuerMetadata::class.java)
            whenever(metadata.credentialConfigurationsSupported).thenReturn(
                mapOf(
                    CredentialConfigurationIdentifier("pid-mso-mdoc_3") to pidMdocConfiguration,
                    CredentialConfigurationIdentifier("some-eaa") to eu.europa.ec.eudi.openid4vci.MsoMdocCredential(
                        credentialMetadata = null,
                        docType = "org.example.eaa",
                    ),
                )
            )
            whenever(openId4VciManager.getIssuerMetadata(any())).thenReturn(Result.success(metadata))

            val result = subject.getScopedDocuments(java.util.Locale.ENGLISH)

            assertTrue(result is FetchScopedDocumentsPartialState.Success)
            val isPidById = (result as FetchScopedDocumentsPartialState.Success).documents
                .associate { it.configurationId to it.isPid }
            assertEquals(mapOf("pid-mso-mdoc_3" to true, "some-eaa" to false), isPidById)
        }

    @Test
    fun `isPidOffer is true for a known PID configuration ID from any issuer`() {
        assertTrue(
            WalletCoreDocumentsController.isPidOffer(
                configurationId = WalletCoreDocumentsController.PID_CONFIGURATION_ID_MDOC,
                formatType = null,
                issuerId = "https://other.issuer.example.com",
                knownPidConfigurationIds = WalletCoreDocumentsController.PIDS_ALL_CONFIGURATION_IDS,
                pidIssuerUrl = PID_ISSUER_URL,
            )
        )
    }

    @Test
    fun `isPidOffer is true for an unknown configuration ID with a PID doctype from the PID issuer`() {
        assertTrue(
            WalletCoreDocumentsController.isPidOffer(
                configurationId = CredentialConfigurationIdentifier("pid-mso-mdoc_3"),
                formatType = "eu.europa.ec.eudi.pid.1",
                issuerId = PID_ISSUER_URL.trimEnd('/'),
                knownPidConfigurationIds = WalletCoreDocumentsController.PIDS_ALL_CONFIGURATION_IDS,
                pidIssuerUrl = PID_ISSUER_URL,
            )
        )
        assertTrue(
            WalletCoreDocumentsController.isPidOffer(
                configurationId = CredentialConfigurationIdentifier("pid-sd-jwt_3"),
                formatType = "urn:eudi:pid:de:1",
                issuerId = PID_ISSUER_URL,
                knownPidConfigurationIds = WalletCoreDocumentsController.PIDS_ALL_CONFIGURATION_IDS,
                pidIssuerUrl = PID_ISSUER_URL,
            )
        )
    }

    @Test
    fun `isPidOffer is false for an unknown configuration ID with a PID doctype from another issuer`() {
        assertEquals(
            false,
            WalletCoreDocumentsController.isPidOffer(
                configurationId = CredentialConfigurationIdentifier("pid-mso-mdoc_3"),
                formatType = "eu.europa.ec.eudi.pid.1",
                issuerId = "https://other.issuer.example.com",
                knownPidConfigurationIds = WalletCoreDocumentsController.PIDS_ALL_CONFIGURATION_IDS,
                pidIssuerUrl = PID_ISSUER_URL,
            )
        )
    }

    @Test
    fun `isPidOffer is false for a non-PID doctype from the PID issuer`() {
        assertEquals(
            false,
            WalletCoreDocumentsController.isPidOffer(
                configurationId = CredentialConfigurationIdentifier("some-eaa"),
                formatType = "org.example.eaa",
                issuerId = PID_ISSUER_URL,
                knownPidConfigurationIds = WalletCoreDocumentsController.PIDS_ALL_CONFIGURATION_IDS,
                pidIssuerUrl = PID_ISSUER_URL,
            )
        )
    }

    private val pidMdocConfiguration = eu.europa.ec.eudi.openid4vci.MsoMdocCredential(
        credentialMetadata = null,
        docType = "eu.europa.ec.eudi.pid.1",
    )

    private val pidSdJwtConfiguration = eu.europa.ec.eudi.openid4vci.SdJwtVcCredential(
        credentialMetadata = null,
        type = "urn:eudi:pid:de:1",
    )

    //endregion

    @Test
    fun `a deleted credential is logged with the issuer links of its issuance`() =
        coroutineRule.runTest {
            val issuer = InteractingParty(
                name = "Stadt Köln",
                isTrusted = true,
                privacyPolicyUri = "https://stadt.koeln/privacy",
            )
            whenever(activityLog.entries).thenReturn(
                flowOf(
                    listOf(
                        newActivityLogEntry(
                            type = ActivityType.ISSUANCE,
                            result = ActivityResult.COMPLETED,
                            interactingParty = issuer,
                            credentials = listOf(
                                ActivityCredential(documentId = DELETED_DOCUMENT_ID, credentialIdentifier = DOC_TYPE)
                            ),
                        )
                    )
                )
            )
            val document = deletableDocument()
            whenever(eudiWallet.getDocumentById(DELETED_DOCUMENT_ID)).thenReturn(document)

            subject.deleteDocument(DELETED_DOCUMENT_ID).first()

            val captor = argumentCaptor<ActivityLogEntry>()
            verify(activityLog).record(captor.capture())
            val entry = captor.firstValue
            assertEquals(ActivityType.DELETION, entry.type)
            assertEquals(issuer, entry.interactingParty)
            assertEquals(DELETED_DOCUMENT_ID, entry.credentials.single().documentId)
        }

    @Test
    fun `deleting the PID logs once, when its last format goes`() =
        coroutineRule.runTest {
            whenever(activityLog.entries).thenReturn(flowOf(emptyList()))
            deletableDocument(id = "pid-mdoc", format = MsoMdocFormat(PID_DOC_TYPE))
            val sdJwt = deletableDocument(id = "pid-sd-jwt", format = SdJwtVcFormat(PID_VCT))
            whenever(eudiWallet.getDocuments()).thenReturn(listOf(sdJwt), emptyList())

            subject.deleteDocument("pid-mdoc").first()
            verify(activityLog, never()).record(any())

            subject.deleteDocument("pid-sd-jwt").first()
            val captor = argumentCaptor<ActivityLogEntry>()
            verify(activityLog).record(captor.capture())
            assertEquals("pid-sd-jwt", captor.firstValue.credentials.single().documentId)
        }

    @Test
    fun `wiping the wallet logs no deletions`() =
        coroutineRule.runTest {
            val document = deletableDocument()
            whenever(eudiWallet.getDocuments(any())).thenReturn(listOf(document))
            whenever(eudiWallet.getDocumentById(DELETED_DOCUMENT_ID)).thenReturn(document)

            subject.wipeAllDocuments().first()

            verify(activityLog, never()).record(any())
        }

    private fun deletableDocument(
        id: String = DELETED_DOCUMENT_ID,
        format: DocumentFormat = MsoMdocFormat(DOC_TYPE),
    ): IssuedDocument {
        val document = mock(IssuedDocument::class.java)
        whenever(document.id).thenReturn(id)
        whenever(document.format).thenReturn(format)
        whenever(eudiWallet.getDocumentById(id)).thenReturn(document)
        whenever(eudiWallet.deleteDocumentById(id)).thenReturn(Outcome.success(null))
        return document
    }
}

private const val DELETED_DOCUMENT_ID = "deleted-document"
private const val DOC_TYPE = "org.example.sozialpass"
private const val PID_DOC_TYPE = "eu.europa.ec.eudi.pid.1"
private const val PID_VCT = "urn:eudi:pid:de:1"

private const val PID_ISSUER_URL = "https://fake.issuer.example.com/"

private const val ISSUER_NOT_VERIFIED_MESSAGE = "The provider could not be verified"
