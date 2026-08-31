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

package org.sprind.wallet.commonfeature.provider

import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContext
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextResult
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType

class MdvmAuthContextProviderImpl(
    private val mdvmInteractor: MdvmInteractor,
    private val mdvmKeyManager: MdvmKeyManager,
) : MdvmAuthContextProvider {
    override suspend fun getMdvmAuthContext(): MdvmAuthContextResult {
        val mdvmRegistration = when (val result = mdvmInteractor.mdvmRegistration()) {
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
            is ApiResult.Success -> result.response
        }
        // The WPB HTTP signature must be created with the MDVM auth key stored in KeyStore.
        val mdvmAuthKeys = mdvmKeyManager.getExistingAuthKeys(mdvmRegistration.wi_mdvm_auth_keys_alias)
            ?: return ApiResult.Failure(
                MdvmError(MdvmErrorType.MDVM_KEY_NOT_FOUND, serverResponse = null)
            )

        return ApiResult.Success(
            MdvmAuthContext(
                mdvmToken = mdvmRegistration.mdvm_token,
                mdvmAuthPrvk = mdvmAuthKeys.private,
            )
        )
    }
}
