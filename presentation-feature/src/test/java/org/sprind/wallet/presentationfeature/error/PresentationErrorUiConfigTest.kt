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

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.testlogic.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PresentationErrorUiConfigTest {

    private val traceFallback = { "fallback-trace" }

    //region Unclassified

    @Test
    fun `unclassified error renders the generic fallback with a trace id`() {
        val config = PresentationErrorUiConfig.from(
            PresentationError.Unclassified(
                traceId = null,
                cause = null,
                displayMessage = "boom",
            ),
            traceIdFallback = traceFallback,
        )

        assertEquals(PresentationErrorLayout.DIALOG, config.layout)
        assertEquals(R.string.global_generic_presentation_error_title, config.titleRes)
        assertEquals(R.string.global_generic_presentation_error_paragraph, config.bodyRes)
        assertEquals(R.string.global_generic_presentation_error_prim_button, config.primaryAction.labelRes)
        assertEquals("fallback-trace", config.traceId)
        assertEquals(PresentationErrorAction.Kind.DISMISS, config.primaryAction.kind)
        assertNull(config.secondaryAction)
        // The generic fallback never exposes an error code: it is by definition unknown.
        assertNull(config.errorCode)
    }

    @Test
    fun `unclassified error keeps its own trace id when present`() {
        val config = PresentationErrorUiConfig.from(
            PresentationError.Unclassified(
                traceId = "own-trace",
                cause = null,
                displayMessage = "boom",
            ),
            traceIdFallback = { "fallback-trace" },
        )

        assertEquals("own-trace", config.traceId)
    }

    //endregion

    //region Relying party side

    @Test
    fun `invalid request renders the malformed-presentation state with the error code`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            )
        )

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.eaa_presentation_malformed_presentation_title, error.titleRes)
        assertEquals("RELYING_PARTY_INVALID_REQUEST", error.errorCode)
        assertEquals(PresentationErrorAction.Kind.DISMISS, error.primaryAction.kind)
    }

    @Test
    fun `response encryption required renders the malformed-presentation state with its own error code`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.RESPONSE_ENCRYPTION_REQUIRED,
            )
        )

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.eaa_presentation_malformed_presentation_title, error.titleRes)
        assertEquals(R.string.eaa_presentation_malformed_presentation_paragraph, error.bodyRes)
        assertEquals("RELYING_PARTY_RESPONSE_ENCRYPTION_REQUIRED", error.errorCode)
        assertEquals(PresentationErrorAction.Kind.DISMISS, error.primaryAction.kind)
        assertEquals(null, error.secondaryAction)
    }

    @Test
    fun `relying party rejected offers contacting the service and cancelling to the overview`() {
        val error = errorOf(rejected())

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.pid_presentation_service_rejection_title, error.titleRes)
        assertEquals(R.string.pid_presentation_service_rejection_paragraph, error.bodyRes)
        assertEquals("RELYING_PARTY_REJECTED", error.errorCode)
        assertEquals(PresentationErrorAction.Kind.CONTACT_SERVICE, error.primaryAction.kind)
        assertEquals(R.string.pid_presentation_service_rejection_prim_button, error.primaryAction.labelRes)
        assertTrue(error.primaryAction.opensExternalLink)
        assertEquals(PresentationErrorAction.Kind.TO_OVERVIEW, error.secondaryAction?.kind)
        assertEquals(R.string.pid_presentation_service_rejection_sec_button, error.secondaryAction?.labelRes)
    }

    @Test
    fun `relying party rejected without a support contact offers cancelling only`() {
        val error = errorOf(rejected()).withoutContactService()

        assertEquals(PresentationErrorAction.Kind.TO_OVERVIEW, error.primaryAction.kind)
        assertEquals(R.string.pid_presentation_service_rejection_sec_button, error.primaryAction.labelRes)
        assertNull(error.secondaryAction)
    }

    @Test
    fun `withoutContactService leaves states without a service contact unchanged`() {
        val error = PresentationErrorUiConfig.blocking(BlockingErrorReason.TRUST_FAILURE, "trace")

        assertEquals(error, error.withoutContactService())
    }

    @Test
    fun `relying party unreachable renders the server-error state with a retry-later dismiss only`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
            )
        )

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(
            R.string.eaa_presentation_dialog_server_error_server_error_title,
            error.titleRes
        )
        // WD-4273: RETRY_LATER classes offer no immediate retry; the body copy
        // ("Please try again in a few minutes") asks the user to come back later and
        // the single "Retry later" action only dismisses.
        assertEquals(
            R.string.eaa_presentation_dialog_server_error_error_server_paragraph,
            error.bodyRes
        )
        assertEquals(PresentationErrorAction.Kind.DISMISS, error.primaryAction.kind)
        assertEquals(R.string.global_try_again_later_button, error.primaryAction.labelRes)
        assertNull(error.secondaryAction)
    }

    @Test
    fun `trust failure renders the blocking unknown-verifier state with a report action`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.TRUST_FAILURE,
            )
        )

        assertEquals(PresentationErrorLayout.BLOCKING, error.layout)
        assertEquals(R.string.eaa_presentation_dialog_rp_unkown_title, error.titleRes)
        assertEquals(PresentationErrorAction.Kind.REPORT_ISSUE, error.primaryAction.kind)
        assertEquals(PresentationErrorAction.Kind.TO_OVERVIEW, error.secondaryAction?.kind)
        assertEquals(R.string.global_to_overview_button, error.secondaryAction?.labelRes)
    }

    @Test
    fun `unknown scope renders the blocking overasking state`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.UNKNOWN_SCOPE,
            )
        )

        assertEquals(PresentationErrorLayout.BLOCKING, error.layout)
        assertEquals(R.string.eaa_presentation_dialog_rp_overasking_title, error.titleRes)
        assertEquals(
            R.string.eaa_presentation_dialog_rp_overasking_paragraph_overasking,
            error.bodyRes
        )
        assertEquals(PresentationErrorAction.Kind.REPORT_ISSUE, error.primaryAction.kind)
        assertEquals(PresentationErrorAction.Kind.TO_OVERVIEW, error.secondaryAction?.kind)
        assertEquals(R.string.global_to_overview_button, error.secondaryAction?.labelRes)
    }

    @Test
    fun `unsupported formats renders the blocking missing-credential state`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.UNSUPPORTED_FORMATS,
            )
        )

        assertEquals(PresentationErrorLayout.BLOCKING, error.layout)
        assertEquals(R.string.eaa_presentation_credential_not_found_title, error.titleRes)
        assertEquals(PresentationErrorAction.Kind.TO_OVERVIEW, error.primaryAction.kind)
    }

    //endregion

    //region Wallet side

    @Test
    fun `no network renders the offline state with a retry action`() {
        val error = errorOf(
            PresentationError.WalletSide(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.WalletSideSubType.NO_NETWORK,
            )
        )

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.global_no_internet_error_title, error.titleRes)
        assertEquals(PresentationErrorAction.Kind.RETRY, error.primaryAction.kind)
        // WD-4273: the button that retries must not be labelled "Got it".
        assertEquals(
            R.string.eaa_presentation_dialog_server_error_prim_button,
            error.primaryAction.labelRes
        )
    }

    @Test
    fun `wallet-side other renders the generic state with the error code`() {
        val error = errorOf(
            PresentationError.WalletSide(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.WalletSideSubType.OTHER,
            )
        )

        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.global_error_title, error.titleRes)
        assertEquals("WALLET_OTHER", error.errorCode)
        assertEquals("trace", error.traceId)
    }

    //endregion

    //region Intermittent

    @Test
    fun `intermittent network renders the timeout state with a single retry`() {
        val error = errorOf(
            PresentationError.Intermittent(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.IntermittentSubType.NETWORK,
            )
        )

        // WD-4273 / WD-4245: "That took too long" with an immediate Retry.
        assertEquals(PresentationErrorLayout.DIALOG, error.layout)
        assertEquals(R.string.global_timeout_slow_internet_error_title, error.titleRes)
        assertEquals(R.string.global_timeout_slow_internet_error_paragraph, error.bodyRes)
        assertEquals(PresentationErrorAction.Kind.RETRY, error.primaryAction.kind)
        assertEquals(
            R.string.global_timeout_slow_internet_error_prim_button,
            error.primaryAction.labelRes
        )
        assertNull(error.secondaryAction)
    }

    @Test
    fun `withoutRetry downgrades a retry action to a retry-later dismiss`() {
        val error = errorOf(
            PresentationError.Intermittent(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.IntermittentSubType.NETWORK,
            )
        ).withoutRetry()

        assertEquals(PresentationErrorAction.Kind.DISMISS, error.primaryAction.kind)
        assertEquals(R.string.global_try_again_later_button, error.primaryAction.labelRes)
        assertNull(error.secondaryAction)
    }

    @Test
    fun `withoutRetry leaves configs without a retry action unchanged`() {
        val error = errorOf(
            PresentationError.RelyingPartySide(
                traceId = null,
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.TRUST_FAILURE,
            )
        )

        assertEquals(error, error.withoutRetry())
    }

    //endregion

    //region Blocking reason

    @Test
    fun `blocking errors carry their reason and resolve to the same state the error screen rebuilds`() {
        val cases = mapOf(
            PresentationError.RelyingPartySideSubType.TRUST_FAILURE to BlockingErrorReason.TRUST_FAILURE,
            PresentationError.RelyingPartySideSubType.UNKNOWN_SCOPE to BlockingErrorReason.OVERASKING,
            PresentationError.RelyingPartySideSubType.UNSUPPORTED_FORMATS to BlockingErrorReason.MISSING_CREDENTIAL,
        )

        cases.forEach { (subType, reason) ->
            val config = PresentationErrorUiConfig.from(
                PresentationError.RelyingPartySide(
                    traceId = "trace",
                    cause = null,
                    displayMessage = "msg",
                    subType = subType,
                ),
                traceIdFallback = { null },
            )

            assertEquals(reason, config.blockingReason, subType.name)
            // Only the reason and the trace ID cross navigation, so the blocking screen
            // must be able to rebuild exactly this state from them.
            assertEquals(PresentationErrorUiConfig.blocking(reason, "trace"), config, subType.name)
        }
    }

    @Test
    fun `dialog errors carry no blocking reason`() {
        val config = PresentationErrorUiConfig.from(
            PresentationError.Unclassified(traceId = "trace", cause = null, displayMessage = "msg"),
            traceIdFallback = { null },
        )

        assertEquals(PresentationErrorLayout.DIALOG, config.layout)
        assertNull(config.blockingReason)
    }

    //endregion

    //region Rendering

    @Test
    fun `dialog config carries the designed fields and routes the retry action`() {
        val config = errorOf(
            PresentationError.Intermittent(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.IntermittentSubType.NETWORK,
            ),
        )
        val calls = mutableListOf<String>()

        val dialog = config.toGenericErrorDialogConfig(
            onRetry = { calls += "retry" },
            onReportIssue = { calls += "report" },
            onDismiss = { calls += "dismiss" },
        )

        assertEquals(config.titleRes, dialog.titleRes)
        assertEquals(config.bodyRes, dialog.bodyTextRes)
        assertEquals("INTERMITTENT_NETWORK", dialog.errorCode)
        assertEquals("trace", dialog.traceId)
        assertEquals(config.primaryAction.labelRes, dialog.primaryButtonTextRes)
        assertEquals(config.secondaryAction?.labelRes, dialog.secondaryButtonTextRes)

        dialog.onPrimaryButtonClick()
        dialog.onSecondaryButtonClick()
        dialog.onDismiss()

        assertEquals(listOf("retry", "dismiss", "dismiss"), calls)
    }

    @Test
    fun `dialog config contacts the service, and cancelling or dismissing leaves for the overview`() {
        val config = errorOf(rejected())
        val calls = mutableListOf<String>()

        val dialog = config.toGenericErrorDialogConfig(
            onRetry = { calls += "retry" },
            onReportIssue = { calls += "report" },
            onDismiss = { calls += "dismiss" },
            onContactService = { calls += "contact" },
            onToOverview = { calls += "overview" },
        )
        dialog.onPrimaryButtonClick()
        dialog.onSecondaryButtonClick()
        dialog.onDismiss()

        assertTrue(dialog.primaryButtonOpensExternalLink)
        assertEquals(listOf("contact", "overview", "overview"), calls)
    }

    @Test
    fun `dialog config dismisses on a non-retry primary action`() {
        val config = errorOf(
            PresentationError.RelyingPartySide(
                traceId = "trace",
                cause = null,
                displayMessage = "msg",
                subType = PresentationError.RelyingPartySideSubType.OTHER,
            ),
        )
        val calls = mutableListOf<String>()

        val dialog = config.toGenericErrorDialogConfig(
            onRetry = { calls += "retry" },
            onReportIssue = { calls += "report" },
            onDismiss = { calls += "dismiss" },
        )
        dialog.onPrimaryButtonClick()

        assertNull(dialog.secondaryButtonTextRes)
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `dialog config opens the report-issue page on a report primary action`() {
        val config = PresentationErrorUiConfig.blocking(BlockingErrorReason.TRUST_FAILURE, "trace")
        val calls = mutableListOf<String>()

        val dialog = config.toGenericErrorDialogConfig(
            onRetry = { calls += "retry" },
            onReportIssue = { calls += "report" },
            onDismiss = { calls += "dismiss" },
        )
        dialog.onPrimaryButtonClick()
        dialog.onSecondaryButtonClick()

        assertEquals(listOf("report", "dismiss"), calls)
    }

    //endregion

    //region WD-4273: retry gated by the taxonomy

    /**
     * AC1 of WD-4273: "A retry action appears only for classes whose taxonomy entry
     * allows it." Exhaustive over the whole taxonomy: every dialog-layout class must
     * expose a `Kind.RETRY` primary (or secondary) action exactly when its
     * `RecommendedAction` permits retry; blocking-layout classes never offer retry
     * by design, so only their taxonomy is asserted.
     */
    @Test
    fun `retry is offered exactly for taxonomy classes whose action permits it`() {
        val unclassified = PresentationError.Unclassified(
            traceId = "trace",
            cause = null,
            displayMessage = "msg",
        )
        val classes: List<Pair<PresentationError, PresentationError.RecommendedAction>> =
            buildList {
                add(unclassified to unclassified.recommendedAction)
                PresentationError.RelyingPartySideSubType.entries.forEach { subType ->
                    val error = PresentationError.RelyingPartySide(
                        traceId = "trace",
                        cause = null,
                        displayMessage = "msg",
                        subType = subType,
                    )
                    add(error to error.recommendedAction)
                }
                PresentationError.WalletSideSubType.entries.forEach { subType ->
                    val error = PresentationError.WalletSide(
                        traceId = "trace",
                        cause = null,
                        displayMessage = "msg",
                        subType = subType,
                    )
                    add(error to error.recommendedAction)
                }
                PresentationError.IntermittentSubType.entries.forEach { subType ->
                    val error = PresentationError.Intermittent(
                        traceId = "trace",
                        cause = null,
                        displayMessage = "msg",
                        subType = subType,
                    )
                    add(error to error.recommendedAction)
                }
            }

        classes.forEach { (error, recommendedAction) ->
            val config = PresentationErrorUiConfig.from(error, traceIdFallback = { "fallback" })
            if (config.layout == PresentationErrorLayout.DIALOG) {
                val hasRetry = config.primaryAction.kind == PresentationErrorAction.Kind.RETRY ||
                    config.secondaryAction?.kind == PresentationErrorAction.Kind.RETRY
                assertEquals(
                    recommendedAction.permitsRetry(),
                    hasRetry,
                    "Retry affordance mismatch for ${error.errorCode} " +
                        "(RecommendedAction=$recommendedAction)",
                )
            }
        }
    }

    @Test
    fun `permitsRetry matches the taxonomy intent`() {
        assertTrue(PresentationError.RecommendedAction.RETRY.permitsRetry())
        assertTrue(PresentationError.RecommendedAction.CHECK_CONNECTION.permitsRetry())
        assertFalse(PresentationError.RecommendedAction.RETRY_LATER.permitsRetry())
        assertFalse(PresentationError.RecommendedAction.CONTACT_RELYING_PARTY.permitsRetry())
        assertFalse(PresentationError.RecommendedAction.CONTACT_SUPPORT.permitsRetry())
        assertFalse(PresentationError.RecommendedAction.DISMISS.permitsRetry())
    }

    //endregion

    private fun rejected() = PresentationError.RelyingPartySide(
        traceId = "trace",
        cause = null,
        displayMessage = "msg",
        subType = PresentationError.RelyingPartySideSubType.RELYING_PARTY_REJECTED,
    )

    private fun errorOf(presentationError: PresentationError): PresentationErrorUiConfig =
        PresentationErrorUiConfig.from(presentationError, traceIdFallback = traceFallback)
}
