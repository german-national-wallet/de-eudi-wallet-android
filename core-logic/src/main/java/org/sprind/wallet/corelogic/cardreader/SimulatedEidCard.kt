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
package org.sprind.wallet.corelogic.cardreader

import com.governikus.ausweisapp.sdkwrapper.card.core.Simulator
import com.governikus.ausweisapp.sdkwrapper.card.core.SimulatorFile
import com.governikus.ausweisapp.sdkwrapper.card.core.SimulatorKey
import org.sprind.wallet.businesslogic.config.SimulatedEidCardKeyGenerator

object SimulatedEidCard {

    const val READER_NAME = "Simulator"

    /** The AusweisApp default data groups (Erika Mustermann), kept identical to iOS. */
    val defaultFiles: List<SimulatorFile> = listOf(
        SimulatorFile("0101", "01", "610413024944"),
        SimulatorFile("0102", "02", "6203130144"),
        SimulatorFile("0103", "03", "630a12083230323931303331"),
        SimulatorFile("0104", "04", "64070c054552494b41"),
        SimulatorFile("0105", "05", "650c0c0a4d55535445524d414e4e"),
        SimulatorFile("0106", "06", "66020c00"),
        SimulatorFile("0107", "07", "67020c00"),
        SimulatorFile("0108", "08", "680a12083139363430383132"),
        SimulatorFile("0109", "09", "690aa1080c064245524c494e"),
        SimulatorFile("010a", "0a", "6a03130144"),
        SimulatorFile("010b", "0b", "6b03130146"),
        SimulatorFile(
            "010c",
            "0c",
            "6c30312e302c06072a8648ce3d0101022100a9fb57dba1eea9bc3e660a909d838d726e3bf623d52620282013481d1f6e5377",
        ),
        SimulatorFile("010d", "0d", "6d080c064741424c4552"),
        SimulatorFile("010f", "0f", "6f0a12083230313931313031"),
        SimulatorFile(
            "0111",
            "11",
            "712d302baa120c10484549444553545241e1ba9e45203137ab070c054bc3964c4ead03130144ae0713053531313437",
        ),
        SimulatorFile("0112", "12", "7209040702760503150000"),
        SimulatorFile("0113", "13", "7316a1140c125245534944454e4345205045524d49542031"),
        SimulatorFile("0114", "14", "7416a1140c125245534944454e4345205045524d49542032"),
        SimulatorFile("0115", "15", "7515131374656c3a2b34392d3033302d31323334353637"),
        SimulatorFile("0116", "16", "761516136572696b61406d75737465726d616e6e2e6465"),
    )

    /** A card with the default data and [pseudonymKey] as key 2. */
    fun create(pseudonymKey: String): Simulator =
        Simulator(
            defaultFiles,
            listOf(SimulatorKey(SimulatedEidCardKeyGenerator.PSEUDONYM_KEY_ID, pseudonymKey)),
        )
}