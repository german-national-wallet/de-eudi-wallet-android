package eu.europa.ec.authenticationlogic.encoder

import android.util.Base64
import eu.europa.ec.businesslogic.extension.splitToLines

interface Base64EncoderDecoder {
    /**
     * Converts Byte Array to encoded Pem base64 String
     *
     * @param Byte Array object
     * @return String object
     */
    fun encodeToPemBase64String(data: ByteArray): String?

    /**
     * Converts Pem base64 String to Byte Array
     *
     * @param String object
     * @return Byte Array object
     */

    fun decodeFromPemBase64String(data: String): ByteArray?
}

internal class Base64EncoderDecoderDecoderImpl : Base64EncoderDecoder {
    override fun encodeToPemBase64String(data: ByteArray): String? {
        val encodedString = Base64.encodeToString(data, Base64.NO_WRAP) ?: return null
        return encodedString.splitToLines(64)
    }

    override fun decodeFromPemBase64String(data: String): ByteArray? {
        return Base64.decode(data.replace("\n", ""), Base64.NO_WRAP)
    }
}
