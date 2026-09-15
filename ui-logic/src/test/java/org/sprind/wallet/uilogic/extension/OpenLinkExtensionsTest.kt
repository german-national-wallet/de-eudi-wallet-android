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

import android.app.Activity
import androidx.core.net.toUri
import eu.europa.ec.uilogic.extension.openDeepLink
import eu.europa.ec.uilogic.extension.openUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenLinkExtensionsTest {

    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
    }

    @After
    fun tearDown() {
        controller.close()
    }

    @Test
    fun `given a web link, when openUrl is called, then the link is forwarded`() {
        val link = "https://issuer.example.org/authorize?code=1".toUri()

        activity.openUrl(link)

        assertEquals(link, shadowOf(activity).nextStartedActivity?.data)
    }

    @Test
    fun `given a custom scheme redirect, when openUrl is called, then the link is forwarded`() {
        val link = "org.example.verifier://redirect?response_code=1".toUri()

        activity.openUrl(link)

        assertEquals(link, shadowOf(activity).nextStartedActivity?.data)
    }

    @Test
    fun `given a tel link, when openUrl is called, then nothing is started`() {
        activity.openUrl("tel://1234".toUri())

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `given an intent link, when openUrl is called, then nothing is started`() {
        activity.openUrl(
            "intent://scan#Intent;scheme=zxing;package=org.example.app;end".toUri()
        )

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `given an app redirect, when openDeepLink is called, then the redirect is forwarded`() {
        val link = "eudi-openid4vp://verifier.example.org?response_code=1".toUri()

        activity.openDeepLink(link)

        assertEquals(link, shadowOf(activity).nextStartedActivity?.data)
    }

    @Test
    fun `given a tel redirect, when openDeepLink is called, then nothing is started`() {
        activity.openDeepLink("tel://1234".toUri())

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `given an intent redirect, when openDeepLink is called, then nothing is started`() {
        activity.openDeepLink(
            "intent://scan#Intent;scheme=zxing;package=org.example.app;end".toUri()
        )

        assertNull(shadowOf(activity).nextStartedActivity)
    }
}