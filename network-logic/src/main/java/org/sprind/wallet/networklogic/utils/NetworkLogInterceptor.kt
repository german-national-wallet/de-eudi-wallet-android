package org.sprind.wallet.networklogic.utils

import eu.europa.ec.businesslogic.controller.log.LogController
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.sprind.wallet.businesslogic.util.RedactedKeys
import java.io.IOException
import java.net.URLDecoder

class NetworkLogInterceptor(private val logController: LogController) : Interceptor {
    private val logTag = javaClass.simpleName

    private companion object {
        val REDACTED = RedactedKeys.REDACTED
        const val MAX_BODY_BYTES = 512L * 1024L
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val responseBody = peekedResponseBody(response)

        logController.d(logTag) {
            buildString {
                append("\n\n==================== Network Request Begin ====================\n")
                append("Request: ${redactedUrl(request)}\n")
                append("Method: ${request.method}\n")
                append("Body: ${requestBodyText(request)}\n")
                append("Headers: ${redactedHeaders(request.headers.toMultimap())}\n")
                append("Status: ${response.code}\n")
                append("Response Headers: ${redactedHeaders(response.headers.toMultimap())}\n")
                append("Response Body: ${redactedBody(responseBody)}\n")
                append("==================== Network Request End ====================\n")
            }
        }

        return response
    }

    private fun isRedacted(key: String) = RedactedKeys.isRedacted(key)

    private fun peekedResponseBody(response: Response): String? = try {
        response.peekBody(MAX_BODY_BYTES).string()
    } catch (_: IOException) {
        null
    }

    private fun requestBodyText(request: Request): String {
        val body = request.body ?: return ""
        if (body.isOneShot() || body.isDuplex()) return "[body not logged, streamed]"
        val length = body.contentLength()
        if (length > MAX_BODY_BYTES) return "[body not logged, $length bytes]"
        return try {
            redactedBody(Buffer().also { body.writeTo(it) }.readUtf8())
        } catch (_: IOException) {
            ""
        }
    }

    private fun redactedUrl(request: Request): String {
        val url = request.url
        if (url.querySize == 0) return url.toString()
        val builder = url.newBuilder()
        for (index in 0 until url.querySize) {
            val name = url.queryParameterName(index)
            if (isRedacted(name)) {
                builder.setQueryParameter(name, REDACTED)
            }
        }
        return builder.build().toString()
    }

    private fun redactedHeaders(headers: Map<String, List<String>>): String =
        headers.entries.joinToString(", ") { (name, values) ->
            "$name: ${if (isRedacted(name)) REDACTED else values.joinToString(", ")}"
        }

    private fun redactedBody(body: String?): String {
        if (body.isNullOrEmpty()) return ""
        if (looksLikeText(body)) {
            redactedJson(body)?.let { return it }
            redactedForm(body)?.let { return it }
        }
        return "[body not logged, ${body.toByteArray().size} bytes]"
    }

    private fun looksLikeText(body: String): Boolean = body.take(64).none {
        it == '�' || (Character.isISOControl(it) && !Character.isWhitespace(it))
    }

    private fun redactedJson(body: String): String? = try {
        when (body.trimStart().firstOrNull()) {
            '{' -> redactedJsonObject(JSONObject(body)).toString(2)
            '[' -> redactedJsonArray(JSONArray(body)).toString(2)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun redactedJsonObject(source: JSONObject): JSONObject {
        val result = JSONObject()
        for (key in source.keys()) {
            result.put(key, if (isRedacted(key)) REDACTED else redactedJsonValue(source.get(key)))
        }
        return result
    }

    private fun redactedJsonArray(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (index in 0 until source.length()) {
            result.put(redactedJsonValue(source.get(index)))
        }
        return result
    }

    private fun redactedJsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> redactedJsonObject(value)
        is JSONArray -> redactedJsonArray(value)
        else -> value
    }

    private fun redactedForm(body: String): String? {
        if (!body.contains("=")) return null
        return body.split("&").joinToString("&") { pair ->
            val name = decoded(pair.substringBefore("="))
            val value = pair.substringAfter("=", "")
            "$name=${if (isRedacted(name)) REDACTED else decoded(value)}"
        }
    }

    private fun decoded(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
