/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.europa.ec.corelogic.config

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WalletCoreConfigImplTest {

    private val storage = mockk<Storage>(relaxed = true)
    private val rwscSecureArea = mockk<SecureArea>(relaxed = true)
    private val androidKeystoreSecureArea = mockk<SecureArea>(relaxed = true)

    private val subject = WalletCoreConfigImpl(
        context = RuntimeEnvironment.getApplication(),
        configLogic = mockk<ConfigLogic>(relaxed = true).apply {
            every { environmentConfig.pidIssuerURL } returns PID_ISSUER_URL
        },
        ausweisSdkAuthorizationHandler = mockk<AuthorizationHandler>(relaxed = true),
        rwscSecureArea = rwscSecureArea,
        androidKeystoreSecureArea = androidKeystoreSecureArea,
        storage = storage,
    )

    @Test
    fun `every vci config stores issuance metadata in the encrypted storage`() {
        assertTrue(subject.vciConfig.isNotEmpty())
        subject.vciConfig.values.forEach { assertSame(storage, it.issuanceMetadataStorage) }
    }

    @Test
    fun `no vci config uses the default DPoP config, which persists metadata unencrypted`() {
        subject.vciConfig.values.forEach { assertNotEquals(DPopConfig.Default, it.dpopConfig) }
    }

    @Test
    fun `default DPoP config keeps its keys in the shared android keystore secure area`() {
        assertSame(androidKeystoreSecureArea, subject.defaultDPopConfig.secureArea)
    }

    @Test
    fun `key attested configs use the encrypted config for their provisional key`() {
        val keyAttested = subject.vciConfig.values.map { it.dpopConfig }
            .filterIsInstance<DPopConfig.KeyAttested>()
        assertTrue(keyAttested.isNotEmpty())
        keyAttested.forEach { assertSame(subject.defaultDPopConfig, it.provisionalConfig) }
    }

    private companion object {
        const val PID_ISSUER_URL = "https://pid-issuer.example.com"
    }
}
