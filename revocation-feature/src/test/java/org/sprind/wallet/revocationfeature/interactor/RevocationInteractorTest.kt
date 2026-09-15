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

package org.sprind.wallet.revocationfeature.interactor

import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.junit.Test
import kotlin.test.assertEquals

class RevocationInteractorTest {

    @Test
    fun `given no saved code confirmation when getting direction then guards point to intermediate route`() {
        val interactor = object : RevocationInteractor {
            override suspend fun getRevocationCode() = "revocationCode"

            override fun hasUserConfirmedSavingCode() = false

            override fun storeUserConfirmedSavingCode(value: Boolean) {
            }
        }
        assertEquals(ModuleRoute.RevocationModule.route, interactor.getDirection())
    }

    @Test
    fun `given saved code confirmation when getting direction then guards point to intermediate route`() {
        val interactor = object : RevocationInteractor {
            override suspend fun getRevocationCode() = "revocationCode"

            override fun hasUserConfirmedSavingCode() = true

            override fun storeUserConfirmedSavingCode(value: Boolean) {
            }
        }
        assertEquals(ModuleRoute.DashboardModule.route, interactor.getDirection())
    }

}