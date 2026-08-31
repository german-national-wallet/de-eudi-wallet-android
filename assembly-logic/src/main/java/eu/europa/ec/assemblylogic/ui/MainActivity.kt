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

package eu.europa.ec.assemblylogic.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import eu.europa.ec.businesslogic.BuildConfig
import org.sprind.wallet.assemblylogic.controller.AppBlockingController
import org.sprind.wallet.assemblylogic.controller.AppBlockingState
import eu.europa.ec.commonfeature.router.featureCommonGraph
import eu.europa.ec.dashboardfeature.router.featureDashboardGraph
import eu.europa.ec.issuancefeature.router.featureIssuanceGraph
import eu.europa.ec.presentationfeature.router.presentationGraph
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.startupfeature.router.featureStartupGraph
import org.sprind.wallet.uilogic.component.AppBlockingScreen
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.container.EudiComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.sprind.wallet.businesslogic.controller.storage.StorageController
import org.sprind.wallet.cardreaderfeature.router.featureCardReaderGraph
import org.sprind.wallet.corelogic.platformauth.PlatformAuthInvariant
import org.sprind.wallet.revocationfeature.router.featureRevocationGraph
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import org.sprind.wallet.pushnotificationsfeature.service.WalletFirebaseMessagingService

class MainActivity : EudiComponentActivity() {

    private val appBlockingController: AppBlockingController by inject()
    private val platformAuthInvariant: PlatformAuthInvariant by inject()
    private val storageController: StorageController by inject()
    private val prefsController: PrefsController by inject()
    private val pushNotificationsInteractor: PushNotificationsInteractor by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalletContent()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Suppress("KotlinConstantCondition")
    @Composable
    private fun WalletContent() {
        var blockingState by remember { mutableStateOf<AppBlockingState?>(null) }
        val coroutineScope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        val notificationPermissionState = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (BuildConfig.ENABLE_PNS_PERMISSION_POP_UP) {
            LaunchedEffect(Unit) {
                if (!notificationPermissionState.status.isGranted) {
                    notificationPermissionState.launchPermissionRequest()
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    evaluateBlockingState { blockingState = it }
                    refreshFeatureFlagsInBackground(coroutineScope) { blockingState = it }
                    retryPushNotificationRegistrationIfNeeded(coroutineScope)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Content(intent) {
                featureStartupGraph(it)
                featureRevocationGraph(it)
                featureCommonGraph(it)
                featureDashboardGraph(it)
                presentationGraph(it)
                featureIssuanceGraph(it)
                featureCardReaderGraph(it)
            }

            when (blockingState) {
                AppBlockingState.PlatformAuthentication -> {
                    AppBlockingScreen(
                        title = stringResource(R.string.app_onboarding_pa_not_set_title),
                        description = stringResource(R.string.app_onboarding_pa_not_set_paragraph),
                        buttonTitle = stringResource(R.string.app_onboarding_pa_not_set_prim_button),
                        iconData = AppIcons.NoPAOnDevice,
                        onButtonClick = { openSecuritySettings(context) }
                    )
                }

                AppBlockingState.MinimumAppVersion -> {
                    AppBlockingScreen(
                        title = stringResource(R.string.app_update_required_title),
                        description = stringResource(R.string.app_update_required_description),
                        buttonTitle = stringResource(R.string.app_update_required_button),
                        onButtonClick = { openAppUpdate(context) }
                    )
                }

                null -> Unit
            }
        }
    }

    private fun openSecuritySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun openAppUpdate(context: Context) {
        val packageName = context.packageName
        val playStoreIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$packageName".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(playStoreIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(browserIntent)
        }
    }

    private fun evaluateBlockingState(onState: (AppBlockingState?) -> Unit) {
        if (platformAuthInvariant.shouldWipe()) {
            Toast.makeText(this, R.string.clear_app_data_success, Toast.LENGTH_LONG).show()
            storageController.wipeAppData()
        }
        onState(appBlockingController.blockingState())
    }

    private fun refreshFeatureFlagsInBackground(
        scope: CoroutineScope,
        onState: (AppBlockingState?) -> Unit,
    ) {
        scope.launch {
            appBlockingController.refreshFeatureFlagsIfNeeded()
            evaluateBlockingState(onState)
        }
    }

    private fun retryPushNotificationRegistrationIfNeeded(scope: CoroutineScope) {
        val fcmToken = prefsController.getString(
            WalletFirebaseMessagingService.FCM_REGISTRATION_ID_KEY, "",
        )
        if (fcmToken.isEmpty()) return
        val isRegistered = prefsController.getBool(
            WalletFirebaseMessagingService.FCM_TOKEN_REGISTERED_KEY, false,
        )
        if (isRegistered) return
        scope.launch {
            val result = pushNotificationsInteractor.registerFcmToken(fcmToken)
            if (result is ApiResult.Success) {
                prefsController.setBool(
                    WalletFirebaseMessagingService.FCM_TOKEN_REGISTERED_KEY, true,
                )
            }
        }
    }
}
