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

package org.sprind.wallet.corelogic.controller

import eu.europa.ec.corelogic.controller.RequestedCredentialSet
import eu.europa.ec.corelogic.controller.toRequestedCredentialSets
import eu.europa.ec.eudi.openid4vp.dcql.CredentialQuery
import eu.europa.ec.eudi.openid4vp.dcql.CredentialQueryIds
import eu.europa.ec.eudi.openid4vp.dcql.CredentialSetQuery
import eu.europa.ec.eudi.openid4vp.dcql.CredentialSets
import eu.europa.ec.eudi.openid4vp.dcql.Credentials
import eu.europa.ec.eudi.openid4vp.dcql.DCQL
import eu.europa.ec.eudi.openid4vp.dcql.DCQLMetaMsoMdocExtensions
import eu.europa.ec.eudi.openid4vp.dcql.DCQLMetaSdJwtVcExtensions
import eu.europa.ec.eudi.openid4vp.dcql.MsoMdocDocType
import eu.europa.ec.eudi.openid4vp.dcql.QueryId
import org.junit.Test
import kotlin.test.assertEquals

class RequestedCredentialSetsTest {

    private val pidMdoc = CredentialQuery.mdoc(
        id = QueryId("pid-mdoc"),
        msoMdocMeta = DCQLMetaMsoMdocExtensions(MsoMdocDocType(PID_DOCTYPE)),
    )
    private val pidSdJwt = CredentialQuery.sdJwtVc(
        id = QueryId("pid-sdjwt"),
        sdJwtVcMeta = DCQLMetaSdJwtVcExtensions(vctValues = listOf(PID_VCT, PID_VCT_LEGACY)),
    )
    private val mdl = CredentialQuery.mdoc(
        id = QueryId("mdl"),
        msoMdocMeta = DCQLMetaMsoMdocExtensions(MsoMdocDocType(MDL_DOCTYPE)),
    )

    @Test
    fun `without credential_sets every query is required in a single option`() {
        val dcql = DCQL(credentials = Credentials(listOf(pidMdoc, mdl)))

        assertEquals(
            listOf(
                RequestedCredentialSet(
                    required = true,
                    options = listOf(listOf(setOf(PID_DOCTYPE), setOf(MDL_DOCTYPE))),
                )
            ),
            dcql.toRequestedCredentialSets(),
        )
    }

    @Test
    fun `credential_sets keep their alternatives, requiredness and every accepted vct`() {
        val dcql = DCQL(
            credentials = Credentials(listOf(pidMdoc, pidSdJwt, mdl)),
            credentialSets = CredentialSets(
                listOf(
                    CredentialSetQuery(
                        options = listOf(
                            CredentialQueryIds(listOf(QueryId("pid-mdoc"))),
                            CredentialQueryIds(listOf(QueryId("pid-sdjwt"))),
                        ),
                    ),
                    CredentialSetQuery(
                        options = listOf(CredentialQueryIds(listOf(QueryId("mdl")))),
                        required = false,
                    ),
                )
            ),
        )

        assertEquals(
            listOf(
                RequestedCredentialSet(
                    required = true,
                    options = listOf(
                        listOf(setOf(PID_DOCTYPE)),
                        listOf(setOf(PID_VCT, PID_VCT_LEGACY)),
                    ),
                ),
                RequestedCredentialSet(
                    required = false,
                    options = listOf(listOf(setOf(MDL_DOCTYPE))),
                ),
            ),
            dcql.toRequestedCredentialSets(),
        )
    }

    private companion object {
        const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        const val PID_VCT = "urn:eudi:pid:1"
        const val PID_VCT_LEGACY = "urn:eudi:pid:de:1"
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    }
}
