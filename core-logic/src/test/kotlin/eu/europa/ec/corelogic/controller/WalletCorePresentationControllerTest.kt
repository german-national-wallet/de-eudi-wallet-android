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

package eu.europa.ec.corelogic.controller

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.model.DisclosedDocumentDomain
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.Request
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.Response
import eu.europa.ec.eudi.iso18013.transfer.response.ResponseResult
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.multipaz.cbor.Tstr
import org.multipaz.claim.Claim
import org.multipaz.claim.MdocClaim
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.document.Document
import org.multipaz.presentment.CredentialMatchSourceOpenID4VP
import org.multipaz.presentment.CredentialPresentmentData
import org.multipaz.presentment.CredentialPresentmentSelection
import org.multipaz.presentment.CredentialPresentmentSetOptionMemberMatch
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim
import org.multipaz.request.Requester
import org.multipaz.securearea.AndroidKeystoreKeyInfo
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.KeyUnlockData
import org.multipaz.securearea.SecureArea

/**
 * Covers the two things the controller does between a received request and the response: deciding
 * which credentials need a device-authentication prompt, and narrowing the presentment selection
 * to what the user consented to.
 *
 * Both moved off `DisclosedDocument`/`DocumentId` with wallet-core v0.30.0 — consent is now a
 * [CredentialPresentmentSelection] the controller narrows and hands to `generateResponse`, and key
 * unlock is per `Credential`, not per document — so every test here drives the real listener path:
 * a selection only ever reaches the controller through a `TransferEvent.RequestReceived`.
 */
class WalletCorePresentationControllerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var eudiWallet: EudiWallet

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var logController: LogController

    private lateinit var closeable: AutoCloseable

    /**
     * Completed with the listener the controller registers with the wallet. `events` runs its
     * upstream on [kotlinx.coroutines.Dispatchers.IO] (`safeAsync`), so registration happens on a
     * real thread that the test scheduler cannot advance — the tests await this instead.
     */
    private val registeredListener = CompletableDeferred<TransferEvent.Listener>()

    private val subject: WalletCorePresentationControllerImpl by lazy {
        WalletCorePresentationControllerImpl(
            eudiWallet = eudiWallet,
            resourceProvider = resourceProvider,
            logController = logController,
            dispatcher = coroutineRule.testDispatcher,
        )
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        doAnswer { invocation ->
            registeredListener.complete(invocation.getArgument(0))
            invocation.mock
        }.whenever(eudiWallet).addTransferEventListener(any())
        subject.setConfig(PresentationControllerConfig.Ble(initiator = "route"))
    }

    @After
    fun after() {
        unmockkObject(DocumentExtensions)
        closeable.close()
    }

    @Test
    fun `checkForKeyUnlock asks for authentication when the credential's keystore key requires it`() =
        coroutineRule.runTest {
            val credential = androidKeystoreCredential(userAuthenticationRequired = true)
            receiveRequest(selectionOf(match(credential)))
            subject.updateRequestedDocuments(mutableListOf(consentTo(DOCUMENT_ID)))

            val result = subject.checkForKeyUnlock().first()

            assertTrue(
                "Expected an authentication request but got $result",
                result is CheckKeyUnlockPartialState.UserAuthenticationRequired,
            )
            assertEquals(
                1,
                (result as CheckKeyUnlockPartialState.UserAuthenticationRequired)
                    .authenticationData.size,
            )
        }

    @Test
    fun `checkForKeyUnlock is ready to send when the keystore key needs no authentication`() =
        coroutineRule.runTest {
            val credential = androidKeystoreCredential(userAuthenticationRequired = false)
            receiveRequest(selectionOf(match(credential)))
            subject.updateRequestedDocuments(mutableListOf(consentTo(DOCUMENT_ID)))

            val result = subject.checkForKeyUnlock().first()

            assertEquals(CheckKeyUnlockPartialState.RequestIsReadyToBeSent, result)
        }

    @Test
    fun `checkForKeyUnlock is ready to send for a credential outside the Android Keystore`() =
        // rWSCA credentials are not secure-area bound here and run their own unlock flow; the
        // Android prompt must not be raised for them.
        coroutineRule.runTest {
            val credential = plainCredential(DOCUMENT_ID, CREDENTIAL_ID)
            receiveRequest(selectionOf(match(credential)))
            subject.updateRequestedDocuments(mutableListOf(consentTo(DOCUMENT_ID)))

            val result = subject.checkForKeyUnlock().first()

            assertEquals(CheckKeyUnlockPartialState.RequestIsReadyToBeSent, result)
        }

    @Test
    fun `checkForKeyUnlock prompts once for a credential matched more than once`() =
        // A credential can satisfy several members of the same request; prompting per match would
        // show the user the same device-authentication dialog twice.
        coroutineRule.runTest {
            val credential = androidKeystoreCredential(userAuthenticationRequired = true)
            receiveRequest(selectionOf(match(credential), match(credential)))
            subject.updateRequestedDocuments(mutableListOf(consentTo(DOCUMENT_ID)))

            val result = subject.checkForKeyUnlock().first()

            assertEquals(
                1,
                (result as CheckKeyUnlockPartialState.UserAuthenticationRequired)
                    .authenticationData.size,
            )
        }

    @Test
    fun `checkForKeyUnlock emits nothing before consent has been reported`() =
        // Nothing may be disclosed - not even a key unlock prompt - until the consent UI reports a
        // selection.
        coroutineRule.runTest {
            receiveRequest(selectionOf(match(androidKeystoreCredential(true))))

            assertEquals(emptyList<CheckKeyUnlockPartialState>(), subject.checkForKeyUnlock().toList())
        }

    @Test
    fun `the sent selection carries only the consented claims`() =
        coroutineRule.runTest {
            val credential = plainCredential(DOCUMENT_ID, CREDENTIAL_ID)
            val kept = requestedClaim("family_name")
            val withheld = requestedClaim("birth_date")
            val processed = receiveRequest(
                selectionOf(match(credential, kept, withheld))
            )
            subject.updateRequestedDocuments(
                mutableListOf(consentTo(DOCUMENT_ID, kept))
            )

            subject.sendRequestedDocuments()

            assertEquals(
                listOf(kept),
                processed.generatedFrom?.matches?.single()?.claims?.keys?.toList(),
            )
        }

    @Test
    fun `a document the user did not consent to is dropped from the sent selection`() =
        coroutineRule.runTest {
            val consented = plainCredential(DOCUMENT_ID, CREDENTIAL_ID)
            val refused = plainCredential(OTHER_DOCUMENT_ID, OTHER_CREDENTIAL_ID)
            val claim = requestedClaim("family_name")
            val processed = receiveRequest(
                selectionOf(match(consented, claim), match(refused, claim))
            )
            subject.updateRequestedDocuments(
                mutableListOf(consentTo(DOCUMENT_ID, claim))
            )

            subject.sendRequestedDocuments()

            assertEquals(
                listOf(DOCUMENT_ID),
                processed.generatedFrom?.matches?.map { it.credential.document.identifier },
            )
        }

    @Test
    fun `a match that requests no claims survives narrowing`() =
        // OpenID4VP 6.4.1: a Credential Query without `claims` asks for a presentation with no
        // selectively disclosable claim. There is nothing to narrow, and dropping it would break
        // the verifier's credential_sets.
        coroutineRule.runTest {
            val credential = plainCredential(DOCUMENT_ID, CREDENTIAL_ID)
            val processed = receiveRequest(selectionOf(match(credential)))
            subject.updateRequestedDocuments(mutableListOf(consentTo(DOCUMENT_ID)))

            subject.sendRequestedDocuments()

            assertEquals(1, processed.generatedFrom?.matches?.size)
        }

    @Test
    fun `sendRequestedDocuments fails while no consent has been reported`() =
        coroutineRule.runTest {
            val processed = receiveRequest(
                selectionOf(match(plainCredential(DOCUMENT_ID, CREDENTIAL_ID)))
            )

            val result = subject.sendRequestedDocuments()

            assertTrue(
                "Expected a failure but got $result",
                result is SendRequestedDocumentsPartialState.Failure,
            )
            assertEquals(null, processed.generatedFrom)
        }

    /**
     * Subscribes to [WalletCorePresentationControllerImpl.events] so the controller registers its
     * listener with the wallet, then delivers [selection] through it the way a real transfer does.
     */
    private suspend fun CoroutineScope.receiveRequest(
        selection: CredentialPresentmentSelection,
    ): FakeProcessedRequest {
        val collector = launch { subject.events.collect { } }
        val listener = registeredListener.await()

        // The controller records the selection synchronously while handling the event, so it is in
        // place the moment this returns.
        val processed = FakeProcessedRequest(selection)
        listener.onTransferEvent(TransferEvent.RequestReceived(processed, FakeRequest))

        // The shared flow was started lazily and stays started, so the controller keeps the
        // request; only this test's collection of it ends here.
        collector.cancel()
        return processed
    }

    private fun selectionOf(vararg matches: CredentialPresentmentSetOptionMemberMatch) =
        CredentialPresentmentSelection(matches = matches.toList())

    private fun match(
        credential: Credential,
        vararg claims: RequestedClaim,
    ) = CredentialPresentmentSetOptionMemberMatch(
        credential = credential,
        claims = claims.associateWith { claimValue() },
        // Narrowing and key unlock never read the source; only the credential and claims matter.
        source = mock<CredentialMatchSourceOpenID4VP>(),
        transactionData = emptyList(),
    )

    private fun consentTo(documentId: String, vararg claims: RequestedClaim) =
        DisclosedDocumentDomain(documentId = documentId, disclosedClaims = claims.toSet())

    private fun requestedClaim(dataElementName: String): RequestedClaim = MdocRequestedClaim(
        id = null,
        docType = DOC_TYPE,
        namespaceName = NAMESPACE,
        dataElementName = dataElementName,
        intentToRetain = false,
        values = JsonArray(emptyList()),
    )

    private fun claimValue(): Claim = MdocClaim(
        displayName = "Claim",
        attribute = null,
        docType = DOC_TYPE,
        namespaceName = NAMESPACE,
        dataElementName = "claim",
        value = Tstr("value"),
    )

    private fun plainCredential(documentId: String, credentialId: String): Credential {
        val document = mock<Document> { on { identifier } doReturn documentId }
        return mock<Credential> {
            on { this.document } doReturn document
            on { identifier } doReturn credentialId
        }
    }

    /** A credential whose key lives in the Android Keystore, as a PID or EAA credential does. */
    private fun androidKeystoreCredential(
        userAuthenticationRequired: Boolean,
    ): SecureAreaBoundCredential {
        val document = mock<Document> { on { identifier } doReturn DOCUMENT_ID }
        val secureArea = mockk<AndroidKeystoreSecureArea>()
        val keyInfo = mockk<AndroidKeystoreKeyInfo>()
        coEvery { secureArea.getKeyInfo(KEY_ALIAS) } returns keyInfo
        every { keyInfo.isUserAuthenticationRequired } returns userAuthenticationRequired

        if (userAuthenticationRequired) {
            val unlockData = mockk<AndroidKeystoreKeyUnlockData>()
            coEvery { unlockData.getCryptoObjectForSigning() } returns null
            mockkObject(DocumentExtensions)
            every {
                DocumentExtensions.getDefaultKeyUnlockData(secureArea as SecureArea, KEY_ALIAS)
            } returns unlockData
        }

        return mock<SecureAreaBoundCredential> {
            on { this.document } doReturn document
            on { identifier } doReturn CREDENTIAL_ID
            on { this.secureArea } doReturn secureArea
            on { alias } doReturn KEY_ALIAS
        }
    }

    /** The raw request is only carried through the event; nothing under test reads it. */
    private object FakeRequest : Request

    /**
     * Stands in for the transport's `ProcessedRequest.Success`, recording the selection the
     * controller hands to `generateResponse` — that narrowed selection is what actually gets sent.
     */
    private class FakeProcessedRequest(
        selection: CredentialPresentmentSelection,
    ) : RequestProcessor.ProcessedRequest.Success(
        presentmentData = CredentialPresentmentData(credentialSets = emptyList()),
        requester = Requester(),
        trustMetadata = null,
    ) {
        override val presentmentSelections: List<CredentialPresentmentSelection> =
            listOf(selection)

        var generatedFrom: CredentialPresentmentSelection? = null
            private set

        var unlockDataUsed: Map<String, KeyUnlockData> = emptyMap()
            private set

        override suspend fun generateResponse(
            selection: CredentialPresentmentSelection,
            keyUnlockData: Map<String, KeyUnlockData>,
        ): ResponseResult {
            generatedFrom = selection
            unlockDataUsed = keyUnlockData
            return ResponseResult.Success(FakeResponse)
        }

        private object FakeResponse : Response
    }

    private companion object {
        const val DOCUMENT_ID = "pid-document"
        const val OTHER_DOCUMENT_ID = "eaa-document"
        const val CREDENTIAL_ID = "pid-credential"
        const val OTHER_CREDENTIAL_ID = "eaa-credential"
        const val KEY_ALIAS = "android-key-alias"
        const val DOC_TYPE = "eu.europa.ec.eudi.pid.1"
        const val NAMESPACE = "eu.europa.ec.eudi.pid.1"
    }
}
