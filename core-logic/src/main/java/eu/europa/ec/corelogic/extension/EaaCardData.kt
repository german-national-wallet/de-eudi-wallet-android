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
 * See the License for the specific language
 * governing permissions and limitations under the License.
 */

package eu.europa.ec.corelogic.extension

import java.net.URI
import java.time.Instant

/**
 * Pure UI data for the EAA (Electronic Attestation of Attributes) document card.
 *
 * Decouples the card composable from wallet-core document types. Produced by
 * [IssuedDocument.toEaaCardData] and [Offer.OfferedDocument.toEaaCardData]
 * factories defined in [EaaCardDataExtensions.kt].
 *
 * @param id Unique identifier (document id for issued docs, configuration id for offers)
 * @param description Display name of the credential type
 * @param name Display name of the issuer
 * @param backgroundColor CSS color string from issuer metadata, null for default
 * @param backgroundImageUri URI of background image, null if absent
 * @param logoUri URI of issuer logo, null if absent
 * @param validityDate Expiry instant, null if not available (e.g. at offer time)
 */
data class EaaCardData(
    val id: String,
    val description: String,
    val name: String,
    val backgroundColor: String?,
    val backgroundImageUri: URI?,
    val logoUri: URI?,
    val validityDate: Instant?,
)