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

package eu.europa.ec.authenticationlogic.model

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import junit.framework.TestCase
import org.junit.Test
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class RwscaPinSessionTest {

    @Test
    fun `expiry returns the Instant corresponding to the exp claim of the JWT`() {
        val expected = Instant.Companion.parse("2026-01-01T00:00:00Z")
        val pinSession =
            RwscaPinSession(rwsca_pin_session_token_jwt = createPinSessionJwtForTest(expected))
        TestCase.assertEquals(expected, pinSession.expiry)
    }

    private fun createPinSessionJwtForTest(expiry: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .expirationTime(Date.from(expiry.toJavaInstant()))
            .build()
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        // 256-bit dummy secret for tests
        signedJWT.sign(MACSigner("01234567890123456789012345678901"))
        return signedJWT.serialize()
    }
}