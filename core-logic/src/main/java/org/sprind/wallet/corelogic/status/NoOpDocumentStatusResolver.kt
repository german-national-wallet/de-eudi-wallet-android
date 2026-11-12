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

package org.sprind.wallet.corelogic.status

import eu.europa.ec.eudi.statium.Status
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.statium.DocumentStatusResolver

/**
 * A [DocumentStatusResolver] that never resolves a status.
 */
object NoOpDocumentStatusResolver : DocumentStatusResolver {

    override suspend fun resolveStatus(document: IssuedDocument): Result<Status> =
        Result.failure(
            UnsupportedOperationException(
                "Document status resolution is disabled: the wallet does not validate credential status lists"
            )
        )
}
