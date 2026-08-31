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

package org.sprind.wallet.uilogic.extension

import android.net.Uri
import java.util.Locale

/**
 * Schemes that must never be handed to another app, whatever the link they arrive on.
 *
 * `intent` and `android-app` let whoever wrote the link pick the component that is started,
 * `file` and `content` point at storage rather than at a destination, `data` and `javascript`
 * carry a payload, and the rest place a call, send a message or open a store page - none of
 * which a wallet flow ever needs.
 */
private val BLOCKED_SCHEMES = setOf(
    "intent",
    "android-app",
    "package",
    "file",
    "content",
    "data",
    "javascript",
    "tel",
    "callto",
    "sms",
    "smsto",
    "mms",
    "mmsto",
    "mailto",
    "market",
    "geo",
)

/**
 * Whether this link may be opened as the redirect that closes an issuance or presentation flow.
 *
 * Issuers and relying parties redirect to their own app through a custom scheme, so the scheme
 * cannot be known upfront and cannot be allowlisted; the schemes that would escalate the redirect
 * into something other than "open the app that asked" are rejected instead. A link with no scheme
 * at all is rejected too.
 */
fun Uri.isSafeRedirectLink(): Boolean =
    normalizedScheme()?.let { scheme -> scheme !in BLOCKED_SCHEMES } == true

private fun Uri.normalizedScheme(): String? = scheme?.lowercase(Locale.ROOT)