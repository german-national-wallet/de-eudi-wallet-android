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

/**
 * A presentation failure classified into one of the four buckets defined by WD-4267:
 * relying-party-side, wallet-side, intermittent, or unclassified.
 *
 * Each instance carries enough context for the UI (WD-4274) to pick a user-facing title,
 * body, and recommended action, plus a [traceId] / [errorCode] the user can quote to support.
 *
 * Until WD-4274 wires bucket-specific strings, callers fall back to [displayMessage].
 *
 * @property bucket One of [ErrorBucket] — the top-level classification.
 * @property errorCode A stable, machine-readable code identifying the specific failure
 *   (e.g. `"RELYING_PARTY_INVALID_REQUEST"`, `"WALLET_NO_NETWORK"`, `"UNKNOWN"`). Derived
 *   from the [subType] (or a constant for buckets without a subType), so it can never drift
 *   out of sync. Unrecognised codes are preserved verbatim so support can still look them up.
 * @property traceId The OpenTelemetry trace ID active when the error occurred, or a
 *   backend-returned `trace_id` when available. Never `null` in practice (Telemetry
 *   always provides one) but typed nullable for safety.
 * @property recommendedAction What the UI should offer the user next. WD-4274 uses this
 *   to decide whether to show a retry button, a "contact relying party" button, etc.
 * @property cause The original throwable, retained for debugging/logging. May be `null`
 *   for errors synthesised without an underlying exception.
 * @property displayMessage A human-readable fallback string for the current generic UI.
 *   WD-4274 replaces this with bucket-specific string resources.
 */
sealed interface PresentationError {

    val bucket: ErrorBucket
    val errorCode: String
    val traceId: String?
    val recommendedAction: RecommendedAction
    val cause: Throwable?
    val displayMessage: String

    /**
     * The verifier/relying party is responsible: malformed request, unsupported formats,
     * relying party rejected, etc.
     */
    data class RelyingPartySide(
        override val traceId: String?,
        override val cause: Throwable?,
        override val displayMessage: String,
        val subType: RelyingPartySideSubType,
    ) : PresentationError {
        override val bucket: ErrorBucket = ErrorBucket.RELYING_PARTY_SIDE
        override val errorCode: String get() = subType.errorCode
        override val recommendedAction: RecommendedAction get() = subType.recommendedAction
    }

    /** The wallet is responsible: missing unlock data, no network, etc. */
    data class WalletSide(
        override val traceId: String?,
        override val cause: Throwable?,
        override val displayMessage: String,
        val subType: WalletSideSubType,
    ) : PresentationError {
        override val bucket: ErrorBucket = ErrorBucket.WALLET_SIDE
        override val errorCode: String get() = subType.errorCode
        override val recommendedAction: RecommendedAction get() = subType.recommendedAction
    }

    /** A transient failure that may succeed on retry: network timeout, relying party temporarily unreachable. */
    data class Intermittent(
        override val traceId: String?,
        override val cause: Throwable?,
        override val displayMessage: String,
        val subType: IntermittentSubType,
    ) : PresentationError {
        override val bucket: ErrorBucket = ErrorBucket.INTERMITTENT
        override val errorCode: String get() = subType.errorCode
        override val recommendedAction: RecommendedAction get() = subType.recommendedAction
    }

    /** Could not be classified. The UI must still show a unique error reference for support. */
    data class Unclassified(
        override val traceId: String?,
        override val cause: Throwable?,
        override val displayMessage: String,
    ) : PresentationError {
        override val bucket: ErrorBucket = ErrorBucket.UNCLASSIFIED
        override val errorCode: String get() = ERROR_CODE_UNKNOWN
        override val recommendedAction: RecommendedAction = RecommendedAction.DISMISS
    }

    enum class ErrorBucket { RELYING_PARTY_SIDE, WALLET_SIDE, INTERMITTENT, UNCLASSIFIED }

    /**
     * What the UI should offer the user after showing the error.
     *
     * WD-4274 maps each value to a button (or no button) in the error dialog / inline error.
     */
    enum class RecommendedAction {
        /** Re-run the failed operation immediately (e.g. re-fetch the request). */
        RETRY,

        /** Re-run later; the user dismisses for now (no immediate retry is offered). */
        RETRY_LATER,

        /** Ask the user to check their network connection. */
        CHECK_CONNECTION,

        /** Direct the user to contact the verifier/relying party. */
        CONTACT_RELYING_PARTY,

        /** Direct the user to contact wallet support, quoting the error code / trace ID. */
        CONTACT_SUPPORT,

        /** Just dismiss — no action is meaningful. */
        DISMISS,
    }

    /**
     * Sub-classification of [RelyingPartySide] errors for finer-grained UI strings (WD-4274).
     *
     * Each constant carries its own [errorCode] and [recommendedAction], so a data-class
     * instance never needs to repeat them.
     */
    enum class RelyingPartySideSubType(
        override val errorCode: String,
        override val recommendedAction: RecommendedAction,
    ) : HasErrorCodeAndAction {
        /** The relying party's request violated the OpenID4VP / SIOPv2 spec ([RequestValidationError]). */
        INVALID_REQUEST("RELYING_PARTY_INVALID_REQUEST", RecommendedAction.CONTACT_RELYING_PARTY),

        /** The relying party rejected the wallet's VP token response. */
        RELYING_PARTY_REJECTED("RELYING_PARTY_REJECTED", RecommendedAction.CONTACT_RELYING_PARTY),

        /** The relying party was unreachable or returned a non-transient HTTP error. */
        RELYING_PARTY_UNREACHABLE("RELYING_PARTY_UNREACHABLE", RecommendedAction.RETRY_LATER),

        /** Registration-certificate / trust chain validation failed. */
        TRUST_FAILURE("RELYING_PARTY_TRUST_FAILURE", RecommendedAction.CONTACT_RELYING_PARTY),

        /** The wallet cannot satisfy the credential formats the relying party asked for. */
        UNSUPPORTED_FORMATS("RELYING_PARTY_UNSUPPORTED_FORMATS", RecommendedAction.CONTACT_RELYING_PARTY),

        /** The relying party asked for a scope the wallet does not recognise. */
        UNKNOWN_SCOPE("RELYING_PARTY_UNKNOWN_SCOPE", RecommendedAction.CONTACT_RELYING_PARTY),

        /**
         * The relying party asked for an unencrypted response mode (e.g. `direct_post`) where
         * HAIP requires an encrypted one (`direct_post.jwt` / `dc_api.jwt`), so the wallet's
         * encryption policy refused to answer.
         */
        RESPONSE_ENCRYPTION_REQUIRED(
            "RELYING_PARTY_RESPONSE_ENCRYPTION_REQUIRED",
            RecommendedAction.CONTACT_RELYING_PARTY,
        ),

        /** A relying-party-side error that does not fit any of the above. */
        OTHER("RELYING_PARTY_OTHER", RecommendedAction.CONTACT_RELYING_PARTY),
    }

    /** Sub-classification of [WalletSide] errors. */
    enum class WalletSideSubType(
        override val errorCode: String,
        override val recommendedAction: RecommendedAction,
    ) : HasErrorCodeAndAction {
        /** Key unlock data for a credential was missing. */
        MISSING_UNLOCK_DATA("WALLET_MISSING_UNLOCK_DATA", RecommendedAction.CONTACT_SUPPORT),

        /** The device has no network connectivity. */
        NO_NETWORK("WALLET_NO_NETWORK", RecommendedAction.CHECK_CONNECTION),

        /** A wallet-side error that does not fit any of the above. */
        OTHER("WALLET_OTHER", RecommendedAction.CONTACT_SUPPORT),
    }

    /** Sub-classification of [Intermittent] errors. */
    enum class IntermittentSubType(
        override val errorCode: String,
        override val recommendedAction: RecommendedAction,
    ) : HasErrorCodeAndAction {
        /**
         * A transient network failure (timeout, connection refused, SSL handshake).
         * Retrying immediately is meaningful (WD-4273, "That took too long" / Retry, WD-4245).
         */
        NETWORK("INTERMITTENT_NETWORK", RecommendedAction.RETRY),
    }

    /**
     * Common interface for sub-type enums so each data class can derive [errorCode] and
     * [recommendedAction] from its subType without duplicating the property declarations.
     */
    private interface HasErrorCodeAndAction {
        val errorCode: String
        val recommendedAction: RecommendedAction
    }

    companion object {
        const val ERROR_CODE_UNKNOWN = "UNKNOWN"
    }
}