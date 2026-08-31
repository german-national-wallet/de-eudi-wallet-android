package org.sprind.wallet.networklogic.utils

import org.sprind.wallet.networklogic.trace.CLIENT_TRACE_ID_HEADER
import retrofit2.Response
import java.io.IOException
import java.net.URI

/**
 * Handy method to read the parameters of an URL, without using Uri.parse from android
 *
 * @param url String url from where we need to read the query parameters
 */
fun parseUrl(url: String): Map<String, String> {
    val uri = URI(url)
    val query = uri.query
    return query.split("&")
        .map { it.split("=") }
        .associate { it[0] to it[1] }
}

/**
 * The trace ID to report for this response.
 *
 * The backend's own ID is preferred, since that is the one it logs against. When the backend does
 * not return one, the client-generated ID added by
 * [org.sprind.wallet.networklogic.trace.TraceContextInterceptor] is used, so that an error shown to
 * the user always carries something traceable.
 */
internal fun Response<*>.traceId(): String? =
    headers()["X-trace-Id"] ?: headers()[CLIENT_TRACE_ID_HEADER]

fun Throwable.getErrorCode(): String {
    return when (this) {
        is IOException -> "NO_INTERNET" //TODO to add the correct code
        else -> "UNKNOWN"
    }
}