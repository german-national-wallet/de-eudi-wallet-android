/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.businesslogic.controller.log

import androidx.test.core.app.ApplicationProvider
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogControllerImpl
import fr.bipi.treessence.file.FileLoggerTree
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogControllerImplTest {

    @Mock
    private lateinit var configLogic: ConfigLogic

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        // Timber's forest is global, so it must not leak between tests
        Timber.uprootAll()
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        closeable.close()
    }

    private fun buildSubject(isLogcatEnabled: Boolean, isLogWriterEnabled: Boolean) {
        whenever(configLogic.isLogcatEnabled).thenReturn(isLogcatEnabled)
        whenever(configLogic.isLogWriterEnabled).thenReturn(isLogWriterEnabled)
        LogControllerImpl(
            context = ApplicationProvider.getApplicationContext(),
            configLogic = configLogic,
        )
    }

    // FileLoggerTree extends Timber.DebugTree, so the logcat tree has to be matched on its
    // exact class - an `is DebugTree` check would also match the tree writing to file.
    private fun plantedLogcatTrees() =
        Timber.forest().filter { it::class == Timber.DebugTree::class }

    private fun plantedFileTrees() = Timber.forest().filterIsInstance<FileLoggerTree>()

    /**
     * The requirement this whole flavor split exists for: a flavor that logs nothing must
     * end up with an empty forest, so no log line reaches logcat or the file system.
     */
    @Test
    fun `when neither logcat nor log writer is enabled, then no tree is planted`() {
        buildSubject(isLogcatEnabled = false, isLogWriterEnabled = false)

        assertTrue(Timber.forest().isEmpty())
    }

    @Test
    fun `when only logcat is enabled, then only a debug tree is planted`() {
        buildSubject(isLogcatEnabled = true, isLogWriterEnabled = false)

        assertEquals(1, Timber.forest().size)
        assertEquals(1, plantedLogcatTrees().size)
        assertTrue(plantedFileTrees().isEmpty())
    }

    @Test
    fun `when only the log writer is enabled, then only a file tree is planted`() {
        buildSubject(isLogcatEnabled = false, isLogWriterEnabled = true)

        assertEquals(1, Timber.forest().size)
        assertEquals(1, plantedFileTrees().size)
        assertTrue(plantedLogcatTrees().isEmpty())
    }

    @Test
    fun `when both are enabled, then a debug tree and a file tree are planted`() {
        buildSubject(isLogcatEnabled = true, isLogWriterEnabled = true)

        assertEquals(2, Timber.forest().size)
        assertEquals(1, plantedLogcatTrees().size)
        assertEquals(1, plantedFileTrees().size)
    }
}
