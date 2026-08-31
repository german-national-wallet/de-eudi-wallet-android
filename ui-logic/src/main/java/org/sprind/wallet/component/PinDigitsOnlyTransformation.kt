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

package org.sprind.wallet.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete

data class PinDigitsOnlyTransformation(private val pinLength: Int): InputTransformation {
    init {
        require(pinLength >= 0) { "Invalid pinLength: $pinLength" }
    }
    override fun TextFieldBuffer.transformInput() {

        // only digits; count downwards to avoid index shifts during delete()
        for (i in length - 1 downTo 0) {
            if (!charAt(i).isDigit()) {
                delete(i, i + 1)
            }
        }
        // removing non-digits might have changed the length, so we apply this constraint second
        if (length > pinLength) {
            delete(pinLength, length)
        }
    }
}