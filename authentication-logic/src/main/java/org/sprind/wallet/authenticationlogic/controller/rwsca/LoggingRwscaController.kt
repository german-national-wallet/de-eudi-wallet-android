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

package org.sprind.wallet.authenticationlogic.controller.rwsca

import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaCreatedKeys
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaResult
import org.sprind.wallet.authenticationlogic.model.RwscaSignature
import org.sprind.wallet.networklogic.rwsca.model.RwscaApiResult

class LoggingRwscaController(
    private val logController: LogController,
    private val delegate: RwscaController,
) : RwscaController {
    private val logTag = javaClass.simpleName

    override suspend fun createKeys(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        numberOfKeys: Int,
        ppCNonce: String,
    ): RwscaResult<RwscaCreatedKeys> =
        delegate.createKeys(mdvmRegistration, rwscaRegistration, numberOfKeys, ppCNonce).also { result ->
            logController.d(logTag) { "createKeys(numberOfKeys=$numberOfKeys): $result" }
        }

    override suspend fun deleteAccount(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
    ): RwscaResult<Unit> =
        delegate.deleteAccount(mdvmRegistration, rwscaRegistration).also { result ->
            logController.d(logTag) { "deleteAccount(): $result" }
        }

    override suspend fun register(
        mdvmRegistration: MdvmRegistration,
    ): RwscaResult<RwscaRegistration> =
        delegate.register(mdvmRegistration).also { result ->
            logController.d(logTag) { "register(): $result" }
        }

    override suspend fun signData(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        pinSession: RwscaPinSession,
        wrappedPrivateKey: String,
        keyBindingDataHash: String,
    ): RwscaResult<RwscaSignature> =
        delegate.signData(mdvmRegistration, rwscaRegistration, pinSession, wrappedPrivateKey, keyBindingDataHash).also { result ->
            logController.d(logTag) { "signData(): $result" }
        }

    override suspend fun startPinSession(
        mdvmRegistration: MdvmRegistration,
        rwscaRegistration: RwscaRegistration,
        withPinKeys: RwscaController.WithPinKeys,
        mode: RwscaController.StartPinSessionMode,
    ): RwscaResult<RwscaPinSession> =
        delegate.startPinSession(
            mdvmRegistration,
            rwscaRegistration,
            withPinKeys,
            mode,
        ).also { result ->
            logController.d(logTag) { "startPinSession($mode): $result" }
        }
}