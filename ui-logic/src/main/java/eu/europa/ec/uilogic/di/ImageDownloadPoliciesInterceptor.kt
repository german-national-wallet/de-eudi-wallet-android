/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.di

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException

/**
 * An OkHttp [Interceptor] that applies the download policies for credential images:
 *
 * 1. **Cache-control override** — strips `Cache-Control: max-age` / `no-cache` directives
 *    and `Expires` headers from the response, so image caching is governed solely by
 *    the disk cache size limit and is not invalidated by server-supplied expiry dates.
 * 2. **Size limit** — enforces a hard [maxBytes] cap on the response body. It first
 *    checks the `Content-Length` header for a fast rejection. When the header is
 *    absent or unreliable (e.g. chunked transfer encoding), it falls back to
 *    enforcing the limit while the body is read, aborting the stream as soon as
 *    [maxBytes] is exceeded.
 *
 * Must be registered as a network interceptor so that it observes the real
 * (non-transformed) `Content-Length` reported by the server.
 *
 * @param maxBytes The maximum allowed size, in bytes, of the response body.
 */
class ImageDownloadPoliciesInterceptor(private val maxBytes: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        val contentLength = response.header("Content-Length")?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes) {
            response.close()
            throw IOException(
                "Image exceeds max allowed size: $contentLength bytes (limit $maxBytes bytes)"
            )
        }

        val body = response.body

        val builder = response.newBuilder()
            .body(LimitedResponseBody(body, maxBytes))

        response.header("Cache-Control")?.let { cacheControl ->
            if (cacheControl.contains("max-age", ignoreCase = true) ||
                cacheControl.contains("no-cache", ignoreCase = true)
            ) {
                builder.removeHeader("Cache-Control")
            }
        }
        if (response.header("Expires") != null) {
            builder.removeHeader("Expires")
        }

        return builder.build()
    }

    private class LimitedResponseBody(
        private val delegate: ResponseBody,
        private val maxBytes: Long,
    ) : ResponseBody() {

        private val limitedSource: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                private var bytesRead = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesRead += read
                        if (bytesRead > maxBytes) {
                            throw IOException(
                                "Image exceeds max allowed size: limit $maxBytes bytes"
                            )
                        }
                    }
                    return read
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): BufferedSource = limitedSource
    }
}
