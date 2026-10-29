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

package org.sprind.wallet.corelogic.model

import eu.europa.ec.eudi.iso18013.transfer.response.WrpRegistrationInfo
import eu.europa.ec.eudi.openid4vp.AuthorizationPolicyValidationError
import eu.europa.ec.eudi.openid4vp.AuthorizationRequestException
import eu.europa.ec.eudi.wallet.registration.LocalizedText
import eu.europa.ec.eudi.wallet.registration.OverAskedClaim
import eu.europa.ec.eudi.wallet.registration.RegistrationCertificateResult
import eu.europa.ec.eudi.wallet.registration.RegistrationFailureReason
import kotlinx.serialization.Serializable
import java.util.Locale

/** What the relying party's registration certificate said about the request. */
sealed interface RelyingPartyRegistrationStatus {

    /** No registration policy applied to this request, it proceeds as before. */
    data object NotEvaluated : RelyingPartyRegistrationStatus

    data class WithinRegisteredScope(
        val purpose: List<LocalizedTextDomain> = emptyList(),
        val supervisoryAuthorityUri: String? = null,
        val supportUri: String? = null,
    ) : RelyingPartyRegistrationStatus

    /** The certificate validated, but the request asks for claims it does not register. */
    data class OverAsking(
        val overAskedClaims: List<OverAskedClaimDomain>,
        val supervisoryAuthorityUri: String? = null,
    ) : RelyingPartyRegistrationStatus

    /** The certificate is absent or failed validation. */
    data class NotRegistered(
        val reason: RegistrationFailureReason,
    ) : RelyingPartyRegistrationStatus
}

data class OverAskedClaimDomain(
    val format: String,
    val path: String,
)

@Serializable
data class LocalizedTextDomain(
    val language: String,
    val value: String,
)

/** Exact language tag first, then the same language, then English, then the first text. */
fun List<LocalizedTextDomain>.forLocale(locale: Locale): String? {
    val texts = filter { it.value.isNotBlank() }
    fun LocalizedTextDomain.languageOnly() = Locale.forLanguageTag(language).language
    return (texts.firstOrNull { it.language.equals(locale.toLanguageTag(), ignoreCase = true) }
        ?: texts.firstOrNull { it.languageOnly() == locale.language }
        ?: texts.firstOrNull { it.languageOnly() == Locale.ENGLISH.language }
        ?: texts.firstOrNull())
        ?.value
}

val RelyingPartyRegistrationStatus.rejectsRequest: Boolean
    get() = when (this) {
        is RelyingPartyRegistrationStatus.OverAsking,
        is RelyingPartyRegistrationStatus.NotRegistered,
            -> true

        RelyingPartyRegistrationStatus.NotEvaluated,
        is RelyingPartyRegistrationStatus.WithinRegisteredScope,
            -> false
    }

/** The relying party's own support contact (WRPRC `support_uri`), from a validated certificate only. */
val RelyingPartyRegistrationStatus.supportUri: String?
    get() = (this as? RelyingPartyRegistrationStatus.WithinRegisteredScope)?.supportUri

val RelyingPartyRegistrationStatus.reportUri: String?
    get() = when (this) {
        is RelyingPartyRegistrationStatus.OverAsking -> supervisoryAuthorityUri
        is RelyingPartyRegistrationStatus.WithinRegisteredScope -> supervisoryAuthorityUri
        RelyingPartyRegistrationStatus.NotEvaluated,
        is RelyingPartyRegistrationStatus.NotRegistered,
            -> null
    }

fun WrpRegistrationInfo?.toRelyingPartyRegistrationStatus(): RelyingPartyRegistrationStatus =
    when (this) {
        null -> RelyingPartyRegistrationStatus.NotEvaluated

        is RegistrationCertificateResult -> toRelyingPartyRegistrationStatus()

        else -> RelyingPartyRegistrationStatus.NotRegistered(RegistrationFailureReason.MALFORMED)
    }

private fun RegistrationCertificateResult.toRelyingPartyRegistrationStatus(): RelyingPartyRegistrationStatus =
    when (this) {
        is RegistrationCertificateResult.Failed ->
            RelyingPartyRegistrationStatus.NotRegistered(reason)

        is RegistrationCertificateResult.Verified -> {
            // Only a validated certificate's authority can be trusted as a report target.
            val supervisoryAuthorityUri = registration.supervisoryAuthority?.uri
                ?.takeIf { it.isNotBlank() }
            if (overAskedClaims.isEmpty()) {
                RelyingPartyRegistrationStatus.WithinRegisteredScope(
                    purpose = registration.purpose.map { it.toDomain() },
                    supervisoryAuthorityUri = supervisoryAuthorityUri,
                    supportUri = registration.supportUri?.takeIf { it.isNotBlank() },
                )
            } else {
                RelyingPartyRegistrationStatus.OverAsking(
                    overAskedClaims = overAskedClaims.map { it.toDomain() },
                    supervisoryAuthorityUri = supervisoryAuthorityUri,
                )
            }
        }
    }

/** openid4vp rejects these while resolving the request, before wallet-core validates anything. */
fun Throwable.toNotRegisteredOrNull(): RelyingPartyRegistrationStatus.NotRegistered? {
    val reason = when ((this as? AuthorizationRequestException)?.error) {
        AuthorizationPolicyValidationError.MissingRequiredRegistrationCertificate ->
            RegistrationFailureReason.CERTIFICATE_ABSENT

        AuthorizationPolicyValidationError.MultipleRegistrationCertificates,
        is AuthorizationPolicyValidationError.MalformedRegistrationCertificate,
            -> RegistrationFailureReason.MALFORMED

        else -> return null
    }
    return RelyingPartyRegistrationStatus.NotRegistered(reason)
}

private fun OverAskedClaim.toDomain(): OverAskedClaimDomain = OverAskedClaimDomain(
    format = format,
    path = path.joinToString(separator = "/"),
)

fun LocalizedText.toDomain(): LocalizedTextDomain = LocalizedTextDomain(
    language = language,
    value = value,
)
