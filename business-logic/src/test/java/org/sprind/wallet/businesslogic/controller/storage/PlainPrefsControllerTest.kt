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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlainPrefsControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val subject = PlainPrefsControllerImpl(context)

    @Test
    fun `missing key returns the default`() {
        assertTrue(subject.getBool("missing", true))
        assertFalse(subject.getBool("missing", false))
    }

    @Test
    fun `setBool round-trips`() {
        subject.setBool("flag", true)
        assertTrue(subject.getBool("flag", false))

        subject.setBool("flag", false)
        assertFalse(subject.getBool("flag", true))
    }

    @Test
    fun `setBoolSync is visible to a fresh instance on the same file`() {
        subject.setBoolSync("flag", true)

        assertTrue(PlainPrefsControllerImpl(context).getBool("flag", false))
    }

    @Test
    fun `clear removes the key so the default applies again`() {
        subject.setBool("flag", true)
        subject.clear("flag")

        assertFalse(subject.getBool("flag", false))
    }

    @Test
    fun `values are kept apart from the encrypted preferences file`() {
        subject.setBool("flag", true)

        val encryptedFile = context.getSharedPreferences("secret_shared_prefs", Context.MODE_PRIVATE)
        assertFalse(encryptedFile.contains("flag"))
    }
}
