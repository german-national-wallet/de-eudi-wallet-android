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
package org.sprind.wallet.flags.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import timber.log.Timber


@Serializable
data class FeatureFlagOverrides(
    // will the ID change? do we need it at all?
    val id: String,
    val features: List<FeatureFlagOverride>,
)

/**
 * Interface for parsing feature flag overrides fetched from a
 * REST API endpoint (at the moment FeatureHub is used)
 */
interface FeatureFlagOverride {
    // we have the key, do we need the ID? There shouldn't be any duplicate flags IMHO
    val id: String
    val key: String

    // what is this one for?
    val l: Boolean
    val version: Int

    val value: Any

    // what is this one for?
    val strategies: List<String>
}

/**
 * Json formatter for featureflag overrides received from REST API
 */
val flagFormatter =
    Json {
        encodeDefaults = true // only for encoding, not needed for decoding
        ignoreUnknownKeys = true
//        classDiscriminator = "type" // indicates the field to look for distinction, "type" is default
        serializersModule =
            SerializersModule {
                polymorphic(FeatureFlagOverride::class) {
                    // add all known descendants
                    // ensures no new data class type can be sideloaded
                    subclass(StringFlagOverride::class)
                    subclass(JsonFlagOverride::class)
                    subclass(IntFlagOverride::class)
                    subclass(BooleanFlagOverride::class)
                }
            }
    }

fun decodeFlagsFromString(flags: String): Map<String, FeatureFlag<*>> {
    // handle parsing errors
    val flags = try {
        flagFormatter.decodeFromString<List<FeatureFlagOverrides>>(flags)
    } catch (cce: ClassCastException) {
        Timber.e(cce, "Cannot parse invalid input to FeatureFlags: $flags")
        return emptyMap()
    }
    // check for duplicate key entries
    flags.forEach { featureFlags ->
        if (featureFlags.features.distinctBy { it.key }.size != featureFlags.features.size) {
            Timber.e("Found duplicates in FeatureFlags: ${featureFlags.features}")
            return emptyMap()
        }
    }
    return flags.flatMap { it.features }.associate { it.key to FeatureFlag(it.key, it.value) }
}