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
 * None of these pages is published yet, so every link defaults to [BLANK_PAGE] and the menu entry
 * opens an empty page. Pointing an entry at the real document is a one-line change here or an
 * override in the flavor's `EnvironmentConfigImpl`.
 *
 * @property faqAndSupport the FAQ and customer support page.
 * @property accessibilityDeclaration the declaration of accessibility.
 * @property about the page describing the wallet.
 * @property privacyPolicy the privacy policy.
 * @property termsAndConditions the terms and conditions.
 * @property imprint the imprint.
 */
data class SettingsLinks(
    val faqAndSupport: String = BLANK_PAGE,
    val accessibilityDeclaration: String = BLANK_PAGE,
    val about: String = BLANK_PAGE,
    val privacyPolicy: String = BLANK_PAGE,
    val termsAndConditions: String = BLANK_PAGE,
    val imprint: String = BLANK_PAGE,
) {
    companion object {
        /**
         * Stands in for a page that is still to be published. The web view only loads https pages,
         * so an unpublished link opens blank rather than navigating anywhere.
         */
        const val BLANK_PAGE = "about:blank"
    }
}