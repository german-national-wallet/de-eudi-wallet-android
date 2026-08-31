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

package org.sprind.wallet.commonfeature.ui.transformer.mdoc

import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.commonfeature.ui.transformer.DocumentFieldSpec
import java.lang.Boolean
import java.util.Locale
import kotlin.String
import kotlin.let
import kotlin.takeIf

/**
 * Ordered schema describing how MDOC claims should be rendered in the document details UI.
 *
 * The schema acts as a declarative definition of:
 *
 * - which MDOC claims should be displayed
 * - in which order they should appear
 * - which label should be shown for each claim
 * - how the final display value should be computed
 */
val mdocSchema = listOf(
    DocumentFieldSpec(
        key = MdocKeys.FAMILY_NAME,
        labelRes = R.string.pid_issuance_data_consent_label_name
    ) { map, _, _ -> map[MdocKeys.FAMILY_NAME]?.value },

    DocumentFieldSpec(
        key = MdocKeys.FAMILY_NAME_BIRTH,
        labelRes = R.string.pid_issuance_data_consent_label_birth_name
    ) { map, _, _ -> map[MdocKeys.FAMILY_NAME_BIRTH]?.value },

    DocumentFieldSpec(
        key = MdocKeys.GIVEN_NAME,
        labelRes = R.string.pid_issuance_data_consent_label_first_names
    ) { map, _, _ -> map[MdocKeys.GIVEN_NAME]?.value },

    // Address composed from resident_* fields
    DocumentFieldSpec(
        key = MdocKeys.ADDRESS,
        labelRes = R.string.pid_issuance_data_consent_label_address
    ) { map, locale, _ ->
        val street = map[MdocKeys.RESIDENT_STREET]?.value.orEmpty()
        val postal = map[MdocKeys.RESIDENT_POSTAL_CODE]?.value.orEmpty()
        val city = map[MdocKeys.RESIDENT_CITY]?.value.orEmpty()
        val countryCode = map[MdocKeys.RESIDENT_COUNTRY]?.value.orEmpty()

        val country = Locale("", countryCode).getDisplayCountry(locale).uppercase()
        val cityLine = listOf(postal, city).filter(String::isNotBlank).joinToString(" ")
        listOf(street, cityLine, country).filter(String::isNotBlank).joinToString("\n")
            .takeIf(String::isNotBlank)
    },

    DocumentFieldSpec(
        key = MdocKeys.NATIONALITY,
        labelRes = R.string.pid_issuance_data_consent_label_nationality
    ) { map, locale, rp ->
        val code = map[MdocKeys.NATIONALITY]?.value ?: return@DocumentFieldSpec null
        when (code) {
            "DE" -> rp.getString(R.string.document_details_data_nationality_german)
            else -> Locale("", code).getDisplayCountry(locale).uppercase()
        }
    },

    DocumentFieldSpec(
        key = MdocKeys.BIRTH_DATE,
        labelRes = R.string.pid_issuance_data_consent_label_birth_date
    ) { map, _, _ -> map[MdocKeys.BIRTH_DATE]?.value },

    DocumentFieldSpec(
        key = MdocKeys.BIRTH_PLACE,
        labelRes = R.string.pid_issuance_data_consent_label_place_of_birth
    ) { map, _, _ ->
        map[MdocKeys.BIRTH_PLACE]?.value
            ?.substringAfter(":")
            ?.trim()
            ?.removeSurrounding("\"")
    },

    // Age composed from age_over_* fields
    DocumentFieldSpec(
        key = MdocKeys.AGE_EQUAL_OR_OVER,
        labelRes = R.string.pid_issuance_data_consent_label_age_equal_or_over
    ) { map, _, rp ->
        MdocKeys.AGE_LIST.joinToString("\n") { age ->
            val key = MdocKeys.ageOver(age)
            val isTrue = Boolean.valueOf(map[key]?.value)

            val result = if (isTrue) {
                rp.getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_yes)
            } else {
                rp.getString(R.string.pid_issuance_data_consent_label_age_equal_or_over_no)
            }

            val label = rp.getString(R.string.document_details_data_age_over, age)
            // 21 Jahre: Ja/Nein
            "$label $result"
        }.takeIf(String::isNotBlank)
    },

    DocumentFieldSpec(
        key = MdocKeys.ISSUING_AUTHORITY,
        labelRes = R.string.pid_issuance_data_consent_label_issuing_authority
    ) { map, locale, _ ->
        map[MdocKeys.ISSUING_AUTHORITY]?.value
            ?.let { Locale("", it).getDisplayCountry(locale).uppercase() }
    },

    DocumentFieldSpec(
        key = MdocKeys.ISSUING_COUNTRY,
        labelRes = R.string.pid_issuance_data_consent_label_issuing_country
    ) { map, locale, _ ->
        map[MdocKeys.ISSUING_COUNTRY]?.value
            ?.let { Locale("", it).getDisplayCountry(locale).uppercase() }
    },

    DocumentFieldSpec(
        key = MdocKeys.ISSUANCE_DATE,
        labelRes = R.string.pid_issuance_data_consent_label_created_at
    ) { map, _, _ -> map[MdocKeys.ISSUANCE_DATE]?.value },

    DocumentFieldSpec(
        key = MdocKeys.EXPIRY_DATE,
        labelRes = R.string.pid_issuance_data_consent_label_expire_date
    ) { map, _, _ -> map[MdocKeys.EXPIRY_DATE]?.value },
)