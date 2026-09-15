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

import eu.europa.ec.corelogic.extension.toClaimPath
import eu.europa.ec.eudi.wallet.document.DocumentId
import org.multipaz.request.RequestedClaim

/**
 * The claims of one document that the user consented to disclose.
 *
 * Replaces the `DisclosedDocument` that wallet-core exposed up to v0.29.0. Since v0.30.0 a
 * response is generated from a `CredentialPresentmentSelection`, which is narrowed to
 * [disclosedClaims] before it is sent, so this carries the multipaz [RequestedClaim]s rather than
 * bare paths.
 *
 * @property documentId the document the claims belong to
 * @property disclosedClaims the requested claims the user consented to
 */
data class DisclosedDocumentDomain(
    val documentId: DocumentId,
    val disclosedClaims: Set<RequestedClaim>,
) {
    /** [disclosedClaims] as claim paths, for UI that reports on what was shared. */
    val disclosedClaimPaths: List<ClaimPathDomain>
        get() = disclosedClaims.map { it.toClaimPath() }
}
