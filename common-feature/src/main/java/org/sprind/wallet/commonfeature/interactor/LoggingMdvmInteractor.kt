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

package org.sprind.wallet.commonfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.MdvmResult

internal class LoggingMdvmInteractor(
    val logController: LogController,
    val delegate: MdvmInteractor,
): MdvmInteractor {
    override suspend fun mdvmRegistration(forceRenewal: Boolean): MdvmResult<MdvmRegistration> =
        delegate.mdvmRegistration(forceRenewal).also { result ->
            logController.d(javaClass.simpleName) { "MDVM registration result (forceRenewal=$forceRenewal): $result" }
        }
}
