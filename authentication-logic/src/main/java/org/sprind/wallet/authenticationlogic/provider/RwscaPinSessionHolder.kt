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

package org.sprind.wallet.authenticationlogic.provider

import org.sprind.wallet.authenticationlogic.model.RwscaPinSession

/**
 * In-memory holder for a [RwscaPinSession].
 *
 * [RwscaSecureArea] reads from this holder when signing. The feature layer writes to it
 * after the user enters their PIN and a PIN session has been obtained from the rWSCA server.
 * When the session expires or is no longer needed, [clear] should be called.
 */
interface RwscaPinSessionHolder {
    /** Returns the held PIN session, or null if none has been set. */
    fun get(): RwscaPinSession?

    /** Sets a fresh PIN session. Replaces any previously held session. */
    fun set(pinSession: RwscaPinSession)

    /** Clears the held PIN session. */
    fun clear()
}

class RwscaPinSessionHolderImpl : RwscaPinSessionHolder {
    @Volatile
    private var pinSession: RwscaPinSession? = null

    override fun get(): RwscaPinSession? = pinSession

    override fun set(pinSession: RwscaPinSession) {
        this.pinSession = pinSession
    }

    override fun clear() {
        pinSession = null
    }
}
