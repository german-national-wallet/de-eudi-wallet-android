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
package org.sprind.wallet.flags

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.flags.api.FeatureFlagApiClient
import org.sprind.wallet.flags.models.FeatureFlagOverrides
import org.sprind.wallet.flags.models.flagFormatter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FeatureFlagUpdateServiceTests {
    private lateinit var closeable: AutoCloseable

    @Mock
    private lateinit var featureFlagStorage: FeatureFlagStorage

    @Mock
    private lateinit var featureFlagApiClient: FeatureFlagApiClient

    private val ioDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(featureFlagStorage.getLastUpdateTime()).thenReturn("")
        whenever(featureFlagStorage.getStoredFlags()).thenReturn("")
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `given first run when refreshFlagsIfNeeded then result is saved`() = runTest {
        whenever(featureFlagApiClient.fetchFeatureFlags()).thenReturn(expectedApiClientResult)
        service().refreshFlagsIfNeeded()

        verify(featureFlagStorage, times(1)).storeUpdateTime(anyString())
        verify(featureFlagStorage, times(1)).storeFlags(anyString())
        verify(featureFlagApiClient, times(1)).fetchFeatureFlags()
    }

    @Test
    fun `given saved flags older than MAX_AGE_FLAGS_IN_HOURS when refreshFlagsIfNeeded then result is saved`() = runTest {
        val olderThanAllowed = Clock.System.now().minus(MAX_AGE_FLAGS_IN_HOURS.hours).minus(1.hours).toString()
        whenever(featureFlagStorage.getLastUpdateTime()).thenReturn(olderThanAllowed)
        whenever(featureFlagApiClient.fetchFeatureFlags()).thenReturn(expectedApiClientResult)
        service().refreshFlagsIfNeeded()

        verify(featureFlagStorage, times(1)).storeUpdateTime(anyString())
        verify(featureFlagStorage, times(1)).storeFlags(anyString())
        verify(featureFlagApiClient, times(1)).fetchFeatureFlags()
    }

    @Test
    fun `given saved flags younger than MAX_AGE_FLAGS_IN_HOURS when refreshFlagsIfNeeded then nothing is fetched`() = runTest {
        val stillValid = Clock.System.now().minus(1.hours).toString()
        whenever(featureFlagStorage.getLastUpdateTime()).thenReturn(stillValid)
        service().refreshFlagsIfNeeded()

        verify(featureFlagStorage, times(0)).storeUpdateTime(anyString())
        verify(featureFlagStorage, times(0)).storeFlags(anyString())
        verify(featureFlagApiClient, times(0)).fetchFeatureFlags()
    }

    @Test
    fun `given an unparsable last update time when refreshFlagsIfNeeded then result is saved`() = runTest {
        whenever(featureFlagStorage.getLastUpdateTime()).thenReturn("not a timestamp")
        whenever(featureFlagApiClient.fetchFeatureFlags()).thenReturn(expectedApiClientResult)
        service().refreshFlagsIfNeeded()

        verify(featureFlagApiClient, times(1)).fetchFeatureFlags()
        verify(featureFlagStorage, times(1)).storeFlags(anyString())
    }

    @Test
    fun `given empty return value when refreshFlagsIfNeeded then flags are cleared and update time is saved`() = runTest {
        whenever(featureFlagApiClient.fetchFeatureFlags()).thenReturn(Result.success(emptyList()))
        service().refreshFlagsIfNeeded()

        verify(featureFlagStorage, times(1)).storeFlags("")
        // without this the gate never engages and every launch would fetch again
        verify(featureFlagStorage, times(1)).storeUpdateTime(anyString())
        verify(featureFlagApiClient, times(1)).fetchFeatureFlags()
    }

    @Test
    fun `given failure returned when refreshFlagsIfNeeded then nothing is saved`() = runTest {
        whenever(featureFlagApiClient.fetchFeatureFlags()).thenReturn(Result.failure(IllegalArgumentException("oh no,… anyway")))
        whenever(featureFlagStorage.storeFlags(anyString())).doAnswer {
            throw IllegalArgumentException("Should not run empty -> argument: ${it.getArgument<String>(0)}")
        }
        service().refreshFlagsIfNeeded()

        verify(featureFlagStorage, times(0)).storeFlags(anyString())
        verify(featureFlagStorage, times(0)).storeUpdateTime(anyString())
        verify(featureFlagApiClient, times(1)).fetchFeatureFlags()
    }

    private fun service() = FeatureFlagUpdateServiceImpl(featureFlagStorage, featureFlagApiClient, ioDispatcher)

    private val expectedFlagsString =
        """
        [
          {
            "id": "3c2aff2b-a1cc-4a5d-803b-5937963d998f",
            "features": [
              {
                "id": "0b8a5871-d763-49a6-ba9d-31c5ea0a84ad",
                "key": "test_json",
                "l": false,
                "version": 1,
                "type": "JSON",
                "value": "{\"color\":\"grey\",\"size\":42,\"other\":\"unknown\"}",
                "strategies": []
              },
              {
                "id": "38ac891e-51e0-4f00-a6ff-87f76ec01601",
                "key": "test_string",
                "l": false,
                "version": 1,
                "type": "STRING",
                "value": "green",
                "strategies": []
              },
              {
                "id": "b939f899-b4e9-4185-b5de-af28703d48ca",
                "key": "test_feature",
                "l": false,
                "version": 2,
                "type": "BOOLEAN",
                "value": true,
                "strategies": []
              },
              {
                "id": "0827f3af-a512-4092-b8e4-cd9f01d6c0c8",
                "key": "test_number",
                "l": false,
                "version": 1,
                "type": "NUMBER",
                "value": 7,
                "strategies": []
              }
            ]
          }
        ]
        """.trimIndent()

    private val expectedApiClientResult = Result.success(flagFormatter.decodeFromString<List<FeatureFlagOverrides>>(expectedFlagsString))
}
