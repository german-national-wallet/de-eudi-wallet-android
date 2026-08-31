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

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * A Lottie animation from `res/raw`, looping by default.
 *
 * Takes both `.json` and `.lottie` files, which the designers export.
 *
 * @param animation the raw resource holding the animation.
 * @param modifier applied to the animation; give it the size the design draws it at.
 * @param iterations how many times to play it; loops forever by default.
 * @param contentScale how the animation fills [modifier]'s bounds.
 */
@Composable
fun WrapLottieAnimation(
    @RawRes animation: Int,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))

    LottieAnimation(
        composition = composition,
        modifier = modifier,
        iterations = iterations,
        contentScale = contentScale,
    )
}