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

package org.sprind.wallet.commonfeature.navigation

import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.uilogic.navigation.CardReaderScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.serialization.json.Json

/**
 * The route of [CardReaderScreens.Reader] that reads the eID card to issue [credentialTypes].
 * Shared by every entry point into the eID card flow (the overview, the missing Digital ID
 * screen), so they all hand the reader the same arguments.
 */
fun cardReaderIssuanceRoute(credentialTypes: Set<CredentialConfigurationIdentifier>): String =
    generateComposableNavigationLink(
        screen = CardReaderScreens.Reader,
        arguments = generateComposableArguments(
            mapOf(
                "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(
                    IssuanceFlowUiConfig.NO_DOCUMENT
                ),
                "credentialTypes" to Json.encodeToString(credentialTypes),
            )
        ),
    )
