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

import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.MdvmResult
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.corelogic.revocation.WalletRevocationHandler
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType

/**
 * Decorator on [MdvmInteractor] that self-locks the wallet whenever an MDVM renewal returns
 * [MdvmErrorType.ACCOUNT_REVOKED], regardless of which consumer triggered the renewal.
 *
 * The push flow (revocation push) and the regular renewal path (app startup, PIN session,
 * document deletion, attestation generation) all flow through [MdvmInteractor], so this
 * decorator is the single interception point for the self-lock policy. The lock persists a
 * flag, notifies the user, and selectively wipes wallet data ([WalletRevocationHandler]); the
 * process stays alive and a blocking screen gates the app until the user resets.
 *
 * A wallet that is already locked never reaches the backend again: every call short-circuits
 * to a synthetic ACCOUNT_REVOKED failure. The nav graph keeps running underneath the blocking
 * overlay, so this guard - not the overlay - is what stops a wiped wallet from silently
 * re-registering as a fresh instance.
 *
 * See `docs/architecture-documentation/.../03-data-flows/13-wallet-revocation.md`
 * (Wallet Instance Self-locking), AD-16 / AD-17: the WI must self-lock upon an explicit
 * REVOKED error from the MDVM and must never self-lock on transient failures.
 */
internal class RevocationHandlingMdvmInteractor(
    private val revocationStore: WalletRevocationStore,
    private val revocationHandler: () -> WalletRevocationHandler,
    private val delegate: MdvmInteractor,
) : MdvmInteractor {
    override suspend fun mdvmRegistration(forceRenewal: Boolean): MdvmResult<MdvmRegistration> {
        if (revocationStore.isRevoked()) {
            return ApiResult.Failure(revokedFailure())
        }
        return delegate.mdvmRegistration(forceRenewal).also { result ->
            if (result is ApiResult.Failure && result.error.type == MdvmErrorType.ACCOUNT_REVOKED) {
                revocationHandler().lockWallet()
            }
        }
    }

    private fun revokedFailure(): MdvmError = MdvmError(
        type = MdvmErrorType.ACCOUNT_REVOKED,
        serverResponse = MdvmErrorResponse(code = MdvmErrorType.ACCOUNT_REVOKED.code),
    )
}