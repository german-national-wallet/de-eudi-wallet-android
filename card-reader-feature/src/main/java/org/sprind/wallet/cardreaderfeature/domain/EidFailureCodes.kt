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

import org.sprind.wallet.corelogic.eid.EidFailure

// The failure codes EidFailureRecovery classifies: the app's own EID_ codes, and the SDK's
// (https://www.ausweisapp.bund.de/sdk/failurecodes.html), grouped by the recovery they get.

const val CARD_DEACTIVATED = EidFailure.CARD_DEACTIVATED
const val PIN_BLOCKED = "EID_PIN_BLOCKED"

internal val deactivatedCodes = setOf(
    CARD_DEACTIVATED,
    "Connect_Card_Eid_Inactive",
)

internal val pukUsedUpCodes = setOf(
    EidFailure.PUK_INOPERATIVE,
    "Reset_Retry_Counter_Puk_Inoperative",
)

internal val unrecoverableCodes = setOf(
    // TODO WD-4778 a blocked PIN is not unblocked in this app.
    PIN_BLOCKED,
    EidFailure.ACCESS_RIGHTS_MISSING,
    EidFailure.REDIRECT_MISSING,
    "Pre_Verification_No_Test_Environment",
    "Get_TcToken_Client_Error",
    "Get_TcToken_Empty_Data",
    "Establish_Pace_Channel_Unknown_Password_Id",
    "Process_Certificates_From_Eac2_Cvc_Chain_Missing",
    "Did_Authenticate_Eac2_Invalid_Cvc_Chain",
    "Generic_Send_Receive_Paos_Unhandled",
    "Generic_Send_Receive_Paos_Unknown",
    "Generic_Send_Receive_Paos_Unexpected",
    "Generic_Send_Receive_Tls_Error",
    "Generic_Send_Receive_Client_Error",
    "Generic_Send_Receive_Invalid_Ephemeral_Key_Length",
    "Generic_Send_Receive_Certificate_Error",
    "Check_Refresh_Address_Client_Error",
    "Check_Refresh_Address_Invalid_Http_Response",
    "Check_Refresh_Address_Empty",
    "Check_Refresh_Address_Invalid_Url",
    "Check_Refresh_Address_No_Https_Scheme",
    "Check_Refresh_Address_Unsupported_Certificate",
    "Check_Refresh_Address_Hash_Missing_In_Certificate",
    "Check_Refresh_Address_Invalid_Ephemeral_Key_Length",
    "Check_Refresh_Address_Fetch_Certificate_Error",
)

internal val unrecoverablePrefixes = listOf(
    "Parse_TcToken_",
    "Get_TcToken_Invalid_",
    "Certificate_Check_Failed_",
    "Pre_Verification_Invalid_",
    "Extract_Cvcs_From_Eac1_",
    "Start_Paos_Response_",
    "Check_Refresh_Address_Fatal_Tls_Error_",
)