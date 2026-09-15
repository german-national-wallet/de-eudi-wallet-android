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

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.flags.models.FeatureFlag
import org.sprind.wallet.flags.models.SAVED_FEATURE_FLAGS_OVERRIDE
import kotlin.test.assertEquals

private const val TEST_FLAG_STRING_KEY = "test_string"
private const val TEST_FLAG_JSON_KEY = "test_json"
private const val TEST_FLAG_BOOLEAN_KEY = "test_feature"
private const val TEST_FLAG_INT_KEY = "test_number"

class FeatureFlagManagerTests {
    private lateinit var closeable: AutoCloseable

    private lateinit var featureFlagManager: FeatureFlagManager

    @Mock
    private lateinit var featureFlagStorage: FeatureFlagStorage

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(featureFlagStorage.getStoredFlags()).thenReturn(allFlagTypes)
        whenever(featureFlagStorage.getLastUpdateTime()).thenReturn("")
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `given no FeatureFlagOverrides when get flagValue then defaultValue is returned`() =
        runTest {
            whenever(featureFlagStorage.getStoredFlags()).thenReturn("")
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_STRING_KEY, "thisIsCrazyUnicornPonyPinkPanda")
            assertEquals(
                expected = "thisIsCrazyUnicornPonyPinkPanda",
                actual = featureFlagManager.getFlagValue(actualTestFlag),
            )
        }

    @Test
    fun `when get existing string flag from map override then override is returned`() =
        runTest {
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_STRING_KEY, "thisIsCrazyUnicornPonyPinkPanda")
            assertEquals("green", featureFlagManager.getFlagValue(actualTestFlag))
        }

    @Test
    fun `when get existing json flag from map override then override is returned`() =
        runTest {
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_JSON_KEY, "{ \"key\": \"thisIsCrazyUnicornPonyPinkPanda\"}")
            assertEquals("{\"color\":\"grey\",\"size\":42,\"other\":\"unknown\"}", featureFlagManager.getFlagValue(actualTestFlag))
        }

    @Test
    fun `when get existing boolean flag from map override then override is returned`() =
        runTest {
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_BOOLEAN_KEY, false)
            assertEquals(true, featureFlagManager.getFlagValue(actualTestFlag))
        }

    @Test
    fun `when get existing number flag from map override then override is returned`() =
        runTest {
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_INT_KEY, 42)
            assertEquals(7, featureFlagManager.getFlagValue(actualTestFlag))
        }

    @Test
    fun `when get not overridden string flag from map override then default is returned`() =
        runTest {
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag("SOMETHING_WEIRD_FOR_TESTING", "don't_known_by_prefs")
            assertEquals("don't_known_by_prefs", featureFlagManager.getFlagValue(actualTestFlag))
        }

    @Test
    fun `reloadFlags picks up freshly stored flags while getFlagValue never re-reads storage`() =
        runTest {
            whenever(featureFlagStorage.getStoredFlags()).thenReturn("")
            featureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)
            val actualTestFlag = FeatureFlag(TEST_FLAG_STRING_KEY, "default")

            // Before a refresh the in-memory cache holds only the default; repeated
            // reads must not touch storage.
            assertEquals("default", featureFlagManager.getFlagValue(actualTestFlag))
            assertEquals("default", featureFlagManager.getFlagValue(actualTestFlag))

            // Fresh flags land in storage, then a refresh triggers an explicit reload.
            whenever(featureFlagStorage.getStoredFlags()).thenReturn(allFlagTypes)
            featureFlagManager.reloadFlags()

            assertEquals("green", featureFlagManager.getFlagValue(actualTestFlag))

            // Storage is read only at init and on reloadFlags() — never per getFlagValue.
            verify(featureFlagStorage, times(2)).getStoredFlags()
        }

    private val allFlagTypes = """
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
}
