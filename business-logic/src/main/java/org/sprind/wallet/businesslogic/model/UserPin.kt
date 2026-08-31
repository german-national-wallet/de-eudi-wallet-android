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

package org.sprind.wallet.businesslogic.model

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

interface UserPin {
    /**
     * Destructively returns the highly sensitive PIN data. May only be called once.
     *
     * Because the returned value is highly sensitive, it should
     * be held for as little time as possible, handled by as little code
     * as possible, and the caller must call close() on the returned value
     * as soon as the value is no longer needed. It is recommended that
     * callers follow this pattern:
     *
     * ```
     * pin.getAndClear().use() {
     *  // Short block of code that uses pin.chars
     * }
     * ```
     *
     * @return the characters making up this PIN, in order.
     * @throws IllegalStateException if [getAndClear] has already been called on this object.
     */
    fun getAndClear(): HighlySensitivePinData

    /**
     * The length of this [UserPin] in digits. After [getAndClear] was called, this will
     * still hold the original number of (now unavailable) digits.
     */
    val length: Int
}


/**
 * A PIN or password entered by the user. Instances of this class should not
 * be used as keys in HashMaps, as that would be very inefficient: [hashCode()]
 * includes no or minimum bits of information from the content.
 *
 * This class is designed to protect the PIN against the following threat model:
 *
 * In the threat model: Careless app code that keeps a long-lived reference to
 * this object, or shares the reference with app code beyond what strictly has
 * a need to know, or carelessly logs an instance's hashCode.
 *
 * Outside the threat model: Malicious app code, attackers who can call methods
 * other than hashCode on this object, or inspect its private attributes.
 *
 * @param text the text of the PIN. Upon construction, this class copies the given [text]'s
 *        characters into an internal field and guarantees to not hold a reference to [text] after
 *        the constructor completes. The caller is responsible for safely erasing [text]'s
 *        memory footprint afterwards.
 */
@OptIn(ExperimentalAtomicApi::class)
class UserPinImpl(text: CharSequence) : UserPin {
    private val data: AtomicReference<HighlySensitivePinData?> = AtomicReference(
        HighlySensitivePinData(CharArray(text.length) { i -> text[i] }) )
    override val length = text.length

    override fun getAndClear(): HighlySensitivePinData {
        return data.exchange(null) ?: throw IllegalStateException("Already cleared")
    }

    // Since our threat model does not include malicious app code or malicious attackers
    // who can call methods on this class, it's okay to override equals and hashCode.
    // This is useful e.g. when the user has to enter a new PIN twice, and the two PINs
    // have to match.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPinImpl || length != other.length) return false
        // A cleared PIN equals nothing, not even another cleared PIN: comparing two consumed
        // instances must not report a match a caller would read as "the PINs are the same".
        val data = data.load() ?: return false
        val otherData = other.data.load() ?: return false
        return data == otherData
    }

    // We return a constant because that fulfills the requirement that equal instances must have
    // equal hashCodes, but without giving away anything about the content.
    override fun hashCode() = 0

    override fun toString(): String {
        return "UserPin[${length} digits]"
    }
}

/**
 * The characters of a PIN, on loan from a [UserPin] until [close] erases them.
 *
 * Deliberately not a `data class`: the generated `copy()` and `component1()` would hand the live
 * [chars] array out to callers that have no part in the [close] contract, which is the opposite of
 * what this type is for.
 */
class HighlySensitivePinData(
    val chars: CharArray
) : AutoCloseable {
    override fun close() = chars.fill('X')

    override fun equals(other: Any?): Boolean =
        other is HighlySensitivePinData && chars.contentEquals(other.chars)

    // Content-based, unlike UserPinImpl.hashCode(), so instances of this class must never be
    // logged or used as map keys: a content hash over six digits is invertible by trying all
    // million candidates.
    override fun hashCode(): Int = chars.contentHashCode()

    override fun toString(): String = "[redacted]"
}
