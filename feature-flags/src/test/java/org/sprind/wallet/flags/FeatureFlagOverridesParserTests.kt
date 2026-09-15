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
import org.junit.Test
import org.sprind.wallet.flags.models.BooleanFlagOverride
import org.sprind.wallet.flags.models.FeatureFlag
import org.sprind.wallet.flags.models.FeatureFlagOverride
import org.sprind.wallet.flags.models.FeatureFlagOverrides
import org.sprind.wallet.flags.models.JsonFlagOverride
import org.sprind.wallet.flags.models.IntFlagOverride
import org.sprind.wallet.flags.models.StringFlagOverride
import org.sprind.wallet.flags.models.decodeFlagsFromString
import org.sprind.wallet.flags.models.flagFormatter
import kotlin.test.assertEquals

class FeatureFlagOverridesParserTests {
    @Test
    fun `when StringFlag Json is received then StringFlag is returned`() =
        runTest {
            val stringFlagJson =
                """
        {
          "id": "38ac891e-51e0-4f00-a6ff-87f76ec01601",
          "key": "test_string",
          "l": false,
          "version": 1,
          "type": "STRING",
          "value": "green",
          "strategies": []
        }  
        """.trimIndent()
            val featureFlagOverride = flagFormatter.decodeFromString<FeatureFlagOverride>(stringFlagJson)
            assert(featureFlagOverride is StringFlagOverride)
            assertEquals(expectedStringFlagOverride, featureFlagOverride)
        }

    @Test
    fun `given StringFlag with new keys when decoding FeatureFlag then StringFlag with known fields is returned`() =
        runTest {
            val canYouCopeWithThisStringFlagJson =
                """
                {
                  "id": "38ac891e-51e0-4f00-a6ff-87f76ec01601",
                  "key": "test_string",
                  "l": false,
                  "version": 1,
                  "type": "STRING",
                  "value": "green",
                  "strategies": [],
                  "evilNewField": "thisIsCrazyUnicornPonyPinkPanda"
                }
                """.trimIndent()
            val featureFlagOverride = flagFormatter.decodeFromString<FeatureFlagOverride>(canYouCopeWithThisStringFlagJson)
            assert(featureFlagOverride is StringFlagOverride)
            assertEquals(expectedStringFlagOverride, featureFlagOverride as StringFlagOverride)
        }

    @Test
    fun `when all FeatureFlags are received then FeatureFlags are returned`() =
        runTest {
            val featureResult = flagFormatter.decodeFromString<List<FeatureFlagOverrides>>(allFlagTypes)
            assertEquals(1, featureResult.size)
            featureResult.forEach { featureFlags ->
                assertEquals(4, featureFlags.features.size)
                featureFlags.features.forEach { feature ->
                    val expectation =
                        when (feature) {
                            is StringFlagOverride -> expectedStringFlagOverride
                            is JsonFlagOverride -> expectedJsonFlagOverride
                            is IntFlagOverride -> expectedIntFlagOverride
                            is BooleanFlagOverride -> expectedBooleanFlagOverride
                            else -> throw IllegalArgumentException("Unknown flag type: $feature")
                        }
                    assertEquals(expectation, feature)
                }
            }
        }

    @Test
    fun `when all FeatureFlags are received then Map of FeatureFlags is returned`() = runTest {
        val featureFlags = decodeFlagsFromString(allFlagTypes)
        assertEquals(4, featureFlags.size)
        featureFlags.forEach { (key, flag) ->
            val expectation =
                when (flag.value) {
                    is String -> {
                        if (key == "test_string") {
                            expectedStringFlagOverride
                        } else {
                            expectedJsonFlagOverride
                        }
                    }

                    is Int -> expectedIntFlagOverride
                    is Boolean -> expectedBooleanFlagOverride
                    else -> throw IllegalArgumentException("Unknown flag type: $flag")
                }
            assertEquals(expectation.value, flag.value)
        }
    }

    @Test
    fun `given duplicate FeatureFlag entries when decodeFromString then no flag is accepted`() = runTest {
        val featureResult = decodeFlagsFromString(allFlagTypesWithDuplicates)
        assert(featureResult.isEmpty()) { "Result shall be empty: $featureResult" }
    }

    private val expectedStringFlagOverride =
        StringFlagOverride(
            id = "38ac891e-51e0-4f00-a6ff-87f76ec01601",
            key = "test_string",
            false,
            version = 1,
            value = "green",
            strategies = emptyList(),
        )

    private val expectedJsonFlagOverride =
        JsonFlagOverride(
            id = "0b8a5871-d763-49a6-ba9d-31c5ea0a84ad",
            key = "test_json",
            l = false,
            version = 1,
            value =
                """
                {"color":"grey","size":42,"other":"unknown"}
                """.trimIndent(),
            strategies = emptyList(),
        )

    private val expectedIntFlagOverride =
        IntFlagOverride(
            id = "0827f3af-a512-4092-b8e4-cd9f01d6c0c8",
            key = "test_number",
            l = false,
            version = 1,
            value = 7,
            strategies = emptyList(),
        )

    private val expectedBooleanFlagOverride =
        BooleanFlagOverride(
            id = "b939f899-b4e9-4185-b5de-af28703d48ca",
            key = "test_feature",
            l = false,
            version = 2,
            value = true,
            strategies = emptyList(),
        )


    private val allFlagTypes =
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

    private val allFlagTypesWithDuplicates =
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
                "id": "this-is-an-entry-with-a-duplicate-key",
                "key": "test_string",
                "l": false,
                "version": 1,
                "type": "STRING",
                "value": "green",
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
