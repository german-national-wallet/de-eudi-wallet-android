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

package org.sprind.wallet.commonfeature.ui.credentialdata

/**
 * One row of the credential data screen, built from the credential's claims and the issuer's
 * `credential_metadata.claims`.
 *
 * [id] is the claim path joined with dots (mdoc paths start with the namespace).
 */
sealed interface CredentialDataItem {
    val id: String

    /** [isMissing]: the PID metadata lists the claim but the credential has no value for it. */
    data class Attribute(
        override val id: String,
        val label: String,
        val value: String,
        val isMissing: Boolean = false,
    ) : CredentialDataItem

    /** The sub-headline of a nested SD-JWT object; its attributes follow it. */
    data class Heading(
        override val id: String,
        val title: String,
    ) : CredentialDataItem
}