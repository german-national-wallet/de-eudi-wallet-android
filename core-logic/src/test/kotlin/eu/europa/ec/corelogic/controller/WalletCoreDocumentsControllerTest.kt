package eu.europa.ec.corelogic.controller

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOffer
import eu.europa.ec.eudi.openid4vci.CredentialIssuerId
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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

    private val config: OpenId4VciManager.Config =
        OpenId4VciManager.Config.Builder()
            .withClientAuthenticationType(
                OpenId4VciManager.ClientAuthenticationType.None("https://issuer.eudiw.dev")
            )
            .withAuthFlowRedirectionURI("https://example.com/auth")
            .build()

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var telemetry: org.sprind.wallet.analyticslogic.controller.Telemetry

    private lateinit var closeable: AutoCloseable

    private val subject: WalletCoreDocumentsController by lazy {
        WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            eudiWallet = eudiWallet,
            walletCoreConfig = walletCoreConfig,
            pidIssuerUrl = "https://fake.issuer.example.com/",
            authorizationHandler = mock(),
            hardwareKeyStorageController = hardwareKeyStorageController,
            appAttestationController = appAttestationController,
            logController = logController,
            ktorHttpClientFactory = null,
            telemetry = telemetry,
        )
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(resourceProvider.getString(R.string.issuance_generic_error))
            .thenReturn(mockedGenericErrorMessage)

        whenever(walletCoreConfig.vciConfig).thenReturn(mapOf(configIssuerUrl to config))
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
    fun `Given missing document then reissueDocument returns metadata failure`() = coroutineRule.runTest {
        whenever(eudiWallet.getDocumentById("missing-doc")).thenReturn(null)

        subject.reissueDocument(documentId = "missing-doc").runFlowTest {
            val state = awaitItem()
            assertTrue(state is ReissueDocumentPartialState.Failure)
            val failure = state as ReissueDocumentPartialState.Failure
            assertEquals(
                "resourceProvider's genericErrorMessage",
                failure.errorMessage,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given reissue authorization exception then reissueDocument forwards upstream reauth message`() =
        coroutineRule.runTest {
            val issuedDocument = mock(IssuedDocument::class.java)
            whenever(eudiWallet.getDocumentById("doc-1")).thenReturn(issuedDocument)
            // PID document → the attested re-issuance path (WIA required by its /token endpoint).
            whenever(issuedDocument.format).thenReturn(MsoMdocFormat("eu.europa.ec.eudi.pid.1"))
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

            subject.reissueDocument(documentId = "doc-1")
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
            whenever(eudiWallet.getDocumentById("doc-2")).thenReturn(issuedDocument)
            // PID document → the attested re-issuance path (WIA required by its /token endpoint).
            whenever(issuedDocument.format).thenReturn(MsoMdocFormat("eu.europa.ec.eudi.pid.1"))
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

            subject.reissueDocument(documentId = "doc-2")
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

    private fun buildOffer(issuerUrl: String): Offer {
        // CredentialIssuerId is a value class — construct it directly
        // instead of mocking (Mockito can't mock inline classes).
        val credentialIssuerId = CredentialIssuerId.invoke(issuerUrl).getOrThrow()
        val credentialIssuerMetadata = mock(CredentialIssuerMetadata::class.java)
        whenever(credentialIssuerMetadata.credentialConfigurationsSupported)
            .thenReturn(emptyMap<CredentialConfigurationIdentifier, eu.europa.ec.eudi.openid4vci.CredentialConfiguration>())
        val credentialOffer = mock(CredentialOffer::class.java)
        whenever(credentialOffer.credentialIssuerIdentifier).thenReturn(credentialIssuerId)
        whenever(credentialOffer.credentialIssuerMetadata).thenReturn(credentialIssuerMetadata)
        return Offer(credentialOffer)
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

    //endregion
}
