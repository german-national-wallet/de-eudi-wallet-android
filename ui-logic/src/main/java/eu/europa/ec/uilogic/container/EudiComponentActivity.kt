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

package eu.europa.ec.uilogic.container

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.helper.DeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.corelogic.util.CoreActions
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.businesslogic.util.SpanAttributes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

open class EudiComponentActivity : FragmentActivity() {

    private val routerHost: RouterHost by inject()
    private val telemetry: Telemetry by inject()

    private var flowStarted: Boolean = false

    internal var pendingDeepLink: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    internal fun cacheDeepLink(intent: Intent?) {
        pendingDeepLink = intent?.data
    }

    @OptIn(KoinExperimentalAPI::class)
    @Composable
    protected fun Content(
        intent: Intent?,
        builder: NavGraphBuilder.(NavController) -> Unit
    ) {
        ThemeManager.instance.Theme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                KoinAndroidContext {
                    routerHost.StartFlow {
                        builder(it)
                    }
                    flowStarted = true
                    handleDeepLink(intent, coldBoot = true)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (flowStarted) {
            handleDeepLink(intent)
        } else {
            runPendingDeepLink(intent)
        }
    }

    private fun runPendingDeepLink(intent: Intent?) {
        lifecycleScope.launch {
            var count = 0
            while (!flowStarted && count <= 10) {
                count++
                delay(500)
            }
            if (count <= 10) {
                handleDeepLink(intent)
            }
        }
    }

    /**
     * Reacts to a link another app sent in, whether the wallet was already running or not.
     *
     * The activity is exported, so the link is whatever the caller chose to send. Only the types
     * the wallet owns a scheme for are acted upon; anything else is dropped here, before it can be
     * cached, navigated for or handed on to another app.
     */
    private fun handleDeepLink(intent: Intent?, coldBoot: Boolean = false) {
        val action = hasDeepLink(intent?.data) ?: return
        if (!action.type.isAcceptedFromOtherApps) {
            telemetry.logEvent(
                "deeplink.rejected",
                SpanAttributes.of("scheme" to action.link.scheme.orEmpty())
            )
            setIntent(Intent())
            return
        }
        if (action.type == DeepLinkType.ISSUANCE && !coldBoot) {
            handleDeepLinkAction(
                routerHost.getNavController(),
                action.link
            )
        } else if (action.type == DeepLinkType.ISSUANCE && coldBoot) {
            cacheDeepLink(intent)
            telemetry.logEvent(
                "issuance.resume.cold_boot_drop",
                SpanAttributes.of(
                    "uri" to action.link.toString().take(256).takeWhile { it != '?' }
                )
            )
            routerHost.popToDashboardScreen()
            routerHost.getNavController().currentBackStackEntry
                ?.savedStateHandle?.set(
                    CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY,
                    action.link.toString()
                )
        } else if (
            action.type == DeepLinkType.CREDENTIAL_OFFER
            && !routerHost.userIsLoggedInWithDocuments()
            && routerHost.userIsLoggedInWithNoDocuments()
        ) {
            cacheDeepLink(intent)
            routerHost.popToIssuanceOnboardingScreen()
        } else if (action.type == DeepLinkType.OPENID4VP
            && routerHost.userIsLoggedInWithDocuments()
            && (routerHost.isScreenForeground(IssuanceScreens.AddDocument)
                    || routerHost.isScreenForeground(IssuanceScreens.DocumentOffer))
        ) {
            handleDeepLinkAction(
                routerHost.getNavController(),
                DeepLinkAction(action.link, DeepLinkType.DYNAMIC_PRESENTATION)
            )
        } else if (action.type != DeepLinkType.ISSUANCE) {
            cacheDeepLink(intent)
            if (routerHost.userIsLoggedInWithDocuments()) {
                routerHost.popToDashboardScreen()
            }
        }
        setIntent(Intent())
    }
}