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

package eu.europa.ec.corelogic.model

import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat

typealias FormatType = String

sealed interface DocumentIdentifier {
    val formatType: FormatType

    /**
     * Defines if the document can be reissued
     * TBD CredentialPolicy once the policy is available
     */
    val eligibleForReissue: Boolean

    /**
     * The batch is refreshed once the remaining credential count is at or below this threshold
     * (i.e. `credentialsCount() <= minAvailableCredentials`).
     * TBD CredentialPolicy once the policy is available
     */
    val minAvailableCredentials: Int

    data object MdocPid : DocumentIdentifier {
        override val formatType: FormatType
            get() = "eu.europa.ec.eudi.pid.1"
        override val eligibleForReissue: Boolean = true
        override val minAvailableCredentials: Int = 2
    }

    data object SdJwtPid : DocumentIdentifier {
        override val formatType: FormatType
            get() = "urn:eudi:pid:de:1"
        override val eligibleForReissue: Boolean = true
        override val minAvailableCredentials: Int = 2
    }

    data class OTHER(
        override val formatType: FormatType,
        override val eligibleForReissue: Boolean = true,
        override val minAvailableCredentials: Int = 2,
    ) : DocumentIdentifier
}

/**
 * @return A [DocumentIdentifier] from a FormatType.
 */
fun FormatType.toDocumentIdentifier(): DocumentIdentifier = when (this.lowercase()) {
    DocumentIdentifier.MdocPid.formatType.lowercase() -> DocumentIdentifier.MdocPid
    DocumentIdentifier.SdJwtPid.formatType.lowercase() -> DocumentIdentifier.SdJwtPid
    else -> DocumentIdentifier.OTHER(formatType = this)
}

fun Document.toDocumentIdentifier(): DocumentIdentifier {
    val formatType = when (val f = format) {
        is MsoMdocFormat -> f.docType
        is SdJwtVcFormat -> f.vct
    }
    return createDocumentIdentifier(formatType)
}

/**
 * Checks if this [DocumentIdentifier] represents a PID (Personal Identification Data) document.
 *
 * @return `true` if this is either [DocumentIdentifier.MdocPid] or [DocumentIdentifier.SdJwtPid],
 *         `false` otherwise.
 */
val DocumentIdentifier.isPid: Boolean
    get() = this is DocumentIdentifier.MdocPid || this is DocumentIdentifier.SdJwtPid

/**
 * Checks if this [DocumentIdentifier] represents an OTHER (non-PID) document type.
 *
 * @return `true` if this is [DocumentIdentifier.OTHER], `false` otherwise.
 */
val DocumentIdentifier.isOther: Boolean
    get() = this is DocumentIdentifier.OTHER

private fun createDocumentIdentifier(
    formatType: FormatType
): DocumentIdentifier {
    return when (formatType.lowercase()) {
        DocumentIdentifier.MdocPid.formatType.lowercase() -> DocumentIdentifier.MdocPid
        DocumentIdentifier.SdJwtPid.formatType.lowercase() -> DocumentIdentifier.SdJwtPid
        else -> DocumentIdentifier.OTHER(formatType = formatType)
    }
}


/**
 * Converts a [DocumentIdentifier] to a [DocumentCategory] based on a provided set of [DocumentCategories].
 *
 * This function searches through the provided `allCategories` to find a category that contains
 * a [DocumentIdentifier] with a matching [FormatType]. If a matching category is found, it's returned.
 * Otherwise, [DocumentCategory.Other] is returned, indicating that the document identifier doesn't belong to
 * any of the specified categories.
 *
 * @param allCategories The set of document categories to search within. Each category is associated with a list of [DocumentIdentifier]s.
 * @return The corresponding [DocumentCategory] if a match is found, or [DocumentCategory.Other] if no match is found.
 *
 * @see DocumentIdentifier
 * @see DocumentCategory
 * @see DocumentCategories
 * @see FormatType
 *
 */
fun DocumentIdentifier.toDocumentCategory(allCategories: DocumentCategories): DocumentCategory {
    return allCategories.value.entries.find { (_, identifiersInCategory) ->
        val formatTypesInCategory: List<FormatType> = identifiersInCategory
            .map { it.formatType.lowercase() }

        this.formatType.lowercase() in formatTypesInCategory
    }?.key ?: DocumentCategory.Other
}
