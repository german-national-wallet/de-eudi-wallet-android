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

package org.sprind.wallet.businesslogic.config

import java.util.Locale

/**
 * A URL that is published in one of the app supported languages.
 *
 * @property en the English page, which is also the fallback.
 * @property de the German page; defaults to the English one when no translation is published.
 */
data class LocalizedUrl(val en: String, val de: String = en) {

    fun forLocale(locale: Locale): String = when (locale.language) {
        Locale.GERMAN.language -> de
        else -> en
    }
}
