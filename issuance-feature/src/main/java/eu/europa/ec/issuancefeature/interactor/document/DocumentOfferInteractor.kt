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

package eu.europa.ec.issuancefeature.interactor.document

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.businesslogic.extension.compareLocaleLanguage
import eu.europa.ec.businesslogic.extension.getLocalizedString
import eu.europa.ec.eudi.openid4vci.CredentialConfiguration
import eu.europa.ec.eudi.openid4vci.TxCodeInputMode
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.util.safeLet
import eu.europa.ec.commonfeature.config.SuccessUIConfig
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.corelogic.controller.ResolveDocumentOfferPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.documentIdentifier
import eu.europa.ec.corelogic.extension.getIssuerLogo
import eu.europa.ec.corelogic.extension.getIssuerName
import eu.europa.ec.corelogic.extension.getName
import eu.europa.ec.corelogic.extension.toEaaCardData
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.PERCENTAGE_25
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.net.URI

sealed class ResolveDocumentOfferInteractorPartialState {
    data class Success(
        val resolvedOffer: ResolvedOffer,
    ) : ResolveDocumentOfferInteractorPartialState()

    data class NoDocument(
        val issuerName: String,
        val issuerLogo: URI?,
    ) : ResolveDocumentOfferInteractorPartialState()

    data class Failure(val errorMessage: String) : ResolveDocumentOfferInteractorPartialState()
}

sealed class IssueDocumentsInteractorPartialState {
    data class Success(
        val documentIds: List<DocumentId>,
    ) : IssueDocumentsInteractorPartialState()

    data class DeferredSuccess(
        val successRoute: String,
    ) : IssueDocumentsInteractorPartialState()

    data class Failure(val errorMessage: String) : IssueDocumentsInteractorPartialState()

    data class UserAuthRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult
    ) : IssueDocumentsInteractorPartialState()

    data class OnRefreshTokenReceived(val refreshToken: String) :
        IssueDocumentsInteractorPartialState()

    data class OnCNonce(val aParameter: Nonce) : IssueDocumentsInteractorPartialState()
}

interface DocumentOfferInteractor {
    fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferInteractorPartialState>

    /**
     * Issues documents for a previously-resolved offer.
     *
     * The [offerUri] must come from a successful [resolveDocumentOffer]
     * call. The wallet-core `Offer` resolved at that time is reused from
     * the controller's offer cache, avoiding a second fetch of the offer.
     *
     * Pass [txCode] = null for the no-txCode path; a non-null value drives
     * the txCode path used by the additional-step screen.
     */
    fun issueDocuments(
        offerUri: String,
        issuerName: String,
        navigation: ConfigNavigation,
        txCode: String? = null,
    ): Flow<IssueDocumentsInteractorPartialState>

    /**
     * Drops the cached resolved offer for [offerUri], if any. Called at
     * terminal states (success/failure/cancel) so a stale offer never
     * outlives the flow that produced it.
     */
    fun clearCachedOffer(offerUri: String)

    val issuanceState: Flow<IssueDocumentsInteractorPartialState>

    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult
    )

    fun resumeOpenId4VciWithAuthorization(uri: String)
}

class DocumentOfferInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
) : DocumentOfferInteractor {

    override val issuanceState: Flow<IssueDocumentsInteractorPartialState> =
        walletCoreDocumentsController.issuanceState.map { response ->
            response.toInteractorState(
                navigation = dashboardNavigation(),
                issuerName = ""
            )
        }

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    private fun dashboardNavigation(): ConfigNavigation = ConfigNavigation(
        navigationType = eu.europa.ec.uilogic.config.NavigationType.PushRoute(
            route = eu.europa.ec.uilogic.navigation.DashboardScreens.Dashboard.screenRoute
        )
    )

    private fun Throwable.toErrorMessage(): String =
        localizedMessage ?: genericErrorMsg

    private fun IssueDocumentsPartialState.toInteractorState(
        navigation: ConfigNavigation,
        issuerName: String
    ): IssueDocumentsInteractorPartialState = when (this) {
        is IssueDocumentsPartialState.Failure ->
            IssueDocumentsInteractorPartialState.Failure(errorMessage = errorMessage)

        is IssueDocumentsPartialState.PartialSuccess ->
            IssueDocumentsInteractorPartialState.Success(documentIds = documentIds)

        is IssueDocumentsPartialState.Success ->
            IssueDocumentsInteractorPartialState.Success(documentIds = documentIds)

        is IssueDocumentsPartialState.UserAuthRequired ->
            IssueDocumentsInteractorPartialState.UserAuthRequired(
                crypto = crypto,
                resultHandler = resultHandler
            )

        is IssueDocumentsPartialState.DeferredSuccess ->
            IssueDocumentsInteractorPartialState.DeferredSuccess(
                successRoute = buildGenericSuccessRouteForDeferred(
                    description = resourceProvider.getString(
                        R.string.issuance_document_offer_deferred_success_description,
                        issuerName
                    ),
                    navigation = navigation
                )
            )

        is IssueDocumentsPartialState.RefreshTokenReceived ->
            IssueDocumentsInteractorPartialState.OnRefreshTokenReceived(refreshToken)
    }

    override fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferInteractorPartialState> =
        flow {
            walletCoreDocumentsController.resolveDocumentOffer(
                offerUri = offerUri
            ).map { response ->
                when (response) {
                    is ResolveDocumentOfferPartialState.Failure -> {
                        ResolveDocumentOfferInteractorPartialState.Failure(errorMessage = response.errorMessage)
                    }

                    is ResolveDocumentOfferPartialState.Success -> {
                        val offerHasNoDocuments = response.offer.offeredDocuments.isEmpty()
                        if (offerHasNoDocuments) {
                            ResolveDocumentOfferInteractorPartialState.NoDocument(
                                issuerName = response.offer.getIssuerName(
                                    resourceProvider.getLocale()
                                ),
                                issuerLogo = response.offer.getIssuerLogo(
                                    resourceProvider.getLocale()
                                ),
                            )
                        } else {

                            val codeMinLength = 4
                            val codeMaxLength = 6

                            safeLet(
                                response.offer.txCodeSpec?.inputMode,
                                response.offer.txCodeSpec?.length
                            ) { inputMode, length ->

                                if ((length !in codeMinLength..codeMaxLength) || inputMode == TxCodeInputMode.TEXT) {
                                    return@map ResolveDocumentOfferInteractorPartialState.Failure(
                                        errorMessage = resourceProvider.getString(
                                            R.string.issuance_document_offer_error_invalid_txcode_format,
                                            codeMinLength,
                                            codeMaxLength
                                        )
                                    )
                                }
                            }

                            val resolvedOffer = response.offer.toResolvedOffer(offerUri)

                            // Cache the wallet-core Offer so the issuance
                            // path can reuse it without re-fetching.
                            walletCoreDocumentsController.cacheOffer(offerUri, response.offer)

                            ResolveDocumentOfferInteractorPartialState.Success(
                                resolvedOffer = resolvedOffer,
                            )
                        }
                    }
                }
            }.collect {
                emit(it)
            }
        }.safeAsync {
            ResolveDocumentOfferInteractorPartialState.Failure(
                errorMessage = it.toErrorMessage()
            )
        }

    override fun issueDocuments(
        offerUri: String,
        issuerName: String,
        navigation: ConfigNavigation,
        txCode: String?,
    ): Flow<IssueDocumentsInteractorPartialState> =
        issueDocumentsByOfferUri(
            offerUri = offerUri,
            issuerName = issuerName,
            navigation = navigation,
            txCode = txCode,
        )

    override fun clearCachedOffer(offerUri: String) {
        walletCoreDocumentsController.clearCachedOffer(offerUri)
    }

    private fun issueDocumentsByOfferUri(
        offerUri: String,
        issuerName: String,
        navigation: ConfigNavigation,
        txCode: String?,
    ): Flow<IssueDocumentsInteractorPartialState> =
        flow {
            walletCoreDocumentsController.issueDocumentsByOfferUri(
                offerUri = offerUri,
                txCode = txCode
            ).map { response ->
                response.toInteractorState(
                    navigation = navigation,
                    issuerName = issuerName
                )
            }.collect {
                emit(it)
            }
        }.safeAsync {
            IssueDocumentsInteractorPartialState.Failure(errorMessage = it.toErrorMessage())
        }

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult
    ) {
        // We do not support this at the moment and it is not needed for the pre-authenticated flow
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        walletCoreDocumentsController.resumeOpenId4VciWithAuthorization(uri)
    }

    // Extracts claim paths with placeholder values from the credential configuration
    // Returns: [("path.to.claim", "—"), ("another.claim", "—")]
    private fun CredentialConfiguration.extractClaimDetails(
        resourceProvider: ResourceProvider
    ): List<Pair<String, String>> {
        return this.credentialMetadata?.claims?.map { claim ->
            val displayName = claim.display.getLocalizedString(
                userLocale = resourceProvider.getLocale(),
                localeExtractor = { it.locale },
                stringExtractor = { it.name },
                fallback = claim.path.toString()
            )
            displayName to "—"
        } ?: emptyList()
    }

    /**
     * Maps the wallet-core [Offer] into the UI-domain [ResolvedOffer] and
     * attaches the [offerUri] so the URI is available downstream as the
     * cache key.
     */
    private fun Offer.toResolvedOffer(offerUri: String): ResolvedOffer = ResolvedOffer(
        offerUri = offerUri,
        issuerName = getIssuerName(resourceProvider.getLocale()),
        issuerLogo = getIssuerLogo(resourceProvider.getLocale()),
        documents = offeredDocuments.map { offeredDocument ->
            ResolvedOfferDocument(
                id = offeredDocument.configurationIdentifier.toString(),
                title = offeredDocument.getName(resourceProvider.getLocale()).orEmpty(),
                details = offeredDocument.configuration.extractClaimDetails(resourceProvider),
                eaaCardData = offeredDocument.toEaaCardData(resourceProvider.getLocale()),
            )
        },
        txCodeLength = txCodeSpec?.length,
    )

    private fun buildGenericSuccessRouteForDeferred(
        description: String,
        navigation: ConfigNavigation
    ): String {
        val successScreenArguments = getDeferredSuccessScreenArguments(description, navigation)
        return generateComposableNavigationLink(
            screen = CommonScreens.Success,
            arguments = successScreenArguments
        )
    }

    private fun getDeferredSuccessScreenArguments(
        description: String,
        navigation: ConfigNavigation
    ): String {
        val (textElementsConfig, imageConfig, buttonText) = Triple(
            first = SuccessUIConfig.TextElementsConfig(
                text = resourceProvider.getString(R.string.issuance_document_offer_deferred_success_text),
                description = description,
                color = ThemeColors.pending
            ),
            second = SuccessUIConfig.ImageConfig(
                type = SuccessUIConfig.ImageConfig.Type.Drawable(
                    icon = AppIcons.InProgress,
                ),
                tint = ThemeColors.primary,
                screenPercentageSize = PERCENTAGE_25,
            ),
            third = resourceProvider.getString(R.string.issuance_document_offer_deferred_success_primary_button_text)
        )

        return generateComposableArguments(
            mapOf(
                SuccessUIConfig.serializedKeyName to uiSerializer.toBase64(
                    SuccessUIConfig(
                        textElementsConfig = textElementsConfig,
                        imageConfig = imageConfig,
                        buttonConfig = listOf(
                            SuccessUIConfig.ButtonConfig(
                                text = buttonText,
                                style = SuccessUIConfig.ButtonConfig.Style.PRIMARY,
                                navigation = navigation
                            )
                        ),
                        onBackScreenToNavigate = navigation,
                    ),
                    SuccessUIConfig.Parser
                ).orEmpty()
            )
        )
    }
}