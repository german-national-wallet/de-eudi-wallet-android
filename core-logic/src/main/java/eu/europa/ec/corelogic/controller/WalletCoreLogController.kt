/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.controller


import eu.europa.ec.eudi.wallet.logging.Logger
import eu.europa.ec.businesslogic.controller.log.LogController

interface WalletCoreLogController : Logger

class WalletCoreLogControllerImpl(
    private val logController: LogController,
) : WalletCoreLogController {
    private val logTag = "WCoreLogControllerImpl" // tags are limited to 23 characters

    override fun log(record: Logger.Record) {
        val tag = record.sourceClassName?.takeLast(23) ?: logTag
        when (record.level) {
            Logger.LEVEL_ERROR -> record.thrown?.let { logController.e(tag, it) }
                ?: logController.e(tag) { record.message }

            Logger.LEVEL_INFO -> logController.i(tag) { record.message }

            Logger.LEVEL_DEBUG -> logController.d(tag) { record.message }
            Logger.OFF -> {
                /* Nothing to log */
            }
        }
    }
}