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

package eu.europa.ec.businesslogic.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Enum representing various date format patterns used for fallback parsing.
 * 
 * The order of enum values is significant and represents the priority order for
 * fallback date parsing. Formats are tried in declaration order until a successful
 * parse is achieved.
 * 
 * This provides type-safe constants for date format patterns that were previously
 * hardcoded strings, improving maintainability and reducing magic string usage.
 */
private enum class DateFormat(val pattern: String) {
    ISO_8601_WITH_MILLISECONDS("yyyy-MM-dd'T'HH:mm:ss.SSS"),
    ISO_8601_BASIC("yyyy-MM-dd'T'HH:mm:ss"),
    ISO_8601_WITH_MILLISECONDS_AND_TIMEZONE("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
    ISO_8601_WITH_TIMEZONE("yyyy-MM-dd'T'HH:mm:ssz"),
    DAY_MONTH_YEAR_ABBREVIATED("dd MMM yyyy"),
    ISO_DATE("yyyy-MM-dd");

    companion object {
        val DTO_DATE_FORMATTERS: List<String> by lazy { entries.map { it.pattern } }
    }
}

// German numeric date format (dd.MM.yyyy) commonly used in Germany
private const val GERMAN_NUMERIC_DATE = "dd.MM.yyyy"


/**
 *  Returns formatted date from string with selected language
 *  @param selectedLanguage example of selectedLanguage : en-GB
 */
fun String.toDateFormatted(
    selectedLanguage: String = LocaleUtils.DEFAULT_LOCALE,
): String? {
    val locale = LocaleUtils.getLocaleFromSelectedLanguage(selectedLanguage)
    val dateFormat = if (locale.country == Locale.GERMANY.country) {
        SimpleDateFormat(GERMAN_NUMERIC_DATE, locale)
    } else {
        SimpleDateFormat(
            DateFormat.DAY_MONTH_YEAR_ABBREVIATED.pattern,
            locale
        )
    }
    return DateFormat.DTO_DATE_FORMATTERS
        .firstNotNullOfOrNull { formatter ->
            SimpleDateFormat(formatter, Locale.ENGLISH)
                .also { it.isLenient = false }
                .parse(this, ParsePosition(0))
        }
        ?.let { dateFormat.format(it) }
}

fun String.toLocalDate(
    selectedLanguage: String = LocaleUtils.DEFAULT_LOCALE,
): LocalDate? = DateFormat.DTO_DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching {
            val dateFormatter = DateTimeFormatter.ofPattern(
                formatter,
                LocaleUtils.getLocaleFromSelectedLanguage(selectedLanguage)
            )
            LocalDate.parse(this, dateFormatter)
        }.getOrNull()
    }

fun Instant.formatInstant(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH
): String? = DateTimeFormatter
    .ofPattern(DateFormat.DAY_MONTH_YEAR_ABBREVIATED.pattern, locale)
    .withZone(zoneId)
    .format(this)

fun Instant.formatInstantToDateString(
    locale: Locale = Locale.ENGLISH
): String {
    val pattern = if (locale.country == Locale.GERMANY.country) {
        GERMAN_NUMERIC_DATE
    } else {
        DateFormat.DAY_MONTH_YEAR_ABBREVIATED.pattern
    }

    return DateTimeFormatter
        .ofPattern(pattern)
        .withZone(ZoneId.systemDefault())
        .format(this)
}
