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

package org.sprind.wallet.dashboardfeature.ui.activities.model

import eu.europa.ec.corelogic.extension.EaaCardData

data class ActivitySectionUi(
    val header: String,
    val items: List<ActivityListItemUi>,
)

data class ActivityListItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val avatar: ActivityAvatarUi,
    val notCompleted: Boolean = false,
)

sealed interface ActivityAvatarUi {
    data class Initials(val text: String, val backgroundColor: String? = null) : ActivityAvatarUi
    /** [fallback] shows when the logo cannot be loaded, e.g. offline or once the issuer removed it. */
    data class Logo(val uri: String, val fallback: Initials) : ActivityAvatarUi
    /** The PID's eagle, which the app ships instead of loading it from the issuer. */
    data object PidEagle : ActivityAvatarUi
}

sealed interface ActivityDetailUi {
    val id: String
    val title: String
    val date: String
    val links: List<ActivityLinkUi>
    val reportUri: String?

    data class Presentation(
        override val id: String,
        override val title: String,
        override val date: String,
        override val links: List<ActivityLinkUi>,
        override val reportUri: String?,
        val outcome: PresentationOutcomeUi,
        val purpose: String?,
        val credentials: List<SharedCredentialUi>,
    ) : ActivityDetailUi {
        val completed: Boolean get() = outcome == PresentationOutcomeUi.COMPLETED
    }

    /** A credential added to or deleted from the wallet. */
    data class CredentialEvent(
        override val id: String,
        override val title: String,
        override val date: String,
        override val links: List<ActivityLinkUi>,
        override val reportUri: String?,
        val deleted: Boolean,
        val isPid: Boolean,
        val card: EaaCardData,
        /** What "view details" shows; available after the credential is deleted too. */
        val credential: SharedCredentialUi,
    ) : ActivityDetailUi
}

enum class PresentationOutcomeUi {
    COMPLETED,
    REJECTED_BY_USER,
    OVERASKING,
    UNKNOWN_SERVICE,
    REJECTED_BY_SERVICE,

    /** Failed or interrupted without anyone rejecting it. */
    NOT_COMPLETED,
}

data class SharedCredentialUi(
    val name: String,
    val issuer: String,
    val avatar: ActivityAvatarUi,
    val claimLabels: List<String>,
)

enum class ActivityLinkKind { SUPPORT, PRIVACY_POLICY, WEBSITE }

data class ActivityLinkUi(
    val kind: ActivityLinkKind,
    val uri: String,
) {
    val displayText: String get() = uri.substringAfter("://").trimEnd('/')
}
