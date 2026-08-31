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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmController
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.MdvmResult
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

interface MdvmInteractor {
    suspend fun mdvmRegistration(forceRenewal: Boolean = false): MdvmResult<MdvmRegistration>
}

class MdvmInteractorImpl(
    private val mdvmController: MdvmController,
    private val mdvmRegistrationStorageController: MdvmRegistrationStorageController,
    private val clock: Clock,
) : MdvmInteractor {
    private val registrationLock = Mutex()

    /**
     * Serialised because two callers racing here would both register, and the second registration
     * overwrites the first one's key under the shared keystore alias, leaving the first holding a
     * token the backend no longer associates with that key.
     */
    override suspend fun mdvmRegistration(forceRenewal: Boolean): MdvmResult<MdvmRegistration> =
        registrationLock.withLock { registerRenewOrReuse(forceRenewal) }

    private suspend fun registerRenewOrReuse(forceRenewal: Boolean): MdvmResult<MdvmRegistration> {
        val existingRegistration = mdvmRegistrationStorageController.getMdvmRegistration()
        val mdvmApiResult = if (existingRegistration == null) {
            // initial registration (forceRenewal has no target to renew)
            mdvmController.register()
        } else if (!forceRenewal) {
            val latestTimeAtWhichToReuseExistingRegistration: Instant = existingRegistration.expiry
                // Buffer to allow for clock skew and time until we actually use the registration
                .minus(DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION)
            val now = clock.now()
            val isStillUsable = now <= latestTimeAtWhichToReuseExistingRegistration
            if (isStillUsable) {
                // reuse previous registration
                return ApiResult.Success(existingRegistration)
            }
            // renewal of previous registration
            mdvmController.renewal(
                mdvmWiId = existingRegistration.mdvm_wi_id,
                wiMdvmAuthKeysAlias = existingRegistration.wi_mdvm_auth_keys_alias,
            )
        } else {
            // forceRenewal == true: bypass the cache and always hit the backend so that
            // a revocation push can observe ACCOUNT_REVOKED instead of reusing a still-valid token
            mdvmController.renewal(
                mdvmWiId = existingRegistration.mdvm_wi_id,
                wiMdvmAuthKeysAlias = existingRegistration.wi_mdvm_auth_keys_alias,
            )
        }
        return when (mdvmApiResult) {
            is ApiResult.Success -> {
                mdvmRegistrationStorageController.saveMdvmRegistration(mdvmApiResult.response)
                ApiResult.Success(mdvmApiResult.response)
            }

            is ApiResult.Failure ->
                ApiResult.Failure(mdvmApiResult.error)
        }
    }

    companion object {
        // TODO(WD-2488) turn this into a Feature Flag
        val DEFAULT_MIN_REMAINING_VALIDITY_TO_REUSE_REGISTRATION: Duration = 10.minutes
    }
}
