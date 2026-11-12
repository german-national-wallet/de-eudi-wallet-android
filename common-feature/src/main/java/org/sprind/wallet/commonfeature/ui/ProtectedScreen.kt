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

package org.sprind.wallet.commonfeature.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.commonfeature.interactor.IdentificationOnlyAuthenticationInteractor
import eu.europa.ec.uilogic.component.content.ContentScreen
import org.koin.compose.koinInject
import org.sprind.wallet.businesslogic.controller.session.SessionManager
import org.sprind.wallet.uilogic.component.AppBackgroundObserver

@Composable
fun ProtectedScreen(
    onDismiss: () -> Unit,
    sessionManager: SessionManager = koinInject(),
    identificationOnlyAuthenticationInteractor: IdentificationOnlyAuthenticationInteractor = koinInject(),
    content: @Composable () -> Unit
) {
    val context = LocalActivity.current ?: run {
        LaunchedEffect(true) { onDismiss() }
        return
    }
    val isUnlocked by sessionManager.isUnlocked.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    AppBackgroundObserver { sessionManager.lock() }

    LaunchedEffect(lifecycleOwner, isUnlocked) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!isUnlocked) {
                identificationOnlyAuthenticationInteractor.getBiometricsAvailability { availability ->
                    if (availability is BiometricsAvailability.CanAuthenticate) {
                        identificationOnlyAuthenticationInteractor.authenticateWithBiometrics(
                            context = context,
                            notifyOnAuthenticationFailure = false
                        ) { result ->
                            when (result) {
                                BiometricsAuthenticate.Success -> sessionManager.unlock()
                                BiometricsAuthenticate.Cancelled,
                                is BiometricsAuthenticate.Failed -> onDismiss()
                            }
                        }
                    } else {
                        onDismiss()
                    }
                }
            }
        }
    }

    if (isUnlocked) {
        content()
    } else {
        ContentScreen(isLoading = true, hideTopBar = true) {}
    }
}
