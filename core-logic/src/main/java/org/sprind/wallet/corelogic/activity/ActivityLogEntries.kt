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

import eu.europa.ec.corelogic.extension.toSelectivelyDisclosableClaimPaths
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificateResult
import eu.europa.ec.eudi.iso18013.transfer.response.WrpRegistrationInfo
import org.sprind.wallet.corelogic.model.LocalizedTextDomain
import org.sprind.wallet.corelogic.model.toDomain
import java.util.UUID

fun Document.toActivityCredential(
    requestedClaims: List<List<String>> = emptyList(),
    presentedClaims: List<List<String>> = emptyList(),
    withHeldClaims: Boolean = false,
): ActivityCredential {
    val claimPaths = (this as? IssuedDocument)?.data?.claims
        ?.flatMap { claim -> claim.toSelectivelyDisclosableClaimPaths().map { it.value } }
    val heldClaims = if (withHeldClaims) claimPaths.orEmpty() else emptyList()
    return ActivityCredential(
        documentId = id,
        credentialIdentifier = toDocumentIdentifier().formatType,
        issuerMetadataJson = issuerMetadata
            ?.snapshot(involvedClaims = requestedClaims + presentedClaims + heldClaims)
            ?.toJson(),
        requestedClaims = requestedClaims,
        presentedClaims = presentedClaims,
        heldClaims = heldClaims,
        totalClaimCount = claimPaths?.size,
    )
}

/** What the log keeps of the issuer's metadata: the display data, and the labels of only the claims the activity involved. */
internal fun IssuerMetadata.snapshot(involvedClaims: List<List<String>>): IssuerMetadata = copy(
    claims = claims?.filter { claim ->
        involvedClaims.any { path -> path == claim.path || path.lastOrNull() == claim.path.lastOrNull() }
    },
)

/** The relying party as its registration certificate describes it, when that validated. */
fun relyingParty(
    name: String?,
    isTrusted: Boolean,
    registration: WrpRegistrationInfo?,
): InteractingParty {
    val certificate = registration.verifiedCertificate()
    return InteractingParty(
        name = certificate?.name ?: certificate?.legalName ?: name,
        isTrusted = isTrusted,
        privacyPolicyUri = certificate?.privacyPolicyUri,
        supportUri = certificate?.supportUri,
        infoUri = certificate?.infoUri,
        registrarUri = certificate?.registryUri,
        supervisoryAuthorityUri = certificate?.supervisoryAuthority?.uri,
        isIntermediary = certificate?.intermediary != null,
    )
}

/**
 * The issuer as its registration certificate describes it, when that validated, otherwise as the
 * first issued credential's metadata names it.
 */
fun issuer(
    credentials: List<Document>,
    registration: RegistrationCertificateResult? = null,
): InteractingParty {
    val metadata = credentials.firstNotNullOfOrNull { it.issuerMetadata }
    val certificate = registration.verifiedCertificate()
    return InteractingParty(
        name = metadata?.issuerDisplay?.firstOrNull()?.name ?: certificate?.name ?: certificate?.legalName,
        isTrusted = true,
        privacyPolicyUri = certificate?.privacyPolicyUri,
        supportUri = certificate?.supportUri,
        infoUri = certificate?.infoUri ?: metadata?.credentialIssuerIdentifier,
        registrarUri = certificate?.registryUri,
        supervisoryAuthorityUri = certificate?.supervisoryAuthority?.uri,
    )
}

/** The purpose a validated registration certificate declares. */
fun WrpRegistrationInfo?.registeredPurpose(): List<LocalizedTextDomain> =
    verifiedCertificate()?.purpose.orEmpty().map { it.toDomain() }

private fun WrpRegistrationInfo?.verifiedCertificate() = (this as? RegistrationCertificateResult.Verified)?.registration

fun newActivityLogEntry(
    type: ActivityType,
    result: ActivityResult,
    interactingParty: InteractingParty,
    credentials: List<ActivityCredential>,
    reasonOfNoncompletion: String? = null,
    rejection: ActivityRejection? = null,
    purpose: List<LocalizedTextDomain> = emptyList(),
    timestampMillis: Long = System.currentTimeMillis(),
): ActivityLogEntry = ActivityLogEntry(
    id = UUID.randomUUID().toString(),
    timestampMillis = timestampMillis,
    type = type,
    result = result,
    reasonOfNoncompletion = reasonOfNoncompletion,
    rejection = rejection,
    interactingParty = interactingParty,
    credentials = credentials,
    purpose = purpose,
)
