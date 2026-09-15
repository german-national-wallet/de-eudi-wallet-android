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

package org.sprind.wallet.corelogic.securearea

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.securearea.RwscaKeyInfo
import eu.europa.ec.corelogic.securearea.exception.RwscaServerException
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.prompt.Reason
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyLockedException
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaCreatedKeys
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaRegistrations
import org.sprind.wallet.authenticationlogic.model.RwscaWrappedKey
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.authenticationlogic.provider.RwscaRegistrationsProvider
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.toRwscaError
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

@RunWith(MockitoJUnitRunner::class)
class RwscaSecureAreaTest {

    @Mock
    private lateinit var rwscaController: RwscaController

    @Mock
    private lateinit var registrationsProvider: RwscaRegistrationsProvider

    @Mock
    private lateinit var pinSessionHolder: RwscaPinSessionHolder

    @Mock
    private lateinit var logController: LogController

    private val ephemeralStorage = EphemeralStorage()

    // Real data objects — only passed through to the mocked controller, so the
    // fake JWT strings in mdvm_token / rwsca_pin_session_token_jwt are never parsed.
    private val fakeMdvmRegistration = MdvmRegistration(
        mdvm_wi_id = "fake-mdvm-id",
        mdvm_token = "fake.mdvm.token",
        wi_mdvm_auth_keys_alias = "fake-alias",
    )
    private val fakeRwscaRegistration = RwscaRegistration(rwsca_account_id = "fake-account-id")
    private val fakeRegistrations = RwscaRegistrations(fakeMdvmRegistration, fakeRwscaRegistration)
    private val fakePinSession = RwscaPinSession(rwsca_pin_session_token_jwt = "fake.pin.jwt")
    private val dataToSign = "data-to-sign".toByteArray()

    private val fakeGenericErrorCode = "SOME_SERVER_ERROR_FOR_TESTING"

    private val subject by lazy {
        RwscaSecureArea(
            rwscaController = rwscaController,
            registrationsProvider = registrationsProvider,
            pinSessionHolder = pinSessionHolder,
            logController = logController,
            storage = ephemeralStorage,
            supportedAlgorithms = listOf(Algorithm.ESP256),
        )
    }

    @Test
    fun `sign clears PIN session and throws KeyLockedException on PIN_SESSION_TOKEN_VERIFICATION_FAILURE`() =
        runTest {
            val alias = insertKeyMetadata()

            whenever(registrationsProvider.getRegistrations())
                .thenReturn(ApiResult.Success(fakeRegistrations))
            whenever(pinSessionHolder.get()).thenReturn(fakePinSession)
            whenever(rwscaController.signData(any(), any(), any(), any(), any()))
                .thenReturn(ApiResult.Failure(RwscaError.FromRwsca(
                    type = RwscaErrorType.PIN_SESSION_TOKEN_VERIFICATION_FAILURE,
                    serverResponse = RwscaErrorResponse(code = RwscaErrorType.PIN_SESSION_TOKEN_VERIFICATION_FAILURE.code))
                ))

            assertThrows(KeyLockedException::class.java) {
                runBlocking { subject.sign(alias, dataToSign, Reason.Unspecified) }
            }

            verify(pinSessionHolder).clear()
        }

    @Test
    fun `sign throws KeyLockedException when no PIN session is available`() = runTest {
        val alias = insertKeyMetadata()

        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Success(fakeRegistrations))
        whenever(pinSessionHolder.get()).thenReturn(null)

        assertThrows(KeyLockedException::class.java) {
            runBlocking { subject.sign(alias, dataToSign, Reason.Unspecified) }
        }
    }

    @Test
    fun `batchCreateKey throws RwscServerException when registrations fails`() = runTest {
        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Failure(RwscaErrorResponse(code = fakeGenericErrorCode).toRwscaError()))

        val exception = assertThrows(RwscaServerException::class.java) {
            runBlocking { subject.batchCreateKey(1, RwscaCreateKeySettings(ppCNonce = "test-cnonce")) }
        }

        assertEquals(fakeGenericErrorCode, exception.errorCode)
    }

    @Test
    fun `batchCreateKey throws IllegalStateException when server returns empty key list`() = runTest {
        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Success(fakeRegistrations))
        whenever(rwscaController.createKeys(any(), any(), any(), any()))
            .thenReturn(ApiResult.Success(
                RwscaCreatedKeys(
                    wrappedKeys = emptyList(),
                    walletTrustEvidence = "fake-walletUnitAttestation"
                )
            ))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { subject.batchCreateKey(1, RwscaCreateKeySettings(ppCNonce = "test-cnonce")) }
        }
    }

    @Test
    fun `batchCreateKey returns key infos and stores retrievable metadata on success`() = runTest {
        // A real P-256 public key in X.509/SubjectPublicKeyInfo base64 format.
        // I took this from RemoteSecureAreaTest, where it was added in commit
        // e6bfb432337e31da4a3f3b1bf464f32e8e8bce8d
        val base64EncodedPublicKey =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECsoA5k+f1g3u/HCO0wI/aZ4fGjokL4mZVz22G+i5YmS5CRe5SJS2s+xkWANg4yD9Ff62jIu8pLz3s8J75g9z4w=="

        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Success(fakeRegistrations))
        whenever(rwscaController.createKeys(any(), any(), any(), any()))
            .thenReturn(ApiResult.Success(
                RwscaCreatedKeys(
                    wrappedKeys = listOf(
                        RwscaWrappedKey(
                            encodedPublicKey = base64EncodedPublicKey,
                            wrappedPrivateKey = "fakeWrappedKey",
                        )
                    ),
                    walletTrustEvidence = "fake-walletUnitAttestation",
                )
            ))

        val result = subject.batchCreateKey(1, RwscaCreateKeySettings(ppCNonce = "test-cnonce"))

        assertEquals(1, result.keyInfos.size)
        // Verify the metadata round-trip: getKeyInfo reads back what batchCreateKey stored.
        val alias = result.keyInfos.first().alias
        val keyInfo = subject.getKeyInfo(alias)
        assertEquals(alias, keyInfo.alias)
    }

    @Test
    fun `batchCreateKey stores WTE and create-key nonce so getKeyInfo returns them`() = runTest {
        // A real P-256 public key in X.509/SubjectPublicKeyInfo base64 format.
        val base64EncodedPublicKey =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECsoA5k+f1g3u/HCO0wI/aZ4fGjokL4mZVz22G+i5YmS5CRe5SJS2s+xkWANg4yD9Ff62jIu8pLz3s8J75g9z4w=="
        val wte = "fake-wallet-trust-evidence"
        val cNonce = "create-key-c-nonce"

        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Success(fakeRegistrations))
        whenever(rwscaController.createKeys(any(), any(), any(), any()))
            .thenReturn(ApiResult.Success(
                RwscaCreatedKeys(
                    wrappedKeys = listOf(
                        RwscaWrappedKey(
                            encodedPublicKey = base64EncodedPublicKey,
                            wrappedPrivateKey = "fakeWrappedKey",
                        )
                    ),
                    walletTrustEvidence = wte,
                )
            ))

        val result = subject.batchCreateKey(1, RwscaCreateKeySettings(ppCNonce = cNonce))

        // Returned KeyInfo carries the WTE and the create-key nonce...
        val created = result.keyInfos.first() as RwscaKeyInfo
        assertEquals(wte, created.walletTrustEvidence)
        assertEquals(cNonce, created.walletTrustEvidenceNonce)

        // ...and so does the value read back from storage.
        val persisted = subject.getKeyInfo(created.alias) as RwscaKeyInfo
        assertEquals(wte, persisted.walletTrustEvidence)
        assertEquals(cNonce, persisted.walletTrustEvidenceNonce)
    }

    @Test
    fun `sign throws RwscServerException when rWSCA signData endpoint returns Failure`() = runTest {
        val alias = insertKeyMetadata()

        whenever(registrationsProvider.getRegistrations())
            .thenReturn(ApiResult.Success(fakeRegistrations))
        whenever(pinSessionHolder.get()).thenReturn(fakePinSession)
        whenever(rwscaController.signData(any(), any(), any(), any(), any()))
            .thenReturn(ApiResult.Failure(RwscaErrorResponse(code = fakeGenericErrorCode).toRwscaError()))

        val exception = assertThrows(RwscaServerException::class.java) {
            runBlocking { subject.sign(alias, dataToSign, Reason.Unspecified) }
        }
        assertEquals(fakeGenericErrorCode, exception.errorCode)
    }

    @Test
    fun `createKey throws IllegalArgumentException when non-null alias is provided`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { subject.createKey("my-hardcoded-alias", CreateKeySettings(Algorithm.ESP256)) }
        }
    }

    @Test
    fun `deleteKey removes key metadata from storage`() = runTest {
        val alias = insertKeyMetadata()

        subject.deleteKey(alias)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { subject.getKeyInfo(alias) }
        }
    }

    /**
     * Inserts a minimal key metadata row into [ephemeralStorage] that satisfies the fields read
     * by [RwscaSecureArea.sign]: [RwscaSecureArea.KEY_CURVE] and
     * [RwscaSecureArea.KEY_WRAPPED_PRIVATE_KEY]. Returns the generated alias.
     */
    private suspend fun insertKeyMetadata(): String {
        val table = ephemeralStorage.getTable(spec = RwscaSecureArea.storageTableSpec)
        val cbor = Cbor.encode(
            CborMap.builder()
                .put(RwscaSecureArea.KEY_CURVE, EcCurve.P256.coseCurveIdentifier)
                .put(RwscaSecureArea.KEY_WRAPPED_PRIVATE_KEY, "fakeWrappedKey")
                .end()
                .build()
        )
        return table.insert(
            key = null,
            partitionId = RwscaSecureArea.IDENTIFIER,
            data = ByteString(cbor),
        )
    }
}
