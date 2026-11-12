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

package org.sprind.wallet.businesslogic.controller.storage

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.businesslogic.controller.crypto.InsecureKeyStorageException
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedPrefsUnavailableExceptionTest {

    @Test
    fun `a keyset that cannot be decrypted is lost`() {
        assertKeysetLost(AEADBadTagException("tag mismatch"))
        assertKeysetLost(IOException("invalid keyset"))
        assertKeysetLost(ProviderException("keystore error"))
        assertKeysetLost(KeyStoreException("unusable master key"))
    }

    @Test
    fun `a missing master key loses the keyset`() {
        assertKeysetLost(UnrecoverableKeyException("no key"))
    }

    @Test
    fun `a permanently invalidated master key loses the keyset`() {
        assertKeysetLost(KeyPermanentlyInvalidatedException())
    }

    @Test
    fun `a keystore rejecting the key itself loses the keyset`() {
        // Neither transient nor a system error: the key cannot be used any more.
        val keyFailure = mock(android.security.KeyStoreException::class.java)
        assertKeysetLost(KeyStoreException("unusable master key", keyFailure))
    }

    @Test
    fun `a missing authentication means locked, also when wrapped`() {
        assertClassifiedAs<EncryptedPrefsUnavailableException.Locked>(UserNotAuthenticatedException())
        assertClassifiedAs<EncryptedPrefsUnavailableException.Locked>(
            KeyStoreException("wrapped", UserNotAuthenticatedException())
        )
    }

    @Test
    fun `a transient keystore failure or a system error is transient`() {
        val transientFailure = mock(android.security.KeyStoreException::class.java).also {
            whenever(it.isTransientFailure).thenReturn(true)
        }
        val systemError = mock(android.security.KeyStoreException::class.java).also {
            whenever(it.isSystemError).thenReturn(true)
        }
        assertClassifiedAs<EncryptedPrefsUnavailableException.Transient>(
            KeyStoreException("the master key exists but is unusable", transientFailure)
        )
        assertClassifiedAs<EncryptedPrefsUnavailableException.Transient>(
            ProviderException("Keystore operation failed", systemError)
        )
    }

    @Test
    fun `a master key outside secure hardware means an insecure device`() {
        assertClassifiedAs<EncryptedPrefsUnavailableException.InsecureDevice>(
            InsecureKeyStorageException("alias", 0)
        )
    }

    @Test
    fun `any keystore failure without a secure lock screen means an insecure device`() {
        assertClassifiedAs<EncryptedPrefsUnavailableException.InsecureDevice>(
            InvalidAlgorithmParameterException("Secure lock screen must be enabled"),
            isDeviceSecure = false,
        )
    }

    @Test
    fun `unrelated failures are not classified`() {
        assertNull(IllegalStateException("bug").toEncryptedPrefsUnavailable(isDeviceSecure = true))
    }

    private fun assertKeysetLost(e: Exception) =
        assertClassifiedAs<EncryptedPrefsUnavailableException.KeysetLost>(e)

    private inline fun <reified T : EncryptedPrefsUnavailableException> assertClassifiedAs(
        e: Exception,
        isDeviceSecure: Boolean = true,
    ) {
        val classified = e.toEncryptedPrefsUnavailable(isDeviceSecure)
        assertTrue("$e classified as $classified", classified is T)
        assertSame(e, classified?.cause)
    }
}
