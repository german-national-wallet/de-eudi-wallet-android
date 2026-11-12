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

package org.sprind.wallet.corelogic.presentationerror

import eu.europa.ec.eudi.openid4vp.AuthorizationPolicyValidationError
import eu.europa.ec.eudi.openid4vp.AuthorizationRequestError
import eu.europa.ec.eudi.openid4vp.AuthorizationRequestException
import eu.europa.ec.eudi.openid4vp.HttpError
import eu.europa.ec.eudi.openid4vp.RequestValidationError
import eu.europa.ec.eudi.openid4vp.ResolutionError
import org.sprind.wallet.corelogic.presentationerror.PresentationError.IntermittentSubType
import org.sprind.wallet.corelogic.presentationerror.PresentationError.RelyingPartySideSubType
import org.sprind.wallet.corelogic.presentationerror.PresentationError.WalletSideSubType
import org.sprind.wallet.networklogic.common.NetworkFailure
import org.sprind.wallet.networklogic.common.isConnectionFailure
import org.sprind.wallet.networklogic.common.isNetworkUnreachable
import org.sprind.wallet.networklogic.common.toNetworkFailure
import org.sprind.wallet.networklogic.utils.causeChain
import org.sprind.wallet.networklogic.utils.hasCause
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Prefix of the message carried by the `IllegalArgumentException` that
 * `OpenId4VpConfig.EncryptionPolicy.HAIP.enforce()` throws when the response mode is not
 * encrypted (see the `HAIP$lambda$0` frame in the wallet-core submodule). R8 preserves string
 * constants in minified builds, so this stays a reliable classifier.
 */
private const val ENCRYPTION_POLICY_MESSAGE_PREFIX = "HAIP profile requires an encrypted response mode"

/**
 * Message of the `IllegalStateException` reported when the verifier rejects our response:
 * `EudiWalletListenerWrapper` raises it for `TransferEvent.Rejected` from `sendResponse`, and
 * `OpenId4VpManager.reject` emits it in a `TransferEvent.Error`. Matched as a literal until
 * wallet-core exposes a typed exception for it (WD-4546: fork change WD-4547, upstream PR WD-4548).
 */
internal const val VERIFIER_REJECTION_MESSAGE = "Verifier rejected the response"

/**
 * Prefix of the message carried by the `IllegalStateException` that
 * `WalletCorePresentationController.getAndroidKeyUnlockDataIfRequired` throws when a credential
 * has no unlock data. The credential identifier is appended to this prefix.
 */
private const val MISSING_UNLOCK_DATA_MESSAGE_PREFIX = "Key data missing for credential"

/** The exchange with the relying party a presentation failure happened in (WD-5012). */
enum class PresentationStage {
    /** Fetching the relying party's request, before it was received. */
    REQUEST,

    /** Once the request was received: sending the response (or a rejection) to the relying party. */
    RESPONSE,
}

/**
 * Maps a presentation [Throwable] to a typed [PresentationError], classifying it into one of
 * the four WD-4267 buckets (relying-party-side, wallet-side, intermittent, unclassified).
 *
 * The mapper inspects [AuthorizationRequestError] hierarchy when the throwable is an
 * [AuthorizationRequestException], and falls back to inspecting the cause chain for [IOException]
 * subtypes when the throwable is a bare network/transport exception.
 *
 * @param throwable The failure emitted by the wallet-core transfer layer.
 * @param traceId The OpenTelemetry trace ID active when the error occurred (from
 *   [org.sprind.wallet.analyticslogic.controller.Telemetry.currentTraceId]).
 * @param genericErrorMessage A fallback human-readable string (from
 *   [eu.europa.ec.resourceslogic.provider.ResourceProvider.genericErrorMessage]) used as
 *   [PresentationError.displayMessage] when no better message is available.
 * @param stage The exchange with the relying party the failure happened in, or `null` when it
 *   did not come from that exchange. A timeout or connection failure is blamed on the relying
 *   party ([RelyingPartySideSubType.RELYING_PARTY_TIMEOUT]) only when the stage is known.
 */
fun mapToPresentationError(
    throwable: Throwable,
    traceId: String?,
    genericErrorMessage: String,
    stage: PresentationStage? = null,
): PresentationError {
    val displayMessage = throwable.localizedMessage ?: throwable.message ?: genericErrorMessage
    val factory = ErrorFactory(traceId, throwable, displayMessage, stage)

    // Typed errors are wrapped in AuthorizationRequestException(error: AuthorizationRequestError).
    // The exception may be the direct throwable or nested in a wrapper's cause chain.
    val niscyError = throwable.causeChain()
        .filterIsInstance<AuthorizationRequestException>()
        .firstOrNull()
        ?.error
        ?: throwable.causeChain()
            .filterIsInstance<AuthorizationRequestError>()
            .firstOrNull()

    if (niscyError != null) {
        return mapNiscyError(niscyError, factory)
    }

    // The wallet-core wrapper wraps DispatchOutcome.VerifierResponse.Rejected in a bare
    // IllegalStateException with a hardcoded message.
    // TODO(WD-4546): typed exception for this — fork change WD-4547, upstream PR WD-4548.
    if (isVerifierRejection(throwable)) {
        return factory.relyingParty(RelyingPartySideSubType.RELYING_PARTY_REJECTED)
    }

    // EncryptionPolicy.enforce() in OpenId4VpManager throws IllegalArgumentException when the
    // relying party asks for an unencrypted response mode (e.g. direct_post instead of
    // direct_post.jwt). The wallet correctly refuses; the relying party violated HAIP.
    // TODO(WD-4546): typed exception for this — fork change WD-4547, upstream PR WD-4548.
    if (isEncryptionPolicyFailure(throwable)) {
        return factory.relyingParty(RelyingPartySideSubType.RESPONSE_ENCRYPTION_REQUIRED)
    }

    // Key data missing for a credential — thrown by WalletCorePresentationController.
    if (isMissingUnlockData(throwable)) {
        return factory.wallet(WalletSideSubType.MISSING_UNLOCK_DATA)
    }

    // Network / IO errors not wrapped (e.g. from ktor during response dispatch).
    val ioCause = throwable.causeChain()
        .filterIsInstance<IOException>()
        .firstOrNull()
    if (ioCause != null) {
        return classifyIoError(ioCause, factory)
    }

    // Everything else is unclassified.
    return factory.unclassified()
}

/**
 * Nullable-throwable variant for failure paths that carry an optional cause (e.g.
 * [eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState.Failure]): a null
 * throwable maps straight to [PresentationError.Unclassified] with [genericErrorMessage].
 */
@JvmName("mapNullableToPresentationError")
fun mapToPresentationError(
    throwable: Throwable?,
    traceId: String?,
    genericErrorMessage: String,
): PresentationError =
    throwable?.let { mapToPresentationError(it, traceId, genericErrorMessage) }
        ?: PresentationError.Unclassified(
            traceId = traceId,
            cause = null,
            displayMessage = genericErrorMessage,
        )

/**
 * Captures the shared context (trace ID, original throwable, display message) that every
 * [PresentationError] variant needs, and provides one-liner factory methods so mapper
 * `when`-branches stay a single line without repeating the same three arguments.
 */
private class ErrorFactory(
    private val traceId: String?,
    private val cause: Throwable,
    private val displayMessage: String,
    val stage: PresentationStage?,
) {
    fun relyingParty(subType: RelyingPartySideSubType) =
        PresentationError.RelyingPartySide(traceId, cause, displayMessage, subType)

    fun wallet(subType: WalletSideSubType) =
        PresentationError.WalletSide(traceId, cause, displayMessage, subType)

    fun intermittent(subType: IntermittentSubType = IntermittentSubType.NETWORK) =
        PresentationError.Intermittent(traceId, cause, displayMessage, subType)

    fun unclassified() =
        PresentationError.Unclassified(traceId, cause, displayMessage)
}

private fun mapNiscyError(
    error: AuthorizationRequestError,
    factory: ErrorFactory,
): PresentationError = when (error) {
    // --- Resolution errors (fetching / parsing the request object) ---

    is ResolutionError.UnableToFetchRequestObject -> {
        // The relying party's request endpoint could not be reached or returned an error.
        // The cause distinguishes transient (timeout, connect refused) from non-transient.
        classifyIoError(error.cause, factory)
    }

    is ResolutionError.ClientVpFormatsNotSupportedFromWallet ->
        factory.relyingParty(RelyingPartySideSubType.UNSUPPORTED_FORMATS)

    is ResolutionError.UnknownScope ->
        factory.relyingParty(RelyingPartySideSubType.UNKNOWN_SCOPE)

    is ResolutionError.ClientMetadataJwksUnparsable,
    is ResolutionError.InvalidTransactionData,
    is ResolutionError.UnsupportedDcApiExchangeProtocol,
    is ResolutionError.DcApiExchangeProtocolNotMatchesReceivedRequest,
        -> factory.relyingParty(RelyingPartySideSubType.INVALID_REQUEST)

    // --- Request validation errors (36 subtypes, all relying-party-side protocol violations) ---

    is RequestValidationError ->
        factory.relyingParty(RelyingPartySideSubType.INVALID_REQUEST)

    // --- Trust / registration-certificate errors ---

    is AuthorizationPolicyValidationError ->
        factory.relyingParty(RelyingPartySideSubType.TRUST_FAILURE)

    // --- HTTP errors (relying party endpoint returned non-2xx or transport failed) ---

    is HttpError -> classifyIoError(error.cause, factory)

    // Any future AuthorizationRequestError subtype we don't know about yet.
    else -> factory.unclassified()
}

/**
 * Classifies a [Throwable] (the cause of an HTTP or request-fetch failure):
 * - HTTP 429 is [IntermittentSubType.RATE_LIMITED].
 * - A timeout while fetching the request, or a connection failure in either stage of the exchange
 *   with the relying party, is [RelyingPartySideSubType.RELYING_PARTY_TIMEOUT] (WD-5012). The
 *   connection to the response endpoint has already been retried by then (`retryConnectionFailures`).
 * - Other transient causes are [IntermittentSubType.NETWORK]: timeouts as classified by
 *   [toNetworkFailure] (including [SocketTimeoutException] and HTTP 408), connection failures,
 *   [SSLException], and a device without a route to the network ([NoRouteToHostException], or
 *   Android's `ENETUNREACH`, see [isNetworkUnreachable]), which is never blamed on the relying party.
 * - Everything else (e.g. a closed stream, a 503 wrapped in an IOException) is treated as
 *   relying-party-side unreachable — retrying immediately is unlikely to help.
 */
private fun classifyIoError(ioCause: Throwable, factory: ErrorFactory): PresentationError {
    val networkFailure = ioCause.toNetworkFailure()
    val isTimeout = networkFailure == NetworkFailure.Timeout
    val isConnectionFailure = ioCause.isConnectionFailure()
    return when {
        networkFailure is NetworkFailure.RateLimited ->
            factory.intermittent(IntermittentSubType.RATE_LIMITED)

        (factory.stage == PresentationStage.REQUEST && (isTimeout || isConnectionFailure)) ||
            (factory.stage == PresentationStage.RESPONSE && isConnectionFailure) ->
            factory.relyingParty(RelyingPartySideSubType.RELYING_PARTY_TIMEOUT)

        isTimeout || isConnectionFailure || ioCause.hasCause<SSLException>() ||
            ioCause.isNetworkUnreachable() ->
            factory.intermittent()

        else -> factory.relyingParty(RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE)
    }
}

/** Detects the `IllegalStateException` carrying [VERIFIER_REJECTION_MESSAGE]. */
private fun isVerifierRejection(throwable: Throwable): Boolean {
    val message = throwable.message ?: return false
    return throwable is IllegalStateException && message.contains(VERIFIER_REJECTION_MESSAGE)
}

/**
 * Detects the `IllegalArgumentException` thrown by `EncryptionPolicy.enforce()` in
 * `OpenId4VpManager`. Matched by message rather than stack trace: the HAIP policy is a SAM
 * lambda (`OpenId4VpConfig$EncryptionPolicy$Companion.HAIP$lambda$0`), so the frame class name
 * could be dropped by R8 inlining in minified builds, while string constants survive R8 and
 * the message is a stable literal from `OpenId4VpConfig.kt`.
 */
private fun isEncryptionPolicyFailure(throwable: Throwable): Boolean {
    if (throwable !is IllegalArgumentException) return false
    return throwable.message?.startsWith(ENCRYPTION_POLICY_MESSAGE_PREFIX) == true
}

/**
 * Detects the `IllegalStateException("Key data missing for credential ...")` thrown by
 * `WalletCorePresentationController.getAndroidKeyUnlockDataIfRequired`.
 */
private fun isMissingUnlockData(throwable: Throwable): Boolean {
    if (throwable !is IllegalStateException) return false
    return throwable.message?.contains(MISSING_UNLOCK_DATA_MESSAGE_PREFIX) == true
}
