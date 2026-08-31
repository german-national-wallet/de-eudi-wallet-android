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

import okio.ByteString
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController.StartPinSessionMode
import org.sprind.wallet.authenticationlogic.crypto.PinKeyFactory
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.RwscaError.FromMdvm
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaRegistrations
import org.sprind.wallet.authenticationlogic.model.RwscaResult
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.networklogic.common.model.map
import org.sprind.wallet.networklogic.common.model.mapError
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import java.security.KeyPair

interface RwscaInteractor {
    suspend fun rwscaRegistration(): RwscaResult<RwscaRegistration>
    suspend fun rwscaPinSession(pin: UserPin): RwscaResult<RwscaPinSession>
    suspend fun getRegistrations(): RwscaResult<RwscaRegistrations>

    /**
     * Attempts to delete the rWSCA account of which a local record exists in
     * the [RwscaStorageController].
     *
     * The return value allows the caller to check whether the call succeeded, and
     * inspect the failure details if it did not.
     *
     * - Returns Success if there was a local record of a registered rWSCA account
     *   and that server-side account and its local record has been successfully deleted.
     * - Returns ApiResult.Failure holding a RwscaError.FromRwsca instance of type
     *   [RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY] if there was no local record of
     *   a registered rWSCA account.
     * - Returns Failure with a different error if there is a local record of an
     *   rWSCA account but we encountered an error while trying to delete it from the
     *   backend. In this case, the local record will not have been deleted.
     *   TODO(WD-2773): Decide on and implement handling of the error conditions.
     */
    suspend fun deleteAccount(): RwscaResult<Unit>
}

class RwscaInteractorImpl(
    private val mdvmInteractor: MdvmInteractor,
    private val rwscaController: RwscaController,
    private val rwscaStorageController: RwscaStorageController,
    private val pinKeyFactory: PinKeyFactory,
) : RwscaInteractor {

    /*
     * Helper function to obtain both an MdvmRegistration and a RwscaRegistration, to avoid
     * the following race condition:
     *
     * If rwscaPinSession() were to call and mdvmRegistrationInteractor.mdvmRegistration() and
     * rwscaRegistration() separately, in a race condition it could happen that between those
     * two calls the MdvmRegistration expires, so rwscaRegistration() would fetch a new
     * MdvmRegistration internally rather than reuse the one loaded first. Even when that race
     * condition doesn't happen, we'd be relying on storing and re-loading the MdvmRegistration
     * between the two calls. That seems like a code smell.
     *
     * Through the mdvmAndRwscaRegistration() helper function, rwscaRegistration() and
     * rwscaPinSession() become much cleaner, and we avoid this race condition at the same time.
     */
    private suspend fun mdvmAndRwscaRegistration(): RwscaResult<RwscaRegistrations> {
        val rwscaResultFromMdvm = mdvmInteractor.mdvmRegistration().mapError(::FromMdvm)
        val mdvmRegistration: MdvmRegistration = when (rwscaResultFromMdvm) {
            is ApiResult.Failure -> return rwscaResultFromMdvm
            is ApiResult.Success -> rwscaResultFromMdvm.response
        }

        // We're building rwscaRegistration() on top of this helper, so unlike mdvmRegistration()
        // we can't call it here but have to do the following "by hand".
        val existingRwscaRegistration = rwscaStorageController.getRwscaRegistration()
        if (existingRwscaRegistration != null) {
            return ApiResult.Success(RwscaRegistrations(mdvmRegistration, existingRwscaRegistration))
        }
        return when (val apiResult = rwscaController.register(mdvmRegistration)) {
            is ApiResult.Failure ->
                ApiResult.Failure(apiResult.error)
            is ApiResult.Success -> {
                rwscaStorageController.saveRwscaRegistration(apiResult.response)
                ApiResult.Success(
                    RwscaRegistrations(mdvmRegistration, apiResult.response)
                )
            }
        }
    }

    override suspend fun rwscaRegistration(): RwscaResult<RwscaRegistration> {
        val existingRegistration = rwscaStorageController.getRwscaRegistration()
        return if (existingRegistration != null) {
            ApiResult.Success(existingRegistration)
        } else {
            // Obtaining a rwscaRegistration would require us to obtain an MdvmRegistration first
            // anyway, so we may as well obtain both and then only return the RwscaRegistration.
            mdvmAndRwscaRegistration().map { it.rwscaRegistration }
        }
    }

    override suspend fun rwscaPinSession(pin: UserPin): RwscaResult<RwscaPinSession> {
        val isPinMarkedAsInitialized = rwscaStorageController.isPinInitialized()
        val mode = if (isPinMarkedAsInitialized) {
            StartPinSessionMode.SubsequentPinSession
        } else {
            StartPinSessionMode.InitialPinSession
        }
        // rwscaController.startPinSession() requires both an MdvmRegistration and a
        // RwscaRegistration, so we obtain both at once
        val registrations = when (val result = mdvmAndRwscaRegistration()) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.response
        }
        val withPinKeys = object : RwscaController.WithPinKeys {
            override suspend fun <T> execute(block: suspend (wiRwscaPinKeys: KeyPair) -> T): T {
                var pinSalt: ByteString? = rwscaStorageController.getPinSalt() ?: run {
                    val newPinSalt = pinKeyFactory.generatePinSalt()
                    rwscaStorageController.savePinSalt(newPinSalt)
                    newPinSalt
                }
                val wiRwscaPinKeys = pinKeyFactory.generatePinKeys(pin, pinSalt!!)
                pinSalt = null
                return block(wiRwscaPinKeys)
            }
        }
        val apiResult = rwscaController.startPinSession(
            mdvmRegistration = registrations.mdvmRegistration,
            rwscaRegistration = registrations.rwscaRegistration,
            withPinKeys = withPinKeys,
            mode = mode,
        )
        if (apiResult is ApiResult.Success && !isPinMarkedAsInitialized) {
            rwscaStorageController.savePinInitialized()
        }
        return apiResult
    }

    override suspend fun getRegistrations(): RwscaResult<RwscaRegistrations> = mdvmAndRwscaRegistration()

    override suspend fun deleteAccount(): RwscaResult<Unit> {
        val rwscaRegistration = rwscaStorageController.getRwscaRegistration()
        if (rwscaRegistration == null) {
            return ApiResult.Failure(RwscaError.FromRwsca(RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY, serverResponse = null))
        }
        val rwscaResultFromMdvm = mdvmInteractor.mdvmRegistration().mapError(::FromMdvm)
        val mdvmRegistration: MdvmRegistration = when (rwscaResultFromMdvm) {
            is ApiResult.Failure -> return rwscaResultFromMdvm
            is ApiResult.Success -> rwscaResultFromMdvm.response
        }
        val result = rwscaController.deleteAccount(mdvmRegistration, rwscaRegistration)
        if (result is ApiResult.Success) {
            rwscaStorageController.clearRwscaRegistration()
            rwscaStorageController.clearPinSalt()
            rwscaStorageController.clearPinInitialized()
        }
        return result
    }
}
