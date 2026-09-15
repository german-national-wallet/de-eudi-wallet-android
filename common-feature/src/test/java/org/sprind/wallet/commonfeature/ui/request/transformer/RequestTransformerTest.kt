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

package org.sprind.wallet.commonfeature.ui.request.transformer

import eu.europa.ec.commonfeature.ui.request.model.DocumentPayloadDomain
import eu.europa.ec.commonfeature.ui.request.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.model.ExpandedUiItem
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.commonfeature.util.generateUniqueFieldId
import eu.europa.ec.corelogic.extension.toNamespacedPath
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.multipaz.request.JsonRequestedClaim
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim

private const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
private const val PID_VCT = "urn:eudi:pid:de:1"

class RequestTransformerTest {

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    private val transformer = RequestTransformer()

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(R.string.request_collapsed_supporting_text))
            .thenReturn("View details")
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `transformToUiItems exposes requested and total claim counts for each document`() {
        val pid = documentPayloadDomain(
            docName = "PID",
            docId = "pid-id",
            documentType = DocumentType.PID,
            requestedClaimNames = listOf("Family name", "Given name"),
            totalClaimsCount = 10,
        )
        val eaa = documentPayloadDomain(
            docName = "EAA",
            docId = "eaa-id",
            documentType = DocumentType.EAA,
            requestedClaimNames = listOf("Degree"),
            totalClaimsCount = 4,
        )

        val actual = transformer.transformToUiItems(
            documentsDomain = listOf(pid, eaa),
            resourceProvider = resourceProvider,
        )

        // PID
        assertEquals(2, actual.size)
        assertEquals(2, actual[0].requestedClaimsCount)
        assertEquals(10, actual[0].totalClaimsCount)
        assertEquals(2, actual[0].expandedUiItems.size)
        // EAA
        assertEquals(1, actual[1].requestedClaimsCount)
        assertEquals(4, actual[1].totalClaimsCount)
        assertEquals(1, actual[1].expandedUiItems.size)
    }

    @Test
    fun `createDisclosedDocuments resolves SD-JWT claims by identity not by value`() {
        // German PID: both `address.country` and `issuing_country` render as the same country
        // code "DE". A value-based lookup would route address.country's disclosure to the
        // issuing_country path. The fix uses itemId (elementIdentifier + docId).
        //
        // transformToDomainItems sorts docClaimsDomain by readableName.lowercase(), so we
        // replicate that ordering here (issuing_country "Ausstellendes Land" sorts before
        // address.country "Land") to faithfully reproduce the original bug.
        val docId = "pid-sd-jwt-id"
        val claims = listOf(
            requestDocumentClaim(
                elementIdentifier = "address.country",
                readableName = "Land",
                value = "DE",
                path = listOf("address", "country"),
            ),
            requestDocumentClaim(
                elementIdentifier = "issuing_country",
                readableName = "Ausstellendes Land",
                value = "DE",
                path = listOf("issuing_country"),
            ),
            requestDocumentClaim(
                elementIdentifier = "family_name",
                readableName = "Familienname",
                value = "MUSTERMANN",
                path = listOf("family_name"),
            ),
        ).sortedBy { it.readableName.lowercase() }
        val payload = sdJwtPayload(docId = docId, claims = claims)

        val uiItems = transformer.transformToUiItems(
            documentsDomain = listOf(payload),
            resourceProvider = resourceProvider,
        )

        // Check every claim; all three should be selected.
        val allChecked = checkAll(uiItems.first())

        val disclosed = transformer.createDisclosedDocuments(allChecked)

        assertEquals(1, disclosed.size)
        val doc = disclosed.first()
        assertEquals(docId, doc.documentId)

        val paths = doc.disclosedClaims.map { it.toNamespacedPath() }
        assertTrue(
            "address.country disclosure must use the nested address path, got $paths",
            paths.any { it == listOf("address", "country") },
        )
        assertTrue(
            "issuing_country disclosure must use its own path, got $paths",
            paths.any { it == listOf("issuing_country") },
        )
        assertTrue(
            "family_name disclosure must use its own path, got $paths",
            paths.any { it == listOf("family_name") },
        )
        assertEquals(3, paths.size)
    }

    @Test
    fun `createDisclosedDocuments resolves only the selected claim when values collide`() {
        // Selecting only address.country must NOT produce an issuing_country disclosure,
        // even though both share the value "DE". Claims are sorted by readableName.lowercase()
        // (as transformToDomainItems does), so issuing_country sorts before address.country
        // and a value-based lookup would return issuing_country for the address.country row.
        val docId = "pid-sd-jwt-id"
        val claims = listOf(
            requestDocumentClaim(
                elementIdentifier = "address.country",
                readableName = "Land",
                value = "DE",
                path = listOf("address", "country"),
            ),
            requestDocumentClaim(
                elementIdentifier = "issuing_country",
                readableName = "Ausstellendes Land",
                value = "DE",
                path = listOf("issuing_country"),
            ),
        ).sortedBy { it.readableName.lowercase() }
        val payload = sdJwtPayload(docId = docId, claims = claims)

        val uiItems = transformer.transformToUiItems(
            documentsDomain = listOf(payload),
            resourceProvider = resourceProvider,
        )

        // Check ONLY address.country.
        val addressCountryId = generateUniqueFieldId("address.country", docId)
        val onlyAddressCountry = checkOnly(uiItems.first(), addressCountryId)

        val disclosed = transformer.createDisclosedDocuments(onlyAddressCountry)

        val paths = disclosed.first().disclosedClaims.map { it.toNamespacedPath() }
        assertEquals(1, paths.size)
        assertEquals(listOf("address", "country"), paths.single())
    }

    @Test
    fun `createDisclosedDocuments resolves MSO-MDOC claims by identity not by value`() {
        // Same value-collision scenario for the mDOC path: both `resident_country` and
        // `issuing_country` render as "DE". The emitted claim must carry the
        // elementIdentifier of the row the user actually checked.
        val docId = "pid-mdoc-id"
        val claims = listOf(
            requestDocumentClaim(
                elementIdentifier = "resident_country",
                readableName = "Wohnsitzland",
                value = "DE",
                namespace = PID_DOCTYPE,
            ),
            requestDocumentClaim(
                elementIdentifier = "issuing_country",
                readableName = "Ausstellendes Land",
                value = "DE",
                namespace = PID_DOCTYPE,
            ),
        ).sortedBy { it.readableName.lowercase() }
        val payload = DocumentPayloadDomain(
            docName = "PID",
            docId = docId,
            documentType = DocumentType.PID,
            docNamespace = PID_DOCTYPE, // MSO-MDOC
            totalClaimsCount = claims.size,
            docClaimsDomain = claims,
        )

        val uiItems = transformer.transformToUiItems(
            documentsDomain = listOf(payload),
            resourceProvider = resourceProvider,
        )

        // Check only resident_country.
        val residentCountryId = generateUniqueFieldId("resident_country", docId)
        val onlyResidentCountry = checkOnly(uiItems.first(), residentCountryId)

        val disclosed = transformer.createDisclosedDocuments(onlyResidentCountry)

        val items = disclosed.first().disclosedClaims.filterIsInstance<MdocRequestedClaim>()
        assertEquals(1, items.size)
        assertEquals(PID_DOCTYPE, items.single().namespaceName)
        assertEquals("resident_country", items.single().dataElementName)
    }

    /**
     * OpenID4VP §6.4.1: a Credential Query without `claims` asks for a presentation carrying no
     * selectively disclosable claim. Such a document reaches the consent screen with no rows, so
     * it has none to tick - it must still be disclosed, otherwise it drops out of the presentment
     * selection and leaves the verifier's `credential_sets` unsatisfied.
     */
    @Test
    fun `createDisclosedDocuments keeps a document the verifier requested without naming claims`() {
        val docId = "diploma-id"
        val payload = DocumentPayloadDomain(
            docName = "University Diploma",
            docId = docId,
            documentType = DocumentType.EAA,
            docNamespace = null, // SD-JWT VC
            totalClaimsCount = 9,
            docClaimsDomain = emptyList(),
        )

        val uiItems = transformer.transformToUiItems(
            documentsDomain = listOf(payload),
            resourceProvider = resourceProvider,
        )
        assertTrue(
            "A claimless query must not produce claim rows",
            uiItems.single().expandedUiItems.isEmpty(),
        )

        val disclosed = transformer.createDisclosedDocuments(uiItems)

        assertEquals(1, disclosed.size)
        assertEquals(docId, disclosed.single().documentId)
        assertEquals(emptySet<RequestedClaim>(), disclosed.single().disclosedClaims)
    }

    /**
     * The counterpart of the case above: rows *were* offered and the user cleared every one of
     * them. That is a deliberate opt-out, so the document must not be disclosed at all.
     */
    @Test
    fun `createDisclosedDocuments drops a document whose rows the user cleared`() {
        val docId = "pid-sd-jwt-id"
        val payload = sdJwtPayload(
            docId = docId,
            claims = listOf(
                requestDocumentClaim(
                    elementIdentifier = "family_name",
                    readableName = "Familienname",
                    value = "MUSTERMANN",
                ),
            ),
        )

        val uiItems = transformer.transformToUiItems(
            documentsDomain = listOf(payload),
            resourceProvider = resourceProvider,
        )
        val noneChecked = checkOnly(uiItems.first(), itemId = "nothing-matches-this")

        assertEquals(emptyList<Any>(), transformer.createDisclosedDocuments(noneChecked))
    }

    private fun checkAll(item: RequestDocumentItemUi): List<RequestDocumentItemUi> {
        return listOf(
            item.copy(
                expandedUiItems = item.expandedUiItems.map { expandedItem ->
                    expandedItem.withChecked(checked = true)
                }
            )
        )
    }

    private fun checkOnly(
        item: RequestDocumentItemUi,
        itemId: String,
    ): List<RequestDocumentItemUi> {
        return listOf(
            item.copy(
                expandedUiItems = item.expandedUiItems.map { expandedItem ->
                    expandedItem.withChecked(checked = expandedItem.uiItem.itemId == itemId)
                }
            )
        )
    }

    private fun ExpandedUiItem.withChecked(checked: Boolean): ExpandedUiItem =
        copy(
            uiItem = uiItem.copy(
                trailingContentData = ListItemTrailingContentData.Checkbox(
                    checkboxData = CheckboxData(
                        isChecked = checked,
                        enabled = true,
                        onCheckedChange = null,
                    )
                )
            )
        )

    private fun sdJwtPayload(
        docId: String,
        claims: List<RequestDocumentClaim>,
    ): DocumentPayloadDomain = DocumentPayloadDomain(
        docName = "PID",
        docId = docId,
        documentType = DocumentType.PID,
        docNamespace = null, // SD-JWT VC
        totalClaimsCount = claims.size,
        docClaimsDomain = claims,
    )

    private fun documentPayloadDomain(
        docName: String,
        docId: String,
        documentType: DocumentType,
        requestedClaimNames: List<String>,
        totalClaimsCount: Int,
    ): DocumentPayloadDomain {
        return DocumentPayloadDomain(
            docName = docName,
            docId = docId,
            documentType = documentType,
            docNamespace = "namespace",
            totalClaimsCount = totalClaimsCount,
            docClaimsDomain = requestedClaimNames.mapIndexed { index, readableName ->
                requestDocumentClaim(
                    elementIdentifier = "claim_$index",
                    readableName = readableName,
                    namespace = "namespace",
                )
            },
        )
    }

    private fun requestDocumentClaim(
        elementIdentifier: String,
        readableName: String,
        value: String = "value",
        path: List<String> = listOf(elementIdentifier),
        namespace: String? = null,
    ): RequestDocumentClaim {
        val requestedClaim: RequestedClaim = if (namespace != null) {
            MdocRequestedClaim(
                docType = PID_DOCTYPE,
                namespaceName = namespace,
                dataElementName = elementIdentifier,
                intentToRetain = false,
            )
        } else {
            JsonRequestedClaim(
                vctValues = listOf(PID_VCT),
                claimPath = JsonArray(path.map { JsonPrimitive(it) }),
            )
        }
        return RequestDocumentClaim(
            elementIdentifier = elementIdentifier,
            requestedClaim = requestedClaim,
            value = value,
            readableName = readableName,
            isRequired = true,
            isAvailable = true,
            path = requestedClaim.toNamespacedPath(),
            withoutDetailLabel = readableName,
            labelValue = value,
        )
    }
}
