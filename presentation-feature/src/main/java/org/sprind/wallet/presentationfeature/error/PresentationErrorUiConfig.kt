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

package org.sprind.wallet.presentationfeature.error

import androidx.annotation.StringRes
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import org.sprind.wallet.commonfeature.ui.error.formatAsRetryTime
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import org.sprind.wallet.networklogic.common.NetworkFailure
import org.sprind.wallet.networklogic.common.toNetworkFailure
import java.time.ZoneId
import java.util.Locale

/**
 * How the error is presented. [DIALOG] renders as a modal sheet
 * (see `GenericErrorDialog`); [BLOCKING] renders as a dedicated full-screen
 * destination (see `PresentationErrorScreen`).
 */
enum class PresentationErrorLayout {
    DIALOG,
    BLOCKING,
}

/**
 * An action button the error UI should offer, resolved against the string catalog.
 *
 * @property labelRes The button label.
 * @property kind How the UI behaves when the button is pressed.
 * @property outlined Drawn outlined even as the primary action, where Figma asks for it
 *   (the missing-credential screen, WD-4808).
 */
data class PresentationErrorAction(
    val labelRes: Int,
    val kind: Kind,
    val outlined: Boolean = false,
) {

    enum class Kind {
        /** Re-run the failed operation immediately. */
        RETRY,

        /** Leave the error state and return to the relying party / previous screen. */
        DISMISS,

        /** Open the report-issue page, after a redirect confirmation. */
        REPORT_ISSUE,

        /** Close the error and return to the wallet overview. */
        TO_OVERVIEW,

        /** Leave the presentation and start adding the Digital ID (PID issuance). */
        ADD_PID,

        /** Delete the locked Digital ID, then start adding it again like [ADD_PID]. */
        REPLACE_PID,

        /**
         * Open the relying party's website, after a redirect confirmation, to restart the
         * presentation there (WD-5012).
         */
        BACK_TO_SERVICE_PROVIDER,

        /** Open the relying party's support contact, after a redirect confirmation. */
        CONTACT_SERVICE,
    }

    /** Whether the action leaves the app, which the button marks with a launch icon. */
    val opensExternalLink: Boolean
        get() = kind == Kind.REPORT_ISSUE || kind == Kind.CONTACT_SERVICE ||
            kind == Kind.BACK_TO_SERVICE_PROVIDER
}

/**
 * Whether the taxonomy action of an error class permits offering an immediate
 * retry button (WD-4273): `RETRY` and `CHECK_CONNECTION` do (the user can retry
 * as soon as the transient condition / their connection is back). `RETRY_LATER`
 * does not: the body copy asks the user to try again later and the only action
 * dismisses. The contact/dismiss actions never do, because retrying cannot change
 * the outcome.
 */
internal fun PresentationError.RecommendedAction.permitsRetry(): Boolean = when (this) {
    PresentationError.RecommendedAction.RETRY,
    PresentationError.RecommendedAction.CHECK_CONNECTION,
        -> true

    PresentationError.RecommendedAction.RETRY_LATER,
    PresentationError.RecommendedAction.CONTACT_RELYING_PARTY,
    PresentationError.RecommendedAction.CONTACT_SUPPORT,
    PresentationError.RecommendedAction.DISMISS,
        -> false
}

/**
 * Why a presentation is blocked (WD-4270). Only this reason and the
 * trace ID cross the navigation boundary to
 * [PresentationErrorScreen][org.sprind.wallet.presentationfeature.error.PresentationErrorScreen];
 * its ViewModel rebuilds the UI state via [PresentationErrorUiConfig.blocking].
 */
enum class BlockingErrorReason {
    /** The verifier is unknown or its registration certificate failed validation. */
    TRUST_FAILURE,

    /** The verifier requests more data than its certificate allows. */
    OVERASKING,

    /** The wallet holds no credential that satisfies the request. */
    MISSING_CREDENTIAL,

    /** The request cannot be satisfied without a PID and the wallet holds none (WD-4759). */
    MISSING_PID,

    /**
     * The Digital ID code was entered wrongly too often and the RWSCA account is locked for good
     * (WD-4980); only adding the Digital ID again sets a new code.
     */
    PID_LOCKED,
}

/**
 * The user-facing state of a typed [PresentationError] (WD-4272 / WD-4274): title, body,
 * buttons and trace ID.
 *
 * Built via [Companion.from] with one branch per error subtype, so a subtype without a
 * branch here fails compilation (exhaustive `when`).
 *
 * Invariants (WD-4267):
 * - Only [PresentationError.Unclassified] uses the generic fallback strings, and it always
 *   carries a trace ID (the caller supplies one when the error has none).
 * - Relying-party-side errors offer no retry: retrying cannot change the outcome.
 * - A retry action is offered exactly when [PresentationError.RecommendedAction.permitsRetry]
 *   allows it (WD-4273).
 *
 * @property layout How to render this state.
 * @property titleRes The title.
 * @property bodyRes The body text.
 * @property bodyArgs The format arguments of [bodyRes].
 * @property errorCode Shown in the dialog sheet for support lookup when non-null.
 * @property traceId Copyable in the dialog; matches the value in the analytics trace. The
 *   blocking screen does not show it (not part of the Figma design).
 * @property primaryAction The high-emphasis action.
 * @property secondaryAction The optional secondary action.
 * @property blockingReason Set exactly when [layout] is [PresentationErrorLayout.BLOCKING];
 *   the navigation argument of the blocking screen.
 */
data class PresentationErrorUiConfig(
    val layout: PresentationErrorLayout,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val bodyArgs: List<Any> = emptyList(),
    val errorCode: String? = null,
    val traceId: String?,
    val primaryAction: PresentationErrorAction,
    val secondaryAction: PresentationErrorAction? = null,
    val blockingReason: BlockingErrorReason? = null,
) {

    /**
     * The [GenericErrorDialog][eu.europa.ec.uilogic.component.dialog.GenericErrorDialog]
     * rendering of a [PresentationErrorLayout.DIALOG] state (request screen).
     *
     * @param onRetry Invoked when the primary action is [PresentationErrorAction.Kind.RETRY].
     * @param onReportIssue Invoked for [PresentationErrorAction.Kind.REPORT_ISSUE]; opens the
     *   report-issue page.
     * @param onContactService Invoked for [PresentationErrorAction.Kind.CONTACT_SERVICE]; opens
     *   the relying party's support contact.
     * @param onToOverview Invoked for [PresentationErrorAction.Kind.TO_OVERVIEW], and when a
     *   dialog offering it is dismissed: the flow cannot continue behind it.
     * @param onBackToServiceProvider Invoked for
     *   [PresentationErrorAction.Kind.BACK_TO_SERVICE_PROVIDER]; opens the relying party's website.
     * @param onDismiss Invoked for every other action and when the dialog is dismissed.
     */
    fun toGenericErrorDialogConfig(
        onRetry: () -> Unit,
        onReportIssue: () -> Unit,
        onDismiss: () -> Unit,
        onContactService: () -> Unit = onDismiss,
        onToOverview: () -> Unit = onDismiss,
        onBackToServiceProvider: () -> Unit = onDismiss,
    ): GenericErrorDialogConfig {
        val onAction: (PresentationErrorAction) -> Unit = { action ->
            when (action.kind) {
                PresentationErrorAction.Kind.RETRY -> onRetry()
                PresentationErrorAction.Kind.REPORT_ISSUE -> onReportIssue()
                PresentationErrorAction.Kind.CONTACT_SERVICE -> onContactService()
                PresentationErrorAction.Kind.TO_OVERVIEW -> onToOverview()
                PresentationErrorAction.Kind.BACK_TO_SERVICE_PROVIDER -> onBackToServiceProvider()
                // Only the blocking screen offers ADD_PID / REPLACE_PID; a dialog never carries them.
                PresentationErrorAction.Kind.DISMISS,
                PresentationErrorAction.Kind.ADD_PID,
                PresentationErrorAction.Kind.REPLACE_PID,
                    -> onDismiss()
            }
        }
        val leavesForOverview = listOfNotNull(primaryAction, secondaryAction)
            .any { it.kind == PresentationErrorAction.Kind.TO_OVERVIEW }
        return GenericErrorDialogConfig(
            titleRes = titleRes,
            bodyTextRes = bodyRes,
            bodyTextArgs = bodyArgs,
            errorCode = errorCode,
            traceId = traceId,
            primaryButtonTextRes = primaryAction.labelRes,
            onPrimaryButtonClick = { onAction(primaryAction) },
            primaryButtonOpensExternalLink = primaryAction.opensExternalLink,
            secondaryButtonTextRes = secondaryAction?.labelRes,
            onSecondaryButtonClick = { secondaryAction?.let(onAction) ?: onDismiss() },
            onDismiss = if (leavesForOverview) onToOverview else onDismiss,
        )
    }

    /**
     * This state with any retry action replaced by a "Retry later" dismiss, for
     * call sites that cannot re-run the failed step (WD-4273). Configs without a
     * retry action are returned unchanged.
     */
    fun withoutRetry(): PresentationErrorUiConfig {
        val retryLater = PresentationErrorAction(
            labelRes = R.string.global_try_again_later_button,
            kind = PresentationErrorAction.Kind.DISMISS,
        )
        return copy(
            primaryAction = primaryAction.takeUnless { it.kind == PresentationErrorAction.Kind.RETRY }
                ?: retryLater,
            secondaryAction = secondaryAction?.takeUnless { it.kind == PresentationErrorAction.Kind.RETRY },
        )
    }

    /**
     * This state without its [PresentationErrorAction.Kind.CONTACT_SERVICE] action, for a relying
     * party whose registration names no support contact; the remaining action becomes primary.
     */
    fun withoutContactService(): PresentationErrorUiConfig =
        withoutPrimaryAction(PresentationErrorAction.Kind.CONTACT_SERVICE)

    /**
     * This state without its [PresentationErrorAction.Kind.BACK_TO_SERVICE_PROVIDER] action, for a
     * relying party with no known website; the remaining action becomes primary.
     */
    fun withoutBackToServiceProvider(): PresentationErrorUiConfig =
        withoutPrimaryAction(PresentationErrorAction.Kind.BACK_TO_SERVICE_PROVIDER)

    private fun withoutPrimaryAction(kind: PresentationErrorAction.Kind): PresentationErrorUiConfig {
        if (primaryAction.kind != kind) return this
        val remaining = secondaryAction ?: return this
        return copy(primaryAction = remaining, secondaryAction = null)
    }

    companion object {

        /**
         * Resolves a typed [PresentationError] into its UI state.
         *
         * @param traceIdFallback Invoked when [PresentationError.traceId] is null so the
         *   unclassified fallback always shows a reference (usually
         *   `telemetry::currentTraceId`).
         * @param zone The time zone a retry time is shown in.
         * @param locale The locale a retry time is formatted for.
         */
        fun from(
            error: PresentationError,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
            traceIdFallback: () -> String?,
        ): PresentationErrorUiConfig {
            val traceId = error.traceId ?: traceIdFallback()

            return when (error) {
                is PresentationError.Unclassified -> unclassified(traceId)

                is PresentationError.RelyingPartySide -> when (error.subType) {
                    // The verifier received the response and refused it: only the relying
                    // party can help, and the flow cannot continue (WD-4714).
                    PresentationError.RelyingPartySideSubType.RELYING_PARTY_REJECTED ->
                        dialog(
                            titleRes = R.string.pid_presentation_service_rejection_title,
                            bodyRes = R.string.pid_presentation_service_rejection_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.pid_presentation_service_rejection_prim_button,
                                kind = PresentationErrorAction.Kind.CONTACT_SERVICE,
                            ),
                            secondaryAction = PresentationErrorAction(
                                labelRes = R.string.pid_presentation_service_rejection_sec_button,
                                kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )

                    // RETRY_LATER (WD-4273): no immediate retry; the body asks the user to
                    // try again in a few minutes and the single "Retry later" action
                    // dismisses (the backend-error "Retry later" pattern in Figma).
                    PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE ->
                        dialog(
                            titleRes = R.string.eaa_presentation_dialog_server_error_server_error_title,
                            bodyRes = R.string.eaa_presentation_dialog_server_error_error_server_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.global_try_again_later_button,
                                kind = PresentationErrorAction.Kind.DISMISS,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )

                    // The relying party did not answer in time, or its response endpoint stayed
                    // unreachable: the presentation has to be restarted there (WD-5012). No error
                    // code, as in the design and on iOS.
                    PresentationError.RelyingPartySideSubType.RELYING_PARTY_TIMEOUT ->
                        dialog(
                            titleRes = R.string.pid_presentation_timeout_rp_title,
                            bodyRes = R.string.pid_presentation_timeout_rp_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.pid_presentation_timeout_rp_prim_button,
                                kind = PresentationErrorAction.Kind.BACK_TO_SERVICE_PROVIDER,
                            ),
                            secondaryAction = PresentationErrorAction(
                                labelRes = R.string.pid_presentation_timeout_rp_sec_button,
                                kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                            ),
                            errorCode = null,
                            traceId = traceId,
                        )

                    // Trust failures block the whole flow: the verifier is unknown or its
                    // registration certificate failed validation, so nothing may be shared.
                    PresentationError.RelyingPartySideSubType.TRUST_FAILURE ->
                        blocking(BlockingErrorReason.TRUST_FAILURE, traceId)

                    // Overasking: the RP requests more data than its certificate allows.
                    PresentationError.RelyingPartySideSubType.UNKNOWN_SCOPE ->
                        blocking(BlockingErrorReason.OVERASKING, traceId)

                    // The wallet cannot satisfy the requested formats: the user simply does
                    // not hold a (suitable) credential, i.e. the "missing credential" flow.
                    PresentationError.RelyingPartySideSubType.UNSUPPORTED_FORMATS ->
                        blocking(BlockingErrorReason.MISSING_CREDENTIAL, traceId)

                    // Malformed / non-compliant request, including an unencrypted response
                    // mode where HAIP requires an encrypted one (same dialog as iOS).
                    PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
                    PresentationError.RelyingPartySideSubType.RESPONSE_ENCRYPTION_REQUIRED,
                    PresentationError.RelyingPartySideSubType.OTHER ->
                        dialog(
                            titleRes = R.string.eaa_presentation_malformed_presentation_title,
                            bodyRes = R.string.eaa_presentation_malformed_presentation_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.eaa_presentation_malformed_presentation_prim_button,
                                kind = PresentationErrorAction.Kind.DISMISS,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )
                }

                is PresentationError.WalletSide -> when (error.subType) {
                    PresentationError.WalletSideSubType.NO_NETWORK ->
                        dialog(
                            titleRes = R.string.global_no_internet_error_title,
                            bodyRes = R.string.global_no_internet_error_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.eaa_presentation_dialog_server_error_prim_button,
                                kind = PresentationErrorAction.Kind.RETRY,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )

                    PresentationError.WalletSideSubType.MISSING_UNLOCK_DATA,
                    PresentationError.WalletSideSubType.OTHER,
                        ->
                        dialog(
                            titleRes = R.string.global_error_title,
                            bodyRes = R.string.global_error_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.global_try_again_later_button,
                                kind = PresentationErrorAction.Kind.DISMISS,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )
                }

                is PresentationError.Intermittent -> when (error.subType) {
                    // Timeout / slow connection (WD-4245 "That took too long"): an
                    // immediate retry is meaningful (WD-4273).
                    PresentationError.IntermittentSubType.NETWORK ->
                        dialog(
                            titleRes = R.string.global_timeout_slow_internet_error_title,
                            bodyRes = R.string.global_timeout_slow_internet_error_paragraph,
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.global_timeout_slow_internet_error_prim_button,
                                kind = PresentationErrorAction.Kind.RETRY,
                            ),
                            errorCode = error.errorCode,
                            traceId = traceId,
                        )

                    // HTTP 429 (WD-5012): the same "Retry later" dialog as everywhere else
                    // (WD-4529), which only dismisses and gives the retry time when known.
                    PresentationError.IntermittentSubType.RATE_LIMITED -> {
                        val retryAt =
                            (error.cause?.toNetworkFailure() as? NetworkFailure.RateLimited)?.retryAt
                        dialog(
                            titleRes = R.string.global_timeout_too_many_requests_title,
                            bodyRes = if (retryAt != null) {
                                R.string.global_timeout_too_many_requests_paragraph
                            } else {
                                R.string.global_error_paragraph
                            },
                            bodyArgs = listOfNotNull(retryAt?.formatAsRetryTime(zone, locale)),
                            primaryAction = PresentationErrorAction(
                                labelRes = R.string.global_timeout_too_many_requests_prim_button,
                                kind = PresentationErrorAction.Kind.DISMISS,
                            ),
                            errorCode = null,
                            traceId = traceId,
                        )
                    }
                }
            }
        }

        // Figma "Generic" presentation error sheet (WD-4274).
        private fun unclassified(traceId: String?) = dialog(
            titleRes = R.string.global_generic_presentation_error_title,
            bodyRes = R.string.global_generic_presentation_error_paragraph,
            primaryAction = PresentationErrorAction(
                labelRes = R.string.global_generic_presentation_error_prim_button,
                kind = PresentationErrorAction.Kind.DISMISS,
            ),
            errorCode = null,
            traceId = traceId,
        )

        private fun dialog(
            titleRes: Int,
            bodyRes: Int,
            primaryAction: PresentationErrorAction,
            errorCode: String?,
            traceId: String?,
            secondaryAction: PresentationErrorAction? = null,
            bodyArgs: List<Any> = emptyList(),
        ) = PresentationErrorUiConfig(
            layout = PresentationErrorLayout.DIALOG,
            titleRes = titleRes,
            bodyRes = bodyRes,
            bodyArgs = bodyArgs,
            errorCode = errorCode,
            traceId = traceId,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
        )

        /**
         * The full-screen state for a [BlockingErrorReason]. Shared by [from]
         * and the blocking screen's ViewModel, so both resolve the same copy.
         */
        fun blocking(reason: BlockingErrorReason, traceId: String?): PresentationErrorUiConfig {
            val (titleRes, bodyRes) = when (reason) {
                BlockingErrorReason.TRUST_FAILURE ->
                    R.string.eaa_presentation_dialog_rp_unkown_title to
                        R.string.eaa_presentation_dialog_rp_unkown_paragraph_cert_failed

                BlockingErrorReason.OVERASKING ->
                    R.string.eaa_presentation_dialog_rp_overasking_title to
                        R.string.eaa_presentation_dialog_rp_overasking_paragraph_overasking

                BlockingErrorReason.MISSING_CREDENTIAL ->
                    R.string.eaa_presentation_credential_not_found_title to
                        R.string.eaa_presentation_credential_not_found_paragraph_credental_not_available

                BlockingErrorReason.MISSING_PID ->
                    R.string.pid_presentation_no_issued_pid_title to
                        R.string.pid_presentation_no_issued_pid_paragraph

                BlockingErrorReason.PID_LOCKED ->
                    R.string.pid_presentation_retry_counter_locked_title to
                        R.string.pid_presentation_retry_counter_blocked_paragraph
            }
            val (primaryAction, secondaryAction) = when (reason) {
                BlockingErrorReason.TRUST_FAILURE -> PresentationErrorAction(
                    labelRes = R.string.eaa_presentation_dialog_rp_unkown_prim_button,
                    kind = PresentationErrorAction.Kind.REPORT_ISSUE,
                ) to PresentationErrorAction(
                    labelRes = R.string.global_to_overview_button,
                    kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                )

                BlockingErrorReason.OVERASKING -> PresentationErrorAction(
                    labelRes = R.string.eaa_presentation_dialog_rp_overasking_prim_button,
                    kind = PresentationErrorAction.Kind.REPORT_ISSUE,
                ) to PresentationErrorAction(
                    labelRes = R.string.global_to_overview_button,
                    kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                )

                // There is no catalog to search for the credential (WD-4374).
                BlockingErrorReason.MISSING_CREDENTIAL -> PresentationErrorAction(
                    labelRes = R.string.global_to_overview_button,
                    kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                    outlined = true,
                ) to null

                BlockingErrorReason.MISSING_PID -> PresentationErrorAction(
                    labelRes = R.string.pid_presentation_no_issued_pid_prim_button,
                    kind = PresentationErrorAction.Kind.ADD_PID,
                ) to null

                BlockingErrorReason.PID_LOCKED -> PresentationErrorAction(
                    labelRes = R.string.pid_presentation_retry_counter_locked_prim_button,
                    kind = PresentationErrorAction.Kind.REPLACE_PID,
                ) to PresentationErrorAction(
                    labelRes = R.string.pid_presentation_retry_counter_locked_sec_button,
                    kind = PresentationErrorAction.Kind.TO_OVERVIEW,
                )
            }
            return PresentationErrorUiConfig(
                layout = PresentationErrorLayout.BLOCKING,
                titleRes = titleRes,
                bodyRes = bodyRes,
                errorCode = null,
                traceId = traceId,
                primaryAction = primaryAction,
                secondaryAction = secondaryAction,
                blockingReason = reason,
            )
        }
    }
}
