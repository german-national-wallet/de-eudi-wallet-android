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

import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.MdvmResult
import org.sprind.wallet.businesslogic.controller.storage.StorageController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType

/**
 * Decorator on [MdvmInteractor] that self-locks the wallet whenever an MDVM renewal returns
 * [MdvmErrorType.ACCOUNT_REVOKED], regardless of which consumer triggered the renewal.
 *
 * The push flow (revocation push) and the regular renewal path (app startup, PIN session,
 * document deletion, attestation generation) all flow through [MdvmInteractor], so this
 * decorator is the single interception point for the self-lock policy. The self-lock is
 * destructive: [StorageController.wipeAppData] tears the process down via
 * `ActivityManager.clearApplicationUserData`.
 *
 * See `docs/architecture-documentation/.../03-data-flows/13-wallet-revocation.md`
 * (Wallet Instance Self-locking), AD-16 / AD-17: the WI must self-lock upon an explicit
 * REVOKED error from the MDVM and must never self-lock on transient failures.
 */
internal class RevocationHandlingMdvmInteractor(
    private val storageController: StorageController,
    private val delegate: MdvmInteractor,
) : MdvmInteractor {
    override suspend fun mdvmRegistration(forceRenewal: Boolean): MdvmResult<MdvmRegistration> =
        delegate.mdvmRegistration(forceRenewal).also { result ->
            if (result is ApiResult.Failure && result.error.type == MdvmErrorType.ACCOUNT_REVOKED) {
                storageController.wipeAppData()
            }
        }
}