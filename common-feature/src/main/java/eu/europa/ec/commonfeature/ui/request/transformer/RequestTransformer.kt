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

package eu.europa.ec.commonfeature.ui.request.transformer

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.commonfeature.ui.request.model.CollapsedUiItem
import eu.europa.ec.commonfeature.ui.request.model.DocumentPayloadDomain
import eu.europa.ec.commonfeature.ui.request.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.model.ExpandedUiItem
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.util.docNamespace
import eu.europa.ec.commonfeature.util.generateUniqueFieldId
import eu.europa.ec.commonfeature.util.keyIsPortrait
import eu.europa.ec.commonfeature.util.keyIsSignature
import eu.europa.ec.commonfeature.util.parseKeyValueUi2
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.corelogic.extension.toClaimPaths
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.DocItem
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.device.MsoMdocItem
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.NameSpace
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.transfer.openId4vp.SdJwtVcItem
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import java.lang.Boolean.parseBoolean
import java.util.Locale

private fun getMandatoryFields(documentIdentifier: DocumentIdentifier): List<String> =
    when {
        documentIdentifier.isPid -> listOf(
            "issuance_date",
            "expiry_date",
            "issuing_authority",
            "document_number",
            "administrative_number",
            "issuing_country",
            "issuing_jurisdiction",
            "portrait",
            "portrait_capture_date"
        )

        // EUDI-removed not anymore in upstream
        /*
        DocumentIdentifier.MdocPseudonym -> listOf(
            "issuance_date",
            "expiry_date",
            "issuing_country",
            "issuing_authority",
        )
         */

        else -> emptyList()
    }

sealed class DomainClaim {
    data class ClaimArray(val items: List<DomainClaim>) : DomainClaim()

    data class ClaimPrimitive(
        val key: String,
        val value: String,
        val displayTitle: String,
    ) : DomainClaim()
}

class RequestTransformer {
    private val logTag = javaClass.simpleName
    suspend fun transformToDomainItems(
        storageDocuments: List<IssuedDocument> = emptyList(),
        resourceProvider: ResourceProvider,
        requestDocuments: List<RequestedDocument>,
        logController : LogController,
    ): Result<List<DocumentPayloadDomain>> = runCatching {
        requestDocuments.map { requestDocument ->
            val storageDocument =
                storageDocuments.firstOrNull { it.id == requestDocument.documentId }
                    ?: error(resourceProvider.getString(R.string.error_no_matching_document))
            val credentialCount = storageDocument.credentialsCount()
            logController.d(logTag) { "Credential count :${credentialCount}, storageDocument.id: ${storageDocument.id}, nameSpace: ${storageDocument.docNamespace}" }
            val docName: String = storageDocument.name
            val docId: DocumentId = storageDocument.id
            val documentIdentifier = storageDocument.toDocumentIdentifier()
            val documentType = if (documentIdentifier.isPid) DocumentType.PID else DocumentType.EAA
            val docNamespace: NameSpace? = storageDocument.docNamespace

            val requestDocumentClaims = requestDocument.requestedItems.keys.map { docItem ->
                val documentClaim = storageDocument.findClaimFromDocItem(docItem)
                    ?: error(resourceProvider.getString(R.string.error_no_matching_claim))
                val identifier = documentClaim.identifier

                val isRequired = getMandatoryFields(
                    documentIdentifier = storageDocument.toDocumentIdentifier()
                ).contains(identifier)

//                val documentClaim = storageDocument.data.claims.find {
//                    it.identifier == docItem.elementIdentifier
//                }

                val readableName: String =
                    getReadableName(
                        fieldName = identifier,
                        resourceProvider = resourceProvider,
                        issuerMetadata = storageDocument.issuerMetadata
                    )

                // value is a listOf(domainClaim)
                val claim = parseKeyValueUi2(
                    coreClaim = documentClaim,
                    readableName = readableName,
                    groupIdentifierKey = identifier,
                    resourceProvider = resourceProvider
                )
                val value = when (claim) {
                    is DomainClaim.ClaimArray -> {
                        (claim.items.first() as DomainClaim.ClaimPrimitive).value
                    }

                    is DomainClaim.ClaimPrimitive -> {
                        claim.value
                    }
                }
                RequestDocumentClaim(
                    elementIdentifier = identifier,
                    value = value,
                    readableName = readableName,
                    isRequired = isRequired,
                    isAvailable = value.isNotEmpty(),
                    path = docItem.toPath(),
                    withoutDetailLabel = getLabelNameWithoutDetail(
                        identifier,
                        resourceProvider
                    ) ?: readableName,
                    labelValue = getLabelValue(
                        identifier,
                        value,
                        resourceProvider
                    ) ?: value
                )
            }

            DocumentPayloadDomain(
                docName = docName,
                docId = docId,
                documentType = documentType,
                docNamespace = docNamespace,
                totalClaimsCount = storageDocument.data.claims.sumOf { it.toClaimPaths().size },
                docClaimsDomain = requestDocumentClaims.sortedBy { it.readableName.lowercase() },
            )
        }
    }

    // Resolves human-readable claim names for PID and EAA documents:
    // 1. Check issuer metadata for localized claim name
    // 2. Fallback to hardcoded string resources for known fields
    // 3. Return original field name if no match found
    private fun getReadableName(
        fieldName: String,
        resourceProvider: ResourceProvider,
        issuerMetadata: eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata?
    ): String {
        val metadataName = issuerMetadata?.claims
            ?.find { it.path.joinToString(".") == fieldName }
            ?.display
            ?.getLocalizedClaimName(
                userLocale = resourceProvider.getLocale(),
                fallback = ""
            )

        if (!metadataName.isNullOrEmpty()) return metadataName

        val hardcodedName = when (fieldName) {
            "age_over_65" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_65)
            "family_name_birth" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_birth_name)
            "age_over_21" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_21)
            "issuing_authority" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_issuing_authority)
            "birth_date" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_birth_date)
            "age_over_14" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_14)
            "age_over_12" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_12)
            "age_in_years" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_in_years)
            "given_name" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_first_names)
            "issuance_date" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_created_at)
            "age_over_16" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_16)
            "issuing_country" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_issuing_country)
            "age_over_18" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_18)
            "nationality" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_nationality)
            "age_birth_year" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_birth_year)
            "family_name" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_name)
            "birth_place" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_place_of_birth)
            "expiry_date" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_expire_date)
            "resident_street" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_street)
            "resident_postal_code" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_postal)
            "resident_city" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_city)
            "resident_country" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_country)

            //SD Jwt
            "birthdate" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_birth_date)
            "resident_address" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_adress)
            "resident_house_number" -> resourceProvider.getString(R.string.resident_house_number)
            "resident_state" -> resourceProvider.getString(R.string.resident_state)
            "birth_family_name" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_birth_name)
            "birth_given_name" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_birth_name)
            "age_equal_or_over.18" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_age_over_18)
            "place_of_birth.locality" -> resourceProvider.getString(R.string.birth_place_locality)
            "place_of_birth.country" -> resourceProvider.getString(R.string.birth_place_country)
            "place_of_birth.region" -> resourceProvider.getString(R.string.birth_place_region)
            "address.formatted" -> resourceProvider.getString(R.string.address_formatted)
            "address.country" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_country)
            "address.region" -> resourceProvider.getString(R.string.resident_region)
            "address.locality" -> resourceProvider.getString(R.string.resident_locality)
            "address.postal_code" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_postal)
            "address.street_address" -> resourceProvider.getString(R.string.pid_issuance_data_consent_label_resident_street)
            "address.house_number" -> resourceProvider.getString(R.string.resident_house_number)
            "sex" -> resourceProvider.getString(R.string.sex)
            "nationalities" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_nationality)
            "iat" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_created_at)
            "exp" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_expire_date)
            "document_number" -> resourceProvider.getString(R.string.document_number)
            "personal_administrative_number" -> resourceProvider.getString(R.string.administrative_number)
            "issuing_jurisdiction" -> resourceProvider.getString(R.string.pid_presentation_data_consent_label_issuing_country)
            "portrait" -> resourceProvider.getString(R.string.portrait)
            "email_address" -> resourceProvider.getString(R.string.pid_inspection_pid_issuer_label_email)
            "mobile_phone_number" -> resourceProvider.getString(R.string.mobile_phone_number)
            "location_status" -> resourceProvider.getString(R.string.location_status)
            else -> null
        }

        return hardcodedName ?: fieldName
    }

    private fun getLabelNameWithoutDetail(
        fieldName: String,
        resourceProvider: ResourceProvider,
    ): String? {
        return when (fieldName) {
            "age_over_65" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            "age_over_14" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            "age_over_12" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            "age_over_16" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            "age_over_18" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            "age_over_21" -> resourceProvider.getString(R.string.eaa_inspection_eaa_detailed_data_label_age_equal_or_over)
            else -> null
        }
    }

    private fun getLabelValue(
        fieldName: String,
        actualValue: String,
        resourceProvider: ResourceProvider,
    ): String? {

        val ageFields = setOf(
            // MSO-MDOC
            "age_over_65",
            "age_over_14",
            "age_over_12",
            "age_over_16",
            "age_over_18",
            "age_over_21",
            // SD-JWT
            "age_equal_or_over.12",
            "age_equal_or_over.14",
            "age_equal_or_over.16",
            "age_equal_or_over.18",
            "age_equal_or_over.21",
            "age_equal_or_over.65",
        )

        val countryFields = setOf(
            "resident_country",
            "country",
            "issuing_authority",
            "issuing_country",
            "nationality",
        )

        val dateFields = setOf(
            "issue_date",
            "birth_date",
            "expiry_date",
        )

        val locale = resourceProvider.getLocale()

        return when {
            fieldName in ageFields -> {
                val resId = if (parseBoolean(actualValue)) {
                    R.string.pid_presentation_data_consent_label_age_equal_or_over_yes
                } else {
                    R.string.pid_presentation_data_consent_label_age_equal_or_over_no
                }
                resourceProvider.getString(resId)
            }

            fieldName in countryFields -> {
                Locale("", actualValue)
                    .getDisplayCountry(locale)
                    .uppercase()
                    .ifEmpty { null }
            }

            fieldName == "nationality" && actualValue == "DE" -> {
                resourceProvider.getString(R.string.nationality_value_label_german)
            }

            locale.country == "DE" && fieldName in dateFields -> {
                actualValue.toDateFormatted(
                    selectedLanguage = locale.toLanguageTag()
                )
            }

            else -> null
        }
    }


    fun transformToUiItems(
        documentsDomain: List<DocumentPayloadDomain>,
        resourceProvider: ResourceProvider,
    ): List<RequestDocumentItemUi> {
        return documentsDomain.map { docPayloadDomain ->

            val collapsedItemId = docPayloadDomain.docId

            val expandedItems = docPayloadDomain.docClaimsDomain.map { docClaimDomain ->
                val expandedItemId = generateUniqueFieldId(
                    elementIdentifier = docClaimDomain.elementIdentifier,
                    documentId = docPayloadDomain.docId,
                )

                val leadingContent =
                    if (keyIsPortrait(key = docClaimDomain.elementIdentifier) && docClaimDomain.isAvailable) {
                        ListItemLeadingContentData.UserImage(userBase64Image = docClaimDomain.value)
                    } else {
                        null
                    }

                val mainContent = when {
                    keyIsPortrait(key = docClaimDomain.elementIdentifier) && docClaimDomain.isAvailable -> {
                        ListItemMainContentData.Text(text = "")
                    }

                    keyIsSignature(key = docClaimDomain.elementIdentifier) && docClaimDomain.isAvailable -> {
                        ListItemMainContentData.Image(base64Image = docClaimDomain.value)
                    }

                    else -> {
                        ListItemMainContentData.Text(text = docClaimDomain.value)
                    }
                }

                ExpandedUiItem(
                    domainPayload = docPayloadDomain,
                    uiItem = ListItemData(
                        itemId = expandedItemId,
                        mainContentData = mainContent,
                        overlineText = docClaimDomain.readableName,
                        leadingContentData = leadingContent,
                        trailingContentData = ListItemTrailingContentData.Checkbox(
                            checkboxData = CheckboxData(
                                isChecked = docClaimDomain.isAvailable,
                                enabled = docClaimDomain.isAvailable && !docClaimDomain.isRequired,
                                onCheckedChange = null,
                            )
                        )
                    )
                )
            }

            RequestDocumentItemUi(
                collapsedUiItem = CollapsedUiItem(
                    uiItem = ListItemData(
                        itemId = collapsedItemId,
                        mainContentData = ListItemMainContentData.Text(text = docPayloadDomain.docName),
                        supportingText = resourceProvider.getString(R.string.request_collapsed_supporting_text),
                        trailingContentData = ListItemTrailingContentData.Icon(
                            iconData = AppIcons.KeyboardArrowDown
                        )
                    ),
                    isExpanded = false
                ),
                expandedUiItems = expandedItems,
                requestedClaimsCount = docPayloadDomain.docClaimsDomain.size,
                totalClaimsCount = docPayloadDomain.totalClaimsCount,
            )
        }
    }

    fun createDisclosedDocuments(items: List<RequestDocumentItemUi>): DisclosedDocuments {
        // Collect all selected expanded items from the list
        val selectedItems = items.flatMap { requestItem ->
            requestItem.expandedUiItems.filter { uiPayload ->
                // Filter only the items the user has selected
                uiPayload.uiItem.trailingContentData is ListItemTrailingContentData.Checkbox &&
                        (uiPayload.uiItem.trailingContentData as ListItemTrailingContentData.Checkbox)
                            .checkboxData.isChecked
            }
        }

        // Group the selected items by their domain payload (document-level grouping)
        val groupedByDocument = selectedItems.groupBy { it.domainPayload }

        // Convert to the format required by DisclosedDocuments
        val disclosedDocuments =
            groupedByDocument.map { (documentPayload, selectedItemsForDocument) ->

                val disclosedItems = selectedItemsForDocument.map { selectedItem ->
                    // Resolve the selected row back to its RequestDocumentClaim by identity, not by
                    // display value. Multiple claims can share the same rendered value (e.g. for
                    // the German PID, `address.country`, `issuing_country` and `nationalities` all
                    // render as the country code "DE"), so a value-based lookup would route the
                    // disclosure of the wrong claim to the verifier. The itemId is the stable,
                    // unique key produced by generateUniqueFieldId(elementIdentifier, docId).
                    val matchedClaim = documentPayload.docClaimsDomain.firstOrNull { claim ->
                        generateUniqueFieldId(
                            elementIdentifier = claim.elementIdentifier,
                            documentId = documentPayload.docId,
                        ) == selectedItem.uiItem.itemId
                    }
                    val elementIdentifier = matchedClaim?.elementIdentifier.orEmpty()
                    val path: List<String> = matchedClaim?.path ?: listOf()

                    when (documentPayload.docNamespace) {
                        null -> SdJwtVcItem(
                            path
                        )

                        else -> MsoMdocItem(
                            namespace = documentPayload.docNamespace,
                            elementIdentifier = elementIdentifier
                        )
                    }
                }

                DisclosedDocument(
                    documentId = documentPayload.docId,
                    disclosedItems = disclosedItems,
                    keyUnlockData = null
                )
            }

        return DisclosedDocuments(disclosedDocuments)
    }
}

fun IssuedDocument.findClaimFromDocItem(docItem: DocItem) =
    findClaimFromPath(docItem.toPath())

fun DocItem.toPath(): List<String> {
    return when (this) {
        is MsoMdocItem -> return listOf(this.namespace, this.elementIdentifier)
        is SdJwtVcItem -> this.path
        else -> emptyList()
    }
}

private fun List<String>.normalizedSdJwtPath(): List<String> {
    if (isEmpty()) return emptyList()

    // Assumption: claim identifiers never contain '.' ie: ["iso.3166_country_code"] -> this will produce ["iso","3166_country_code"]
    // If someone passed ["address.locality"] treat it as nested segments.
    if (size == 1 && first().contains('.')) {
        return first().split('.').filter { it.isNotBlank() }
    }

    // Supports:
    // ["address", "locality"] -> ["address","locality"]
    // ["address.locality"]    -> ["address","locality"]
    // ["a", "b.c", "d"]       -> ["a","b","c","d"]
    return flatMap { seg -> seg.split('.').filter { it.isNotBlank() } }
}

fun IssuedDocument.findClaimFromPath(path: List<String>): DocumentClaim? {
    return when (format) {
        is MsoMdocFormat -> {
            if (path.size == 2) null
            val nameSpace = path.first()
            val elementIdentifier = path.last()
            data.claims.filterIsInstance<MsoMdocClaim>().firstOrNull {
                it.identifier == elementIdentifier && it.nameSpace == nameSpace
            }

        }

        is SdJwtVcFormat -> {
            val normalized = path.normalizedSdJwtPath()
            if (normalized.isEmpty()) return null

            var currentClaims: List<DocumentClaim> = data.claims
            var found: SdJwtVcClaim? = null

            for (segment in normalized) {
                found = currentClaims
                    .filterIsInstance<SdJwtVcClaim>()
                    .firstOrNull { it.identifier == segment }
                    ?: return null

                currentClaims = found.children
            }

            if (normalized.size > 1) {
                found?.copy(identifier = normalized.joinToString("."))
            } else {
                found
            }
        }
    }
}
