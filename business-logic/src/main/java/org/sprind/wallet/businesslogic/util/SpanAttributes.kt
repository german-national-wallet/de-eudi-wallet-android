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

package org.sprind.wallet.businesslogic.util

/**
 * A data class representing a collection of key-value attributes for telemetry purposes.
 *
 * This class encapsulates a map of string key-value pairs, typically used to attach metadata to telemetry events or spans.
 * It is designed to be immutable by ensuring its internal map is a read-only, defensive copy.
 * The attributes maintain insertion order for consistent iteration.
 *
 * @param map The internal, read-only map storing the attributes.
 *
 * @see Telemetry
 * @see io.opentelemetry.api.common.AttributeKey
 */
data class SpanAttributes(
    private val map: Map<String, String>
) : Iterable<Map.Entry<String, String>> {

    companion object {
        /** An empty, shared instance. */
        val EMPTY: SpanAttributes = SpanAttributes(emptyMap())

        /** Creates from pairs, preserving insertion order. */
        fun of(vararg pairs: Pair<String, String>): SpanAttributes =
            if (pairs.isEmpty()) EMPTY
            else SpanAttributes(pairs.filter { it.first.isNotEmpty() }.toMap())


        /** Wraps a map; copies to a LinkedHashMap to guarantee order & isolation. */
        fun fromMap(src: Map<String, String>): SpanAttributes =
            if (src.isEmpty()) EMPTY
            else SpanAttributes(src.filterKeys { it.isNotEmpty() }.toMap())

    }

    /** True if name exists (case-sensitive). */
    fun contains(name: String): Boolean = map.containsKey(name)

    /** Returns the value for [name], or null if absent. */
    operator fun get(name: String): String? = map[name]

    /** Number of attributes. */
    val size: Int get() = map.size

    /** True if there are no attributes. */
    fun isEmpty(): Boolean = map.isEmpty()

    /** Returns a read-only copy as a `Map`. */
    fun toMap(): Map<String, String> = map

    /** Adds/replaces a single attribute. */
    fun withAttribute(name: String, value: String): SpanAttributes =
        if (map[name] == value) this
        else fromMap(LinkedHashMap(map).apply { put(name, value) })


    /** Merges two sets; values in [other] overwrite existing keys. */
    operator fun plus(other: SpanAttributes): SpanAttributes =
        if (other.isEmpty()) this
        else fromMap(LinkedHashMap(map).apply { putAll(other.map) })

    /** Allows `for ((k, v) in attributes)` style iteration in insertion order. */
    override fun iterator(): Iterator<Map.Entry<String, String>> =
        map.iterator()
}