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

import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "org.sprind.wallet.pushnotificationsfeature"
}

moduleConfig {
    module = LibraryModule.PushNotificationsFeature
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.PushNotificationsFeature.classes,
    excludedPackages = KoverExclusionRules.PushNotificationsFeature.packages,
)