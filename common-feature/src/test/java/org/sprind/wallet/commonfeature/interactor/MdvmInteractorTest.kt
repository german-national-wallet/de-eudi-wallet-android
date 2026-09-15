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

package org.sprind.wallet.commonfeature.interactor

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmController
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.commonfeature.interactor.MdvmInteractorImpl.Companion.DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.commonfeature.testing.TestClock
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class MdvmInteractorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mdvmController: MdvmController
    @Mock
    private lateinit var mdvmRegistrationStorageController: MdvmRegistrationStorageController

    private var clock = TestClock(Instant.parse("2026-06-01T00:00:00Z"))

    private val mdvmTokenReuseLimit = clock.now().plus(DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION)
    // Right on the border of what we still reuse (expires soon).
    val expiresSoon = mdvmTokenReuseLimit
    // not expired yet, but expires too soon for us to still reuse
    val expiresTooSoon = mdvmTokenReuseLimit.minus(1.milliseconds)
    val expiredInThePast = clock.now().minus(1.hours)
    val expiresInTheDistantFuture = mdvmTokenReuseLimit.plus(100.hours)

    private val wiMdvmAuthKeysAlias = "wi_mdvm_auth_keys"

    private lateinit var closeable: AutoCloseable

    private fun makeRegistration(
        expiry: Instant,
        id: String = "fake mdvm_wi_id for test (register)",
    ) = MdvmRegistration(
        mdvm_wi_id = id,
        mdvm_token = createMdvmTokenJwtForTest(expiry),
        wi_mdvm_auth_keys_alias = wiMdvmAuthKeysAlias,
    )

    private val renewedRegistration = MdvmRegistration(
        mdvm_wi_id = "fake mdvm_wi_id for test (renewal)",
        mdvm_token = createMdvmTokenJwtForTest(expiresInTheDistantFuture),
        wi_mdvm_auth_keys_alias = wiMdvmAuthKeysAlias,
    )

    private val errorResponse = MdvmErrorResponse(code = "fake error for test")
    private val mdvmError = errorResponse.toMdvmError()

    private val subject by lazy {
        MdvmInteractorImpl(mdvmController, mdvmRegistrationStorageController, clock)
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `mdvmRegistration reuses stored registration when not expired`() =
        coroutineRule.runTest {
            val expiresSoonRegistration = makeRegistration(expiresSoon)
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresSoonRegistration)

            val actual = subject.mdvmRegistration()
            assertEquals(
                ApiResult.Success(expiresSoonRegistration),
                actual)

            verify(mdvmController, never()).register()
            verify(mdvmController, never()).renewal(any(), any())
        }

    @Test
    fun `mdvmRegistration emits Success when register call succeeds`() =
        coroutineRule.runTest {
            val resuableRegistration = makeRegistration(expiresInTheDistantFuture)
            whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
            whenever(mdvmController.register()).thenReturn(ApiResult.Success(resuableRegistration))

            val actual = subject.mdvmRegistration()
            assertEquals(
                ApiResult.Success(resuableRegistration),
                actual)
        }

    @Test
    fun `mdvmRegistration saves response to storage on register success`() =
        coroutineRule.runTest {
            val reusableRegistration = makeRegistration(expiresInTheDistantFuture)
            whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
            whenever(mdvmController.register()).thenReturn(ApiResult.Success(reusableRegistration))

            subject.mdvmRegistration()

            verify(mdvmRegistrationStorageController).saveMdvmRegistration(reusableRegistration)
        }

    @Test
    fun `mdvmRegistration emits Failure when register call fails`() =
        coroutineRule.runTest {
            whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
            whenever(mdvmController.register()).thenReturn(ApiResult.Failure(mdvmError))

            val actual = subject.mdvmRegistration()

            assertEquals(ApiResult.Failure(mdvmError), actual)
        }

    @Test
    fun `mdvmRegistration does not save to storage on register failure`() =
        coroutineRule.runTest {
            whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
            whenever(mdvmController.register()).thenReturn(ApiResult.Failure(mdvmError))

            subject.mdvmRegistration()

            verify(mdvmRegistrationStorageController, never()).saveMdvmRegistration(any())
        }

    @Test
    fun `mdvmRegistration calls renewal with correct id and alias when stored registration is expired`() =
        coroutineRule.runTest {
            val expiresTooSoonRegistration = makeRegistration(expiresTooSoon, "expires-too-soon-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresTooSoonRegistration)
            whenever(mdvmController.renewal("expires-too-soon-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Success(renewedRegistration))

            subject.mdvmRegistration()

            verify(mdvmController).renewal("expires-too-soon-id", wiMdvmAuthKeysAlias)
            verify(mdvmController, never()).register()
        }

    @Test
    fun `mdvmRegistration emits Success when renewal succeeds`() =
        coroutineRule.runTest {
            val expiresTooSoonRegistration = makeRegistration(expiresTooSoon, "expires-too-soon-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresTooSoonRegistration)
            whenever(mdvmController.renewal("expires-too-soon-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Success(renewedRegistration))

            val actual = subject.mdvmRegistration()

            assertEquals(
                    ApiResult.Success(renewedRegistration),
                    actual)
        }

    @Test
    fun `mdvmRegistration saves renewed registration on renewal success`() =
        coroutineRule.runTest {
            val expiresTooSoonRegistration = makeRegistration(expiresTooSoon, "expires-too-soon-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresTooSoonRegistration)
            whenever(mdvmController.renewal("expires-too-soon-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Success(renewedRegistration))

            subject.mdvmRegistration()

            verify(mdvmRegistrationStorageController).saveMdvmRegistration(renewedRegistration)
        }

    @Test
    fun `mdvmRegistration saves renewed registration on renewal success even if it's also expired`() =
        coroutineRule.runTest {
            val expiredRegistration = makeRegistration(expiredInThePast, "expired-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiredRegistration)
            val expiredRenewalResult = MdvmRegistration(
                mdvm_wi_id = "fake mdvm_wi_id for test (renewal)",
                mdvm_token = createMdvmTokenJwtForTest(expiresTooSoon),
                wi_mdvm_auth_keys_alias = wiMdvmAuthKeysAlias,
            )
            whenever(mdvmController.renewal("expired-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Success(expiredRenewalResult))

            subject.mdvmRegistration()

            verify(mdvmRegistrationStorageController).saveMdvmRegistration(expiredRenewalResult)
        }

    @Test
    fun `mdvmRegistration emits Failure when renewal fails`() =
        coroutineRule.runTest {
            val expiresTooSonRegistration = makeRegistration(expiresTooSoon, "expires-too-soon-id" +
                    "")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresTooSonRegistration)
            whenever(mdvmController.renewal("expires-too-soon-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Failure(mdvmError))

            val actual = subject.mdvmRegistration()
            assertEquals(
                ApiResult.Failure(mdvmError),
                actual)

            verify(mdvmRegistrationStorageController, never()).saveMdvmRegistration(any())
        }

    @Test
    fun `mdvmRegistration does not save to storage on renewal failure`() =
        coroutineRule.runTest {
            val expiresTooSoonRegistration = makeRegistration(expiresTooSoon, "expires-too-soon-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(expiresTooSoonRegistration)
            whenever(mdvmController.renewal("expires-too-soon-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Failure(mdvmError))

            subject.mdvmRegistration()

            verify(mdvmRegistrationStorageController, never()).saveMdvmRegistration(any())
        }

    // This test ensures that any changes to the value are carefully considered (not accidental).
    @Test
    fun `The value of DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION has not changed`() {
        assertEquals(10.minutes, DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION)
    }

    // region forceRenewal

    @Test
    fun `mdvmRegistration with forceRenewal bypasses cache and calls renewal even when stored registration is still valid`() =
        coroutineRule.runTest {
            val stillValidRegistration = makeRegistration(expiresInTheDistantFuture, "still-valid-id")
            whenever(mdvmRegistrationStorageController.getMdvmRegistration())
                .thenReturn(stillValidRegistration)
            whenever(mdvmController.renewal("still-valid-id", wiMdvmAuthKeysAlias))
                .thenReturn(ApiResult.Success(renewedRegistration))

            val actual = subject.mdvmRegistration(forceRenewal = true)

            assertEquals(ApiResult.Success(renewedRegistration), actual)
            verify(mdvmController).renewal("still-valid-id", wiMdvmAuthKeysAlias)
            verify(mdvmController, never()).register()
        }

    @Test
    fun `mdvmRegistration with forceRenewal and no stored registration falls back to register`() =
        coroutineRule.runTest {
            whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
            whenever(mdvmController.register()).thenReturn(ApiResult.Success(makeRegistration(expiresInTheDistantFuture)))

            subject.mdvmRegistration(forceRenewal = true)

            verify(mdvmController).register()
            verify(mdvmController, never()).renewal(any(), any())
        }

    // endregion

    private fun createMdvmTokenJwtForTest(expiry: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .expirationTime(Date.from(expiry.toJavaInstant()))
            .build()
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        // 256-bit dummy secret for tests
        signedJWT.sign(MACSigner("01234567890123456789012345678901"))
        return signedJWT.serialize()
    }
}