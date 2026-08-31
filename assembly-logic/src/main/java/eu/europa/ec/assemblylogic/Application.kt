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

package eu.europa.ec.assemblylogic

import android.app.Application
import android.widget.Toast
import org.sprind.wallet.analyticslogic.controller.Telemetry
import eu.europa.ec.assemblylogic.di.setupKoin
import eu.europa.ec.authenticationlogic.provider.TakIdProvider
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.businesslogic.controller.storage.StorageController
import org.sprind.wallet.corelogic.platformauth.PlatformAuthInvariant
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.inject
import org.koin.core.KoinApplication
import org.multipaz.context.initializeApplication

class Application : Application() {

    private val storageController : StorageController by inject()
    private val platformAuthInvariant: PlatformAuthInvariant by inject()
    private val takIdProvider: TakIdProvider by inject()
    private val telemetry: Telemetry by inject()
    private val appJob = SupervisorJob()
    private val handler = CoroutineExceptionHandler { _, t ->
        //TODO to have a proper handling
    }
    private val appScope = CoroutineScope(appJob + Dispatchers.Default + handler)

    companion object {
        private const val AA2_PROCESS = "ausweisapp2_service"

        private const val CHECK_INTERVAL_SECS = 3
        private const val STAGING_LICENSE_FILE = "staging-license"
    }

    override fun onCreate() {
        super.onCreate()
        if (isAA2Process()) return
        initializeApplication(this)
        initializeKoin()
        storageController.onInitializeStorage()
        // Wipe wallet data when the Platform Authentication invariant is violated:
        // the device has no secure lock screen (user removed their PIN/passcode/
        // biometric) but wallet data is present. This runs before any Activity is
        // created so the user can never reach data without a device lock. It is
        // also re-checked on every ON_RESUME from MainActivity for the case where
        // the lock is removed while the app is backgrounded.
        if (platformAuthInvariant.shouldWipe()) {
            Toast.makeText(this, R.string.clear_app_data_success, Toast.LENGTH_LONG).show()
            storageController.wipeAppData()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appJob.cancel()
        telemetry.closeSpans()
    }

    private fun initializeKoin(): KoinApplication {
        return setupKoin()
    }

    private fun isAA2Process(): Boolean {
        return getProcessName().endsWith(AA2_PROCESS)
    }
}
