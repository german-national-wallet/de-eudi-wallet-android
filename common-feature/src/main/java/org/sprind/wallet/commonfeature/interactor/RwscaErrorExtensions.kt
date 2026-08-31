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

import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

/**
 * `true` when the RWSCA reports that the account is already absent locally
 * ([RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY]).
 *
 * Account deletion treats this as a non-fatal, idempotent outcome: the account is already gone, so
 * a delete/retry can safely continue with the remaining local cleanup instead of surfacing an error.
 */
fun RwscaError.isAccountNotFoundLocally(): Boolean =
    this is RwscaError.FromRwsca && type == RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY
