/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.corelogic.controller

import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.corelogic.controller.ResolveDocumentOfferPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsControllerImpl
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertSame
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.corelogic.activity.ActivityLogRepository
import org.sprind.wallet.corelogic.config.PidConfigurationOverrides
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

/**
 * Regression tests: resolving an offer for an issuer that has no static
 * `vciConfig` entry must not pin a `None` (public client) manager that the
 * following issuance then reuses. The issuer advertises
 * `attest_jwt_client_auth`, so issuance has to run on an `AttestationBased`
 * manager; otherwise PAR is sent without client authentication and the issuer
 * answers `unauthorized_client`. The browser-authorization resume must then
 * reach that same manager.
 *
 * Robolectric is needed because offer-URI parsing uses `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class WalletCoreDocumentsControllerOfferClientAuthTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val resourceProvider: ResourceProvider = mock(ResourceProvider::class.java)
    private val eudiWallet: EudiWallet = mock(EudiWallet::class.java)
    private val walletCoreConfig: WalletCoreConfig = mock(WalletCoreConfig::class.java)

    /** Every manager the controller creates, with the config it was created from. */
    private val createdManagers = mutableListOf<Pair<OpenId4VciManager.Config, OpenId4VciManager>>()

    /** Managers that received `issueDocumentByOffer`. */
    private val issuingManagers = mutableListOf<OpenId4VciManager>()

    /**
     * When true, `issueDocumentByOffer` fails right away. When false, it never
     * calls back, like a manager suspended waiting for browser authorization.
     */
    private var failIssuanceImmediately = true

    /** Completed once a manager receives `issueDocumentByOffer`. */
    private val issuanceStarted = CompletableDeferred<Unit>()

    /** Managers that received `resolveDocumentOffer`. */
    private val resolvingManagers = mutableListOf<OpenId4VciManager>()

    /** Issuer of the offer the tests resolve and issue. */
    private var offerIssuerUrl = UNKNOWN_EAA_ISSUER_URL

    private val offerUri: String
        get() {
            val credentialOffer =
                """{"credential_issuer":"$offerIssuerUrl","credential_configuration_ids":["mdl"]}"""
            return "openid-credential-offer://?credential_offer=" +
                URLEncoder.encode(credentialOffer, "UTF-8")
        }

    private val offer: Offer
        get() = buildOffer(
            issuerUrl = offerIssuerUrl,
            authorizationServerMetadata = buildAsMetadata(
                listOf(
                    ClientAuthenticationMethod("private_key_jwt"),
                    ClientAuthenticationMethod("attest_jwt_client_auth"),
                )
            ),
        )

    private val subject by lazy {
        WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            eudiWallet = eudiWallet,
            walletCoreConfig = walletCoreConfig,
            pidIssuerUrl = PID_ISSUER_URL,
            authorizationHandler = mock(),
            hardwareKeyStorageController = mock(HardwareKeyStorageController::class.java),
            appAttestationController = mock(AppAttestationController::class.java),
            logController = mock(LogController::class.java),
            ktorHttpClientFactory = null,
            telemetry = mock(Telemetry::class.java),
            pidConfigurationOverridesProvider = { PidConfigurationOverrides() },
            dispatcher = coroutineRule.testDispatcher,
            activityLog = mock(ActivityLogRepository::class.java),
        )
    }

    @Before
    fun before() {
        whenever(resourceProvider.getString(R.string.issuance_generic_error))
            .thenReturn(mockedGenericErrorMessage)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)

        whenever(walletCoreConfig.vciConfig).thenReturn(
            mapOf(
                NONE_ISSUER_URL to configWith(
                    OpenId4VciManager.ClientAuthenticationType.None(NONE_ISSUER_URL)
                ),
                ATTESTATION_ISSUER_URL to configWith(
                    OpenId4VciManager.ClientAuthenticationType.AttestationBased(ATTESTATION_ISSUER_URL)
                ),
                // Declared last on purpose: the default manager must be the PID
                // issuer's regardless of the cache's iteration order.
                PID_ISSUER_URL to configWith(
                    OpenId4VciManager.ClientAuthenticationType.None(PID_ISSUER_URL)
                ),
            )
        )
        whenever(walletCoreConfig.defaultDPopConfig).thenReturn(DPopConfig.Disabled)

        doAnswer { invocation ->
            val config = invocation.getArgument<OpenId4VciManager.Config>(0)
            newManager().also { createdManagers += config to it }
        }.whenever(eudiWallet).createOpenId4VciManager(any(), anyOrNull())
    }

    @Test
    fun `Given unknown EAA issuer with attest_jwt_client_auth, resolving then issuing the offer uses AttestationBased config`() =
        coroutineRule.runTest {
            resolveAndCacheOffer()

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null).runFlowTest {
                assertTrue(awaitItem() is IssueDocumentsPartialState.Failure)
                cancelAndIgnoreRemainingEvents()
            }

            val issuingConfig = configOf(issuingManagers.single())
            assertTrue(
                "Expected issuance to use an AttestationBased manager, " +
                    "but it used ${issuingConfig.clientAuthenticationType}",
                issuingConfig.clientAuthenticationType is
                    OpenId4VciManager.ClientAuthenticationType.AttestationBased
            )
        }

    @Test
    fun `Given offer issuance waiting for browser authorization, resumeOpenId4VciWithAuthorization reaches the issuing manager`() =
        coroutineRule.runTest {
            failIssuanceImmediately = false
            resolveAndCacheOffer()

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null).runFlowTest {
                // Wait until the producer has started issuance and is waiting for authorization.
                // Real time: runTest's virtual clock would expire the timeout as soon as the
                // test scheduler is idle, before issuance has had a chance to start.
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5.seconds) { issuanceStarted.await() }
                }
                subject.resumeOpenId4VciWithAuthorization(REDIRECT_URI)
                cancelAndIgnoreRemainingEvents()
            }

            val issuingManager = issuingManagers.single()
            assertTrue(
                "Expected the issuing manager to be AttestationBased",
                configOf(issuingManager).clientAuthenticationType is
                    OpenId4VciManager.ClientAuthenticationType.AttestationBased
            )
            verify(issuingManager).resumeWithAuthorization(REDIRECT_URI)
            createdManagers
                .map { it.second }
                .filter { it !== issuingManager }
                .forEach { verify(it, never()).resumeWithAuthorization(any<String>()) }
        }

    @Test
    fun `Given configured issuer, resolving then issuing the offer with metadata reuses the cached manager`() =
        coroutineRule.runTest {
            offerIssuerUrl = ATTESTATION_ISSUER_URL
            resolveAndCacheOffer()

            subject.issueDocumentsByOfferUri(offerUri = offerUri, txCode = null).runFlowTest {
                assertTrue(awaitItem() is IssueDocumentsPartialState.Failure)
                cancelAndIgnoreRemainingEvents()
            }

            // The browser-auth resume relies on this: a configured issuer must keep
            // its cached manager even when the lookup passes AS metadata.
            val issuingManager = issuingManagers.single()
            assertSame(resolvingManagers.single(), issuingManager)
            assertTrue(
                "Expected no manager beyond the configured ones",
                createdManagers.size == walletCoreConfig.vciConfig.size,
            )
        }

    @Test
    fun `Given offer by reference, resolveDocumentOffer uses the PID issuer manager`() =
        coroutineRule.runTest {
            val offerByReference = "openid-credential-offer://?credential_offer_uri=" +
                URLEncoder.encode("$UNKNOWN_EAA_ISSUER_URL/offer/1", "UTF-8")

            subject.resolveDocumentOffer(offerByReference).runFlowTest {
                assertTrue(awaitItem() is ResolveDocumentOfferPartialState.Success)
                cancelAndIgnoreRemainingEvents()
            }

            val resolvingConfig = configOf(resolvingManagers.single())
            assertTrue(
                "Expected the PID issuer's manager, but it used ${resolvingConfig.clientAuthenticationType}",
                resolvingConfig.clientAuthenticationType ==
                    OpenId4VciManager.ClientAuthenticationType.None(PID_ISSUER_URL),
            )
        }

    private suspend fun resolveAndCacheOffer() {
        subject.resolveDocumentOffer(offerUri).runFlowTest {
            assertTrue(awaitItem() is ResolveDocumentOfferPartialState.Success)
            cancelAndIgnoreRemainingEvents()
        }
        subject.cacheOffer(offerUri, offer)
    }

    private fun configOf(manager: OpenId4VciManager): OpenId4VciManager.Config =
        createdManagers.single { it.second === manager }.first

    private fun newManager(): OpenId4VciManager {
        val manager = mock(OpenId4VciManager::class.java)
        doAnswer { invocation ->
            resolvingManagers += manager
            invocation.getArgument<OpenId4VciManager.OnResolvedOffer>(2)(OfferResult.Success(offer))
            null
        }.whenever(manager).resolveDocumentOffer(
            offerUri = any(),
            executor = anyOrNull(),
            onResolvedOffer = any(),
        )
        doAnswer { invocation ->
            issuingManagers += manager
            issuanceStarted.complete(Unit)
            if (failIssuanceImmediately) {
                invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)(
                    IssueEvent.Failure(IllegalStateException("stubbed issuance failure"))
                )
            }
            null
        }.whenever(manager).issueDocumentByOffer(
            offer = any(),
            txCode = anyOrNull(),
            executor = anyOrNull(),
            onIssueEvent = any(),
        )
        return manager
    }

    private fun configWith(clientAuthenticationType: OpenId4VciManager.ClientAuthenticationType) =
        OpenId4VciManager.Config.Builder()
            .withClientAuthenticationType(clientAuthenticationType)
            .withAuthFlowRedirectionURI("https://example.com/auth")
            .build()

    private companion object {
        const val PID_ISSUER_URL = "https://pid-issuer.example.com"
        const val NONE_ISSUER_URL = "https://issuer.eudiw.dev"
        const val ATTESTATION_ISSUER_URL = "https://wallet-provider.eudiw.dev"
        const val UNKNOWN_EAA_ISSUER_URL = "https://eaa-provider.example.com"
        const val REDIRECT_URI = "${BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK}?code=abc&state=xyz"
    }
}
