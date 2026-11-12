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

package org.sprind.wallet.corelogic.storage

import android.content.Context
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import java.io.File

/**
 * The encrypted Multipaz storage database holding documents, keys and the activity log. Its
 * existence is what [org.sprind.wallet.corelogic.platformauth.PlatformAuthInvariant] takes as
 * "the wallet holds data", so the path is defined once here for both uses.
 */
fun walletStorageDatabaseFile(context: Context): File =
    context.noBackupFilesDir.resolve("${EudiWalletConfig.DEFAULT_DOCUMENT_MANAGER_IDENTIFIER}.bin")
