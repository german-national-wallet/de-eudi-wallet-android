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

package org.sprind.wallet.dashboardfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.util.formatInstantToDateString
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.eudi.wallet.document.format.MsoMdocData
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.copy
import eu.europa.ec.testfeature.createMockedNamespaceData
import eu.europa.ec.testfeature.getMockedMdlWithBasicFields
import eu.europa.ec.testfeature.getMockedMdlWithNoExpirationDate
import eu.europa.ec.testfeature.mockedDocumentCreationDate
import eu.europa.ec.testfeature.mockedDocumentValidUntilDate
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testfeature.mockedMdlBasicFields
import eu.europa.ec.testfeature.mockedMdlDocName
import eu.europa.ec.testfeature.mockedMdlDocType
import eu.europa.ec.testfeature.mockedMdlId
import eu.europa.ec.testfeature.mockedMdlNameSpace
import eu.europa.ec.testfeature.mockedMsoMdocMdlFormat
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import java.time.Instant
import java.util.Locale

class DashboardDocumentDetailInteractorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor

    private lateinit var closeable: AutoCloseable
    private lateinit var interactor: DashboardDocumentDetailInteractor

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)

        interactor = DashboardDocumentDetailInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            resourceProvider = resourceProvider,
            logController = logController,
            rwscaInteractor = rwscaInteractor,
        )
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `Given mDL with full ISO-8601 expiry_date, When getDocumentDetail, Then Success with formatted validTill`() {
        coroutineRule.runTest {
            val document = getMockedMdlWithBasicFields()
            val locale = Locale.ENGLISH
            whenever(resourceProvider.getLocale()).thenReturn(locale)
            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(document)

            interactor.getDocumentDetail(mockedMdlId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                        documentType = mockedMdlDocName,
                        createdOn = Instant.parse(mockedDocumentCreationDate)
                            .formatInstantToDateString(locale = locale),
                        validTill = "30 Mar 2050",
                        physicalDocumentName = mockedMdlDocName,
                        topBarBackgroundColor = null,
                        topBarBackgroundImageUri = null,
                        topBarTextColor = null,
                        eaaCardData = expectedMdlEaaCardData(),
                    ),
                    result,
                )
            }
        }
    }

    @Test
    fun `Given mDL with date-only expiry_date, When getDocumentDetail, Then Success with formatted validTill`() {
        coroutineRule.runTest {
            val document = getMockedMdlWithBasicFields().copy(
                data = MsoMdocData(
                    format = mockedMsoMdocMdlFormat,
                    issuerMetadata = null,
                    nameSpacedData = createMockedNamespaceData(
                        mockedMdlNameSpace,
                        mockedMdlBasicFields + ("expiry_date" to tdateBytes("2027-12-31")),
                    ),
                ),
            )
            val locale = Locale.ENGLISH
            whenever(resourceProvider.getLocale()).thenReturn(locale)
            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(document)

            interactor.getDocumentDetail(mockedMdlId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                        documentType = mockedMdlDocName,
                        createdOn = Instant.parse(mockedDocumentCreationDate)
                            .formatInstantToDateString(locale = locale),
                        validTill = "31 Dec 2027",
                        physicalDocumentName = mockedMdlDocName,
                        topBarBackgroundColor = null,
                        topBarBackgroundImageUri = null,
                        topBarTextColor = null,
                        eaaCardData = expectedMdlEaaCardData(),
                    ),
                    result,
                )
            }
        }
    }

    @Test
    fun `Given mDL with no expiry_date, When getDocumentDetail, Then Success with validTill is dash`() {
        coroutineRule.runTest {
            val document = getMockedMdlWithNoExpirationDate()
            val locale = Locale.ENGLISH
            whenever(resourceProvider.getLocale()).thenReturn(locale)
            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(document)

            interactor.getDocumentDetail(mockedMdlId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                        documentType = mockedMdlDocName,
                        createdOn = Instant.parse(mockedDocumentCreationDate)
                            .formatInstantToDateString(locale = locale),
                        validTill = "-",
                        physicalDocumentName = mockedMdlDocName,
                        topBarBackgroundColor = null,
                        topBarBackgroundImageUri = null,
                        topBarTextColor = null,
                        eaaCardData = expectedMdlEaaCardData(),
                    ),
                    result,
                )
            }
        }
    }

    @Test
    fun `Given mDL with unparseable expiry_date, When getDocumentDetail, Then Success with validTill is dash`() {
        coroutineRule.runTest {
            val document = getMockedMdlWithBasicFields().copy(
                data = MsoMdocData(
                    format = mockedMsoMdocMdlFormat,
                    issuerMetadata = null,
                    nameSpacedData = createMockedNamespaceData(
                        mockedMdlNameSpace,
                        mockedMdlBasicFields + ("expiry_date" to tdateBytes("not-a-date")),
                    ),
                ),
            )
            val locale = Locale.ENGLISH
            whenever(resourceProvider.getLocale()).thenReturn(locale)
            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(document)

            interactor.getDocumentDetail(mockedMdlId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                        documentType = mockedMdlDocName,
                        createdOn = Instant.parse(mockedDocumentCreationDate)
                            .formatInstantToDateString(locale = locale),
                        validTill = "-",
                        physicalDocumentName = mockedMdlDocName,
                        topBarBackgroundColor = null,
                        topBarBackgroundImageUri = null,
                        topBarTextColor = null,
                        eaaCardData = expectedMdlEaaCardData(),
                    ),
                    result,
                )
            }
        }
    }

    @Test
    fun `Given document not found, When getDocumentDetail, Then Failure`() {
        coroutineRule.runTest {
            val docId = "missing-doc-id"
            whenever(walletCoreDocumentsController.getDocumentById(docId))
                .thenReturn(null)

            interactor.getDocumentDetail(docId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Failure("Document not found"),
                    result,
                )
            }
        }
    }

    @Test
    fun `Given mDL with German locale, When getDocumentDetail, Then validTill uses German numeric format`() {
        coroutineRule.runTest {
            val document = getMockedMdlWithBasicFields()
            val locale = Locale.GERMANY
            whenever(resourceProvider.getLocale()).thenReturn(locale)
            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(document)

            interactor.getDocumentDetail(mockedMdlId).runFlowTest {
                val result = awaitItem()
                assertEquals(
                    DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                        documentType = mockedMdlDocName,
                        createdOn = Instant.parse(mockedDocumentCreationDate)
                            .formatInstantToDateString(locale = locale),
                        validTill = "30.03.2050",
                        physicalDocumentName = mockedMdlDocName,
                        topBarBackgroundColor = null,
                        topBarBackgroundImageUri = null,
                        topBarTextColor = null,
                        eaaCardData = expectedMdlEaaCardData(),
                    ),
                    result,
                )
            }
        }
    }

    private fun tdateBytes(value: String): ByteArray {
        val length = value.length
        require(length < 24) { "CBOR inline text string length must be < 24" }
        val textStringHeader = (0x60 or length).toByte()
        return byteArrayOf(-64, textStringHeader) + value.map { it.code.toByte() }.toByteArray()
    }

    private fun expectedMdlEaaCardData(): EaaCardData = EaaCardData(
        id = mockedMdlId,
        description = mockedMdlDocType,
        name = mockedMdlDocType,
        backgroundColor = null,
        backgroundImageUri = null,
        logoUri = null,
        validityDate = Instant.parse(mockedDocumentValidUntilDate),
    )
}