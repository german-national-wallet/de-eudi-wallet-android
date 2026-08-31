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

package org.sprind.wallet.cardreaderfeature.domain

/**
 * Small helper around [CardReaderFlowDefinition] used to query neighboring
 * routes without duplicating index arithmetic in tests or orchestration code.
 */
class CardReaderFlowNavigator(
    private val definition: CardReaderFlowDefinition,
) {
    fun nextRoute(currentRoute: CardReaderRoute): CardReaderRoute? {
        val currentIndex = definition.routes.indexOf(currentRoute)
        if (currentIndex < 0 || currentIndex == definition.routes.lastIndex) {
            return null
        }

        return definition.routes[currentIndex + 1]
    }

    fun previousRoute(currentRoute: CardReaderRoute): CardReaderRoute? {
        val currentIndex = definition.routes.indexOf(currentRoute)
        if (currentIndex <= 0) {
            return null
        }

        return definition.routes[currentIndex - 1]
    }

    fun contains(route: CardReaderRoute): Boolean = definition.routes.contains(route)

    fun navigationPolicy(route: CardReaderRoute): CardReaderNavigationPolicy =
        definition.navigationPolicyFor(route)
}
