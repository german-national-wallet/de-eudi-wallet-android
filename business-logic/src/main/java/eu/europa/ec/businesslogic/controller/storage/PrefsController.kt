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

package eu.europa.ec.businesslogic.controller.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.decodeFromPemBase64String
import eu.europa.ec.resourceslogic.provider.ResourceProvider

interface PrefsController {

    /**
     * Defines if [SharedPreferences] contains a value for given [key]. This function will only
     * identify if a key exists in storage and will not check if corresponding value is valid.
     *
     * @param key The name of the preference to check.
     *
     * @return `true` if preferences contain given key. `false` otherwise.
     */
    fun contains(key: String): Boolean

    /**
     * Removes given preference key from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    fun clear(key: String)

    /**
     * Removes all keys from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    fun clear()

    /**
     * Removes all keys from shared preferences and waits until the change is persisted.
     *
     * Use this only when the next operation depends on the data being cleared immediately, for
     * example before writing a replacement value and restarting the app. Prefer [clear] for normal
     * asynchronous preference updates.
     */
    fun clearSync() {
        clear()
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setString(key: String, value: String)

    /**
     * Assigns given [value] to shared preferences and waits until the change is persisted.
     *
     * Use this only when the value must survive an immediate process restart or data cleanup.
     * Prefer [setString] for normal asynchronous preference updates.
     */
    fun setStringSync(key: String, value: String) {
        setString(key, value)
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setLong(
        key: String, value: Long
    )

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setBool(key: String, value: Boolean)

    /**
     * Retrieves a string value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    fun getString(key: String, defaultValue: String): String

    /**
     * Retrieves a long value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    fun getLong(key: String, defaultValue: Long): Long

    /**
     * Retrieves a boolean value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    fun getBool(key: String, defaultValue: Boolean): Boolean
    fun setInt(key: String, value: Int)
    fun getInt(key: String, defaultValue: Int): Int

    /**
     * Sets a listener on a specific key. If the key's value changes, the consumer will
     * be informed about the new value.
     *
     * @param key         the key to listen for changes
     * @param onNewValue  a function to be called if a new value is saved for the observed
     * key, might return null if the value is deleted or its type is not supported
     */
    fun <T> setOnKeyChangedListener(key: String, defaultValue: T, onNewValue: (T?) -> Unit)
}

/**
 * Controller used to manipulate data stored in device [SharedPreferences]. Data are encrypted so
 * you are strongly advised to used this controller to set or get values.
 */
class PrefsControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val logController: LogController
) : PrefsController {

    private val logTag = javaClass.simpleName

    /**
     * Master key used to encrypt/decrypt shared preferences.
     */
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(resourceProvider.provideContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Pref key scheme used to initialize [EncryptedSharedPreferences] instance.
     */
    private val prefKeyEncryptionScheme by lazy {
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
    }

    /**
     * Pref value scheme used to initialize [EncryptedSharedPreferences] instance.
     */
    private val prefValueEncryptionScheme by lazy {
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    }

    /**
     * Initializes and returns a new [SharedPreferences] instance. Instance is using an encryption
     * to store data in device.
     *
     * @return A new [SharedPreferences] instance.
     */
    private fun getSharedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            resourceProvider.provideContext(),
            "secret_shared_prefs",
            masterKey,
            prefKeyEncryptionScheme,
            prefValueEncryptionScheme
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> setOnKeyChangedListener(key: String, defaultValue: T, onNewValue: (T?) -> Unit) {
        getSharedPrefs().registerOnSharedPreferenceChangeListener { sharedPrefs, key ->
            val newValue = when (sharedPrefs.all[key]) {
                is String -> sharedPrefs.getString(key, defaultValue as String)
                is Boolean -> sharedPrefs.getBoolean(key, defaultValue as Boolean)
                is Int -> sharedPrefs.getInt(key, defaultValue as Int)
                is Long -> sharedPrefs.getLong(key, defaultValue as Long)
                else -> null
            } as? T
            onNewValue(newValue)
        }
    }

    /**
     * Defines if [SharedPreferences] contains a value for given [key]. This function will only
     * identify if a key exists in storage and will not check if corresponding value is valid.
     *
     * @param key The name of the preference to check.
     *
     * @return `true` if preferences contain given key. `false` otherwise.
     */
    override fun contains(key: String): Boolean {
        return getSharedPrefs().contains(key)
    }

    /**
     * Removes given preference key from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    override fun clear(key: String) {
        logController.d(logTag) { "[STORAGE] clear key=$key dest=prefs" }
        getSharedPrefs().edit {
            remove(key)
        }
    }

    /**
     * Removes all keys from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    override fun clear() {
        logController.d(logTag) { "[STORAGE] clear-all dest=prefs" }
        getSharedPrefs().edit {
            clear()
        }
    }

    override fun clearSync() {
        logController.d(logTag) { "[STORAGE] clear-all dest=prefs" }
        getSharedPrefs().edit(commit = true) {
            clear()
        }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    override fun setString(key: String, value: String) {
        logController.d(logTag) { "[STORAGE] store key=$key dest=prefs" }
        getSharedPrefs().edit {
            putString(key, value)
        }
    }

    override fun setStringSync(key: String, value: String) {
        logController.d(logTag) { "[STORAGE] store key=$key dest=prefs" }
        getSharedPrefs().edit(commit = true) {
            putString(key, value)
        }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    override fun setLong(
        key: String, value: Long
    ) {
        logController.d(logTag) { "[STORAGE] store key=$key dest=prefs" }
        getSharedPrefs().edit {
            putLong(key, value)
        }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    override fun setBool(key: String, value: Boolean) {
        logController.d(logTag) { "[STORAGE] store key=$key dest=prefs" }
        getSharedPrefs().edit {
            putBoolean(key, value)
        }
    }

    /**
     * Retrieves a string value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    override fun getString(key: String, defaultValue: String): String {
        return getSharedPrefs().getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Retrieves a long value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    override fun getLong(key: String, defaultValue: Long): Long {
        return getSharedPrefs().getLong(key, defaultValue)
    }

    /**
     * Retrieves a boolean value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    override fun getBool(key: String, defaultValue: Boolean): Boolean {
        return getSharedPrefs().getBoolean(key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return getSharedPrefs().getInt(key, defaultValue)
    }

    override fun setInt(key: String, value: Int) {
        logController.d(logTag) { "[STORAGE] store key=$key dest=prefs" }
        getSharedPrefs().edit {
            putInt(key, value)
        }
    }
}

interface PrefKeys {
    companion object {
        const val KEY_BIOMETRIC_ALIAS = "BiometricAlias"
        const val KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS = "WalletInstancePrivateKeyAlias"
        const val KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS = "WalletInstanceAttestationPrivateKeyAlias"
        const val KEY_WALLET_REGISTRATION_ALIAS = "WalletRegistrationAlias"
    }

    /**
     * Returns the alias if saved, in order to find the secret key in android keystore.
     */
    fun getAlias(key: String): String

    /**
     * Stores the alias used for the secret key in android keystore.
     *
     * @param value the alias value.
     */
    fun saveAlias(key: String, value: String)
    fun getBiometricAlias(): String
    fun setBiometricAlias(value: String)
    fun getStorageKey(): ByteArray?
    fun setStorageKey(key: String)
}

internal class PrefKeysImpl(
    private val prefsController: PrefsController
) : PrefKeys {
    override fun getAlias(key: String): String = prefsController.getString(key, "")

    override fun saveAlias(key: String, value: String) = prefsController.setString(key, value)

    /**
     * Returns the biometric alias in order to find the biometric secret key in android keystore.
     */
    override fun getBiometricAlias(): String {
        return prefsController.getString("BiometricAlias", "")
    }

    /**
     * Stores the biometric alias used for the secret key in android keystore.
     *
     * @param value the biometric alias value.
     */
    override fun setBiometricAlias(value: String) {
        prefsController.setString("BiometricAlias", value)
    }

    override fun setStorageKey(key: String) {
        prefsController.setString("StorageKey", key)
    }

    override fun getStorageKey(): ByteArray? {
        val key = prefsController.getString("StorageKey", "")
        return if (key.isNotEmpty()) key.decodeFromPemBase64String() else null
    }
}
