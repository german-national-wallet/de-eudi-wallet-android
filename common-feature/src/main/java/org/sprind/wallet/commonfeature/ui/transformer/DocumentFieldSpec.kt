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
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.util.Locale

/**
 * Describes how a single Document claim should be presented in the document details UI.
 *
 * Each [DocumentFieldSpec] represents one logical field in the UI and defines:
 *
 * - [key]: The SD-JWT claim key (see [SdJwtKeys, MdocKeys]) used as the identifier for the field.
 * - [labelRes]: The string resource used as the UI label (overline text).
 * - [value]: A function responsible for extracting and formatting the display value
 *   from the raw SD-JWT claim map.
 *
 * The [value] lambda receives:
 * - the full map of flattened SD-JWT claims (`elementIdentifier -> DocumentItem`)
 * - the current device [java.util.Locale]
 * - the [eu.europa.ec.resourceslogic.provider.ResourceProvider] for localization and string resources
 *
 * It returns the final string value that should be displayed in the UI,
 * or `null` if the field should not be rendered.
 *
 * Example usage:
 *
 * ```
 * DocumentFieldSpec(
 *     key = SdJwtKeys.BIRTHDATE,
 *     labelRes = R.string.pid_issuance_data_consent_label_birth_date
 * ) { map, locale, _ ->
 *     map[SdJwtKeys.BIRTHDATE]?.value?.toDateFormatted(locale.toLanguageTag())
 * }
 * ```
 */
data class DocumentFieldSpec(
    val key: String,
    val labelRes: Int,
    val value: (Map<String, DocumentItem>, Locale, ResourceProvider) -> String?
)