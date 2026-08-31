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

package org.sprind.wallet.businesslogic.controller.storage

import android.app.ActivityManager
import android.content.Context
import eu.europa.ec.businesslogic.controller.storage.PrefsController

/**
 * Increment this compile-time constant when breaking changes occur in local storage
 * (for example, when renaming SharedPreferences keys), or when server data/API
 * assumptions change in a way that requires reset.
 *
 * It is expected that the frequency at which we need to increment this value will
 * decrease over time, so that we never have to increase it again after our public
 * release.
 */
const val CURRENT_APP_DATA_VERSION: AppDataVersion = 11

typealias AppDataVersion = Int

interface StorageController {
    /**
     * This method expects to be called once during application startup, to perform
     * any initialization of our persisted storage (e.g. clear it, if the app data
     * version has changed incompatibly).
     */
    fun onInitializeStorage()

    /**
     * Wipes all wallet app data by delegating to
     * [ActivityManager.clearApplicationUserData]. This clears SharedPreferences
     * (including the encryption-at-rest key), all databases, the noBackupFilesDir,
     * filesDir, cacheDir and codeCacheDir, and closes the app so the user must
     * restart it. Used when the Platform Authentication invariant is violated
     * (wallet data exists but the device has no secure lock screen) and on
     * incompatible app-data-version bumps.
     *
     * Safe to call from any thread: only performs the synchronous binder IPC
     * [ActivityManager.clearApplicationUserData]. UI feedback (e.g. Toast) is the
     * responsibility of the caller when running on the main thread.
     */
    fun wipeAppData()
}

class StorageControllerImpl(val context: Context, private val prefsController: PrefsController) :
    StorageController {
    val appDataCompatibility = AppDataCompatibility(prefsController, CURRENT_APP_DATA_VERSION)

    override fun onInitializeStorage() {
        if (appDataCompatibility.shouldClearAppData()) {
            wipeAppData()
        }
    }

    override fun wipeAppData() {
        // We could instead clear preferences and keystore manually. This approach
        // here is less bug prone, but closes the app and requires the user to
        // restart.
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .clearApplicationUserData()
    }

}

class AppDataCompatibility(
    private val prefsController: PrefsController,
    private val currentAppDataVersion: Int
) {
    init {
        require(currentAppDataVersion > NOT_INITIALIZED_VERSION) {
            "Invalid app data version: $currentAppDataVersion"
        }
    }

    fun shouldClearAppData(): Boolean {
        val v: Int = prefsController.getInt(PREF_KEY, NOT_INITIALIZED_VERSION)
        val appDataVersion: Int? = if (v == NOT_INITIALIZED_VERSION) null else v
        val shouldClear = appDataVersion != null && appDataVersion != currentAppDataVersion
        if (appDataVersion == null) {
            // We only write the current appDataVersion if it was previously not
            // stored:
            // - If the version was previously stored but hasn't changed, then it
            //   already has the right value.
            // - If the version was previously stored but has changed, then we will
            //   attempt to clear app data.
            //   - If app data clearance succeeds, it doesn't matter whether we
            //     updated the stored version, since it's about to be cleared. The
            //     current version will be stored the next time this app runs.
            //   - If app data clearance fails, we DON'T want to have stored the
            //     current version, since app data clearance is still needed.
            prefsController.setInt(PREF_KEY, currentAppDataVersion)
        }
        return shouldClear
    }

    companion object {
        val PREF_KEY = "app_data_version"
        val NOT_INITIALIZED_VERSION: AppDataVersion = 0
    }
}
