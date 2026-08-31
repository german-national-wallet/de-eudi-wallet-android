/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.commonfeature.ui.document_details.transformer

import eu.europa.ec.businesslogic.extension.ifEmptyOrNull
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.commonfeature.model.DocumentUi
import eu.europa.ec.commonfeature.model.DocumentUiIssuanceState
import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentDetailsDomain
import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.documentHasExpired
import eu.europa.ec.commonfeature.util.extractFullNameFromDocumentOrEmpty
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.commonfeature.util.parseKeyValueUi
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.sprind.wallet.commonfeature.ui.transformer.toListItemData

object DocumentDetailsTransformer {

    fun transformToDocumentDetailsDomain(
        document: IssuedDocument,
        resourceProvider: ResourceProvider,
    ): Result<DocumentDetailsDomain> = runCatching {

        val detailsDocumentItems = document.data.claims
            .map { claim ->
                transformToDocumentDetailsDocumentItem(
                    displayKey = claim.issuerMetadata?.display?.getLocalizedClaimName(
                        userLocale = resourceProvider.getLocale(),
                        fallback = claim.identifier
                    ),
                    key = claim.identifier,
                    item = claim.value ?: "",
                    resourceProvider = resourceProvider,
                    documentId = document.id
                )
            }

        val documentImage = extractValueFromDocumentOrEmpty(
            document = document,
            key = DocumentJsonKeys.PORTRAIT
        )

        val documentExpirationDate = extractValueFromDocumentOrEmpty(
            document = document,
            key = DocumentJsonKeys.EXPIRY_DATE
        )

        val docHasExpired = documentHasExpired(documentExpirationDate)

        return@runCatching DocumentDetailsDomain(
            docName = document.name,
            docId = document.id,
            documentIdentifier = document.toDocumentIdentifier(),
            documentExpirationDateFormatted = documentExpirationDate.toDateFormatted().orEmpty(),
            documentHasExpired = docHasExpired,
            documentImage = documentImage,
            userFullName = extractFullNameFromDocumentOrEmpty(document),
            detailsItems = detailsDocumentItems,
            issuerMetadata = document.issuerMetadata?.claims
        )
    }

    fun DocumentDetailsDomain.transformToDocumentDetailsUi(
        resourceProvider: ResourceProvider,
        documentFormat: DocumentFormat,
    ): DocumentUi {
        val documentDetailsListItemData = this.detailsItems.toListItemData(
            resourceProvider = resourceProvider,
            documentFormat = documentFormat,
            documentIdentifier = this.documentIdentifier,
            issuerMetadata = this.issuerMetadata
        )
        return DocumentUi(
            documentId = this.docId,
            documentName = this.docName,
            documentIdentifier = this.documentIdentifier,
            documentExpirationDateFormatted = this.documentExpirationDateFormatted,
            documentHasExpired = this.documentHasExpired,
            documentImage = this.documentImage,
            documentDetails = documentDetailsListItemData,
            userFullName = this.userFullName,
            documentIssuanceState = DocumentUiIssuanceState.Issued,
        )
    }

    fun transformToDocumentDetailsDocumentItem(
        key: String,
        displayKey: String?,
        item: Any,
        resourceProvider: ResourceProvider,
        documentId: String,
    ): DocumentItem {

        val values = StringBuilder()
        val localizedKey = displayKey.ifEmptyOrNull(default = key)

        parseKeyValueUi(
            item = item,
            groupIdentifier = localizedKey,
            groupIdentifierKey = key,
            resourceProvider = resourceProvider,
            allItems = values
        )
        val groupedValues = values.toString()

        return DocumentItem(
            elementIdentifier = key,
            value = groupedValues,
            readableName = localizedKey,
            docId = documentId
        )
    }
}