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

package org.sprind.wallet.uilogic.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

/**
 * The motion spec's "Fade & Slide"
 */
private const val TRANSITION_DURATION_MS = 420

/** The spec's fade factor: the opacity runs for 70% of the movement. */
private const val FADE_DURATION_MS = 294

/** How far a screen travels, as a fraction of the width it travels across. */
private const val SLIDE_FRACTION = 0.3f

private val transformSpec = tween<IntOffset>(
    durationMillis = TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

private val fadeSpec = tween<Float>(
    durationMillis = FADE_DURATION_MS,
    easing = FastOutSlowInEasing,
)

private fun slideOffset(fullWidth: Int, towardsStart: Boolean): Int {
    val distance = (fullWidth * SLIDE_FRACTION).roundToInt()
    return if (towardsStart) -distance else distance
}

/** Going forward: the new screen arrives from the end side. */
val screenEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(transformSpec) { width -> slideOffset(width, towardsStart = false) } +
            fadeIn(fadeSpec)
}

/** Going forward: the screen being left gives way towards the start side. */
val screenExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(transformSpec) { width -> slideOffset(width, towardsStart = true) } +
            fadeOut(fadeSpec)
}

/** Going back: the screen returned to comes back from the start side, the way it left. */
val screenPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(transformSpec) { width -> slideOffset(width, towardsStart = true) } +
            fadeIn(fadeSpec)
}

/** Going back: the screen being dismissed leaves towards the end side. */
val screenPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(transformSpec) { width -> slideOffset(width, towardsStart = false) } +
            fadeOut(fadeSpec)
}
