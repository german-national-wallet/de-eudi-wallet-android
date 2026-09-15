/*
 * Copyright (c) 2026 European Commission
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

package org.sprind.wallet.presentationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorImpl
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.presentment.CredentialMatchSourceOpenID4VP
import org.multipaz.presentment.CredentialPresentmentSetOptionMemberMatch
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor

/**
 * A verifier may send a Credential Query with no `claims` - OpenID4VP §6.4.1, meaning "prove you
 * hold this credential, disclose nothing selectively disclosable". wallet-core then reports a
 * match whose claim map is empty.
 *
 * Up to wallet-core v0.29.0 the wallet's own DCQL processor silently substituted *all* of the
 * document's claims for such a query, so this case never reached the UI layer. v0.30.0 made it
 * spec-correct, and the request must now travel the normal consent path: treating "no claims" as
 * "nothing was requested" sends the user to the no-document screen and the presentation never
 * happens (the playground's sports-shop / FitLife loyalty card request is exactly this shape).
 */
class PresentationRequestClaimlessQueryTest {

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
    fun `Given a claimless query, When getRequestDocuments is called, Then consent is requested rather than NoData`() =
        coroutineRule.runTest {
            val document = loyaltyCardDocument()
            whenever(walletCoreDocumentsController.getAllIssuedDocuments())
                .thenReturn(listOf(document))
            events.emit(requestReceived(claimlessMatch()))

            val state = interactor.getRequestDocuments().firstState()

            assertTrue(
                "A claimless query must reach the consent screen, got $state",
                state is PresentationRequestInteractorPartialState.Success,
            )
            val success = state as PresentationRequestInteractorPartialState.Success
            assertEquals(1, success.requestDocuments.size)

            val item = success.requestDocuments.single()
            assertEquals(DOCUMENT_ID, item.domainPayload.docId)
            assertEquals(LOYALTY_CARD_NAME, item.domainPayload.docName)
            assertTrue(
                "A claimless query must not produce claim rows",
                item.expandedUiItems.isEmpty(),
            )
            assertEquals(0, item.requestedClaimsCount)
        }

    @Test
    fun `Given no match at all, When getRequestDocuments is called, Then NoData is reported`() =
        coroutineRule.runTest {
            events.emit(requestReceived())

            val state = interactor.getRequestDocuments().firstState()
            assertTrue(
                "A request nothing in the wallet can satisfy is the real NoData case, got $state",
                state is PresentationRequestInteractorPartialState.NoData,
            )
        }

    private suspend fun Flow<PresentationRequestInteractorPartialState>.firstState() = first()

    private fun requestReceived(
        vararg matches: CredentialPresentmentSetOptionMemberMatch,
    ) = TransferEventPartialState.RequestReceived(
        requestData = matches.toList(),
        verifierName = VERIFIER_NAME,
        verifierIsTrusted = true,
    )

    /** A match reporting an empty claim map, as wallet-core does for a query without `claims`. */
    private fun claimlessMatch(): CredentialPresentmentSetOptionMemberMatch {
        val document = mock<Document> {
            on { identifier } doReturn DOCUMENT_ID
        }
        val credential = mock<Credential> {
            on { this.document } doReturn document
        }
        return CredentialPresentmentSetOptionMemberMatch(
            credential = credential,
            claims = emptyMap(),
            // The consent path never reads the source; only the claim map and the document matter.
            source = mock<CredentialMatchSourceOpenID4VP>(),
            transactionData = emptyList(),
        )
    }

    private fun loyaltyCardDocument(): IssuedDocument {
        val data = mock<SdJwtVcData> {
            on { claims } doReturn emptyList()
        }
        return mock<IssuedDocument> {
            on { id } doReturn DOCUMENT_ID
            on { name } doReturn LOYALTY_CARD_NAME
            on { format } doReturn SdJwtVcFormat(LOYALTY_CARD_VCT)
            on { this.data } doReturn data
            on { issuerMetadata } doReturn null
            onBlocking { credentialsCount() } doReturn 1
        }
    }

    private companion object {
        const val DOCUMENT_ID = "fitlife-membership-id"
        const val LOYALTY_CARD_NAME = "FitLife Membership"
        const val LOYALTY_CARD_VCT = "urn:eudi:eaa:loyalty-card:1"
        const val VERIFIER_NAME = "Sports Shop"
    }
}
