package eu.europa.ec.uilogic.navigation

interface NavigatableItem

open class Screen(name: String, parameters: String = "") : NavigatableItem {
    val screenRoute: String = name + parameters
    val screenName = name
}

sealed class StartupScreens {
    data object Splash : Screen(name = "SPLASH")
}

sealed class CommonScreens {
    data object Success : Screen(name = "SUCCESS", parameters = "?successConfig={successConfig}")
    data object Biometric : Screen(
        name = "BIOMETRIC",
        parameters = "?biometricConfig={biometricConfig}"
    )
}

sealed class DashboardScreens {
    data object Dashboard : Screen(name = "DASHBOARD")
    data object SignDocument :
        Screen(name = "SIGN_DOCUMENT")

    data object DashboardDocumentDetails : Screen(
        name = "DASHBOARD_DOCUMENT_DETAILS",
        parameters = "?${ParamKey.DOCUMENT_ID}={${ParamKey.DOCUMENT_ID}}"
    ) {
        object ParamKey {
            const val DOCUMENT_ID = "documentId"
        }
    }
}

sealed class PresentationScreens {
    data object PresentationRequest : Screen(
        name = "PRESENTATION_REQUEST",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object PresentationLoading : Screen(name = "PRESENTATION_LOADING")

    data object PresentationNoDocument : Screen(name = "PRESENTATION_NO_DOCUMENT")

    data object PresentationSuccess : Screen(name = "PRESENTATION_SUCCESS")
}

sealed class ProximityScreens {
    data object QR : Screen(
        name = "PROXIMITY_QR",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object Request : Screen(
        name = "PROXIMITY_REQUEST",
        parameters = "?requestUriConfig={requestUriConfig}"
    )

    data object Loading : Screen(name = "PROXIMITY_LOADING")

    data object Success : Screen(name = "PROXIMITY_SUCCESS")
}

sealed class IssuanceScreens {
    data object AddDocument : Screen(
        name = "ISSUANCE_ADD_DOCUMENT",
        parameters = "?flowType={flowType}"
    )

    data object WalletPinSet: Screen(
        name = "ISSUANCE_WALLET_PIN_SET",
        parameters = "?flowType={flowType}" + "&redirectUrl={redirectUrl}"
    )

    data object DocumentDetails : Screen(
        name = "ISSUANCE_DOCUMENT_DETAILS",
        parameters = "?${ParamKey.DOCUMENT_ID}={${ParamKey.DOCUMENT_ID}}"
    ) {
        object ParamKey {
            const val DOCUMENT_ID = "documentId"
        }
    }

    data object DocumentOffer : Screen(
        name = "ISSUANCE_DOCUMENT_OFFER",
        parameters = "?offerConfig={offerConfig}"
    )

    data object DocumentOfferCode : Screen(
        name = "ISSUANCE_DOCUMENT_OFFER_CODE",
        parameters = "?offerCodeConfig={offerCodeConfig}"
    )

    data object DocumentIssuanceSuccess : Screen(
        name = "ISSUANCE_DOCUMENT_SUCCESS",
        parameters = "?issuanceSuccessConfig={issuanceSuccessConfig}"
    )

    data object AdditionalStep: Screen(
        name = "ADDITIONAL_STEP",
        parameters = "?offerCodeConfig={offerCodeConfig}"
    )
}

sealed class CardReaderScreens {
    data object Reader : Screen(
        name = "READER",
        parameters = "?credentialTypes={credentialTypes}"
                + "&flowType={flowType}"
    )

    data object PinScreen : Screen(name = "PIN_SCREEN")

    data object IssuerDetailsScreen : Screen(name = "ISSUER_DETAILS_SCREEN")
}

sealed class RevocationScreens {
    data object Intro : Screen(name = "REVOCATION_INTRO")

    data object SaveCode : Screen(name = "REVOCATION_SAVE_CODE")
}

sealed class ModuleRoute(val route: String) : NavigatableItem {
    data object StartupModule : ModuleRoute("STARTUP_MODULE")
    data object CommonModule : ModuleRoute("COMMON_MODULE")
    data object DashboardModule : ModuleRoute("DASHBOARD_MODULE")
    data object PresentationModule : ModuleRoute("PRESENTATION_MODULE")
    data object ProximityModule : ModuleRoute("PROXIMITY_MODULE")
    data object IssuanceModule : ModuleRoute("ISSUANCE_MODULE")
    data object CardReadModule : ModuleRoute("CARD_READ_MODULE")
    data object RevocationModule : ModuleRoute("REVOCATION_MODULE")
}