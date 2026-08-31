/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.issuancefeature.interactor.document

import eu.europa.ec.corelogic.extension.EaaCardData
import java.net.URI

/**
 * UI-domain representation of a resolved credential offer.
 *
 * This is the issuance-feature-side mirror of the wallet-core `Offer`:
 * it carries only the fields the UI needs, and decouples the viewmodel
 * layer from the `eu.europa.ec.eudi.wallet.issue.openid4vci` package.
 *
 * A [ResolvedOffer] is produced by [DocumentOfferInteractor.resolveDocumentOffer]
 * on success and cached so the issuance path can reuse it without
 * re-fetching the offer.
 */
data class ResolvedOffer(
    val offerUri: String,
    val issuerName: String,
    val issuerLogo: URI?,
    val documents: List<ResolvedOfferDocument>,
    val txCodeLength: Int?,
)

/**
 * One document offered inside a [ResolvedOffer].
 *
 * Mirrors the fields of `DocumentOfferItemUi` that the UI consumes.
 */
data class ResolvedOfferDocument(
    val id: String,
    val title: String,
    val details: List<Pair<String, String>>,
    val eaaCardData: EaaCardData? = null,
)