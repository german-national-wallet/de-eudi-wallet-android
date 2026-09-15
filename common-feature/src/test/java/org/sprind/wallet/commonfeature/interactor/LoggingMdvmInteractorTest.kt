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

package org.sprind.wallet.commonfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse

class LoggingMdvmInteractorTest {

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var delegate: MdvmInteractor

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var subject: LoggingMdvmInteractor;

    private lateinit var closeable: AutoCloseable


    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        subject = LoggingMdvmInteractor(logController, delegate)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `mdvmRegistration logs returned result`() = coroutineRule.runTest {
        // Given
        val result = ApiResult.Failure(
            MdvmErrorResponse("test-error").toMdvmError()
        )
        whenever(delegate.mdvmRegistration())
            .thenReturn(result)

        // When
        val actual = subject.mdvmRegistration()

        // Then
        assertEquals(result, actual) // result is returned
        // result is contained within logged message
        val messageCaptor = argumentCaptor<() -> String>()
        verify(logController).d(
            eq(subject.javaClass.simpleName),
            messageCaptor.capture()
        )
        val message = messageCaptor.singleValue()
        assertTrue(message.contains(result.toString()))
    }
}