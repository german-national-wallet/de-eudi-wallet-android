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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.allCorneredShapeSmall
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sprind.wallet.component.PinDigitsOnlyTransformation
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

/** Test tag of the single real input widget behind a [CodeEntryField]. */
const val CODE_ENTRY_FIELD_TEST_TAG = "codeEntryField"

/**
 * Digit counts of the codes the wallet asks for, named so call sites read as intent rather than as
 * a bare number. A transaction code is deliberately absent: its length is chosen by the issuer at
 * runtime and travels in the credential offer.
 */
object CodeLength {
    /** The eID card's own 6-digit PIN. */
    const val EID_PIN = 6

    /** The Card Access Number printed on the front of the eID card. */
    const val CAN = 6

    /** The 5-digit transport PIN from the eID PIN letter, used before a card PIN is chosen. */
    const val TRANSPORT_PIN = 5

    /** The wallet's own app PIN. */
    const val WALLET_PIN = 6

    /** The 10-digit PUK that unblocks a card whose PIN attempts are exhausted. */
    const val PUK = 10
}

/**
 * Whether a [CodeEntryField] shows the digits the user has typed.
 */
enum class CodeVisibility {
    /**
     * Digits are always legible and no reveal control is offered. For codes that are not secrets
     * the user must protect — a transaction code is copied off a paper document, so hiding it would
     * only make it harder to check.
     */
    ALWAYS_VISIBLE,

    /**
     * Digits are masked, with an eye control to reveal them on demand. For real secrets — the
     * wallet PIN, the eID card PIN — so the code cannot be read over the user's shoulder.
     */
    MASKED_REVEALABLE,
}

/**
 * The entry state of a code field, owned by the screen's ViewModel.
 *
 * @property buffer the digits entered so far. Deliberately a mutable holder rather than a [String]:
 *   see [CodeEntryBuffer]. Its [CodeEntryBuffer.capacity] is what decides how many cells are drawn.
 * @property isValid whether the code is complete and acceptable, i.e. whether the screen's primary
 *   action should be enabled. The field itself never decides this — completeness is the least a
 *   caller checks, and some flows also require the code to match a previous entry.
 * @property supportingText an error to show under the cells, or `null` for none.
 */
@Stable
data class CodeEntryState(
    val buffer: CodeEntryBuffer,
    val isValid: Boolean = false,
    val supportingText: String? = null,
)

/**
 * Erases whatever was entered and returns the state a screen should show for a fresh attempt,
 * optionally with an error explaining why the last one was thrown away.
 *
 * @param capacity the length of the next code. Defaults to keeping the current one; the card reader
 *   flow passes a new value because it asks for codes of several lengths through one state.
 */
fun CodeEntryState.cleared(
    capacity: Int = buffer.capacity,
    supportingText: String? = null,
): CodeEntryState {
    buffer.wipe()
    return if (capacity == buffer.capacity) {
        copy(isValid = false, supportingText = supportingText)
    } else {
        CodeEntryState(CodeEntryBuffer(capacity), supportingText = supportingText)
    }
}

/**
 * A state already holding [code], for previews and screenshot tests.
 *
 * Production code never builds an entry state out of a [String] — not keeping the digits in one is
 * the whole point of [CodeEntryBuffer] — so this exists as an obvious seam rather than as a
 * convenience anyone should reach for.
 */
fun codeEntryStateForPreview(
    capacity: Int,
    code: String = "",
    supportingText: String? = null,
): CodeEntryState = CodeEntryState(
    buffer = CodeEntryBuffer(capacity).apply { set(code) },
    isValid = code.length == capacity,
    supportingText = supportingText,
)

/**
 * Options for a [CodeEntryField]. How many digits the code has is not among them — that comes from
 * the [CodeEntryState.buffer], which is the only thing that can hold that many.
 *
 * @property visibility whether the digits are legible; see [CodeVisibility].
 * @property focusOnCreate whether the field takes focus and raises the keyboard as it appears.
 */
@Immutable
data class CodeEntryConfig(
    val visibility: CodeVisibility = CodeVisibility.MASKED_REVEALABLE,
    val focusOnCreate: Boolean = true,
)

/**
 * The wallet's one code-entry control: a row of digit cells the user fills from the numeric
 * keyboard, optionally with an eye to reveal what has been typed.
 *
 * Every code the wallet asks for goes through this composable — wallet PIN, eID card PIN, CAN,
 * transport PIN, new card PIN, and the issuance transaction code. They differ only in how many
 * digits their buffer holds and in [CodeEntryConfig.visibility]; nothing else about them is
 * screen-specific, which is why there is a single component rather than one per flow.
 *
 * ### How it is built
 *
 * The cells are **decoration**. All input goes through one [BasicTextField] that is stretched over
 * the cells but painted with a transparent text style and cursor, so it is invisible while still
 * being the thing the user taps, focuses, and long-presses. Sizing it to the cells (rather than to
 * the 1×1 dp of the component this replaces) is what makes the paste menu reachable at all.
 *
 * ### Input rules
 *
 * - Digits only, capped at the buffer's capacity, enforced by [PinDigitsOnlyTransformation]; a
 *   pasted `"12-34 56"` therefore lands as `123456`.
 * - **Nothing can be copied out, and only a non-secret code can be pasted in** — see
 *   [PasteOnlyTextToolbar]. Pasting a transaction code out of a mail is expected; a PIN travelling
 *   through the system clipboard, in either direction, is not.
 * - The keyboard is dismissed once the last digit lands, so the primary button is never left
 *   hidden behind it.
 * - Wiping the buffer empties the field, so a ViewModel can throw the input away after a wrong code
 *   or a failed confirmation and be sure nothing is left on screen or in the field's own storage.
 *
 * ### Accessibility
 *
 * The cell row is decorative and hidden, leaving the text field as the single focusable control
 * with its native editing semantics intact — a screen reader user gets a real edit field rather
 * than a row of unlabelled boxes. The reveal control is a button whose label flips between
 * "Show" and "Hide" so its current effect is always announced, and [supportingText] sits in the
 * same semantics subtree so an error is read out with the field.
 *
 * @param state the entry state; the caller owns it, and the field writes the digits it receives into
 *   its [CodeEntryState.buffer] rather than handing them back.
 * @param onCodeChange called after every edit, so the owner can re-read the buffer and decide
 *   whether the code is now valid. It carries no value: the digits stay in the buffer.
 * @param modifier applied to the outer column.
 * @param config visibility and focus behaviour; see [CodeEntryConfig].
 */
@Composable
fun CodeEntryField(
    state: CodeEntryState,
    onCodeChange: () -> Unit,
    modifier: Modifier = Modifier,
    config: CodeEntryConfig = CodeEntryConfig(),
) {
    // Transient: revealing is a gesture, not screen state, and a secret should not come back
    // revealed after the process is restarted.
    var revealed by remember { mutableStateOf(false) }
    val masked = config.visibility == CodeVisibility.MASKED_REVEALABLE && !revealed

    if (config.visibility == CodeVisibility.MASKED_REVEALABLE) {
        // A transaction code is copied off a document and is not ours to protect, but a PIN must not
        // end up in a screenshot, a screen recording or the recent-apps thumbnail.
        SecureScreenEffect()
    }

    // Read once here rather than per cell, so the cells still follow a theme radius change.
    val cellShape = MaterialTheme.shapes.allCorneredShapeSmall

    // fillMaxWidth + wrapContentWidth centres the block on screen; the intrinsic width sizes the
    // column to the cell row, so the error below wraps within the cells instead of widening it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CodeCells(
                    buffer = state.buffer,
                    masked = masked,
                    cellShape = cellShape,
                )
                // Stretched over the cells so the whole row is the tap, focus and long-press
                // target; a 1 dp field cannot be long-pressed, and without a long-press there
                // is no paste menu.
                HiddenCodeTextField(
                    buffer = state.buffer,
                    onCodeChange = onCodeChange,
                    focusOnCreate = config.focusOnCreate,
                    // Only a code the user reads off a document may be pasted; see
                    // PasteOnlyTextToolbar.
                    allowPaste = config.visibility == CodeVisibility.ALWAYS_VISIBLE,
                    modifier = Modifier.matchParentSize(),
                )
            }

            if (config.visibility == CodeVisibility.MASKED_REVEALABLE) {
                RevealToggle(
                    revealed = revealed,
                    onToggle = { revealed = !revealed },
                )
            }
        }

        state.supportingText?.let { text -> CodeEntryError(text = text) }
    }
}

/**
 * The error under the cells: a warning icon and the message, both in the error colour.
 *
 * The row is merged for accessibility and the icon keeps its "Warning" description, so a screen
 * reader announces "Warning, <message>" rather than dropping the cue that something went wrong.
 */
@Composable
private fun CodeEntryError(
    text: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Centres the icon on the first line, also when the message wraps.
    var firstLineHeight by remember { mutableStateOf(0.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // A fixed minimum, so the weighted layout around the cells does not move.
            .heightIn(min = ERROR_ICON_SLOT_SIZE)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(width = ERROR_ICON_SLOT_SIZE, height = firstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            WrapIcon(
                iconData = AppIcons.Warning,
                modifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                customTint = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            onTextLayout = { layout ->
                firstLineHeight = with(density) {
                    (layout.getLineBottom(0) - layout.getLineTop(0)).toDp()
                }
            },
        )
    }
}

/**
 * The row of digit cells. Purely decorative — the real control is [HiddenCodeTextField] on top of
 * it — so the whole row is dropped from the accessibility tree.
 */
@Composable
private fun CodeCells(
    buffer: CodeEntryBuffer,
    masked: Boolean,
    cellShape: Shape,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The next cell to receive a digit, i.e. where the (invisible) cursor sits. Past the last
        // index once the code is complete, so no cell matches and none is drawn as active.
        val activeIndex = remember(buffer.revision) { buffer.length }
        repeat(buffer.capacity) { index ->
            CodeCell(
                // Keyed on the revision rather than reading the array directly, which is what makes
                // an erasable char array observable; see CodeEntryBuffer.revision.
                digit = remember(buffer.revision) { buffer.digitAt(index) },
                masked = masked,
                isActive = index == activeIndex,
                shape = cellShape,
            )
        }
    }
}

/**
 * One cell. An entered digit fills the cell with `primaryContainer` and shows either the digit or
 * a mask glyph; an empty cell stays `tertiaryContainer` behind a `tertiaryOutline` border.
 *
 * Masked cells stay filled on purpose: the user still needs to see how many digits have landed,
 * and the count is not a secret.
 *
 * The empty cell that would receive the next digit — [isActive] — gets a highlighted outline, so
 * the cell row doubles as a cursor without the invisible field showing one.
 */
@Composable
private fun CodeCell(
    digit: Char?,
    masked: Boolean,
    isActive: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val filled = digit != null

    val fillProgress = remember { Animatable(initialAnimatedValue(filled)) }
    val dotAlpha = remember { Animatable(initialAnimatedValue(filled)) }

    // previousFilled seed from the live value so first composition doesn't animate,
    // and each reassigns before calling the suspending, cancellable animate function below — not
    // after — so a canceled run (fast type-then-delete) can't leave the bookkeeping stale and
    // desync the next transition's guard, freezing the Animatable wherever the canceled run left it.
    var previousFilled by remember { mutableStateOf(filled) }
    LaunchedEffect(filled) {
        val wasFilled = previousFilled
        previousFilled = filled
        if (wasFilled != filled) {
            animateFillChange(filled, fillProgress, dotAlpha)
        }
    }

    val restingBackground = MaterialTheme.colorScheme.tertiaryContainer
    val cellFillColor = MaterialTheme.colorScheme.primaryContainer

    // The radius needed to fully cover the cell from its center is the distance to a corner, i.e.
    // half the rectangle's diagonal. Computed once here, from the cell's fixed dp size, rather than
    // every animation frame inside drawBehind below.
    val maxFillRadiusPx = with(LocalDensity.current) {
        hypot(CELL_WIDTH.toPx(), CELL_HEIGHT.toPx()) / 2f
    }

    Box(
        modifier = modifier
            .width(CELL_WIDTH)
            .height(CELL_HEIGHT)
            .clip(shape)
            .drawBehind {
                // fillProgress is read here, in the draw phase, rather than in CodeCell's own
                // body, so its updates redraw this cell without recomposing or re-laying
                // it out.
                drawRect(color = restingBackground)
                drawCircle(
                    color = cellFillColor,
                    radius = fillProgress.value * maxFillRadiusPx,
                    center = center,
                )
            }
            .border(
                width = if (isActive) ACTIVE_CELL_BORDER_WIDTH else BORDER_STROKE_1.dp,
                color = when {
                    filled -> Color.Transparent
                    isActive -> ThemeColors.primaryButtonOutline
                    else -> ThemeColors.tertiaryOutline
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (digit != null) {
            // alpha is a lambda so dotAlpha.value is read in the draw phase (inside
            // CodeCellDigit's graphicsLayer), not here, so this doesn't recompose every frame.
            CodeCellDigit(digit = digit, masked = masked, alpha = { dotAlpha.value })
        }
    }
}

/** `1f` if a cell's animation should start already in its "arrived" state, `0f` otherwise. */
private fun initialAnimatedValue(active: Boolean): Float = if (active) 1f else 0f

/**
 * The fill/dot choreography for one cell, split out of [CodeCell] to keep its Cognitive Complexity
 * down.
 *
 * Doesn't touch the outline: becoming filled always coincides with losing `isActive` (typing only
 * happens into the active cell).
 */
private suspend fun animateFillChange(
    filled: Boolean,
    fillProgress: Animatable<Float, AnimationVector1D>,
    dotAlpha: Animatable<Float, AnimationVector1D>,
) {
    if (filled) {
        coroutineScope {
            launch {
                fillProgress.animateTo(1f, tween(CELL_FILL_DURATION_MS, easing = CELL_EASING))
            }
            launch {
                delay((CELL_FILL_DURATION_MS * CELL_DOT_DELAY_FRACTION).toLong().milliseconds)
                dotAlpha.animateTo(1f, tween(CELL_DOT_DURATION_MS, easing = CELL_EASING))
            }
        }
    } else {
        fillProgress.snapTo(0f)
        dotAlpha.snapTo(0f)
    }
}

/**
 * The digit, or its mask glyph, inside a filled [CodeCell].
 *
 * @param alpha a lambda, not a resolved `Float`, so it's read inside [Modifier.graphicsLayer]'s
 *   draw-phase block rather than here — keeping the fade-in from recomposing the caller.
 */
@Composable
private fun CodeCellDigit(digit: Char, masked: Boolean, alpha: () -> Float) {
    Text(
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
        text = if (masked) MASK_GLYPH else digit.toString(),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/**
 * The eye control. Styled as a circular sibling of the cells, and labelled with the action it will
 * perform — "Show" while masked, "Hide" while revealed — so a screen reader announces the effect
 * rather than the current state.
 *
 * The icon is a [WrapIconButton] rather than a bare clickable, which brings the ripple and the
 * 48 dp minimum touch target the design's 44 dp circle would otherwise fall short of.
 */
@Composable
private fun RevealToggle(
    revealed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(TOGGLE_SIZE)
            .background(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape)
            .border(
                width = BORDER_STROKE_1.dp,
                color = ThemeColors.tertiaryOutline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WrapIconButton(
            iconData = if (revealed) AppIcons.VisibilityOff else AppIcons.Visibility,
            customTint = MaterialTheme.colorScheme.onSurface,
            size = SIZE_LARGE.dp,
            // Revealing is idempotent and instant; throttling would just swallow quick toggles.
            throttleClicks = false,
            onClick = onToggle,
        )
    }
}

/**
 * The single real input widget: invisible, but the only thing that actually receives typing and
 * pasting. Transparent text and cursor keep it unseen while the cells behind it show the value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenCodeTextField(
    buffer: CodeEntryBuffer,
    onCodeChange: () -> Unit,
    focusOnCreate: Boolean,
    allowPaste: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Seeded a digit at a time rather than from a string, so that restoring the field after a
    // configuration change does not put the code back into an object that cannot be erased.
    val textFieldState = remember(buffer) {
        TextFieldState().apply {
            edit {
                repeat(buffer.length) { index -> buffer.digitAt(index)?.let(::append) }
                placeCursorAtEnd()
            }
        }
    }
    val transformation = remember(buffer) { PinDigitsOnlyTransformation(buffer.capacity) }

    // LocalTextToolbar is a *static* CompositionLocal and this toolbar has no equals, so providing
    // a fresh instance would invalidate the whole BasicTextField subtree on every keystroke.
    val platformToolbar = LocalTextToolbar.current
    val pasteOnlyToolbar = remember(platformToolbar, allowPaste) {
        PasteOnlyTextToolbar(delegate = platformToolbar, allowPaste = allowPaste)
    }

    LaunchedEffect(textFieldState, buffer) {
        snapshotFlow { textFieldState.text }.collect { newText ->
            textFieldState.undoState.clearHistory()
            buffer.set(newText)
            onCodeChange()
            if (buffer.isComplete) keyboardController?.hide()
        }
    }

    // Lets a ViewModel throw the input away — e.g. after a wrong code, or once the code has been
    // consumed. Kept in one long-lived collector rather than a LaunchedEffect(revision), which would
    // tear down and relaunch a coroutine on every keystroke only to find nothing to do.
    LaunchedEffect(textFieldState, buffer) {
        snapshotFlow { buffer.revision }.collect {
            if (buffer.length == 0 && textFieldState.text.isNotEmpty()) {
                textFieldState.clearText()
                textFieldState.undoState.clearHistory()
            }
        }
    }

    LaunchedEffect(focusOnCreate) {
        if (focusOnCreate) {
            delay(FOCUS_SETTLE_DELAY_MS) // Let the screen settle before raising the keyboard.
            focusRequester.requestFocus()
        }
    }

    CompositionLocalProvider(
        LocalTextToolbar provides pasteOnlyToolbar,
        // The field is invisible, so a selection highlight would show up as a stray coloured band.
        LocalTextSelectionColors provides TRANSPARENT_SELECTION_COLORS,
    ) {
        BasicTextField(
            state = textFieldState,
            modifier = modifier
                .focusRequester(focusRequester)
                .exposeTestTagsAsResourceId()
                .applyTestTag(CODE_ENTRY_FIELD_TEST_TAG),
            inputTransformation = transformation,
            textStyle = INVISIBLE_TEXT_STYLE,
            cursorBrush = INVISIBLE_CURSOR,
            keyboardOptions = CODE_KEYBOARD_OPTIONS,
        )
    }
}

private val CELL_WIDTH = 32.dp
private val CELL_HEIGHT = 44.dp
private val CELL_GAP = SPACING_EXTRA_MEDIUM.dp
private val TOGGLE_SIZE = 44.dp
private val ERROR_ICON_SLOT_SIZE = 28.dp
private const val MASK_GLYPH = "•"
private const val FOCUS_SETTLE_DELAY_MS = 300L

// Per the Figma spec.
private val CELL_EASING = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val CELL_FILL_DURATION_MS = 320
private const val CELL_DOT_DURATION_MS = 200
private const val CELL_DOT_DELAY_FRACTION = 0.35f
private val ACTIVE_CELL_BORDER_WIDTH = 2.dp

// Hoisted so the invisible field's per-keystroke recompositions allocate nothing.
private val TRANSPARENT_SELECTION_COLORS = TextSelectionColors(
    handleColor = Color.Transparent,
    backgroundColor = Color.Transparent,
)
private val INVISIBLE_TEXT_STYLE = TextStyle(color = Color.Transparent)
private val INVISIBLE_CURSOR = SolidColor(Color.Transparent)
private val CODE_KEYBOARD_OPTIONS = KeyboardOptions(
    // NumberPassword keeps the keypad numeric and suppresses IME suggestions, so a code is never
    // offered back as an autocomplete candidate.
    keyboardType = KeyboardType.NumberPassword,
    imeAction = ImeAction.Done,
)

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryFieldEmptyPreview() {
    PreviewTheme {
        CodeEntryField(
            state = codeEntryStateForPreview(capacity = CodeLength.WALLET_PIN),
            onCodeChange = {},
            config = CodeEntryConfig(focusOnCreate = false),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryFieldMaskedPreview() {
    PreviewTheme {
        CodeEntryField(
            state = codeEntryStateForPreview(
                capacity = CodeLength.WALLET_PIN,
                code = "1234",
            ),
            onCodeChange = {},
            config = CodeEntryConfig(focusOnCreate = false),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryFieldTransactionCodePreview() {
    PreviewTheme {
        CodeEntryField(
            state = codeEntryStateForPreview(capacity = 6, code = "141131"),
            onCodeChange = {},
            config = CodeEntryConfig(
                visibility = CodeVisibility.ALWAYS_VISIBLE,
                focusOnCreate = false,
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryFieldTransportPinWithErrorPreview() {
    PreviewTheme {
        CodeEntryField(
            state = codeEntryStateForPreview(
                capacity = CodeLength.TRANSPORT_PIN,
                code = "12",
                supportingText = stringResource(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin),
            ),
            onCodeChange = {},
            config = CodeEntryConfig(focusOnCreate = false),
        )
    }
}
