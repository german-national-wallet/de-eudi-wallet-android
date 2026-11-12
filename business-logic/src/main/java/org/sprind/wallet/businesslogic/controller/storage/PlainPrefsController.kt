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

package org.sprind.wallet.businesslogic.controller.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.koin.core.annotation.Single

/**
 * Unencrypted preferences that can be read and written without user authentication.
 *
 * State that code running with no user present (the FCM service handling a revocation push or a
 * token rotation) must read or write lives here, because
 * [eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController] may be unreadable then.
 *
 * Nothing stored here is secret: only flags whose exposure or tampering gives an attacker nothing
 * they could not already achieve with the same file access. Keep the surface minimal, so that
 * adding data to the plain store is a visible, reviewed decision.
 */
interface PlainPrefsController {

    fun getBool(key: String, defaultValue: Boolean): Boolean

    /** Writes asynchronously ([SharedPreferences.Editor.apply]). */
    fun setBool(key: String, value: Boolean)

    /**
     * Writes synchronously ([SharedPreferences.Editor.commit]): the value is on disk when this
     * returns, so the writer may die right afterwards.
     */
    fun setBoolSync(key: String, value: Boolean)

    fun clear(key: String)
}

@Single(binds = [PlainPrefsController::class])
class PlainPrefsControllerImpl(private val context: Context) : PlainPrefsController {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun getBool(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun setBool(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun setBoolSync(key: String, value: Boolean) {
        prefs.edit(commit = true) { putBoolean(key, value) }
    }

    override fun clear(key: String) {
        prefs.edit { remove(key) }
    }

    companion object {
        internal const val FILE_NAME = "plain_shared_prefs"
    }
}
