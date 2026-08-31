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

package org.sprind.wallet.networklogic.rwsca.model.request

import kotlinx.serialization.Serializable

/*
Example:
{
  "number_of_keys": 1,
  "pp_c_nonce": "YTZhMGUzYmYtNDcxYS00NDE3LWIwYTQtODEwZmVjNTBhMjMx"
}
 */
@Serializable
data class RwscaCreateKeysRequest(
    val number_of_keys: Int,
    val pp_c_nonce: String,
)