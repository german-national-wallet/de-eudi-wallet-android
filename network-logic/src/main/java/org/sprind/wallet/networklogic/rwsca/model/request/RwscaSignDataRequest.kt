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
  "rwsca_wi_wrapped_prvk": "eyJraWQiOiIxIiwidHlwIjoicndzY2FfYm91bmRfd3JhcHBlZF9rZXkrandlIiwiZW5jIjoiQTI1NkdDTSIsImFsZyI6ImRpciJ9..bYuntsDWU76ksTqr.QFZv6OC7fdcJ7W6AH-NtT6CjED6lRtCasSRh92TqrN7o62Xy3wbfDtDuy_ibMStEt0lLgaWQH0Wk7OW1qvzRnUJkiJclAcz1ay0YKgMwaGIxSuBnaU7Vnlk50URKmJTEuN24xNOt3C-DTZGPd8LApfY613EwetILz5LoKXTiDAaYy1GWChXVTj4h6QlRf4hEB-DERdUsoE2MUAAWfDIwIjOHO5QgE-dRTZ-4yx21MKu4yll__Bw9jP5P1bY_2ah4GOGRyMmOSPsuWx4VG2AWQ_zJboeZ8C8_sRiAuumvysdrLj81Nq4lzIJIisQNDyyY5uyl8FajCTLnnsea7y1YHY2_lg8PDu4XwZkkl5tc_BvmDbslsrnrw7uQn5ySGx_MWkpmwNfSEeGl9G-KngI86O1txe79PW7xLciUUcy7GcKv4MBAJKglKQ.8P0for_7-SRkG6b_o1mzbA",
  "wi_key_binding_data_hash": "YTZhMGUzYmYtNDcxYS00NDE3LWIwYTQtODEwZmVjNTBhMjMx"
}
 */
@Serializable
data class RwscaSignDataRequest(
    val rwsca_wi_wrapped_prvk: String,
    val wi_key_binding_data_hash: String,
)