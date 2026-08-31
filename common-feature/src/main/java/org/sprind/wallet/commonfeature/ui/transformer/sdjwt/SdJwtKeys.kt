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

/**
 * Sd-jwt keys for credential type PID.
 *
 *
 * References:
 * - https://bmi.usercontent.opencode.de/eudi-wallet/eidas-2.0-architekturkonzept/content/ecosystem-architecture/PID/german-pid-rulebook/#german-mapping-rules-for-sd-jwt-vc
 * - https://preprod.pid-provider.bundesdruckerei.de/sdjwt
 */
object SdJwtKeys {
    const val FAMILY_NAME = "family_name"
    const val FAMILY_BIRTH_NAME = "birth_family_name"
    const val GIVEN_NAME = "given_name"
    const val ADDRESS = "address"
    const val BIRTHDATE = "birthdate"
    const val PLACE_OF_BIRTH = "place_of_birth"
    const val AGE_IN_YEARS = "age_in_years"
    const val AGE_BIRTH_YEAR = "age_birth_year"
    const val AGE_EQUAL_OR_OVER = "age_equal_or_over"
    const val ISSUED_AT = "iat"
    const val EXPIRED = "exp"
    const val ISSUING_AUTHORITY = "issuing_authority"
    const val ISSUING_COUNTRY = "issuing_country"
    const val NATIONALITIES = "nationalities"

    // nested key claims for 'address'
    const val ADDRESS_LOCALITY = "address.locality"
    const val ADDRESS_POSTAL_CODE = "address.postal_code"
    const val ADDRESS_STREET_ADDRESS = "address.street_address"
    const val ADDRESS_COUNTRY = "address.country"

    // nested key claims for 'place_of_birth'
    const val PLACE_OF_BIRTH_LOCALITY = "place_of_birth.locality"

    // nested key claims for 'age_equal_or_over'
    const val AGE_OVER_12 = "age_equal_or_over.12"
    const val AGE_OVER_14 = "age_equal_or_over.14"
    const val AGE_OVER_16 = "age_equal_or_over.16"
    const val AGE_OVER_18 = "age_equal_or_over.18"
    const val AGE_OVER_21 = "age_equal_or_over.21"
    const val AGE_OVER_65 = "age_equal_or_over.65"
}