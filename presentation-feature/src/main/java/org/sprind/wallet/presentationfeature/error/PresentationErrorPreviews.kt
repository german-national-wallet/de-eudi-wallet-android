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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialog
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.corelogic.presentationerror.PresentationError

/*
 * Each preview resolves a sample [PresentationError] through the production
 * [PresentationErrorUiConfig.from] mapper and renders it with the same conversion the
 * ViewModels use, so the preview matches what the user sees (WD-4270 / WD-4274).
 */

private const val PREVIEW_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

private fun previewConfig(error: PresentationError): PresentationErrorUiConfig =
    PresentationErrorUiConfig.from(error) { PREVIEW_TRACE_ID }

private fun relyingPartySide(subType: PresentationError.RelyingPartySideSubType) =
    previewConfig(
        PresentationError.RelyingPartySide(
            traceId = PREVIEW_TRACE_ID,
            cause = null,
            displayMessage = "",
            subType = subType,
        )
    )

private fun walletSide(subType: PresentationError.WalletSideSubType) =
    previewConfig(
        PresentationError.WalletSide(
            traceId = PREVIEW_TRACE_ID,
            cause = null,
            displayMessage = "",
            subType = subType,
        )
    )

private fun intermittent(subType: PresentationError.IntermittentSubType) =
    previewConfig(
        PresentationError.Intermittent(
            traceId = PREVIEW_TRACE_ID,
            cause = null,
            displayMessage = "",
            subType = subType,
        )
    )

private fun unclassified() =
    previewConfig(
        PresentationError.Unclassified(
            traceId = PREVIEW_TRACE_ID,
            cause = null,
            displayMessage = "",
        )
    )

@Composable
private fun BlockingErrorPreview(config: PresentationErrorUiConfig) {
    PreviewTheme {
        PresentationErrorContent(
            state = State(config = config),
            onEvent = {},
        )
    }
}

@Composable
private fun DialogErrorPreview(config: PresentationErrorUiConfig) {
    PreviewTheme {
        GenericErrorDialog(
            config = config.toGenericErrorDialogConfig(
                onRetry = {},
                onReportIssue = {},
                onDismiss = {},
            )
        )
    }
}

//region Blocking full-screen states

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorScreenTrustFailurePreview() {
    BlockingErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.TRUST_FAILURE))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorScreenOveraskingPreview() {
    BlockingErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.UNKNOWN_SCOPE))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorScreenMissingCredentialPreview() {
    BlockingErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.UNSUPPORTED_FORMATS))
}

/** Large font scale so the body overflows: it scrolls and shows the bottom fade. */
@Preview(name = "Overflowing content", showBackground = true, fontScale = 2f, locale = "de")
@Composable
private fun PresentationErrorScreenOverflowingPreview() {
    BlockingErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.TRUST_FAILURE))
}

//endregion

//region Dialog states

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogRelyingPartyRejectedPreview() {
    DialogErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.RELYING_PARTY_REJECTED))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogRelyingPartyUnreachablePreview() {
    DialogErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogMalformedRequestPreview() {
    DialogErrorPreview(relyingPartySide(PresentationError.RelyingPartySideSubType.INVALID_REQUEST))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogNoNetworkPreview() {
    DialogErrorPreview(walletSide(PresentationError.WalletSideSubType.NO_NETWORK))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogWalletErrorPreview() {
    DialogErrorPreview(walletSide(PresentationError.WalletSideSubType.OTHER))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogIntermittentNetworkPreview() {
    DialogErrorPreview(intermittent(PresentationError.IntermittentSubType.NETWORK))
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PresentationErrorDialogUnclassifiedPreview() {
    DialogErrorPreview(unclassified())
}

//endregion
