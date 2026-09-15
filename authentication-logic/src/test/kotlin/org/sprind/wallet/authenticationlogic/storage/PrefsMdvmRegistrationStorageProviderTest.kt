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

package org.sprind.wallet.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration

private const val mdvm_wi_id = "fake mdvm_wi_id for test"
private const val mdvm_token = "fake mdvm_token for test"
private const val wi_mdvm_auth_keys_alias = "wi_mdvm_auth_keys"

class PrefsMdvmRegistrationStorageProviderTest {

    @Mock
    private lateinit var prefsController: PrefsController

    private val subject: PrefsMdvmRegistrationStorageProvider by lazy {
        PrefsMdvmRegistrationStorageProvider(
            PrefsJsonStorageProvider(prefsController, Gson()))
    }

    private val gson = Gson()

    private lateinit var closeable: AutoCloseable

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `getMdvmRegisterResponse returns parsed response when stored data is valid`() {
        val expected = MdvmRegistration(
            mdvm_wi_id = mdvm_wi_id,
            mdvm_token = mdvm_token,
            wi_mdvm_auth_keys_alias = wi_mdvm_auth_keys_alias,
        )
        whenever(prefsController.getString(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY, ""))
            .thenReturn(gson.toJson(expected))

        assertEquals(expected, subject.getMdvmRegistration())
    }

    @Test
    fun `getMdvmRegisterResponse returns null when nothing is stored`() {
        whenever(prefsController.getString(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY, ""))
            .thenReturn("")

        assertNull(subject.getMdvmRegistration())
    }

    @Test
    fun `getMdvmRegisterResponse returns null when stored data is corrupt`() {
        whenever(prefsController.getString(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY, ""))
            .thenReturn("not valid json{")

        assertNull(subject.getMdvmRegistration())
    }

    @Test
    fun `saveMdvmRegisterResponse stores correct JSON under the correct key`() {
        val registration = MdvmRegistration(
            mdvm_wi_id = mdvm_wi_id,
            mdvm_token = mdvm_token,
            wi_mdvm_auth_keys_alias = wi_mdvm_auth_keys_alias,
        )

        subject.saveMdvmRegistration(registration)

        verify(prefsController).setString(SHARED_PREFERENCE_MDVM_REGISTRATION_KEY, gson.toJson(registration))
    }

    /* Check that the JSON serialization format hasn't changed. This may in some cases
     * be okay, but our code under test relies on gson producing (and parsing) a
     * reasonable JSON representation, so it's worth checking this.
     */
    @Test
    fun `JSON serialized form is as expected`() {
        val expected = "{\"mdvm_wi_id\":\"fake mdvm_wi_id for test\",\"mdvm_token\":\"fake mdvm_token for test\",\"wi_mdvm_auth_keys_alias\":\"wi_mdvm_auth_keys\"}"

        val registration = MdvmRegistration(
            mdvm_wi_id = mdvm_wi_id,
            mdvm_token = mdvm_token,
            wi_mdvm_auth_keys_alias = wi_mdvm_auth_keys_alias,
        )

        assertEquals(expected, gson.toJson(registration))
    }
}
