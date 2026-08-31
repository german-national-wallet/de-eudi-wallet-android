package eu.europa.ec.businesslogic.extension

import android.util.Base64
import java.security.MessageDigest

fun <T> MutableList<T>.addOrReplace(value: T, replaceCondition: (T) -> Boolean) {
    for (i in indices) {
        if (replaceCondition(this[i])) {
            this[i] = value
        }
    }
}

fun List<String>.toHashedStringWithSha256(): String {
    val concatenated = this.joinToString(separator = "")
    val messageDigest = MessageDigest.getInstance("SHA-256")
    val hashBytes = messageDigest.digest(concatenated.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(hashBytes, Base64.NO_WRAP or Base64.URL_SAFE)
}