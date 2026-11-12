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

package org.sprind.wallet.designsystem.typography

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.sprind.wallet.resourceslogic.theme.values.LEGACY_STYLE_MESSAGE

/** Hand-set styles outside the typescale; each screen should move to the style its design uses. */
object CustomTypography {
    @Deprecated(LEGACY_STYLE_MESSAGE)
    val titleMediumLarge: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.15.sp
        )

    @Deprecated(LEGACY_STYLE_MESSAGE)
    val titleMedium: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.W500,
        )

    @Deprecated(LEGACY_STYLE_MESSAGE)
    val titleLargeBold: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.W700
        )
}
