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

/**
 * The web pages the settings menu opens in the app's own web view.
 *
 * Every link defaults to [BLANK_PAGE]; the backend config points the published documents at their
 * real pages (see `WalletBackendEnvironmentConfig.settingsLinks`), the rest open empty until they
 * are published. The legal documents are published per language, hence [LocalizedUrl].
 *
 * @property faqAndSupport the FAQ and customer support page.
 * @property accessibilityDeclaration the declaration of accessibility.
 * @property about the page describing the wallet.
 * @property privacyPolicy the privacy policy.
 * @property pidPrivacyPolicy the privacy policy shown during PID enrollment, not from the
 *   settings menu.
 * @property termsAndConditions the terms and conditions.
 * @property imprint the imprint.
 */
data class SettingsLinks(
    val faqAndSupport: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val accessibilityDeclaration: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val about: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val privacyPolicy: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val pidPrivacyPolicy: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val termsAndConditions: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
    val imprint: LocalizedUrl = LocalizedUrl(en = BLANK_PAGE, de = BLANK_PAGE),
) {
    companion object {
        /**
         * Stands in for a page that is still to be published. The web view only loads https pages,
         * so an unpublished link opens blank rather than navigating anywhere.
         */
        const val BLANK_PAGE = "about:blank"
    }
}
