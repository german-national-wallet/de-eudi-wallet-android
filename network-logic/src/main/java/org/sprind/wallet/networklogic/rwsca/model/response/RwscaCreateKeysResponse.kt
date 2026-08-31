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

package org.sprind.wallet.networklogic.rwsca.model.response

import kotlinx.serialization.Serializable

/*
Example:
{
  "rwsca_wi_keys": [
    {
      "rwscd_wi_pubk": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/8x7cG7gGnEOxMjC9D7nxtMzEUJmanPZk5w5A9bY+/iitjD2ymuHG0Vl5Jno08RshEtlL24Xe8Kzq3iothxO9Q==",
      "rwsca_wi_wrapped_prvk": "eyJraWQiOiIxIiwidHlwIjoicndzY2FfYm91bmRfd3JhcHBlZF9rZXkrandlIiwiZW5jIjoiQTI1NkdDTSIsImFsZyI6ImRpciJ9..bYuntsDWU76ksTqr.QFZv6OC7fdcJ7W6AH-NtT6CjED6lRtCasSRh92TqrN7o62Xy3wbfDtDuy_ibMStEt0lLgaWQH0Wk7OW1qvzRnUJkiJclAcz1ay0YKgMwaGIxSuBnaU7Vnlk50URKmJTEuN24xNOt3C-DTZGPd8LApfY613EwetILz5LoKXTiDAaYy1GWChXVTj4h6QlRf4hEB-DERdUsoE2MUAAWfDIwIjOHO5QgE-dRTZ-4yx21MKu4yll__Bw9jP5P1bY_2ah4GOGRyMmOSPsuWx4VG2AWQ_zJboeZ8C8_sRiAuumvysdrLj81Nq4lzIJIisQNDyyY5uyl8FajCTLnnsea7y1YHY2_lg8PDu4XwZkkl5tc_BvmDbslsrnrw7uQn5ySGx_MWkpmwNfSEeGl9G-KngI86O1txe79PW7xLciUUcy7GcKv4MBAJKglKQ.8P0for_7-SRkG6b_o1mzbA"
    }
  ],
  "rwsca_wi_wte": "eyJ4NWMiOlsiTUlJQmFUQ0NBUThDQ1FEZ1BpQ1VIbUF5M2pBSkJnY3Foa2pPUFFRQk1EMHhHREFXQmdOVkJBTU1EMWRVUlNCSmMzTjFaWElnVkdWemRERVVNQklHQTFVRUNnd0xSVlZFU1NCWFlXeHNaWFF4Q3pBSkJnTlZCQVlUQWtSRk1CNFhEVEkxTURZeU1ERTNOREl4TjFvWERUSTJNRFl5TURFM05ESXhOMW93UFRFWU1CWUdBMVVFQXd3UFYxUkZJRWx6YzNWbGNpQlVaWE4wTVJRd0VnWURWUVFLREF0RlZVUkpJRmRoYkd4bGRERUxNQWtHQTFVRUJoTUNSRVV3V1RBVEJnY3Foa2pPUFFJQkJnZ3Foa2pPUFFNQkJ3TkNBQVEzamJsb1NuQU94SlF5RzZDOERPb1IxbEh0SmJTcW4zRXRQRDhHVzFDSUN3aDl4dWtuVDNRR1V4K2VPaE40RFUyOEFUK1ZpbVUyWmZobVBZTDc4anJJTUFrR0J5cUdTTTQ5QkFFRFNRQXdSZ0loQU5jKzlZK1BIa2JXbVFmeUhIbEFhOEZoZGtWNUo5U2hSSVdSWkhJbGQ3NnFBaUVBdHJSSFNKdTZ5Vm1JUU40QjlGRStyL0x2UEFsMU1ObmVPSzB3OHNmdVdVWT0iXSwidHlwIjoia2V5LWF0dGVzdGF0aW9uK2p3dCIsImFsZyI6IkVTMjU2In0.eyJ1c2VyX2F1dGhlbnRpY2F0aW9uIjpbImlzb18xODA0NV9oaWdoIl0sImF0dGVzdGVkX2tleXMiOlt7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJ4IjoiXzh4N2NHN2dHbkVPeE1qQzlEN254dE16RVVKbWFuUFprNXc1QTliWS1fZyIsInkiOiJvcll3OXNwcmh4dEZaZVNaNk5QRWJJUkxaUzl1RjN2Q3M2dDRxTFljVHZVIn1dLCJrZXlfc3RvcmFnZSI6WyJpc29fMTgwNDVfaGlnaCJdLCJleHAiOjE3NzM1ODIyNzgsImlhdCI6MTc3MzQ5NTg3OCwibm9uY2UiOiJGUHNrbWZLZWRTbklKamc0Sm1kUDZEalBWbEd3c0FCUCtEREJ2MzFmUjFjPSJ9.KnYqNTRF7COBl1QqO9AEAVCnkKPRMhQSeWxjzXEpqDqHZ9FaC8chbAw2q22c4FC0sZtFAlZF2p3ev19lFWAjKA"
}
*/
@Serializable
data class RwscaCreateKeysResponse(
    val rwsca_wi_keys: List<RwscaWiKeySpec>,
    val rwsca_wte: String,
)

@Serializable
data class RwscaWiKeySpec(
    val rwscd_wi_pubk: String,
    val rwsca_wi_wrapped_prvk: String,
)