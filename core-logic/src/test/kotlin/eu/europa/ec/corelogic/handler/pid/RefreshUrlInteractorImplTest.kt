package eu.europa.ec.corelogic.handler.pid

import app.cash.turbine.test
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any

@RunWith(MockitoJUnitRunner::class)
class RefreshUrlInteractorImplTest {

    private val logController: LogController = mock(LogController::class.java)
    private val resourceProvider: ResourceProvider = mock(ResourceProvider::class.java)
    private val httpClient: OkHttpClient = mock(OkHttpClient::class.java)
    private val call: Call = mock(Call::class.java)
    private val response: Response = mock(Response::class.java)
    private val headers: Headers = mock(Headers::class.java)

    private val refreshUrl =
        "https://demo.pid-issuer.bundesdruckerei.de/c1/finish-authorization?issuer_state=haBeUGXJR5NX3cRQj5gBDs"
    val location =
        "https://uri.example.com?code=WuJc8ZgWPVMALj2c8vCdSR&state=CvlX5nerkUYOH-9KPJr0fM2FUvx0tc3tg3NyTy6aKjM"
    val dpopNonce = "IGGY8b8zFvEhyvuriYW2ZM"
    val code = "WuJc8ZgWPVMALj2c8vCdSR"
    val state = "CvlX5nerkUYOH-9KPJr0fM2FUvx0tc3tg3NyTy6aKjM"

    private val interactor = RefreshUrlInteractorImpl(
        logController = logController,
        resourceProvider = resourceProvider,
        httpClient = httpClient
    )

    @Test
    fun `Given a valid finish-Authorization response, then return RefreshUrlResult Success`() {
        `when`(headers["location"]).thenReturn(location)
        `when`(headers["dpop-Nonce"]).thenReturn(dpopNonce)
        `when`(response.headers).thenReturn(headers)
        `when`(httpClient.newCall(any<Request>())).thenReturn(call)
        `when`(call.execute()).thenReturn(response)

        runTest {
            interactor.callRefreshUrl(refreshUrl).test {
                val result = awaitItem()
                assertTrue(result is RefreshUrlResult.Success)
                val successResult = result as RefreshUrlResult.Success
                assertEquals(code, successResult.authorizationResponse.code)
                assertEquals(state, successResult.authorizationResponse.state)
                assertEquals(dpopNonce, successResult.authorizationResponse.dPoPNonce)

                awaitComplete()
            }
        }
    }

    @Test
    fun `Given a valid finish-Authorization response with a missing header,then return RefreshUrlResult Failure`() {
        `when`(resourceProvider.getString(R.string.generic_error_message)).thenReturn("Error occurred")
        `when`(headers["location"]).thenReturn(location)
        `when`(headers["dpop-Nonce"]).thenReturn(null)
        `when`(response.headers).thenReturn(headers)
        `when`(httpClient.newCall(any<Request>())).thenReturn(call)
        `when`(call.execute()).thenReturn(response)

        runTest {
            interactor.callRefreshUrl(refreshUrl).test {
                val result = awaitItem()
                assertTrue(result is RefreshUrlResult.Failure)
                assertEquals("Error occurred", (result as RefreshUrlResult.Failure).errorMessage)
                awaitComplete()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun `test request failure`() {
        `when`(resourceProvider.getString(R.string.generic_error_message)).thenReturn("Error occurred")
        `when`(httpClient.newCall(any<Request>())).thenReturn(call)
        runTest {
            interactor.callRefreshUrl(refreshUrl).test {
                val result = awaitItem()
                assertTrue(result is RefreshUrlResult.Failure)
                assertEquals("Error occurred", (result as RefreshUrlResult.Failure).errorMessage)
                awaitComplete()
            }
        }
    }
}