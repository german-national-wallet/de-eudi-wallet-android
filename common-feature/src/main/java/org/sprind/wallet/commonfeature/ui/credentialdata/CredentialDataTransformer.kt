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

package org.sprind.wallet.commonfeature.ui.credentialdata

import eu.europa.ec.businesslogic.extension.decodeFromBase64
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.commonfeature.util.getGenderValue
import eu.europa.ec.commonfeature.util.keyIsGender
import eu.europa.ec.commonfeature.util.keyIsUserPseudonym
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.util.Locale

/**
 * Builds the credential data screen from the credential's claims, labelled and ordered by the
 * issuer's `credential_metadata.claims`, as the architecture concept (3.1.2 Wallet Dashboard,
 * Credential Data) describes for PID and EAA alike:
 *
 * - every claim is shown; a claim without a translation is labelled with its own name and goes
 *   after the translated ones
 * - an SD-JWT object whose own path is translated gets a [CredentialDataItem.Heading] followed by
 *   its children; otherwise the children are listed in place. mdoc data elements form one flat
 *   list, a map-valued element contributing one entry per key
 * - SD-JWT format claims (`iat`, `exp`, `nbf`) are left out; `iss`, `vct`, `cnf` and `status`
 *   never reach the claim tree. Meta information an EAA Provider lists as an ordinary attribute,
 *   such as an mdoc `expiry_date`, is shown
 *
 * For the PID, every claim its metadata lists is shown, with [MISSING_VALUE] when the credential
 * has none, and issuance and expiry dates are left out (WD-4413). Its address and age claims get a
 * heading in both formats, from the metadata when it translates the SD-JWT object, else from the
 * app's strings; mdoc has no such object, so its `resident_*` and `age_over_*` elements are
 * gathered under one.
 */
class CredentialDataTransformer(
    private val resourceProvider: ResourceProvider,
) {

    fun transform(document: IssuedDocument): List<CredentialDataItem> =
        transform(
            claims = document.data.claims,
            metadataClaims = document.issuerMetadata?.claims.orEmpty(),
            isPid = document.toDocumentIdentifier().isPid,
        )

    fun transform(
        claims: List<DocumentClaim>,
        metadataClaims: List<IssuerMetadata.Claim>,
        isPid: Boolean,
    ): List<CredentialDataItem> {
        val pass = Pass(ClaimMetadata(metadataClaims, resourceProvider.getLocale()), isPid)
        val sdJwtClaims = claims.filterIsInstance<SdJwtVcClaim>()
        val mdocClaims = claims.filterIsInstance<MsoMdocClaim>()
        return when {
            sdJwtClaims.isNotEmpty() -> pass.sdJwtItems(sdJwtClaims)
            mdocClaims.isNotEmpty() -> pass.mdocItems(mdocClaims)
            else -> emptyList()
        }
    }

    private inner class Pass(
        private val metadata: ClaimMetadata,
        private val isPid: Boolean,
    ) {
        private val excludedSdJwtClaims =
            if (isPid) SD_JWT_FORMAT_CLAIMS + PID_VALIDITY_CLAIMS else SD_JWT_FORMAT_CLAIMS

        fun sdJwtItems(claims: List<SdJwtVcClaim>): List<CredentialDataItem> {
            val issued = claims.mapNotNull { it.toNode() }
            val tree = if (isPid) {
                metadata.paths.fold(issued) { nodes, path -> nodes.withMissing(path) }
            } else {
                issued
            }
            return nodeItems(tree, emptyList())
        }

        private fun nodeItems(nodes: List<Node>, parentPath: List<String>): List<CredentialDataItem> =
            nodes
                .map { (parentPath + it.name) to it }
                .filterNot { (path, _) -> path.size == 1 && path.first() in excludedSdJwtClaims }
                .sortedBy { (path, _) -> metadata.orderOf(path) ?: Int.MAX_VALUE }
                .flatMap { (path, node) -> nodeItem(node, path) }

        private fun nodeItem(node: Node, path: List<String>): List<CredentialDataItem> {
            val label = metadata.labelOf(path)
            if (node.children.isEmpty()) return listOfNotNull(attribute(path, label, node.value))

            val title = label
                ?: path.singleOrNull()?.takeIf { isPid }
                    ?.let { SD_JWT_PID_SECTIONS[it] }
                    ?.let(resourceProvider::getString)
            return withHeading(path.toId(), title, nodeItems(node.children, path))
        }

        fun mdocItems(claims: List<MsoMdocClaim>): List<CredentialDataItem> {
            val issued = claims.flatMap { it.toEntries() }
            val entries = if (isPid) issued + missingMdocEntries(issued) else issued
            return entries
                .filterNot { isPid && it.path[1] in PID_VALIDITY_CLAIMS }
                .sortedBy { it.order }
                .groupBy { entry -> mdocPidSection(entry.path[1]) ?: entry.path.toId() }
                .flatMap { (key, group) ->
                    val attributes = group.mapNotNull { attribute(it.path, metadata.labelOf(it.path), it.value) }
                    val title = MDOC_PID_SECTIONS[key]?.let(resourceProvider::getString)
                    withHeading(key, title, attributes)
                }
        }

        /** The section a PID data element belongs to; the key doubles as the heading's id. */
        private fun mdocPidSection(elementName: String): String? = when {
            !isPid -> null
            elementName.startsWith(MDOC_RESIDENT_PREFIX) -> ADDRESS_SECTION
            elementName.startsWith(MDOC_AGE_OVER_PREFIX) -> AGE_SECTION
            else -> null
        }

        private fun withHeading(
            id: String,
            title: String?,
            items: List<CredentialDataItem>,
        ): List<CredentialDataItem> =
            if (title == null || items.isEmpty()) items else listOf(CredentialDataItem.Heading(id, title)) + items

        private fun MsoMdocClaim.toEntries(): List<Entry> {
            val elementPath = listOf(nameSpace, dataElementName)
            val elementOrder = metadata.orderOf(elementPath) ?: Int.MAX_VALUE
            return when (val value = value) {
                is Map<*, *> -> value.map { (key, entry) ->
                    val path = elementPath + key.toString()
                    Entry(path, entry, metadata.orderOf(path) ?: elementOrder)
                }

                else -> listOf(Entry(elementPath, value, elementOrder))
            }
        }

        /** The metadata paths the credential has no value for, at the depth the metadata lists them. */
        private fun missingMdocEntries(issued: List<Entry>): List<Entry> {
            val paths = metadata.paths.filter { it.size in MDOC_PATH_SIZES }
            return paths
                .filterNot { path -> paths.any { it.size > path.size && it.startsWith(path) } }
                .filterNot { path -> issued.any { it.path.startsWith(path) || path.startsWith(it.path) } }
                .map { Entry(it, null, metadata.orderOf(it) ?: Int.MAX_VALUE) }
        }

        private fun attribute(path: List<String>, label: String?, value: Any?): CredentialDataItem? {
            if (isImage(path.last(), value)) return null
            val formatted = formatValue(path.last(), value)
            if (formatted == null && !isPid) return null
            return CredentialDataItem.Attribute(
                id = path.toId(),
                label = label ?: path.last(),
                value = formatted ?: MISSING_VALUE,
                isMissing = formatted == null,
            )
        }
    }

    /**
     * Portraits and signatures, which this text-only screen leaves out: mdoc carries them as bytes,
     * SD-JWT as a base64 or `data:` string under a known name.
     */
    private fun isImage(name: String, value: Any?): Boolean =
        value is ByteArray ||
                name in IMAGE_CLAIMS ||
                (value as? String)?.startsWith(DATA_IMAGE_URI_PREFIX, ignoreCase = true) == true

    private fun formatValue(name: String, value: Any?): String? {
        val locale = resourceProvider.getLocale()
        val formatted = when (value) {
            null, is ByteArray -> null
            is Boolean -> resourceProvider.getString(
                if (value) {
                    R.string.pid_issuance_data_consent_label_age_equal_or_over_yes
                } else {
                    R.string.pid_issuance_data_consent_label_age_equal_or_over_no
                }
            )

            is Map<*, *> -> value.entries.mapNotNull { (key, entry) ->
                formatValue(key.toString(), entry)?.let { "$key: $it" }
            }.joinToString(LINE_SEPARATOR)

            is Collection<*> -> value.mapNotNull { formatValue(name, it) }
                .joinToString(LINE_SEPARATOR)

            else -> formatScalar(name, value.toString(), locale)
        }
        return formatted?.takeIf(String::isNotBlank)
    }

    private fun formatScalar(name: String, value: String, locale: Locale): String = when {
        keyIsGender(name) -> getGenderValue(value, resourceProvider)
        keyIsUserPseudonym(name) -> value.decodeFromBase64()
        name in COUNTRY_CODE_CLAIMS && COUNTRY_CODE.matches(value) ->
            Locale.Builder().setRegion(value).build().getDisplayCountry(locale).uppercase(locale)

        ISO_DATE.matches(value) -> value.toDateFormatted(locale.toLanguageTag()) ?: value
        else -> value
    }

    /** An SD-JWT claim reduced to its object structure; arrays stay in [value]. */
    private data class Node(
        val name: String,
        val value: Any?,
        val children: List<Node> = emptyList(),
    )

    private data class Entry(
        val path: List<String>,
        val value: Any?,
        val order: Int,
    )

    private class ClaimMetadata(
        private val claims: List<IssuerMetadata.Claim>,
        private val locale: Locale,
    ) {
        // A path listed twice keeps its first position, as the descendant fallback below does.
        private val indexByPath: Map<List<String>, Int> = buildMap {
            claims.forEachIndexed { index, claim -> putIfAbsent(claim.path, index) }
        }

        /**
         * Each listed path once. wallet-core keeps only the claim names of a path
         * (`toMetaDataClaimName`), so an array element or wildcard such as `["nationalities", null]`
         * arrives as its array's own path.
         */
        val paths: List<List<String>> = claims.map { it.path }.filter { it.isNotEmpty() }.distinct()

        /**
         * The position of [path] in the metadata: its own, else that of its first listed
         * descendant, as an SD-JWT object may be listed only through its children.
         */
        fun orderOf(path: List<String>): Int? =
            indexByPath[path]
                ?: indexByPath.entries
                    .filter { (claimPath, _) -> claimPath.size > path.size && claimPath.startsWith(path) }
                    .minOfOrNull { (_, index) -> index }

        fun labelOf(path: List<String>): String? =
            claims.firstOrNull { it.path == path }
                ?.display
                ?.takeIf { it.isNotEmpty() }
                ?.getLocalizedClaimName(userLocale = locale, fallback = "")
                ?.takeIf(String::isNotBlank)
    }

    companion object {
        /** Shown for a claim the PID metadata lists but the credential has no value for. */
        const val MISSING_VALUE = "-"

        private const val PATH_SEPARATOR = "."
        private const val LINE_SEPARATOR = "\n"
        private const val DATA_IMAGE_URI_PREFIX = "data:image/"

        private val IMAGE_CLAIMS = setOf("picture", "portrait", "image", "signature_usual_mark")

        private val SD_JWT_FORMAT_CLAIMS = setOf("iat", "exp", "nbf")

        // Issuance and expiry the PID Provider lists as attributes; the credential inspection
        // screen shows the validity instead.
        private val PID_VALIDITY_CLAIMS = setOf(
            "date_of_expiry",
            "date_of_issuance",
            "expiry_date",
            "issuance_date",
        )

        // Not a claim path, so a heading id can never clash with an attribute's.
        private const val ADDRESS_SECTION = "#address"
        private const val AGE_SECTION = "#age"
        private const val MDOC_RESIDENT_PREFIX = "resident_"
        private const val MDOC_AGE_OVER_PREFIX = "age_over_"

        private val ADDRESS_TITLE = R.string.eaa_inspection_eaa_detailed_data_label_adress
        private val AGE_TITLE = R.string.eaa_inspection_eaa_detailed_data_label_age
        private val SD_JWT_PID_SECTIONS = mapOf("address" to ADDRESS_TITLE, "age_equal_or_over" to AGE_TITLE)
        private val MDOC_PID_SECTIONS = mapOf(ADDRESS_SECTION to ADDRESS_TITLE, AGE_SECTION to AGE_TITLE)

        /** A data element, or a key inside a map-valued one, each led by the namespace. */
        private val MDOC_PATH_SIZES = 2..3

        private val COUNTRY_CODE_CLAIMS = setOf(
            "country",
            "issuing_authority",
            "issuing_country",
            "nationalities",
            "nationality",
            "resident_country",
        )
        private val COUNTRY_CODE = Regex("[A-Za-z]{2}")
        private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}(T.*)?""")

        private fun SdJwtVcClaim.toNode(): Node? =
            claimName?.let { name -> Node(name, value, children.mapNotNull { it.toNode() }) }

        /** Adds [path] as a claim without value unless the credential already has it or its leaf. */
        private fun List<Node>.withMissing(path: List<String>): List<Node> {
            val head = path.first()
            val rest = path.drop(1)
            val existing = find { it.name == head } ?: return this + missingNode(path)
            val isIssuedLeaf = existing.children.isEmpty() && existing.value != null
            return if (rest.isEmpty() || isIssuedLeaf) {
                this
            } else {
                map { if (it === existing) it.copy(children = it.children.withMissing(rest)) else it }
            }
        }

        private fun missingNode(path: List<String>): Node =
            Node(
                name = path.first(),
                value = null,
                children = if (path.size > 1) listOf(missingNode(path.drop(1))) else emptyList(),
            )

        private fun List<String>.startsWith(prefix: List<String>): Boolean =
            size >= prefix.size && take(prefix.size) == prefix

        private fun List<String>.toId(): String = joinToString(PATH_SEPARATOR)
    }
}