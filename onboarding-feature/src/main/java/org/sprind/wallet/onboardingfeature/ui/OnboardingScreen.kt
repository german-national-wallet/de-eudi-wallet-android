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

package org.sprind.wallet.onboardingfeature.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import eu.europa.ec.uilogic.component.wrap.WrapStickyTextButton
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.navigation.ModuleRoute
import kotlinx.coroutines.launch
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.WrapVideoAnimation

private val ONBOARDING_BOTTOM_SHEET_HEIGHT = 396.dp
private val ONBOARDING_BUTTON_HORIZONTAL_PADDING = 14.dp

data class OnboardingPageModel(
    val titleRes: Int,
    val bodyRes: Int,
    val animationRes: Int,
    val placeholderRes: Int,
    val primaryButtonTextRes: Int,
    val secondaryButtonTextRes: Int? = null,
    val isLastPage: Boolean = false,
    val isAnimationPausable: Boolean = false,
)

@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel,
) {
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.route) {
                        popUpTo(ModuleRoute.OnboardingModule.route) {
                            inclusive = true
                        }
                    }
                }
            }
        }
    }

    OnboardingRouteHost(
        pages = viewModel.pages,
        onFinish = viewModel::enterApplication,
    )
}

@Composable
private fun OnboardingRouteHost(
    pages: List<OnboardingPageModel>,
    onFinish: () -> Unit,
) {
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        OnboardingRouteScreen(
            model = pages[page],
            page = page,
            isCurrentPage = pagerState.currentPage == page,
            isPagerScrolling = pagerState.isScrollInProgress,
            onNext = {
                if (page == pages.lastIndex) {
                    onFinish()
                } else {
                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                }
            },
            onBack = {
                coroutineScope.launch { pagerState.animateScrollToPage(page - 1) }
                Unit
            }.takeIf { page > 0 },
            onSkip = onFinish,
        )
    }
}

@Composable
private fun OnboardingRouteScreen(
    model: OnboardingPageModel,
    page: Int,
    isCurrentPage: Boolean,
    isPagerScrolling: Boolean,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
) {
    var isAnimationPlaying by remember(model.animationRes.toString()) {
        mutableStateOf(true)
    }
    val primaryButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isCurrentPage) {
        // Only the visible page should move accessibility focus when the pager settles.
        if (isCurrentPage) {
            primaryButtonFocusRequester.requestFocus()
        }
    }

    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = onBack,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // TODO: Revisit WrapLottieAnimation once the animation issues
            //  are resolved, then switch back from video playback if appropriate.
            WrapVideoAnimation(
                video = model.animationRes,
                placeholder = model.placeholderRes,
                // Keep every animation still during the drag and pager settling animation.
                isPlaying = isAnimationPlaying && isCurrentPage && !isPagerScrolling,
            )

            onBack?.let { back ->
                OnboardingBackButton(
                    modifier = Modifier.align(Alignment.TopStart),
                    onBack = back,
                )
            }

            OnboardingBottomSheet(
                modifier = Modifier.align(Alignment.BottomCenter),
                model = model,
                page = page,
                primaryButtonFocusRequester = primaryButtonFocusRequester,
                onNext = onNext,
                onSkip = onSkip,
            )

            if (model.isAnimationPausable) {
                OnboardingAnimationToggleButton(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    isAnimationPlaying = isAnimationPlaying,
                    onToggle = { isAnimationPlaying = !isAnimationPlaying },
                )
            }
        }
    }
}

@Composable
private fun OnboardingBackButton(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    OutlinedIconButton(
        modifier = modifier
            .padding(
                horizontal = ONBOARDING_BUTTON_HORIZONTAL_PADDING,
                vertical = SPACING_EXTRA_LARGE.dp,
            ),
        onClick = onBack,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = BorderStroke(
            width = WEIGHT_1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        WrapIcon(iconData = AppIcons.ArrowBack)
    }
}

@Composable
private fun OnboardingAnimationToggleButton(
    modifier: Modifier = Modifier,
    isAnimationPlaying: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedIconButton(
        modifier = modifier
            .padding(
                end = ONBOARDING_BUTTON_HORIZONTAL_PADDING,
                bottom = ONBOARDING_BOTTOM_SHEET_HEIGHT + SPACING_EXTRA_MEDIUM.dp,
            ),
        onClick = onToggle,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xB2EDEDED),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        border = BorderStroke(
            width = WEIGHT_1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        WrapIcon(iconData = if (isAnimationPlaying) AppIcons.AnimationPause else AppIcons.AnimationPlay)
    }
}

@Composable
private fun OnboardingBottomSheet(
    modifier: Modifier = Modifier,
    model: OnboardingPageModel,
    page: Int,
    primaryButtonFocusRequester: FocusRequester,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ONBOARDING_BOTTOM_SHEET_HEIGHT),
        shape = RoundedCornerShape(topStart = SIZE_EXTRA_LARGE.dp, topEnd = SIZE_EXTRA_LARGE.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(
                bottom = SPACING_EXTRA_LARGE.dp,
                top = SPACING_LARGE_32.dp,
            ),
        ) {
            ContentTemplateBody(
                modifier = Modifier
                    .padding(horizontal = SPACING_MEDIUM.dp + SPACING_EXTRA_SMALL.dp)
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                templateConfig = ContentTemplateConfig(
                    illustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
                ),
                title = {
                    WrapText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() },
                        text = stringResource(model.titleRes),
                        textConfig = TextConfig(
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = Int.MAX_VALUE,
                        ),
                    )
                },
                body = {
                    WrapText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(model.bodyRes),
                        textConfig = TextConfig(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = Int.MAX_VALUE,
                        ),
                    )
                },
            )

            OnboardingStepIndicator(currentPage = page)

            Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                WrapStickyPrimaryButton(
                    modifier = Modifier.semantics(mergeDescendants = true) { role = Role.Button },
                    buttonModifier = Modifier.focusRequester(primaryButtonFocusRequester),
                    text = stringResource(model.primaryButtonTextRes),
                    paddingValues = PaddingValues(),
                    onClick = onNext,
                    enabled = true,
                    trailingIcon = if (model.isLastPage) AppIcons.ArrowForward else null,
                )
                model.secondaryButtonTextRes?.let {
                    WrapStickyTextButton(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                        text = stringResource(it),
                        paddingValues = PaddingValues(),
                        onClick = onSkip,
                    )
                } ?: Spacer(modifier = Modifier.height(SIZE_EXTRA_LARGE.dp))
            }
        }
    }
}

@Composable
private fun OnboardingStepIndicator(currentPage: Int) {
    val paginationContentDescription = stringResource(
        when (currentPage) {
            0 -> R.string.app_onboarding_onboarding_1_pagination_1_3_a11y
            1 -> R.string.app_onboarding_onboarding_2_pagination_2_3_a11y
            else -> R.string.app_onboarding_onboarding_3_pagination_3_3_a11y
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SPACING_MEDIUM.dp)
            .semantics { contentDescription = paginationContentDescription },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = SPACING_EXTRA_SMALL.dp)
                    .size(SPACING_SMALL.dp)
                    .background(
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = CircleShape,
                    )
                    .clearAndSetSemantics { },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OnboardingPageScreenPreview() {
    PreviewTheme {
        OnboardingRouteHost(
            pages = listOf(
                OnboardingPageModel(
                    titleRes = R.string.app_onboarding_onboarding_1_title,
                    bodyRes = R.string.app_onboarding_onboarding_1_paragraph,
                    animationRes = R.raw.onboarding_1_video,
                    placeholderRes = R.drawable.onboarding_1_placeholder,
                    primaryButtonTextRes = R.string.app_onboarding_onboarding_1_prim_button,
                    secondaryButtonTextRes = R.string.app_onboarding_onboarding_1_tertiary_button,
                ),
            ),
            onFinish = {},
        )
    }
}