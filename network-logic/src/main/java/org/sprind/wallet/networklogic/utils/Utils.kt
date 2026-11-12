package org.sprind.wallet.networklogic.utils

import org.sprind.wallet.networklogic.common.HttpRateLimitedException
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


/**
 * Sent by the wallet backend, unlike the codes below, when its HSM is overloaded (HTTP 503 from the
 * WPB, RWSCA and MDVM endpoints). [getErrorCode] never returns it.
 */
const val HSM_UNAVAILABLE_ERROR_CODE = "HSM_UNAVAILABLE"

//TODO to add the correct codes
const val NO_INTERNET_ERROR_CODE = "NO_INTERNET"
const val UNKNOWN_ERROR_CODE = "UNKNOWN"
const val RATE_LIMITED_ERROR_CODE = "RATE_LIMITED"

private const val MAX_CAUSE_DEPTH = 16

/**
 * A [RequestSigningException] or [HttpRateLimitedException] is an [IOException] only so that OkHttp
 * delivers it, and arrives wrapped (e.g. in a `TracedIOException`), so the cause chain is checked
 * before the offline case.
 */
fun Throwable.getErrorCode(): String = when {
    hasCause<RequestSigningException>() -> UNKNOWN_ERROR_CODE
    hasCause<HttpRateLimitedException>() -> RATE_LIMITED_ERROR_CODE
    this is IOException -> NO_INTERNET_ERROR_CODE
    else -> UNKNOWN_ERROR_CODE
}

/** Whether this throwable, or one of its causes, is a [T]. */
inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    causeChain().any { it is T }

/** This throwable followed by its causes, bounded in case of a cycle. */
fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)