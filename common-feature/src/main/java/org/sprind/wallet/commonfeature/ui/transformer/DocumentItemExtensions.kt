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

package org.sprind.wallet.commonfeature.ui.transformer

import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem
import eu.europa.ec.commonfeature.util.flattenNestedItems
import eu.europa.ec.commonfeature.util.keyIsPortrait
import eu.europa.ec.commonfeature.util.keyIsSignature
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import org.sprind.wallet.commonfeature.ui.transformer.mdoc.mdocSchema
import org.sprind.wallet.commonfeature.ui.transformer.sdjwt.sdJwtSchema

/**
 * Transforms a list of [DocumentItem] into UI-ready [ListItemData] for display in the document details screen.
 *
 * **Behavior by document type:**
 * - **PID documents**: Uses schema-based approach (preserves legacy ordering and formatting)
 * - **EAA documents**: Uses metadata-based approach (displays ALL claims with metadata ordering)
 *
 * @param resourceProvider Resource provider for localization
 * @param documentFormat The format of the document (mDOC or SD-JWT VC)
 * @param documentIdentifier The document identifier to determine if this is a PID or EAA
 * @param issuerMetadata Optional issuer metadata for EAA claim ordering and display
 * @return List of [ListItemData] ready for UI display
 */
fun List<DocumentItem>.toListItemData(
    resourceProvider: ResourceProvider,
    documentFormat: DocumentFormat,
    documentIdentifier: DocumentIdentifier,
    issuerMetadata: List<IssuerMetadata.Claim>? = null,
): List<ListItemData> {
    val locale = resourceProvider.getLocale()

    val items = when (documentFormat) {
        is MsoMdocFormat -> this
        is SdJwtVcFormat -> this.flattenNestedItems()
    }

    // Detect if this is a PID document
    return if (documentIdentifier.isPid) {
        // ============================================
        // PID DOCUMENTS: Use legacy schema-based approach
        // Preserves exact ordering, formatting, and filtering for PID claims
        // ============================================
        val schema = when (documentFormat) {
            is MsoMdocFormat -> mdocSchema
            is SdJwtVcFormat -> sdJwtSchema
        }

        schema.mapNotNull { spec ->
            val rawValue = spec.value(items.associateBy { it.elementIdentifier }, locale, resourceProvider)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val mainContent = when {
                keyIsPortrait(spec.key) -> ListItemMainContentData.Text("")
                keyIsSignature(spec.key) -> ListItemMainContentData.Image(base64Image = rawValue)
                else -> ListItemMainContentData.Text(rawValue)
            }

            val leadingContent = if (keyIsPortrait(spec.key)) {
                ListItemLeadingContentData.UserImage(userBase64Image = rawValue)
            } else null

            ListItemData(
                itemId = spec.key,
                mainContentData = mainContent,
                overlineText = resourceProvider.getString(spec.labelRes),
                leadingContentData = leadingContent
            )
        }
    } else {
        // ============================================
        // EAA DOCUMENTS: Use metadata-based approach
        // Displays ALL claims with ordering from issuer metadata
        // ============================================

        // TODO: Handle nested SD-JWT claims with children
        // For now, treat nested claims the same as flat claims
        // Nested claims are already flattened by flattenNestedItems() for SD-JWT format
        // Future enhancement: Support expandable/collapsible groups for nested structures
        // Example: degree_name, degree_type, graduation_date could be grouped under "Education Details"

        // Order claims based on issuer metadata if available
        val orderedItems = if (!issuerMetadata.isNullOrEmpty()) {
            try {
                // Build a map of claim identifier to its position in metadata
                // For SD-JJWT: use the last element of path (the claim name)
                // For mDOC: use namespace.claim_name format
                val metadataClaimOrder = issuerMetadata
                    .mapIndexed { index, claim ->
                        // Try different path formats to match elementIdentifier
                        val claimKey = when {
                            // SD-JWT: path might be just ["claim_name"]
                            claim.path.size == 1 -> claim.path.first()
                            // SD-JWT nested: join all elements
                            claim.path.size > 1 -> claim.path.joinToString(".")
                            // Fallback: use last element
                            else -> claim.path.lastOrNull() ?: ""
                        }
                        claimKey to index
                    }
                    .toMap()

                // Sort items: metadata claims first (in order), then non-metadata claims (original order)
                items.sortedWith(compareBy { item ->
                    metadataClaimOrder[item.elementIdentifier] ?: Int.MAX_VALUE
                })
            } catch (e: Exception) {
                // If ordering fails, fall back to original order
                items
            }
        } else {
            // No metadata: keep original order
            items
        }

        // Transform ALL claims (not just schema-defined ones)
        orderedItems.map { item ->
            val mainContent = when {
                keyIsPortrait(item.elementIdentifier) -> ListItemMainContentData.Text("")
                keyIsSignature(item.elementIdentifier) -> ListItemMainContentData.Image(base64Image = item.value)
                else -> ListItemMainContentData.Text(item.value)
            }

            val leadingContent = if (keyIsPortrait(item.elementIdentifier)) {
                ListItemLeadingContentData.UserImage(userBase64Image = item.value)
            } else null

            ListItemData(
                itemId = item.elementIdentifier,
                mainContentData = mainContent,
                overlineText = item.readableName,
                leadingContentData = leadingContent
            )
        }
    }
}
