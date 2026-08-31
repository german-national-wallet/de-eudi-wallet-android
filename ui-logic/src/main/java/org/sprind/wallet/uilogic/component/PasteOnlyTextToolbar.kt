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

package org.sprind.wallet.uilogic.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * A [TextToolbar] for a code field: nothing may be copied out, and pasting in is allowed only where
 * the code is not a secret.
 *
 * Copy, cut and select-all are always withheld, because lifting an entered PIN onto the system
 * clipboard would hand it to every app on the device.
 *
 * Paste is the interesting half. A transaction code arrives in a mail or a document and typing it
 * back by hand is pure friction, so pasting it in is supported. A PIN is different: a paste can only
 * come from the clipboard, so allowing it invites the user to keep their wallet PIN there, where any
 * app can read it and where Android will show it in a clipboard preview. Fields that ask for a real
 * secret therefore offer no paste either — see [allowPaste].
 *
 * It [delegate]s to the platform toolbar rather than drawing its own menu, so the paste item keeps
 * the real Android look and behaviour, and simply passes `null` for the actions it withholds —
 * `null` is how [TextToolbar.showMenu] is told to omit an item.
 *
 * @property allowPaste whether the paste item is offered at all.
 */
class PasteOnlyTextToolbar(
    private val delegate: TextToolbar,
    private val allowPaste: Boolean,
) : TextToolbar {

    override val status: TextToolbarStatus
        get() = delegate.status

    override fun hide() = delegate.hide()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // Copy, cut and select-all are dropped so a secret cannot leave the field, and paste only
        // survives for codes that are not secrets. If nothing is left to offer, the platform shows
        // no menu at all.
        delegate.showMenu(
            rect = rect,
            onCopyRequested = null,
            onPasteRequested = onPasteRequested.takeIf { allowPaste },
            onCutRequested = null,
            onSelectAllRequested = null,
        )
    }
}
