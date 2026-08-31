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

package org.sprind.wallet.businesslogic.model

/**
 * A parsed [semantic version](https://semver.org) (major.minor.patch with optional
 * prerelease identifiers) ordered by SemVer precedence; build metadata is ignored.
 * Used to compare the current app version against the minimum required version.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prereleaseIdentifiers: List<String>,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 } ?: comparePrereleaseIdentifiers(other)

    private fun comparePrereleaseIdentifiers(other: SemanticVersion): Int {
        if (prereleaseIdentifiers.isEmpty() && other.prereleaseIdentifiers.isEmpty()) return 0
        if (prereleaseIdentifiers.isEmpty()) return 1
        if (other.prereleaseIdentifiers.isEmpty()) return -1

        prereleaseIdentifiers.zip(other.prereleaseIdentifiers).forEach { (left, right) ->
            if (left == right) return@forEach

            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            return when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
        }

        return prereleaseIdentifiers.size.compareTo(other.prereleaseIdentifiers.size)
    }

    companion object {
        private val semverRegex = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                    "(?:-((?:0|[1-9A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9A-Za-z-][0-9A-Za-z-]*))*))?" +
                    "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
        )

        fun parse(value: String): SemanticVersion? {
            val match = semverRegex.matchEntire(value) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prereleaseIdentifiers = match.groupValues[4]
                    .takeIf { it.isNotEmpty() }
                    ?.split(".")
                    .orEmpty()
            )
        }
    }
}
