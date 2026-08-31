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

package org.sprind.wallet.walletpinfeature.interactor.wscd

import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.businesslogic.model.UserPin

class RwscaRegistrationInteractorImpl(
    private val rwscaPinHandler: RwscaPinHandler,
    private val rwscaStorageController: RwscaStorageController,
) : WscaRegistrationInteractor {

    override fun isAlreadyRegistered(): Boolean =
        rwscaStorageController.getRwscaRegistration() != null &&
                rwscaStorageController.isPinInitialized()

    override suspend fun register(pin: UserPin): Flow<WscaRegistrationResult> = flow {
        val result = rwscaPinHandler.startPinSession(pin)
        emit(
            when (result) {
                StartPinSessionResult.Success -> WscaRegistrationResult.Success
                is StartPinSessionResult.Failure -> WscaRegistrationResult.Failure(
                    errorCode = result.error.code,
                    traceId = result.error.traceId,
                )
            }
        )
    }
}
