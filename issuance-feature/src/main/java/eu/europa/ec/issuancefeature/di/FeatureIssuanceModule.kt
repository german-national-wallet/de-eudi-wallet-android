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

package eu.europa.ec.issuancefeature.di


import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationController
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractorImpl
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractorImpl
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractorImpl
import eu.europa.ec.issuancefeature.interactor.document.DocumentIssuanceSuccessInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentIssuanceSuccessInteractorImpl
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor

@Module
@ComponentScan("eu.europa.ec.issuancefeature")
class FeatureIssuanceModule

@Single
fun provideIssuanceCoroutineScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Single
fun provideAddDocumentInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    coroutineScope: CoroutineScope,
    authorizationHandler: AusweisSdkAuthorizationHandler,
): AddDocumentInteractor =
    AddDocumentInteractorImpl(
        walletCoreDocumentsController = walletCoreDocumentsController,
        resourceProvider = resourceProvider,
        interactorScope = coroutineScope,
        ausweisSdkAuthorizationHandler = authorizationHandler
    )

@Factory
fun provideDocumentDetailsInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    rwscaInteractor: RwscaInteractor,
): DocumentDetailsInteractor =
    DocumentDetailsInteractorImpl(
        walletCoreDocumentsController = walletCoreDocumentsController,
        resourceProvider = resourceProvider,
        rwscaInteractor = rwscaInteractor,
    )

@Factory
fun provideDocumentIssuanceSuccessInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
): DocumentIssuanceSuccessInteractor = DocumentIssuanceSuccessInteractorImpl(
    walletCoreDocumentsController,
    resourceProvider,
)

@Factory
fun provideDeviceAuthenticationInteractor(
    deviceAuthenticationController: DeviceAuthenticationController,
): DeviceAuthenticationInteractor =
    DeviceAuthenticationInteractorImpl(deviceAuthenticationController)


@Factory
fun provideDocumentOfferInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    uiSerializer: UiSerializer,
): DocumentOfferInteractor =
    DocumentOfferInteractorImpl(
        walletCoreDocumentsController = walletCoreDocumentsController,
        resourceProvider = resourceProvider,
        uiSerializer = uiSerializer,
        deviceAuthenticationInteractor = deviceAuthenticationInteractor,
    )
