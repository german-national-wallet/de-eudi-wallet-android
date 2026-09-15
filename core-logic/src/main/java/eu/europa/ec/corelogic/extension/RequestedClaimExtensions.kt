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
import eu.europa.ec.corelogic.model.ClaimPathDomain.Companion.toClaimPathDomain
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.request.JsonRequestedClaim
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim

/**
 * The requested claim's path as the UI models it.
 *
 * Replaces `DocItem.toClaimPath()`: since wallet-core v0.30.0 a request carries multipaz
 * [RequestedClaim]s instead of the `DocItem`/`MsoMdocItem`/`SdJwtVcItem` family. Mdoc claims keep
 * yielding just the data element name, as `MsoMdocItem.elementIdentifier` did; use
 * [toNamespacedPath] where the namespace is needed too.
 */
fun RequestedClaim.toClaimPath(): ClaimPathDomain {
    return when (this) {
        is MdocRequestedClaim -> listOf(dataElementName)
        is JsonRequestedClaim -> claimPath.jsonPathSegments()
    }.toClaimPathDomain()
}

/**
 * The requested claim's path including the namespace for mdoc claims, matching what
 * `DocItem.toPath()` produced.
 */
fun RequestedClaim.toNamespacedPath(): List<String> {
    return when (this) {
        is MdocRequestedClaim -> listOf(namespaceName, dataElementName)
        is JsonRequestedClaim -> claimPath.jsonPathSegments()
    }
}

private fun Iterable<*>.jsonPathSegments(): List<String> =
    mapNotNull { (it as? JsonPrimitive)?.content }
