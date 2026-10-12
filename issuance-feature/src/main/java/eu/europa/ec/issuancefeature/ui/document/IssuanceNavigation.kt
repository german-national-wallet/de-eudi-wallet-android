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

package eu.europa.ec.issuancefeature.ui.document

import android.content.Context
import androidx.navigation.NavController
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.navigation.DashboardScreens

/** Leaves the issuance flow: back to the dashboard if it is on the back stack, otherwise closes the app. */
internal fun NavController.popToDashboardOrFinish(context: Context) {
    try {
        getBackStackEntry(DashboardScreens.Dashboard.screenRoute)
    } catch (_: IllegalArgumentException) {
        context.finish()
        return
    }
    popBackStack(route = DashboardScreens.Dashboard.screenRoute, inclusive = false)
}
