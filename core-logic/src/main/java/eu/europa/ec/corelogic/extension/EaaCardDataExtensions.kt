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

import eu.europa.ec.businesslogic.extension.getLocalizedValue
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import java.time.Instant
import java.util.Locale

/**
 * Far-future sentinel used to filter out implausible validity dates (e.g. issuers
 * that return year-9999-style values to mean "never expires"). Validity instants
 * at or after ~year 5138 are treated as "no real expiry" and dropped to null so
 * the EAA card hides the validity badge instead of showing a meaningless date.
 */
private val MAX_PLAUSIBLE_VALIDITY: Instant =
    Instant.ofEpochSecond(100_000_000_000L)

/**
 * Maps an [IssuedDocument] to [EaaCardData] for display by the EAA card composable.
 *
 * Resolves the localized issuer metadata for [locale], derives a credential name
 * from the metadata display name (falling back to the format-specific type), and
 * reads the validity instant from [IssuedDocument.getValidUntil]. Must be called
 * in a suspend context because [IssuedDocument.getValidUntil] is suspend.
 */
suspend fun IssuedDocument.toEaaCardData(locale: Locale): EaaCardData {
    val metadata = localizedIssuerMetadata(locale)
    val credentialType = when (val format = format) {
        is MsoMdocFormat -> format.docType
        is SdJwtVcFormat -> format.vct
    }
    val description = metadata?.description ?: credentialType
    val name = metadata?.name ?: credentialType
    val validityDate = getValidUntil()
        .getOrNull()
        ?.takeIf { it.isBefore(MAX_PLAUSIBLE_VALIDITY) }
    return EaaCardData(
        id = id,
        description = description,
        name = name,
        backgroundColor = metadata?.backgroundColor,
        backgroundImageUri = metadata?.backgroundImageUri,
        logoUri = metadata?.logo?.uri,
        validityDate = validityDate,
    )
}

/**
 * Maps an [Offer.OfferedDocument] to [EaaCardData] for display on the issuance
 * offer screen.
 *
 * Resolves the localized credential display name from the configuration metadata
 * (falling back to the format-specific type), and pulls branding (background
 * color/image, logo) from the per-credential display. Validity date is not
 * available at offer time, so it is always null.
 */
fun Offer.OfferedDocument.toEaaCardData(locale: Locale): EaaCardData {
    val display = configuration.credentialMetadata?.display?.getLocalizedValue(
        userLocale = locale,
        localeExtractor = { it.locale },
        valueExtractor = { it },
        fallback = null,
    )
    val credentialType = when (val format = documentFormat) {
        is MsoMdocFormat -> format.docType
        is SdJwtVcFormat -> format.vct
        null -> configurationIdentifier.toString()
    }
    val name = display?.name ?: credentialType
    return EaaCardData(
        id = configurationIdentifier.toString(),
        description = name,
        name = name,
        backgroundColor = display?.backgroundColor,
        backgroundImageUri = display?.backgroundImage,
        logoUri = display?.logo?.uri,
        validityDate = null,
    )
}