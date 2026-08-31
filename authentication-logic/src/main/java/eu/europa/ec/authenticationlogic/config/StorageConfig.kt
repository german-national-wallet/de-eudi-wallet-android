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

package eu.europa.ec.authenticationlogic.config

import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.authenticationlogic.provider.HardwareKeyStorageProvider
import org.sprind.wallet.authenticationlogic.provider.MdvmRegistrationStorageProvider
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.authenticationlogic.provider.WalletPinUnBlockTimeStorageProvider

interface StorageConfig {
    // EUDI-removed
    /*
    val pinStorageProvider: PinStorageProvider
    */
    val biometryStorageProvider: BiometryStorageProvider
    val hardwareKeyStorageProvider: HardwareKeyStorageProvider
    val walletRegistrationStorageProvider: WalletRegistrationStorageProvider
    val walletPinUnBlockTimeStorageProvider: WalletPinUnBlockTimeStorageProvider
    val mdvmRegistrationStorageProvider: MdvmRegistrationStorageProvider
}

internal class StorageConfigImpl(
    // EUDI-removed
    /*
    private val pinImpl: PinStorageProvider,
    */
    private val biometryImpl: BiometryStorageProvider,
    private val hardwareKeyImpl: HardwareKeyStorageProvider,
    private val walletRegistrationImpl: WalletRegistrationStorageProvider,
    private val walletPinBlockTimeImpl: WalletPinUnBlockTimeStorageProvider,
    private val mdvmRegistrationImpl: MdvmRegistrationStorageProvider,
) : StorageConfig {
    // EUDI-removed
    /*
    override val pinStorageProvider: PinStorageProvider
        get() = pinImpl
     */
    override val biometryStorageProvider: BiometryStorageProvider
        get() = biometryImpl
    override val hardwareKeyStorageProvider: HardwareKeyStorageProvider
        get() = hardwareKeyImpl
    override val walletRegistrationStorageProvider: WalletRegistrationStorageProvider
        get() = walletRegistrationImpl
    override val walletPinUnBlockTimeStorageProvider: WalletPinUnBlockTimeStorageProvider
        get() = walletPinBlockTimeImpl
    override val mdvmRegistrationStorageProvider: MdvmRegistrationStorageProvider
        get() = mdvmRegistrationImpl
}