import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

android {
    namespace = "org.sprind.wallet.card.reader.feature"
}

moduleConfig {
    module = LibraryModule.CardReaderFeature
}

dependencies {
    implementation(project(LibraryModule.AuthenticationLogic.path))
    implementation(project(LibraryModule.Core.path))
    implementation(project(LibraryModule.NetworkLogic.path))
    implementation(libs.ausweiss.sdk)
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.IssuanceFeature.classes,
    excludedPackages = KoverExclusionRules.IssuanceFeature.packages,
)