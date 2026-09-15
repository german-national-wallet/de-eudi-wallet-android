import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "org.sprind.wallet.revocationfeature"
}

moduleConfig {
    module = LibraryModule.RevocationFeature
}

dependencies {
    implementation(project(LibraryModule.Core.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.RevocationFeature.classes,
    excludedPackages = KoverExclusionRules.RevocationFeature.packages,
)