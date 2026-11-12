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
import eu.europa.ec.corelogic.controller.RequestedCredentialSet
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorImpl
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
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
import org.multipaz.credential.SecureAreaBoundCredential
import org.sprind.wallet.corelogic.model.RelyingPartyRegistrationStatus
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A request nothing in the wallet can satisfy is told apart by whether the wallet holds the
 * requested credentials at all, as the iOS wallet does (WD-4430): a missing credential sends the
 * user to add one, a credential that lacks the requested claims is the request's fault. Unlike
 * iOS, the outcome honours `credential_sets` alternatives and does not depend on query order.
 */
class PresentationRequestUnmatchedRequestTest {

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
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `a held credential that lacks the requested claims makes the request unsatisfiable`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `an sd-jwt credential is recognised by any of the accepted vcts`() =
        coroutineRule.runTest {
            holding(document(SdJwtVcFormat(PID_VCT)))
            events.emit(unmatched(allOf(setOf("urn:other", PID_VCT))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a requested credential type the wallet does not hold is reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(MDL_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `a missing credential is reported as missing whatever the query order`() =
        // The iOS wallet reports whichever query fails first; here the outcome must not depend on it.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))

            events.emit(unmatched(allOf(setOf(PID_DOCTYPE), setOf(MDL_DOCTYPE))))
            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())

            events.emit(unmatched(allOf(setOf(MDL_DOCTYPE), setOf(PID_DOCTYPE))))
            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `a held alternative makes the request unsatisfiable rather than missing a credential`() =
        // "PID as mdoc or as SD-JWT": holding the mdoc one means the user has a PID; it only lacks
        // the claims, so telling them to add a credential would be wrong.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_VCT)), listOf(setOf(PID_DOCTYPE))),
                        )
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a required set with no held option is reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_DOCTYPE))),
                        ),
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(MDL_DOCTYPE)), listOf(setOf(PID_VCT))),
                        ),
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `an optional set the wallet cannot serve does not count as a missing credential`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_DOCTYPE))),
                        ),
                        RequestedCredentialSet(
                            required = false,
                            options = listOf(listOf(setOf(MDL_DOCTYPE))),
                        ),
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a request without credential types keeps the missing-credential outcome`() =
        // Proximity requests are not DCQL, so nothing tells the wallet what type was asked for.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(requestedCredentialSets = null))

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `a required PID the wallet does not hold is reported as a missing PID`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(MDL_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertTrue(state.missingPid)
        }

    @Test
    fun `a PID required by every alternative is reported as a missing PID`() =
        // "PID as mdoc or as SD-JWT": whichever option the verifier gets, it needs a PID.
        coroutineRule.runTest {
            holding()
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(DE_PID_VCT)), listOf(setOf(PID_DOCTYPE))),
                        )
                    )
                )
            )

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertTrue(state.missingPid)
        }

    @Test
    fun `a request that can do without a PID is not reported as a missing PID`() =
        coroutineRule.runTest {
            holding()
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_DOCTYPE)), listOf(setOf(MDL_DOCTYPE))),
                        ),
                        RequestedCredentialSet(
                            required = false,
                            options = listOf(listOf(setOf(DE_PID_VCT))),
                        ),
                    )
                )
            )

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertFalse(state.missingPid)
        }

    @Test
    fun `a held PID is not reported as missing when another credential is`() =
        coroutineRule.runTest {
            holding(document(SdJwtVcFormat(DE_PID_VCT)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE), setOf(MDL_DOCTYPE))))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertFalse(state.missingPid)
        }

    @Test
    fun `a PID is not reported as missing when another required credential is missing too`() =
        // Adding the Digital ID alone would not satisfy the request (WD-4759 review).
        coroutineRule.runTest {
            holding()
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE), setOf(MDL_DOCTYPE))))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertFalse(state.missingPid)
        }

    @Test
    fun `a PID is reported as missing when the wallet holds everything else the request needs`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(MDL_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(DE_PID_VCT), setOf(MDL_DOCTYPE))))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertTrue(state.missingPid)
        }

    @Test
    fun `a request without credential types is not reported as a missing PID`() =
        coroutineRule.runTest {
            holding()
            events.emit(unmatched(requestedCredentialSets = null))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertFalse(state.missingPid)
        }

    @Test
    fun `an expired PID is reported as a missing PID`() =
        // The wallet never matches an expired credential, so the request arrives unmatched.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE), validUntil = listOf(PAST)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            val state = assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
            assertTrue(state.missingPid)
        }

    @Test
    fun `a PID with a credential still valid is not reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE), validUntil = listOf(PAST, FUTURE)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `an expired PID is not reported as missing when a valid PID is held too`() =
        coroutineRule.runTest {
            holding(
                document(MsoMdocFormat(PID_DOCTYPE), validUntil = listOf(PAST)),
                document(SdJwtVcFormat(DE_PID_VCT)),
            )
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a PID without credentials is not reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE), validUntil = emptyList()))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    private suspend fun firstState() = interactor.getRequestDocuments().first()

    private fun holding(vararg documents: IssuedDocument) {
        whenever(walletCoreDocumentsController.getAllIssuedDocuments()).thenReturn(documents.toList())
    }

    /** A document whose credentials expire at [validUntil]. */
    private suspend fun document(
        format: DocumentFormat,
        validUntil: List<Instant> = listOf(FUTURE),
    ): IssuedDocument {
        val document = mock<IssuedDocument> {
            on { this.format } doReturn format
        }
        val credentials = validUntil.map { instant ->
            mock<SecureAreaBoundCredential> { on { this.validUntil } doReturn instant }
        }
        whenever(document.getCredentials()).thenReturn(credentials)
        return document
    }

    /** A request without `credential_sets`: every query is required. */
    private fun allOf(vararg queryTypes: Set<String>) =
        listOf(RequestedCredentialSet(required = true, options = listOf(queryTypes.toList())))

    private fun unmatched(requestedCredentialSets: List<RequestedCredentialSet>?) =
        TransferEventPartialState.RequestReceived(
            requestData = emptyList(),
            verifierName = "Verifier",
            verifierIsTrusted = true,
            relyingPartyRegistration = RelyingPartyRegistrationStatus.NotEvaluated,
            requestedCredentialSets = requestedCredentialSets,
        )

    private companion object {
        const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        const val PID_VCT = "urn:eudi:pid:1"
        const val DE_PID_VCT = "urn:eudi:pid:de:1"
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        val PAST: Instant = Instant.parse("2020-01-01T00:00:00Z")
        val FUTURE: Instant = Instant.parse("2100-01-01T00:00:00Z")
    }
}
