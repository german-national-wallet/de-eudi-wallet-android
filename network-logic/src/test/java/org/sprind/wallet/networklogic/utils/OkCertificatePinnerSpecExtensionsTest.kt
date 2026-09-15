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

package org.sprind.wallet.networklogic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.sprind.wallet.businesslogic.config.OkCertificatePinnerSpec
import org.sprind.wallet.businesslogic.config.PidIssuerSpec
import java.net.URI
import kotlin.test.assertNotEquals

@RunWith(Parameterized::class)
class OkCertificatePinnerSpecExtensionsTest(private val spec: PidIssuerSpec) {
    private val pinner by lazy { spec.okCertificatePinnerSpec.toCertificatePinner() }
    private val hostname by lazy { URI(spec.url).host }

    @Test
    fun `each pin has valid sha256 format`() {
        val validPin = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
        for (pin in spec.okCertificatePinnerSpec.pins) {
            assertTrue("Pin has invalid format: $pin", validPin.matches(pin))
        }
    }

    /**
     * "identical" follows from this test passing for both Parameterized [specs].
     */
    @Test
    fun `pin has not changed and is the same for all endpoints`() {
        assertEquals(
            OkCertificatePinnerSpec(
                pattern = "**.pid-provider.bundesdruckerei.de",
                pins = setOf("sha256/rHb2OkbnYbWswyWXBckgy38FY9JI2NGA+TSvaAmaFfk=")
            ),
            spec.okCertificatePinnerSpec)
    }

    @Test
    fun `toCertificatePinner pins the issuer URL hostname`() {
        assertPinsHost(hostname)
    }

    @Test
    fun `toCertificatePinner pins subdomains of the issuer URL hostname`() {
        assertPinsHost("foobar.$hostname")
        assertPinsHost("a.b.$hostname")
    }

    @Test
    fun `toCertificatePinner pins pattern root`() {
        val pattern = spec.okCertificatePinnerSpec.pattern
        val hostname = pattern.removePrefix("**.")
        assertNotEquals(hostname, pattern) // check that prefix was actually removed
        assertPinsHost(hostname)
    }

    private fun assertPinsHost(hostname: String) {
        assertFalse("Expected pins for $hostname but found none", pinner.findMatchingPins(hostname).isEmpty())
    }

    @Test
    fun `pattern does not match unrelated hosts`() {
        for (host in listOf("evil.com", "bundesdruckerei.de")) {
            assertTrue("Pattern should not match $host", pinner.findMatchingPins(host).isEmpty())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun specs() = listOf(PidIssuerSpec.DEMO, PidIssuerSpec.PREPROD)
    }
}
