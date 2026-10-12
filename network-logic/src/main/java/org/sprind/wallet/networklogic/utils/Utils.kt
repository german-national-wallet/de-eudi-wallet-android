package org.sprind.wallet.networklogic.utils

import org.sprind.wallet.networklogic.common.RequestSigningException
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


//TODO to add the correct codes
const val NO_INTERNET_ERROR_CODE = "NO_INTERNET"
const val UNKNOWN_ERROR_CODE = "UNKNOWN"

private const val MAX_CAUSE_DEPTH = 16

/**
 * A [RequestSigningException] is an [IOException] only so that OkHttp delivers it, and arrives
 * wrapped (e.g. in a `TracedIOException`), so the cause chain is checked before the offline case.
 */
fun Throwable.getErrorCode(): String = when {
    hasCause<RequestSigningException>() -> UNKNOWN_ERROR_CODE
    this is IOException -> NO_INTERNET_ERROR_CODE
    else -> UNKNOWN_ERROR_CODE
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is T }