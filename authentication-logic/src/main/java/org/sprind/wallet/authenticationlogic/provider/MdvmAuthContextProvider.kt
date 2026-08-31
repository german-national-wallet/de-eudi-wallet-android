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

package org.sprind.wallet.authenticationlogic.provider

import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.networklogic.common.model.ApiResult
import java.security.PrivateKey

data class MdvmAuthContext(
    val mdvmToken: String,
    val mdvmAuthPrvk: PrivateKey,
)

typealias MdvmAuthContextResult = ApiResult<MdvmAuthContext, MdvmError>

interface MdvmAuthContextProvider {
    /**
     * Returns a usable MDVM auth context, registering or renewing MDVM credentials if needed.
     */
    suspend fun getMdvmAuthContext(): MdvmAuthContextResult
}
