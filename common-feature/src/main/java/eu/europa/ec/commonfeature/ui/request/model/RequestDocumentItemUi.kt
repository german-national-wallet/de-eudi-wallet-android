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

package eu.europa.ec.commonfeature.ui.request.model

import eu.europa.ec.commonfeature.ui.request.transformer.DomainClaim
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.ElementIdentifier
import eu.europa.ec.eudi.wallet.document.NameSpace
import eu.europa.ec.uilogic.component.ListItemData
import org.multipaz.request.RequestedClaim

data class RequestDocumentItemUi(
    /**
     * The document this row stands for. Also carried by every [ExpandedUiItem], but kept here
     * too: a verifier can ask for a credential without naming any claim, which leaves the row
     * with no expanded items and would otherwise make the document unidentifiable.
     */
    val domainPayload: DocumentPayloadDomain,
    val collapsedUiItem: CollapsedUiItem,
    val expandedUiItems: List<ExpandedUiItem>,
    val requestedClaimsCount: Int,
    val totalClaimsCount: Int,
)

data class CollapsedUiItem(
    val isExpanded: Boolean,
    val uiItem: ListItemData,
)

data class ExpandedUiItem(
    val domainPayload: DocumentPayloadDomain,
    val uiItem: ListItemData,
)

enum class DocumentType {
    PID,
    EAA,
}

data class DocumentPayloadDomain(
    val docName: String,
    val docId: DocumentId,
    val documentType: DocumentType,
    val docNamespace: NameSpace?,
    val totalClaimsCount: Int,
    val docClaimsDomain: List<RequestDocumentClaim>,
)

data class RequestDocumentClaim(
    val elementIdentifier: ElementIdentifier,
    /** The request entry this row stands for; needed to narrow the presentment selection. */
    val requestedClaim: RequestedClaim,
    val value: String,
    val newValue: DomainClaim? = null,
    val readableName: String,
    val isRequired: Boolean,
    val isAvailable: Boolean,
    val path: List<String>,
    val withoutDetailLabel: String,
    val labelValue: String
)
