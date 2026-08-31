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

package org.sprind.wallet.commonfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaRegistrations
import org.sprind.wallet.authenticationlogic.model.RwscaResult
import org.sprind.wallet.businesslogic.model.UserPin

class LoggingRwscaInteractor(
    private val logController: LogController,
    private val delegate: RwscaInteractor,
) : RwscaInteractor {
    private val logTag = javaClass.simpleName

    override suspend fun rwscaRegistration(): RwscaResult<RwscaRegistration> {
        val result = delegate.rwscaRegistration()
        logController.d(logTag) { "rwscaRegistration(): $result" }
        return result
    }

    override suspend fun rwscaPinSession(pin: UserPin): RwscaResult<RwscaPinSession> {
        val result = delegate.rwscaPinSession(pin)
        logController.d(logTag) { "rwscaPinSession(): $result" }
        return result
    }

    override suspend fun getRegistrations(): RwscaResult<RwscaRegistrations> {
        val result = delegate.getRegistrations()
        logController.d(logTag) { "getRegistrations(): $result" }
        return result
    }

    override suspend fun deleteAccount(): RwscaResult<Unit> {
        val result = delegate.deleteAccount()
        logController.d(logTag) { "deleteAccount(): $result" }
        return result
    }
}
