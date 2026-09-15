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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sprind.wallet.businesslogic.config.PidIssuerSpec
import org.sprind.wallet.businesslogic.config.WalletBackendEnvironmentConfig

/**
 * PLACEHOLDER overlay for the open-source mirror. The internal test pins real backend
 * hosts; this variant keeps the same assertions against the placeholder patterns from
 * [WalletBackendEnvironmentConfig.WALLET_BACKEND_PINNER_SPECS], whose hosts live under
 * the reserved `.example.invalid` name. See "Supplying your own services" in README.md.
 */
class WalletBackendCertificatePinnerTest {

    private val specs = WalletBackendEnvironmentConfig.WALLET_BACKEND_PINNER_SPECS
    private val pinner by lazy {
        (specs + PidIssuerSpec.DEMO.okCertificatePinnerSpec).toCertificatePinner()
    }

    @Test
    fun `pins the four lets encrypt roots for every pattern`() {
        val expected = setOf(
            "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1
            "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=", // ISRG Root X2
            "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=", // Root YE
            "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=", // Root YR
        )
        assertEquals(
            listOf("**.wallet-backend.example.invalid", "**.eudi-wallet.example.invalid"),
            specs.map { it.pattern },
        )
        for (spec in specs) {
            assertEquals("Unexpected pins for ${spec.pattern}", expected, spec.pins)
        }
    }

    @Test
    fun `each pin has valid sha256 format`() {
        val validPin = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
        for (pin in specs.flatMap { it.pins }) {
            assertTrue("Pin has invalid format: $pin", validPin.matches(pin))
        }
    }

    @Test
    fun `pins the backend hosts and their subdomains`() {
        for (host in listOf(
            "wallet-backend.example.invalid",
            "dev.wallet-backend.example.invalid",
            "staging.wallet-backend.example.invalid",
            "sandbox.wallet-backend.example.invalid",
            "eudi-wallet.example.invalid",
            "wallet.eudi-wallet.example.invalid",
        )) {
            assertTrue("Expected pins for $host but found none", pinner.findMatchingPins(host).isNotEmpty())
        }
    }

    @Test
    fun `does not pin unrelated or partially matching hosts`() {
        for (host in listOf(
            "evil.com",
            "example.invalid",
            "invalid",
            "notwallet-backend.example.invalid",
            "wallet-backend.example.invalid.evil.com",
            "fhd.example.invalid",
        )) {
            assertTrue("Pattern should not match $host", pinner.findMatchingPins(host).isEmpty())
        }
    }

    @Test
    fun `keeps the backend pins separate from the PID Provider pin`() {
        val pidPins = pinner.findMatchingPins("demo.pid-provider.bundesdruckerei.de").map { it.toString() }
        val backendPins = pinner.findMatchingPins("sandbox.wallet-backend.example.invalid")
            .map { it.toString() }

        assertEquals(PidIssuerSpec.DEMO.okCertificatePinnerSpec.pins.size, pidPins.size)
        assertTrue("Backend pins must not apply to the PID Provider", pidPins.none { it in backendPins })
        assertTrue("PID Provider pin must not apply to the backend", backendPins.none { it in pidPins })
    }
}