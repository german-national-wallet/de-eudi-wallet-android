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

package org.sprind.wallet.commonfeature.ui.transformer.sdjwt

import eu.europa.ec.businesslogic.util.formatInstantToDateString
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.commonfeature.ui.transformer.DocumentFieldSpec
import java.lang.Boolean
import java.time.Instant
import java.util.Locale
import kotlin.String
import kotlin.let
import kotlin.takeIf

/**
 * Ordered schema describing how SD-JWT claims should be rendered in the document details UI.
 *
 * The schema acts as a declarative definition of:
 *
 * - which SD-JWT claims should be displayed
 * - in which order they should appear
 * - which label should be shown for each claim
 * - how the final display value should be computed
 */
val sdJwtSchema: List<DocumentFieldSpec> = listOf(
    DocumentFieldSpec(
        key = SdJwtKeys.FAMILY_NAME,
        labelRes = R.string.pid_issuance_data_consent_label_name
    ) { map, _, _ -> map[SdJwtKeys.FAMILY_NAME]?.value },

    DocumentFieldSpec(
        key = SdJwtKeys.FAMILY_BIRTH_NAME,
        labelRes = R.string.pid_issuance_data_consent_label_birth_name
    ) { map, _, _ -> map[SdJwtKeys.FAMILY_BIRTH_NAME]?.value },

    DocumentFieldSpec(
        key = SdJwtKeys.GIVEN_NAME,
        labelRes = R.string.pid_issuance_data_consent_label_first_names
    ) { map, _, _ -> map[SdJwtKeys.GIVEN_NAME]?.value },

    DocumentFieldSpec(
        key = SdJwtKeys.ADDRESS,
        labelRes = R.string.pid_issuance_data_consent_label_address
    ) { map, locale, rp ->
        val locality = map[SdJwtKeys.ADDRESS_LOCALITY]?.value.orEmpty()
        val postal = map[SdJwtKeys.ADDRESS_POSTAL_CODE]?.value.orEmpty()
        val street = map[SdJwtKeys.ADDRESS_STREET_ADDRESS]?.value.orEmpty()
        val countryCode = map[SdJwtKeys.ADDRESS_COUNTRY]?.value.orEmpty()

        val country = Locale("", countryCode).getDisplayCountry(locale).uppercase()
        val cityLine = listOf(postal, locality).filter(String::isNotBlank).joinToString(" ")
        listOf(street, cityLine, country).filter(String::isNotBlank).joinToString("\n")
            .takeIf(String::isNotBlank)
    },

    DocumentFieldSpec(
        key = SdJwtKeys.NATIONALITIES,
        labelRes = R.string.pid_issuance_data_consent_label_nationality
    ) { map, locale, _ ->
        map[SdJwtKeys.NATIONALITIES]?.value
            ?.let { Locale("", it).getDisplayCountry(locale).uppercase() }
    },

    DocumentFieldSpec(
        key = SdJwtKeys.BIRTHDATE,
        labelRes = R.string.pid_issuance_data_consent_label_birth_date
    ) { map, locale, _ ->
        map[SdJwtKeys.BIRTHDATE]?.value
            ?.toDateFormatted(locale.toLanguageTag())
    },

    DocumentFieldSpec(
        key = SdJwtKeys.PLACE_OF_BIRTH,
        labelRes = R.string.pid_issuance_data_consent_label_place_of_birth
    ) { map, _, _ ->
        map[SdJwtKeys.PLACE_OF_BIRTH_LOCALITY]?.value
    },

    DocumentFieldSpec(
        key = SdJwtKeys.AGE_EQUAL_OR_OVER,
        labelRes = R.string.pid_issuance_data_consent_label_age_equal_or_over
    ) { map, _, rp ->
        val ages = listOf(12, 14, 16, 18, 21, 65)
        ages.joinToString("\n") { age ->
            val raw = map["${SdJwtKeys.AGE_EQUAL_OR_OVER}.$age"]?.value
            val isTrue = Boolean.valueOf(raw)
            val result = if (isTrue)
                rp.getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_yes)
            else
                rp.getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_no)

            val label = rp.getString(R.string.document_details_data_age_over, age.toString())
            "$label $result"
        }.takeIf(String::isNotBlank)
    },

    DocumentFieldSpec(
        key = SdJwtKeys.ISSUING_AUTHORITY,
        labelRes = R.string.pid_issuance_data_consent_label_issuing_authority
    ) { map, locale, _ ->
        map[SdJwtKeys.ISSUING_AUTHORITY]?.value
            ?.let { Locale("", it).getDisplayCountry(locale).uppercase() }
    },

    DocumentFieldSpec(
        key = SdJwtKeys.ISSUING_COUNTRY,
        labelRes = R.string.pid_issuance_data_consent_label_issuing_country
    ) { map, locale, _ ->
        map[SdJwtKeys.ISSUING_COUNTRY]?.value
            ?.let { Locale("", it).getDisplayCountry(locale).uppercase() }
    },

    DocumentFieldSpec(
        key = SdJwtKeys.ISSUED_AT,
        labelRes = R.string.pid_issuance_data_consent_label_created_at
    ) { map, locale, _ ->
        map[SdJwtKeys.ISSUED_AT]?.value?.toLongOrNull()
            ?.let { Instant.ofEpochSecond(it).formatInstantToDateString(locale = locale) }
    },

    DocumentFieldSpec(
        key = SdJwtKeys.EXPIRED,
        labelRes = R.string.pid_issuance_data_consent_label_expire_date
    ) { map, locale, _ ->
        map[SdJwtKeys.EXPIRED]?.value?.toLongOrNull()
            ?.let { Instant.ofEpochSecond(it).formatInstantToDateString(locale = locale) }
    },
)
