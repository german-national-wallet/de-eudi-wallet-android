package eu.europa.ec.authenticationlogic.controller.storage.util

import android.security.keystore.KeyProperties.KEY_ALGORITHM_EC
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

interface KeyConverter {
    fun convertToECPublicKey(base64PublicKey: String): ECPublicKey
}

internal class KeyConverterImpl : KeyConverter {
    override fun convertToECPublicKey(base64PublicKey: String): ECPublicKey {
        val keyBytes = Base64.getDecoder().decode(base64PublicKey)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_EC)
        return keyFactory.generatePublic(keySpec) as ECPublicKey
    }
}