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

import org.junit.Assert
import org.junit.Test
import org.sprind.wallet.businesslogic.controller.storage.KEYSTORE_ALIAS_WALLET_INSTANCE_ATTESTATION_KEYS
import org.sprind.wallet.businesslogic.controller.storage.KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
import kotlin.collections.iterator

class KeystoreAliasesTest {

    @Test
    fun `KeyStore aliases are only changed after careful consideration`() {
        val currentToOriginal = mapOf(
            KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS to "wi_wb_auth_keys",
            KEYSTORE_ALIAS_WALLET_INSTANCE_ATTESTATION_KEYS to "wi_wia_keys",
        )
        val unexpectedRenameWarnings = mutableListOf<String>()
        for ((currentValue, originalValue) in currentToOriginal) {
            if (currentValue != originalValue) {
                unexpectedRenameWarnings.add("$originalValue was renamed to $currentValue")
            }
        }
        if (!unexpectedRenameWarnings.isEmpty()) {
            // When a keystore alias changes, an existing key may no longer exist. Please
            // consider whether you need to implement a migration path for existing users
            // who have the key under the old alias. If a migration path is impossible or
            // too much work, consider incrementing StorageController.CURRENT_APP_DATA_VERSION
            // which will cause app data to be cleared on first app launch after update to
            // a version after your change.
            Assert.fail("KeyStore aliases have changed, may need to increment CURRENT_APP_DATA_VERSION: $unexpectedRenameWarnings")
        }
    }
}