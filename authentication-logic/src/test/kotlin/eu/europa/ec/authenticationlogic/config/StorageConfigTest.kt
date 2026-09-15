package eu.europa.ec.authenticationlogic.config

import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.authenticationlogic.provider.HardwareKeyStorageProvider
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.authenticationlogic.provider.WalletPinUnBlockTimeStorageProvider
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.sprind.wallet.authenticationlogic.provider.MdvmRegistrationStorageProvider

class StorageConfigTest {

    @Mock
    private lateinit var biometryStorageProvider: BiometryStorageProvider

    @Mock
    private lateinit var hardwareKeyStorageProvider: HardwareKeyStorageProvider

    @Mock
    private lateinit var walletRegistrationStorageProvider: WalletRegistrationStorageProvider

    @Mock
    private lateinit var walletPinUnBlockTimeStorageProvider: WalletPinUnBlockTimeStorageProvider

    @Mock
    private lateinit var mdvmRegistrationStorageProvider: MdvmRegistrationStorageProvider

    private lateinit var closeable: AutoCloseable

    private val storageConfig: StorageConfig by lazy {
        StorageConfigImpl(
            biometryImpl = biometryStorageProvider,
            hardwareKeyImpl = hardwareKeyStorageProvider,
            walletRegistrationImpl = walletRegistrationStorageProvider,
            walletPinBlockTimeImpl = walletPinUnBlockTimeStorageProvider,
            mdvmRegistrationImpl = mdvmRegistrationStorageProvider,
        )
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `biometryStorageProvider should return the correct provider`() {
        assertEquals(biometryStorageProvider, storageConfig.biometryStorageProvider)
    }

    @Test
    fun `hardwareKeyStorageProvider should return the correct provider`() {
        assertEquals(hardwareKeyStorageProvider, storageConfig.hardwareKeyStorageProvider)
    }

    @Test
    fun `walletRegistrationStorageProvider should return the correct provider`() {
        assertEquals(
            walletRegistrationStorageProvider,
            storageConfig.walletRegistrationStorageProvider
        )
    }

    @Test
    fun `walletPinUnBlockTimeStorageProvider should return the correct provider`() {
        assertEquals(
            walletPinUnBlockTimeStorageProvider,
            storageConfig.walletPinUnBlockTimeStorageProvider
        )
    }

    @Test
    fun `mdvmRegistrationStorageProvider should return the correct provider`() {
        assertEquals(
            mdvmRegistrationStorageProvider,
            storageConfig.mdvmRegistrationStorageProvider
        )
    }
}
