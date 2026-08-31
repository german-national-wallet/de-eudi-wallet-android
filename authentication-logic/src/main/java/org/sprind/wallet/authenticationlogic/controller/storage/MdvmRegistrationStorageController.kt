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

package org.sprind.wallet.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration

interface MdvmRegistrationStorageController {
    fun getMdvmRegistration(): MdvmRegistration?
    fun saveMdvmRegistration(response: MdvmRegistration)
}

class MdvmRegistrationStorageControllerImpl(
    private val storageConfig: StorageConfig,
) : MdvmRegistrationStorageController {
    override fun getMdvmRegistration(): MdvmRegistration? =
        storageConfig.mdvmRegistrationStorageProvider.getMdvmRegistration()

    override fun saveMdvmRegistration(response: MdvmRegistration) =
        storageConfig.mdvmRegistrationStorageProvider.saveMdvmRegistration(response)
}