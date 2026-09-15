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

package org.sprind.wallet.uilogic.extension

import androidx.core.net.toUri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkSchemePolicyTest {

    @Test
    fun `given an app redirect, when isSafeRedirectLink is called, then it is accepted`() {
        listOf(
            "https://verifier.example.org/redirect?code=1",
            "http://verifier.example.org/redirect?code=1",
            "HTTPS://verifier.example.org/redirect?code=1",
            "eudi-openid4vp://verifier.example.org?response_code=1",
            "org.example.verifier://redirect",
        ).forEach { link ->
            assertTrue(link.toUri().isSafeRedirectLink(), "expected $link to be accepted")
        }
    }

    @Test
    fun `given a redirect that escalates, when isSafeRedirectLink is called, then it is rejected`() {
        listOf(
            "intent://scan#Intent;scheme=zxing;package=org.example.app;end",
            "INTENT://scan#Intent;scheme=zxing;end",
            "android-app://org.example.app",
            "file:///data/data/org.sprind.wallet/files/secret",
            "content://org.sprind.wallet.provider/documents/1",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "tel://1234",
            "tel:1234",
            "sms:1234",
            "mailto:someone@example.org",
            "market://details?id=org.sprind.wallet",
            "geo:0,0?q=somewhere",
            "/no/scheme/at/all",
        ).forEach { link ->
            assertFalse(link.toUri().isSafeRedirectLink(), "expected $link to be rejected")
        }
    }
}