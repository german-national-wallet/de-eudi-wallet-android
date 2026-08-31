package org.sprind.wallet.walletpinfeature.di

import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.RwscaPinHandlerImpl
import org.sprind.wallet.walletpinfeature.interactor.wscd.RwscaRegistrationInteractorImpl
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationInteractor

@Module
@ComponentScan("org.sprind.wallet.walletpinfeature")
class FeatureWalletPinModule

@Single
fun provideWscPinHandler(
    rwscaInteractor: RwscaInteractor,
    rwscaPinSessionHolder: RwscaPinSessionHolder,
): RwscaPinHandler = RwscaPinHandlerImpl(rwscaInteractor, rwscaPinSessionHolder)

@Single
fun provideWscRegistrationInteractor(
    rwscaPinHandler: RwscaPinHandler,
    rwscaStorageController: RwscaStorageController,
): WscaRegistrationInteractor = RwscaRegistrationInteractorImpl(rwscaPinHandler, rwscaStorageController)
