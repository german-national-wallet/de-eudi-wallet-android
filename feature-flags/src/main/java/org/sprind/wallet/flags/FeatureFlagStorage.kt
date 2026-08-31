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
package org.sprind.wallet.flags

/**
 * Abstraction for persistent storage of feature flags.
 *
 * This interface decouples the feature-flags module from Android-specific storage implementations
 * (SharedPreferences, EncryptedSharedPreferences, etc.). Implementations are responsible for
 * persisting and retrieving feature flag data as serialized strings.
 *
 * ## Storage Semantics
 * - Empty string (`""`) represents "no value stored" - callers should handle this as initial/default state
 * - All methods are synchronous and should complete quickly (no blocking I/O on main thread)
 * - Implementations must be thread-safe as they may be accessed from multiple threads
 *
 * ## Listener Behavior
 * The [setOnFlagsChangedListener] callback is invoked when stored flags change. Callbacks may be
 * executed on the main thread or background threads depending on the implementation. Callers should
 * ensure thread-safety when handling callbacks, especially if accessing shared state or UI components.
 *
 * ## Usage Example
 * ```kotlin
 * val storage: FeatureFlagStorage = ...
 * val flags = storage.getStoredFlags()
 * if (flags.isEmpty()) {
 *     // No flags stored yet - use defaults
 * }
 * storage.setOnFlagsChangedListener { newFlags ->
 *     // Handle flag updates (ensure thread-safety)
 * }
 * ```
 */
interface FeatureFlagStorage {
    /**
     * Retrieves the currently stored feature flags as a serialized string.
     *
     * @return Serialized feature flags, or empty string if no flags are stored.
     *   Empty string should be treated as "use default values" by callers.
     */
    fun getStoredFlags(): String

    /**
     * Stores feature flags as a serialized string.
     *
     * @param flags Serialized feature flags to persist. Empty string clears stored flags.
     */
    fun storeFlags(flags: String)

    /**
     * Registers a listener to be notified when stored feature flags change.
     *
     * The listener is invoked whenever the stored flags are modified by any source (API updates,
     * manual changes, etc.). Only one listener can be active at a time - subsequent calls replace
     * the previous listener.
     *
     * **Thread Safety:** Callbacks may be executed on any thread. Implementations should document
     * their threading behavior. Callers must ensure thread-safety when accessing shared state or
     * UI components from the callback. Consider dispatching to the appropriate thread/coroutine
     * context before processing.
     *
     * @param listener Callback function invoked with the new serialized flags string.
     *   Receives empty string if flags are cleared.
     */
    fun setOnFlagsChangedListener(listener: (String) -> Unit)

    /**
     * Retrieves the timestamp of the last successful feature flag update.
     *
     * @return ISO 8601 formatted datetime string of the last update, or empty string if no update
     *   has occurred yet. Empty string should be treated as "never updated" by callers.
     */
    fun getLastUpdateTime(): String

    /**
     * Stores the timestamp of a successful feature flag update.
     *
     * @param time ISO 8601 formatted datetime string representing the update time.
     */
    fun storeUpdateTime(time: String)
}
