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

package org.sprind.wallet.presentationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.eudi.wallet.registration.RegistrationFailureReason
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorImpl
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.presentationfeature.interactor.RequestRejectionReason
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.multipaz.presentment.CredentialPresentmentSetOptionMemberMatch
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.corelogic.model.OverAskedClaimDomain
import org.sprind.wallet.corelogic.model.RelyingPartyRegistrationStatus

/** No documents are stubbed: a rejected request must not reach them. */
class PresentationRequestOverAskingTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var walletCorePresentationController: WalletCorePresentationController

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor

    private lateinit var interactor: PresentationRequestInteractor
    private lateinit var closeable: AutoCloseable

    private val events = MutableSharedFlow<TransferEventPartialState>(replay = 1)

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        whenever(walletCorePresentationController.events).thenReturn(events)
        interactor = PresentationRequestInteractorImpl(
            resourceProvider = resourceProvider,
            walletCorePresentationController = walletCorePresentationController,
            walletCoreDocumentsController = walletCoreDocumentsController,
            walletPinUnBlockTimeStorageController = walletPinUnBlockTimeStorageController,
            logController = logController,
            rwscaInteractor = rwscaInteractor,
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given an over-asking relying party, When getRequestDocuments is called, Then the request is rejected`() =
        coroutineRule.runTest {
            events.emit(
                requestReceived(
                    RelyingPartyRegistrationStatus.OverAsking(
                        overAskedClaims = listOf(
                            OverAskedClaimDomain(format = SD_JWT_VC, path = "family_name"),
                        ),
                    )
                )
            )

            val state = interactor.getRequestDocuments().first()

            val rejected = state as? PresentationRequestInteractorPartialState.Rejected
            assertTrue("Over-asking must be rejected, got $state", rejected != null)
            assertEquals(RequestRejectionReason.OVER_ASKING, rejected!!.reason)
            assertEquals(VERIFIER_NAME, rejected.verifierName)
        }

    @Test
    fun `Given an over-asking relying party, When getRequestDocuments is called, Then the wallet's documents are never read`() =
        coroutineRule.runTest {
            events.emit(
                requestReceived(
                    RelyingPartyRegistrationStatus.OverAsking(
                        overAskedClaims = listOf(
                            OverAskedClaimDomain(format = SD_JWT_VC, path = "family_name"),
                        ),
                    )
                )
            )

            interactor.getRequestDocuments().first()

            verify(walletCoreDocumentsController, never()).getAllIssuedDocuments()
        }

    @Test
    fun `Given an over-asking relying party, When getRequestDocuments is called, Then it does not stop the presentation itself`() =
        coroutineRule.runTest {
            events.emit(
                requestReceived(
                    RelyingPartyRegistrationStatus.OverAsking(
                        overAskedClaims = listOf(
                            OverAskedClaimDomain(format = SD_JWT_VC, path = "family_name"),
                        ),
                    )
                )
            )

            interactor.getRequestDocuments().first()

            // The ViewModel stops the presentation once the state is collected.
            verify(walletCorePresentationController, never()).stopPresentation()
        }

    @Test
    fun `Given an over-asking certificate naming an authority, When rejected, Then its uri is reported`() =
        coroutineRule.runTest {
            events.emit(
                requestReceived(
                    RelyingPartyRegistrationStatus.OverAsking(
                        overAskedClaims = listOf(
                            OverAskedClaimDomain(format = SD_JWT_VC, path = "family_name"),
                        ),
                        supervisoryAuthorityUri = AUTHORITY_URI,
                    )
                )
            )

            val rejected = interactor.getRequestDocuments().first()
                    as PresentationRequestInteractorPartialState.Rejected

            assertEquals(AUTHORITY_URI, rejected.supervisoryAuthorityUri)
        }

    @Test
    fun `Given a certificate that failed validation, When getRequestDocuments is called, Then the request is rejected as not registered`() =
        coroutineRule.runTest {
            events.emit(
                requestReceived(
                    RelyingPartyRegistrationStatus.NotRegistered(
                        RegistrationFailureReason.CERTIFICATE_ABSENT,
                    )
                )
            )

            val state = interactor.getRequestDocuments().first()

            val rejected = state as? PresentationRequestInteractorPartialState.Rejected
            assertTrue("A failed validation must be rejected, got $state", rejected != null)
            assertEquals(RequestRejectionReason.NOT_REGISTERED, rejected!!.reason)
            assertNull(rejected.supervisoryAuthorityUri)
            assertFalse(rejected.verifierAnswered)
        }

    @Test
    fun `Given openid4vp refused the request for its certificate, When getRequestDocuments is called, Then it is rejected as not registered and already answered`() =
        coroutineRule.runTest {
            events.emit(
                TransferEventPartialState.RelyingPartyNotRegistered(
                    RelyingPartyRegistrationStatus.NotRegistered(
                        RegistrationFailureReason.CERTIFICATE_ABSENT,
                    )
                )
            )

            val state = interactor.getRequestDocuments().first()

            val rejected = state as? PresentationRequestInteractorPartialState.Rejected
            assertTrue("A refused certificate must be rejected, got $state", rejected != null)
            assertEquals(RequestRejectionReason.NOT_REGISTERED, rejected!!.reason)
            assertNull(rejected.supervisoryAuthorityUri)
            assertTrue(rejected.verifierAnswered)
        }

    @Test
    fun `Given a registration within scope, When getRequestDocuments is called, Then the request is not rejected`() =
        coroutineRule.runTest {
            whenever(walletCoreDocumentsController.getAllIssuedDocuments()).thenReturn(emptyList())
            events.emit(requestReceived(RelyingPartyRegistrationStatus.WithinRegisteredScope()))

            val state = interactor.getRequestDocuments().first()

            assertTrue(
                "An in-scope registration must not be rejected, got $state",
                state !is PresentationRequestInteractorPartialState.Rejected,
            )
        }

    @Test
    fun `Given the policy is disabled, When getRequestDocuments is called, Then the request is not rejected`() =
        coroutineRule.runTest {
            whenever(walletCoreDocumentsController.getAllIssuedDocuments()).thenReturn(emptyList())
            events.emit(requestReceived(RelyingPartyRegistrationStatus.NotEvaluated))

            val state = interactor.getRequestDocuments().first()

            assertTrue(
                "NotEvaluated must not be read as a failed check, got $state",
                state !is PresentationRequestInteractorPartialState.Rejected,
            )
        }

    private fun requestReceived(
        relyingPartyRegistration: RelyingPartyRegistrationStatus,
        vararg matches: CredentialPresentmentSetOptionMemberMatch,
    ) = TransferEventPartialState.RequestReceived(
        requestData = matches.toList(),
        verifierName = VERIFIER_NAME,
        verifierIsTrusted = true,
        relyingPartyRegistration = relyingPartyRegistration,
    )

    private companion object {
        const val VERIFIER_NAME = "Example Relying Party"
        const val SD_JWT_VC = "dc+sd-jwt"
        const val AUTHORITY_URI = "https://authority.example.invalid/complaint"
    }
}
