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

package eu.europa.ec.corelogic.extension

import eu.europa.ec.corelogic.model.ClaimPathDomain
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim

/**
 * The claim's own name.
 *
 * wallet-core up to v0.29.0 had this on [DocumentClaim] itself; since v0.30.0 it is split per
 * format, into [MsoMdocClaim.dataElementName] and [SdJwtVcClaim]'s [ClaimPathElement][
 * org.multipaz.request.RequestedClaim] name.
 */
val DocumentClaim.identifier: String
    get() = when (this) {
        is MsoMdocClaim -> dataElementName
        is SdJwtVcClaim -> claimName.orEmpty()
    }

/**
 * The paths of the leaf claims the holder can choose to disclose, which is what the "x of y"
 * consent headline counts.
 *
 * An SD-JWT object is walked down to its leaves, an array counts as one leaf, and a leaf counts
 * when it or anything below it has a Disclosure. Plain claims such as `iat` and `exp` are always
 * sent and so are left out; `iss`, `vct`, `cnf` and `status` never reach the claim tree. Every
 * mdoc data element is selectively disclosable; its path includes the namespace.
 */
fun DocumentClaim.toSelectivelyDisclosableClaimPaths(
    parentPath: List<String> = emptyList()
): List<ClaimPathDomain> {
    return when (this) {
        is SdJwtVcClaim -> {
            val currentPath: List<String> = parentPath + this.identifier
            if (children.isEmpty() || this.value is Collection<*>) {
                if (isOrContainsSelectivelyDisclosable()) listOf(ClaimPathDomain(currentPath))
                else emptyList()
            } else {
                children.flatMap { child ->
                    child.toSelectivelyDisclosableClaimPaths(currentPath)
                }
            }
        }

        is MsoMdocClaim -> listOf(ClaimPathDomain(listOf(nameSpace, dataElementName)))
    }
}

private fun SdJwtVcClaim.isOrContainsSelectivelyDisclosable(): Boolean =
    selectivelyDisclosable || children.any { it.isOrContainsSelectivelyDisclosable() }
