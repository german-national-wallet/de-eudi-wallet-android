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

/**
 *
 * MDOC keys for credential type PID.
 *
 * References:
 * - https://bmi.usercontent.opencode.de/eudi-wallet/eidas-2.0-architekturkonzept/content/ecosystem-architecture/PID/german-pid-rulebook/#example-mdoc
 * - https://preprod.pid-provider.bundesdruckerei.de/msomdoc
 *
 */
object MdocKeys {
    const val FAMILY_NAME = "family_name"
    const val FAMILY_NAME_BIRTH = "family_name_birth"
    const val GIVEN_NAME = "given_name"
    const val RESIDENT_POSTAL_CODE = "resident_postal_code"
    const val RESIDENT_STREET = "resident_street"
    const val RESIDENT_COUNTRY = "resident_country"
    const val RESIDENT_CITY = "resident_city"
    const val ADDRESS = "address"
    const val BIRTH_DATE = "birth_date"
    const val EXPIRY_DATE = "expiry_date"
    const val ISSUANCE_DATE = "issuance_date"
    const val SOURCE_DOCUMENT_TYPE = "source_document_type"
    const val AGE_EQUAL_OR_OVER = "age_equal_or_over"

    const val ISSUING_AUTHORITY = "issuing_authority"
    const val ISSUING_COUNTRY = "issuing_country"
    const val NATIONALITY = "nationality"
    const val BIRTH_PLACE = "birth_place"

    val AGE_LIST = listOf(12, 14, 16, 18, 21, 65)
    fun ageOver(age: Int) = "age_over_$age"
}