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

package org.sprind.wallet.cardreaderfeature.domain

import org.junit.Test
import kotlin.test.assertEquals

class EidFailureRecoveryTest {

    @Test
    fun `when the card or the connection let the read down, then a retry is offered`() {
        listOf(
            "Card_Removed",
            "Connect_Card_Connection_Failed",
            "Generic_Send_Receive_Network_Error",
            "Check_Refresh_Address_Service_Timeout",
            "Change_Pin_Input_Timeout",
            "EID_BAD_STATE_SET_PIN",
            "EID_INTERNAL_ERROR",
        ).forEach { assertEquals(EidFailureRecovery.RETRY, EidFailureRecovery.forErrorCode(it), it) }
    }

    @Test
    fun `when an unknown code is reported, then a retry is offered`() {
        assertEquals(EidFailureRecovery.RETRY, EidFailureRecovery.forErrorCode("Some_Future_Code"))
    }

    @Test
    fun `when the card's online function is off, then the citizen's office is to switch it on`() {
        listOf(
            CARD_DEACTIVATED,
            "Connect_Card_Eid_Inactive",
        ).forEach {
            assertEquals(EidFailureRecovery.ACTIVATE_AT_CITIZEN_OFFICE, EidFailureRecovery.forErrorCode(it), it)
        }
    }

    @Test
    fun `when the PUK is used up, then the citizen's office is to issue a new PIN`() {
        listOf(
            "EID_PUK_INOPERATIVE",
            "Reset_Retry_Counter_Puk_Inoperative",
        ).forEach {
            assertEquals(EidFailureRecovery.NEW_PIN_AT_CITIZEN_OFFICE, EidFailureRecovery.forErrorCode(it), it)
        }
    }

    @Test
    fun `when the service or its certificates are at fault, then nothing is offered`() {
        listOf(
            PIN_BLOCKED,
            "Certificate_Check_Failed_Hash_Mismatch",
            "Pre_Verification_Invalid_Certificate_Chain",
            "Get_TcToken_Invalid_Url",
            "Generic_Send_Receive_Tls_Error",
            "Check_Refresh_Address_Fatal_Tls_Error_After_Reply",
            "Check_Refresh_Address_Fetch_Certificate_Error",
            "Get_TcToken_Invalid_Data",
        ).forEach { assertEquals(EidFailureRecovery.NONE, EidFailureRecovery.forErrorCode(it), it) }
    }
}