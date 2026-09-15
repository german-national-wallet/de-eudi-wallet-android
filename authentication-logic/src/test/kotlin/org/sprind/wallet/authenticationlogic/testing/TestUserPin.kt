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

package org.sprind.wallet.authenticationlogic.testing

import org.sprind.wallet.businesslogic.model.HighlySensitivePinData
import org.sprind.wallet.businesslogic.model.UserPin
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * An insecure (because it freely allows access to the [text]) implementation of [org.sprind.wallet.businesslogic.model.UserPin].
 *
 * @param text the text of the PIN.
 */
@OptIn(ExperimentalAtomicApi::class)
data class TestUserPin(val text: String) : UserPin {
    private val isCleared = AtomicBoolean(false)

    private val lastCharsReturned = AtomicReference<CharArray?>(null)
    override val length = text.length

    override fun getAndClear(): HighlySensitivePinData {
        if (isCleared.exchange(true)) {
            throw IllegalStateException("Already cleared")
        }
        val result = text.toCharArray()
        lastCharsReturned.exchange(result)
        return HighlySensitivePinData(result)
    }

    fun isCleared() = isCleared.load()

    fun lastCharsReturned() = lastCharsReturned.load()

    override fun toString() = text
}