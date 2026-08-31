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
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.sprind.wallet.authenticationlogic.provider.JsonStorageProvider
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration

private const val SHARED_PREFERENCE_RWSCA_REGISTRATION_KEY = "RwscaRegistration"
private const val SHARED_PREFERENCE_RWSCA_PIN_INITIALIZED_KEY = "RwscaPinInitialized"
private const val SHARED_PREFERENCE_RWSCA_PIN_SALT_KEY = "RwscaPinSalt"


class RwscaStorageControllerImpl(
    private val prefsController: PrefsController,
    private val gson: Gson,
) : RwscaStorageController {
    // Don't use DI for this to guarantee the same underlying prefsController
    private val jsonStorageProvider: JsonStorageProvider =
        PrefsJsonStorageProvider(prefsController, gson)

    override fun getRwscaRegistration(): RwscaRegistration? =
        jsonStorageProvider.get(SHARED_PREFERENCE_RWSCA_REGISTRATION_KEY)

    override fun saveRwscaRegistration(rwscaRegistration: RwscaRegistration) =
        jsonStorageProvider.save(SHARED_PREFERENCE_RWSCA_REGISTRATION_KEY, rwscaRegistration)

    override fun clearRwscaRegistration() =
        jsonStorageProvider.clear(SHARED_PREFERENCE_RWSCA_REGISTRATION_KEY)

    override fun isPinInitialized(): Boolean =
        prefsController.getBool(SHARED_PREFERENCE_RWSCA_PIN_INITIALIZED_KEY, false)

    override fun savePinInitialized() =
        prefsController.setBool(SHARED_PREFERENCE_RWSCA_PIN_INITIALIZED_KEY, true)

    override fun clearPinInitialized() =
        prefsController.clear(SHARED_PREFERENCE_RWSCA_PIN_INITIALIZED_KEY)

    override fun getPinSalt(): ByteString? {
        val stringValue = prefsController.getString(SHARED_PREFERENCE_RWSCA_PIN_SALT_KEY, "")
        if (stringValue == "") {
            return null
        }
        return stringValue.decodeBase64()
    }

    override fun savePinSalt(value: ByteString) {
        prefsController.setString(SHARED_PREFERENCE_RWSCA_PIN_SALT_KEY, value.base64())
    }

    override fun clearPinSalt() =
        prefsController.clear(SHARED_PREFERENCE_RWSCA_PIN_SALT_KEY)
}
