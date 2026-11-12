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

package org.sprind.wallet.authenticationlogic.storage

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.sprind.wallet.authenticationlogic.provider.JsonStorageProvider
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import java.lang.reflect.Type

class PrefsJsonStorageProvider(
    private val prefsController: EncryptedPrefsController,
    private val gson: Gson,
) : JsonStorageProvider {

    /**
     * Returns `null` when nothing is stored or the stored JSON is corrupt. A failure to read the
     * encrypted preferences themselves (e.g. the master key is locked until the next device
     * unlock) propagates: callers treat `null` as "not registered" and would otherwise start a
     * fresh registration over an existing one.
     */
    override fun <T> get(key: String, type: Type): T? {
        val json = prefsController.getString(key, "")
        if (json.isNullOrEmpty()) return null
        return try {
            gson.fromJson(json, type)
        } catch (e: JsonParseException) {
            null
        }
    }

    override fun <T> save(key: String, value: T) {
        prefsController.setString(key, gson.toJson(value))
    }

    override fun clear(key: String) = prefsController.clear(key)
}

inline fun <reified T> JsonStorageProvider.get(key: String): T? {
    val type = object : TypeToken<T>() {}.type
    return get(key, type)
}
