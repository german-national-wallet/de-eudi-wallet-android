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

package org.sprind.wallet.corelogic.activity

import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import kotlinx.serialization.Serializable
import org.sprind.wallet.corelogic.model.LocalizedTextDomain

/**
 * One entry of the wallet's activity log, modelled on the TS10 transaction log entry. Claim
 * values are never stored, only claim paths.
 */
@Serializable
data class ActivityLogEntry(
    val id: String,
    val timestampMillis: Long,
    val type: ActivityType,
    val result: ActivityResult,
    val reasonOfNoncompletion: String? = null,
    /** Who stopped a presentation that did not complete; null when it failed or was interrupted. */
    val rejection: ActivityRejection? = null,
    val interactingParty: InteractingParty,
    val credentials: List<ActivityCredential>,
    /** The purpose the relying party registered for the request. */
    val purpose: List<LocalizedTextDomain> = emptyList(),
)

@Serializable
enum class ActivityType { ISSUANCE, PRESENTATION, DELETION }

@Serializable
enum class ActivityRejection {
    BY_USER,

    /** The wallet declined because the request asked for more than the relying party registered. */
    OVERASKING,

    /** The wallet declined because the relying party's registration is absent or invalid. */
    UNKNOWN_SERVICE,

    /** The relying party rejected the wallet's response. */
    BY_SERVICE,
}

@Serializable
enum class ActivityResult { COMPLETED, NOT_COMPLETED }

@Serializable
data class InteractingParty(
    val name: String?,
    val isTrusted: Boolean,
    val privacyPolicyUri: String? = null,
    val supportUri: String? = null,
    val infoUri: String? = null,
    val registrarUri: String? = null,
    val supervisoryAuthorityUri: String? = null,
    val isIntermediary: Boolean = false,
)

/**
 * A credential involved in an activity. [issuerMetadataJson] is a snapshot taken when the
 * activity happened, so names and claim labels stay readable after the credential is deleted.
 */
@Serializable
data class ActivityCredential(
    val documentId: String,
    val credentialIdentifier: String,
    val issuerMetadataJson: String? = null,
    val requestedClaims: List<List<String>> = emptyList(),
    val presentedClaims: List<List<String>> = emptyList(),
    /** Every claim the credential held, kept for issuance and deletion. */
    val heldClaims: List<List<String>> = emptyList(),
    /** How many claims the credential held, the "out of" figure the request screen shows. */
    val totalClaimCount: Int? = null,
) {
    // Delegated, so not serialized; parsed once per entry.
    val issuerMetadata: IssuerMetadata? by lazy {
        issuerMetadataJson?.let { IssuerMetadata.fromJson(it).getOrNull() }
    }
}
