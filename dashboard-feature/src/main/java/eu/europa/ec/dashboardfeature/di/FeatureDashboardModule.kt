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

package eu.europa.ec.dashboardfeature.di

import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.controllers.FiltersController
import eu.europa.ec.dashboardfeature.controllers.FiltersControllerImpl
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.dashboardfeature.interactor.DashboardDocumentDetailInteractor
import org.sprind.wallet.dashboardfeature.interactor.DashboardDocumentDetailInteractorImpl

@Module
@ComponentScan("eu.europa.ec.dashboardfeature", "org.sprind.wallet.dashboardfeature")
class FeatureDashboardModule

@Factory
fun provideDashboardInteractor(
    configLogic: ConfigLogic,
    logController: LogController,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    hardwareKeyStorageController: HardwareKeyStorageController,
): DashboardInteractor =
    DashboardInteractorImpl(
        configLogic,
        logController,
        walletCoreDocumentsController,
        resourceProvider,
        hardwareKeyStorageController,
    )

@Factory
fun provideDocumentsInteractor(
    resourceProvider: ResourceProvider,
    documentsController: WalletCoreDocumentsController,
    filtersController: FiltersController,
): DocumentsInteractor =
    DocumentsInteractorImpl(resourceProvider, documentsController, filtersController)

@Factory
fun provideDashboardDocumentDetailInteractorImpl(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    logController: LogController,
    rwscaInteractor: RwscaInteractor,
): DashboardDocumentDetailInteractor = DashboardDocumentDetailInteractorImpl(
    walletCoreDocumentsController,
    resourceProvider,
    logController,
    rwscaInteractor,
)

@Factory
fun provideFiltersController(
    resourceProvider: ResourceProvider,
): FiltersController = FiltersControllerImpl(resourceProvider)
