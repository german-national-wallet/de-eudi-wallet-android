package eu.europa.ec.corelogic.util

import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun extractCountry(cert: X509Certificate): String? {
    val subject = cert.subjectX500Principal.name
    val regex = Regex("C=([^,]+)")
    return regex.find(subject)?.groupValues?.get(1)
}

/**
 *
 * Field	        Regex
 * CN	            Regex("CN=([^,]+)")
 * O (Org)	        Regex("O=([^,]+)")
 * OU (Org Unit)	Regex("OU=([^,]+)")
 * L (City)	        Regex("L=([^,]+)")
 */
fun extractCommonName(cert: X509Certificate): String? {
    val subject = cert.subjectX500Principal.name
    val regex = Regex("CN=([^,]+)")
    return regex.find(subject)?.groupValues?.get(1)
}

fun formatDate(raw: String): String {
    val inputFormatter = DateTimeFormatter.ofPattern(
        "EEE MMM dd HH:mm:ss zzz yyyy",
        Locale.ENGLISH
    )

    val outputFormatter = DateTimeFormatter.ofPattern(
        "d MMMM yyyy", // ➜ 24 March 2028
        Locale.ENGLISH
    )

    val date = ZonedDateTime.parse(raw, inputFormatter)
    return outputFormatter.format(date)
}