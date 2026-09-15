package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.provider.HardwareKeyStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.intArrayOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardwareKeyStorageControllerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var storageConfig: StorageConfig

    @Mock
    private lateinit var hardwareKeyStorageProvider: HardwareKeyStorageProvider

    @Mock
    private lateinit var cryptoController: CryptoController

    private lateinit var closeable: AutoCloseable

    private val subject: HardwareKeyStorageControllerImpl by lazy {
        HardwareKeyStorageControllerImpl(
            storageConfig = storageConfig,
            cryptoController = cryptoController,
        )
    }

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(storageConfig.hardwareKeyStorageProvider).thenReturn(hardwareKeyStorageProvider)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `saveRefreshToken saves refresh token string to storage`() = coroutineRule.runTest {
        subject.saveRefreshToken("token")
        verify(hardwareKeyStorageProvider).storeRefreshToken("token")
    }

    @Test
    fun `getRefreshToken returns refresh token string from storage`() = coroutineRule.runTest {
        whenever(hardwareKeyStorageProvider.getRefreshToken()).thenReturn("token")
        assertEquals("token", subject.getRefreshToken())
    }

}
