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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapCard
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import org.sprind.wallet.uilogic.component.preview.ContentTemplatePreviewButtons

/**
 * Where the [ContentTemplateBody] illustration is placed relative to the title/body text.
 *
 * - [ABOVE_TITLE] — the illustration sits at the top, before the title (the "Variant 2" and
 *   "Push notification" layouts).
 * - [BELOW_TEXT] — the illustration sits between the body text and the `extraContent` block
 *   (the "Default" intro layout).
 */
enum class ContentIllustrationPlacement { ABOVE_TITLE, BELOW_TEXT }

/**
 * Layout options for [ContentTemplateBody].
 *
 * The body's *content* is supplied through composable slots (title, body, illustration, …) and its
 * *text styling* flows through [LocalContentTemplateStyle]; this holder only carries the remaining
 * non-composable layout knobs. A screen's MVI `State` typically maps into one of these.
 *
 * @property verticalSpacing gap between the major body sections (illustration, text, extra content).
 * @property illustrationPlacement where the `illustration` slot is positioned; see
 *   [ContentIllustrationPlacement].
 */
@Immutable
data class ContentTemplateConfig(
    val verticalSpacing: Dp = SPACING_LARGE_32.dp,
    val illustrationPlacement: ContentIllustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
)

/**
 * Text styling applied to [ContentTemplateBody]'s `title` and `body` slots.
 *
 * It is threaded through [LocalContentTemplateStyle] rather than passed as parameters, so the slots
 * can hold plain [Text] composables that inherit the right style and color, and nested content can
 * read the active style if needed. Override it for a subtree with [ProvideContentTemplateStyle],
 * e.g. `ContentTemplateDefaults.style.copy(titleTextStyle = MaterialTheme.typography.titleLarge)`.
 *
 * @property titleTextStyle style applied to the `title` slot (exposed to a plain [Text] via
 *   [ProvideTextStyle]).
 * @property bodyTextStyle style applied to the `body` slot.
 * @property contentColor color provided to both slots via [LocalContentColor].
 */
@Immutable
data class ContentTemplateStyle(
    val titleTextStyle: TextStyle,
    val bodyTextStyle: TextStyle,
    val contentColor: Color,
)

/** Defaults for [ContentTemplateBody], resolved from the current [MaterialTheme]. */
object ContentTemplateDefaults {
    val style: ContentTemplateStyle
        @Composable get() = ContentTemplateStyle(
            titleTextStyle = MaterialTheme.typography.headlineMedium,
            bodyTextStyle = MaterialTheme.typography.bodyLarge,
            contentColor = MaterialTheme.colorScheme.onBackground,
        )
}

/**
 * Ambient [ContentTemplateStyle] for [ContentTemplateBody]'s text slots. `null` means ´use
 * [ContentTemplateDefaults.style]´; provide a value with [ProvideContentTemplateStyle] to override.
 */
val LocalContentTemplateStyle = staticCompositionLocalOf<ContentTemplateStyle?> { null }

/**
 * Provides [style] as the ambient [ContentTemplateStyle] for [content], overriding the default text
 * styling of any [ContentTemplateBody] rendered inside.
 */
@Composable
fun ProvideContentTemplateStyle(
    style: ContentTemplateStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentTemplateStyle provides style, content = content)
}

/**
 * The shared body layout for the "content" informational screens (see the EUDI design system
 * "Content intro" and "Content push notification" templates). Drop it into a [ContentScreen]'s
 * `bodyContent` slot: [ContentScreen] already owns the chrome (toolbar, loading, sticky-bottom
 * actions, back handling), so this composable is *only* the scrolling title/body/illustration column
 * and stays reusable under any screen configuration. Pass the body [PaddingValues] via [modifier].
 *
 * It expresses the design variants through slots + [templateConfig] rather than near-duplicate layouts:
 *
 * 1. **Intro – Default:** [ContentTemplateConfig.illustrationPlacement] =
 *    [ContentIllustrationPlacement.BELOW_TEXT], with an `extraContent` of [ContentNotice].
 * 2. **Intro – Variant 2:** [ContentTemplateConfig.illustrationPlacement] =
 *    [ContentIllustrationPlacement.ABOVE_TITLE] (a boxed [ContentIllustration] on top) plus a
 *    [ContentNotice] as `extraContent`.
 * 3. **Push notification:** a boxed [ContentIllustration] on top and a [ContentChecklist] as
 *    `extraContent`.
 *
 * Text styling is not passed in: [title] and [body] typically hold plain [Text] composables that
 * inherit their style and color from [LocalContentTemplateStyle] (defaulting to
 * [ContentTemplateDefaults.style]). Wrap the caller in [ProvideContentTemplateStyle] to override —
 * the push-notification template, for instance, provides a `titleLarge` title style that way.
 *
 * Accessibility:
 * - the title slot is exposed as a heading so screen readers can navigate to it directly;
 * - the body scrolls vertically, so it stays usable at large font scales and on short screens;
 * - decorative iconography (illustrations, notice/checklist icons) is hidden from the accessibility
 *   tree, and notice/checklist rows are merged so each is announced as a single phrase;
 * - the checklist exposes list/collection semantics so each row is announced with its position
 *   (e.g. "item 2 of 3").
 *
 * @param title the headline slot; announced as a heading and styled with
 *   [ContentTemplateStyle.titleTextStyle].
 * @param modifier applied to the outer column; pass the [ContentScreen] body [PaddingValues] here,
 *   e.g. `Modifier.padding(paddingValues)`.
 * @param templateConfig the layout options; see [ContentTemplateConfig].
 * @param body optional supporting-paragraph slot shown under the title, styled with
 *   [ContentTemplateStyle.bodyTextStyle].
 * @param illustration optional illustration slot; use [ContentIllustration] for the boxed style. It
 *   is centered horizontally in the body, and its vertical position is controlled by
 *   [ContentTemplateConfig.illustrationPlacement].
 * @param progressContent optional slot pinned above the scroll (e.g. a step progress indicator).
 * @param extraContent optional slot under the text; typically a [ContentNotice] or [ContentChecklist].
 */
@Composable
fun ContentTemplateBody(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    templateConfig: ContentTemplateConfig = ContentTemplateConfig(),
    body: (@Composable () -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null,
    progressContent: (@Composable ColumnScope.() -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val style = LocalContentTemplateStyle.current ?: ContentTemplateDefaults.style

    // Re-provide the resolved style so the text slots (and any nested content) share it.
    CompositionLocalProvider(LocalContentTemplateStyle provides style) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(templateConfig.verticalSpacing),
        ) {
            // Pinned above the scroll so a step progress indicator stays visible while
            // the rest of the content scrolls underneath it.
            progressContent?.invoke(this)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(templateConfig.verticalSpacing),
            ) {
                if (illustration != null && templateConfig.illustrationPlacement == ContentIllustrationPlacement.ABOVE_TITLE) {
                    CenteredIllustration(illustration)
                }

                Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { heading() },
                    ) {
                        ProvideContentTemplateTextStyle(
                            textStyle = style.titleTextStyle,
                            color = style.contentColor,
                            content = title,
                        )
                    }
                    body?.let { bodySlot ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ProvideContentTemplateTextStyle(
                                textStyle = style.bodyTextStyle,
                                color = style.contentColor,
                                content = bodySlot,
                            )
                        }
                    }
                }

                if (illustration != null && templateConfig.illustrationPlacement == ContentIllustrationPlacement.BELOW_TEXT) {
                    CenteredIllustration(illustration)
                }

                extraContent?.invoke(this)
            }
        }
    }
}

/**
 * Places a [ContentTemplateBody] illustration slot across the body and centers it horizontally.
 *
 * The body column is start-aligned, which is right for text but leaves artwork narrower than the
 * screen — the eID card, the PIN letter — hugging the left edge instead of sitting centered under
 * the title as the designs show. Illustrations that already fill the width, such as
 * [ContentIllustration], are unaffected.
 */
@Composable
private fun CenteredIllustration(illustration: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
        content = { illustration() },
    )
}

/**
 * Provides [textStyle] and [color] to a [ContentTemplateBody] text slot through the standard
 * [ProvideTextStyle]/[LocalContentColor] CompositionLocals, so a plain [Text] in the slot inherits
 * both without the caller restating them.
 */
@Composable
private fun ProvideContentTemplateTextStyle(
    textStyle: TextStyle,
    color: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides color) {
        ProvideTextStyle(value = textStyle, content = content)
    }
}

/**
 * A boxed illustration panel: a rounded [MaterialTheme.colorScheme] `surfaceContainer` box of fixed
 * [height] that centers its [content], used by the "Variant 2" and "Push notification" templates.
 * Put the artwork (typically a [WrapImage]) in the [content] slot.
 *
 * @param contentDescription a description for screen readers describing the whole panel, or `null`
 *   to mark it purely decorative and hide it (and its content) from the accessibility tree. It has
 *   no default on purpose: callers must make the decorative-vs-described choice explicitly, so a
 *   meaningful illustration is never silently dropped from the accessibility tree.
 * @param modifier applied to the panel.
 * @param height the panel height.
 * @param content the artwork centered inside the panel.
 */
@Composable
fun ContentIllustration(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = ILLUSTRATION_HEIGHT,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(ILLUSTRATION_CORNER))
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .describedForAccessibility(contentDescription),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A highlighted notice: a rounded `surfaceContainer` card with a leading icon and a paragraph slot,
 * used by the intro templates to call out an important instruction. It shares the [WrapCard]
 * primitive with [eu.europa.ec.uilogic.component.wrap.Banner] so notice styling stays consistent.
 *
 * The [content] slot typically holds a plain [Text]; it inherits the notice's body text style and
 * `onSurface` color (via [ProvideTextStyle]/[LocalContentColor]), so callers don't restate them and
 * can pass annotated/linked text when needed.
 *
 * The row is merged for accessibility and the [icon] keeps its content description (e.g. "Warning"),
 * so a screen reader announces the notice as, for example, "Warning, <text>" rather than dropping
 * the cue that it is an alert. With no icon the notice is announced as just its text.
 *
 * @param icon the leading icon, or `null` for the plain notice the designs use where the text alone
 *   carries the message (the NFC activation screen, for instance).
 */
@Composable
fun ContentNotice(
    modifier: Modifier = Modifier,
    icon: IconData? = AppIcons.Warning,
    content: @Composable () -> Unit,
) {
    WrapCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(NOTICE_CORNER),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_MEDIUM.dp),
            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            verticalAlignment = Alignment.Top,
        ) {
            icon?.let {
                WrapIcon(
                    iconData = it,
                    modifier = Modifier.size(SIZE_LARGE.dp),
                    customTint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                // The card already provides onSurface via LocalContentColor; only the text style needs setting.
                ProvideTextStyle(value = MaterialTheme.typography.bodyLarge, content = content)
            }
        }
    }
}

/**
 * A single row of a [ContentChecklist]: a decorative leading [icon] followed by its [text].
 *
 * @property text the row text.
 * @property icon the leading icon; hidden from the accessibility tree. Defaults to [AppIcons.Check]
 *   for the "positive points" checklist, but can be set per row to build mixed-icon lists such as
 *   the wallet-revocation intro (a key row and a lock row).
 */
data class ContentChecklistItem(
    val text: String,
    val icon: IconData = AppIcons.Check,
)

/**
 * A list of rows, each with a leading icon and a line of text. Used both for the push-notification
 * template's "positive points" (a uniform check icon) and for mixed-icon lists such as the
 * wallet-revocation intro, where each row carries its own [ContentChecklistItem.icon].
 *
 * Each row is merged for accessibility and its icon is decorative, so a row is announced as just
 * its text. The list is exposed to accessibility services as a single-column collection, and each
 * row reports its position, so screen readers announce it as e.g. "item 2 of 3".
 */
@Composable
fun ContentChecklist(
    items: List<ContentChecklistItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(rowCount = items.size, columnCount = 1)
            },
        verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SPACING_EXTRA_SMALL.dp)
                    .semantics(mergeDescendants = true) {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = index,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1,
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                verticalAlignment = Alignment.Top,
            ) {
                WrapIcon(
                    iconData = item.icon,
                    modifier = Modifier
                        .size(SIZE_LARGE.dp)
                        .clearForAccessibility(),
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A stepped linear progress indicator for the top of an onboarding flow (the intro template's
 * \"4-step progress indicator\"). It renders [totalSteps] equal segments: the steps before
 * [currentStep] are filled with the primary color, the rest use the track color.
 *
 * [currentStep] is the step the user is *on*, not a count of finished ones, so by default its
 * segment is drawn half filled: standing on a screen means working through that step, not having
 * finished it. Screens that close a step out — a success screen at the end of the journey — pass
 * `currentStepCompleted = true` to fill it whole.
 *
 * Moving to another step animates: the fill travels along the row
 *
 * Typically passed to [ContentTemplateBody]'s `progressContent` slot.
 *
 * @param currentStep the step being worked on, counting from 1 (coerced into `0..totalSteps`); 0
 *   means the flow is not on a step of this journey and nothing is filled.
 * @param totalSteps total number of steps; values below 1 render nothing.
 * @param modifier applied to the indicator row.
 * @param currentStepCompleted whether [currentStep] is done rather than in progress, which fills its
 *   segment whole instead of half.
 * @param contentDescription optional spoken description (e.g. "Step 2 of 4").
 */
@Composable
fun ContentStepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    currentStepCompleted: Boolean = false,
    contentDescription: String? = null,
) {
    if (totalSteps < 1) return

    val current = currentStep.coerceIn(0, totalSteps)
    val completedSteps = if (currentStepCompleted) current else (current - 1).coerceAtLeast(0)
    // No step is under way when the flow is off the journey, or when the current one is closed out.
    val stepInProgress = current > 0 && !currentStepCompleted

    // How far the fill reaches, measured across the whole row rather than per segment
    val targetProgress = completedSteps + if (stepInProgress) STEP_IN_PROGRESS_FRACTION else 0f
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "stepProgress",
    )

    val semanticsModifier = if (contentDescription == null) {
        Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = targetProgress,
                range = 0f..totalSteps.toFloat(),
                // The bar lands on halves, so two values per step, less the closing endpoint.
                steps = totalSteps * 2 - 1,
            )
        }
    } else {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(semanticsModifier),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        repeat(totalSteps) { index ->
            LinearProgressIndicator(
                progress = { (progress - index).coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(STEP_BAR_HEIGHT)
                    .clearForAccessibility(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/** Hides a purely decorative node from accessibility services. */
internal fun Modifier.clearForAccessibility(): Modifier = clearAndSetSemantics { }

/**
 * Labels a node with [description] for accessibility, or hides it entirely when [description] is
 * null. Replaces any descendant semantics so an inner labeled child (e.g. a [WrapImage]) is not
 * announced separately.
 */
private fun Modifier.describedForAccessibility(description: String?): Modifier =
    if (description == null) {
        clearForAccessibility()
    } else {
        clearAndSetSemantics { contentDescription = description }
    }

private val ILLUSTRATION_HEIGHT = 184.dp
private val ILLUSTRATION_CORNER = 24.dp
private val NOTICE_CORNER = 12.dp
private val STEP_BAR_HEIGHT = 4.dp

private const val STEP_IN_PROGRESS_FRACTION = 0.5f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentTemplateIntroDefaultPreview() {
    PreviewTheme {
        ContentScreen(
            toolBarConfig = ToolbarConfig(
                actions = listOf(
                    ToolbarAction(icon = AppIcons.Info, onClick = {}),
                    ToolbarAction(icon = AppIcons.Close, onClick = {}),
                ),
            ),
            stickyBottom = { padding -> ContentTemplatePreviewButtons(padding = padding) },
        ) { paddingValues ->
            ContentTemplateBody(
                modifier = Modifier.padding(paddingValues),
                title = {
                    Text(stringResource(R.string.pid_inspection_pid_personal_data_banner))
                },
                body = {
                    Text(stringResource(R.string.pid_inspection_pid_personal_data_banner)
                    )
                },
                templateConfig = ContentTemplateConfig(
                    illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
                ),
                illustration = {
                    WrapImage(
                        iconData = AppIcons.LogoPlain,
                        modifier = Modifier.size(ILLUSTRATION_HEIGHT),
                        contentScale = ContentScale.Fit,
                    )
                },
                extraContent = {
                    ContentNotice {
                        Text(stringResource(R.string.pid_inspection_pid_personal_data_banner)
                        )
                    }
                },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentTemplateIntroWithProgressPreview() {
    PreviewTheme {
        ContentScreen(
            toolBarConfig = ToolbarConfig(
                actions = listOf(
                    ToolbarAction(icon = AppIcons.Info, onClick = {}),
                    ToolbarAction(icon = AppIcons.Close, onClick = {}),
                ),
            ),
            stickyBottom = { padding -> ContentTemplatePreviewButtons(padding = padding) },
        ) { paddingValues ->
            ContentTemplateBody(
                modifier = Modifier.padding(paddingValues),
                title = {
                    Text("Hast du die 6-stellige Karten-PIN deines Ausweises schon festgelegt?")
                },
                body = {
                    Text(
                        "Um den Vorgang abzuschließen, gehe zurück zum Dienst, von dem aus du die " +
                            "Identifizierung gestartet hast.",
                    )
                },
                templateConfig = ContentTemplateConfig(
                    illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
                ),
                progressContent = {
                    ContentStepProgressIndicator(
                        currentStep = 1,
                        totalSteps = 4,
                        contentDescription = "Step 1 of 4",
                    )
                },
                illustration = {
                    WrapImage(
                        iconData = AppIcons.LogoPlain,
                        modifier = Modifier.size(ILLUSTRATION_HEIGHT),
                        contentScale = ContentScale.Fit,
                    )
                },
                extraContent = {
                    ContentNotice {
                        Text(
                            "Um den Vorgang abzuschließen, gehe zurück zum Dienst, von dem aus " +
                                "du die Identifizierung gestartet hast.",
                        )
                    }
                },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentTemplateIntroVariant2Preview() {
    PreviewTheme {
        ContentScreen(
            toolBarConfig = ToolbarConfig(
                actions = listOf(
                    ToolbarAction(icon = AppIcons.Info, onClick = {}),
                    ToolbarAction(icon = AppIcons.Close, onClick = {}),
                ),
            ),
            stickyBottom = { padding -> ContentTemplatePreviewButtons(padding = padding) },
        ) { paddingValues ->
            ContentTemplateBody(
                modifier = Modifier.padding(paddingValues),
                title = {
                    Text("Hast du die 6-stellige Karten-PIN deines Ausweises schon festgelegt?")
                },
                body = {
                    Text(
                        "Um den Vorgang abzuschließen, gehe zurück zum Dienst, von dem aus du die " +
                            "Identifizierung gestartet hast.",
                    )
                },
                templateConfig = ContentTemplateConfig(
                    illustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
                ),
                illustration = {
                    ContentIllustration(contentDescription = null) {
                        WrapImage(
                            iconData = AppIcons.LogoPlain,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                },
                extraContent = {
                    ContentNotice {
                        Text(stringResource(R.string.pid_presentation_rwscd_auth_verification_failed_sec_button),
                        )
                    }
                },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentTemplatePushNotificationPreview() {
    PreviewTheme {
        // The push-notification template overrides the title style through the CompositionLocal.
        ProvideContentTemplateStyle(
            style = ContentTemplateDefaults.style.copy(
                titleTextStyle = MaterialTheme.typography.titleLarge,
            ),
        ) {
            ContentScreen(
                onBack = {},
                toolBarConfig = ToolbarConfig(
                    actions = listOf(ToolbarAction(icon = AppIcons.Info, onClick = {})),
                ),
                stickyBottom = { padding ->
                    WrapStickyBottomContent(
                        stickyBottomModifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SPACING_MEDIUM.dp, bottom = padding.calculateBottomPadding())
                            .padding(horizontal = SPACING_MEDIUM.dp),
                        stickyBottomConfig = StickyBottomConfig(
                            showDivider = false,
                            type = StickyBottomType.OneButton(
                                config = ButtonConfig(
                                    type = ButtonType.PRIMARY,
                                    enabled = true,
                                    onClick = {},
                                ),
                            ),
                        ),
                    ) {
                        Text(text = stringResource(R.string.pid_presentation_success_prim_button))
                    }
                },
            ) { paddingValues ->
                ContentTemplateBody(
                    modifier = Modifier.padding(paddingValues),
                    title = { Text("Enable security notifications") },
                    body = {
                        Text(stringResource(R.string.pid_inspection_pid_personal_data_banner))
                    },
                    templateConfig = ContentTemplateConfig(
                        verticalSpacing = SPACING_MEDIUM.dp,
                        illustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
                    ),
                    illustration = {
                        ContentIllustration(contentDescription = null) {
                            WrapImage(
                                iconData = AppIcons.LogoPlain,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    },
                    extraContent = {
                        ContentChecklist(
                            items = listOf(
                                ContentChecklistItem(stringResource(R.string.pid_inspection_pid_personal_data_banner)),
                                ContentChecklistItem(stringResource(R.string.pid_inspection_pid_personal_data_banner)),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentChecklistMixedIconsPreview() {
    PreviewTheme {
        ContentChecklist(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            items = listOf(
                ContentChecklistItem(
                    text = "App data is protected by the highest security standards.",
                    icon = AppIcons.Shield,
                ),
                ContentChecklistItem(
                    text = "Notifications only if there’s a security issue in your app.",
                ),
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentStepProgressIndicatorPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        ) {
            // The journey walked from its first step to its last, which the success screen closes.
            ContentStepProgressIndicator(currentStep = 1, totalSteps = 4)
            ContentStepProgressIndicator(currentStep = 2, totalSteps = 4)
            ContentStepProgressIndicator(currentStep = 4, totalSteps = 4)
            ContentStepProgressIndicator(
                currentStep = 4,
                totalSteps = 4,
                currentStepCompleted = true,
            )
        }
    }
}