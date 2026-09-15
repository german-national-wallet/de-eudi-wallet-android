package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrefsWalletRegistrationStorageProviderTest {
    @Mock
    private lateinit var prefsController: PrefsController

    private val subject: PrefsWalletRegistrationStorageProvider by lazy {
        PrefsWalletRegistrationStorageProvider(prefsController)
    }

    private val gson = Gson()

    private lateinit var closeable: AutoCloseable

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `getWalletRegistration returns HardwareKeyData when stored data is valid`() {
        // Given
        val expectedWalletRegistration = WalletRegistration(
            encryptedString = "enryptedString",
            ivString = "iv"
        )
        val storedDataJson = gson.toJson(expectedWalletRegistration)
        whenever(prefsController.getString("WalletRegistration", ""))
            .thenReturn(storedDataJson)

        // Then
        val result = subject.getWalletRegistration()

        assertNotNull(result)
        assertEquals(expectedWalletRegistration, result)
    }


    @Test
    fun `getWalletRegistration returns null when no data is stored`() {
        // Given
        whenever(prefsController.getString("WalletRegistration", ""))
            .thenReturn("")

        // Then
        val result = subject.getWalletRegistration()

        assertNull(result)
    }

    @Test
    fun `storeWalletRegistration saves data in shared preferences`() {
        // Given
        val expectedWalletRegistration = WalletRegistration(
            encryptedString = "enryptedString",
            ivString = "iv"
        )
        val walletRegistrationJson = gson.toJson(expectedWalletRegistration)

        // Then
        subject.storeWalletRegistration(expectedWalletRegistration)

        verify(prefsController).setString(
            "WalletRegistration",
            walletRegistrationJson
        )
    }
}