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

import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.networklogic.common.model.ApiResult

class RwscaPinHandlerImpl(
    private val rwscaInteractor: RwscaInteractor,
    private val rwscaPinSessionHolder: RwscaPinSessionHolder,
) : RwscaPinHandler {
    /**
     * Starts a PIN session by making a network call to the rWSCA server.
     */
    override suspend fun startPinSession(pin: UserPin): StartPinSessionResult {
        return when (val result = rwscaInteractor.rwscaPinSession(pin)) {
            is ApiResult.Success -> {
                rwscaPinSessionHolder.set(result.response)
                StartPinSessionResult.Success
            }
            is ApiResult.Failure -> StartPinSessionResult.Failure(result.error)
        }
    }

    override fun clearPinSession() {
        rwscaPinSessionHolder.clear()
    }
}