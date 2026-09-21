package org.sprind.wallet.networklogic.utils

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


fun Throwable.getErrorCode(): String {
    return when (this) {
        is IOException -> "NO_INTERNET" //TODO to add the correct code
        else -> "UNKNOWN"
    }
}