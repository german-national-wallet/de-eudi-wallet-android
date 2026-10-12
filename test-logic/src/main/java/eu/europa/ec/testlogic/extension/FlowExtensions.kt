/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.testlogic.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

fun <T> T.toFlow(): StateFlow<T> =
    MutableStateFlow(this).asStateFlow()

/**
 * Suspends until this flow emits a value of type [T] and returns it, skipping any other
 * emissions, e.g. `viewModel.effect.awaitFirstOf<Effect.Navigation.SwitchScreen>()`.
 */
suspend inline fun <reified T> Flow<*>.awaitFirstOf(): T =
    filterIsInstance<T>().first()
