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

import org.sprind.wallet.authenticationlogic.provider.JsonStorageProvider
import org.sprind.wallet.authenticationlogic.provider.MdvmRegistrationStorageProvider
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration

// suffix .v2 is to distinguish from older storage format; reuse would require clearing app data
const val SHARED_PREFERENCE_MDVM_REGISTRATION_KEY = "MdvmRegistration.v2"

class PrefsMdvmRegistrationStorageProvider(
    private val jsonStorageProvider: JsonStorageProvider,
) : MdvmRegistrationStorageProvider {
    override fun getMdvmRegistration(): MdvmRegistration? =
        jsonStorageProvider.get(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY)
    override fun saveMdvmRegistration(mdvmRegistration: MdvmRegistration) =
        jsonStorageProvider.save(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY, mdvmRegistration)
}