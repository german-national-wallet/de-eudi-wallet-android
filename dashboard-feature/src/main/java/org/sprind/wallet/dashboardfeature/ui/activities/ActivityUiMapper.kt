/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.dashboardfeature.ui.activities

import eu.europa.ec.businesslogic.extension.getLocalizedString
import eu.europa.ec.businesslogic.extension.getLocalizedValue
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.corelogic.extension.getLocalizedDocumentName
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.sprind.wallet.corelogic.activity.ActivityCredential
import org.sprind.wallet.corelogic.activity.ActivityLogEntry
import org.sprind.wallet.corelogic.activity.ActivityRejection
import org.sprind.wallet.corelogic.activity.ActivityResult
import org.sprind.wallet.corelogic.activity.ActivityType
import org.sprind.wallet.corelogic.model.forLocale
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityAvatarUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityDetailUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityLinkKind
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityLinkUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityListItemUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivitySectionUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.PresentationOutcomeUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.SharedCredentialUi
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class ActivityUiMapper(
    private val resourceProvider: ResourceProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val locale: Locale get() = resourceProvider.getLocale()

    fun toSections(entries: List<ActivityLogEntry>): List<ActivitySectionUi> {
        val today = LocalDate.now(clock)
        return entries
            .filter { it.isRenderable() }
            .groupBy { section(it.localDate(), today) }
            .toSortedMap(compareBy<Section> { it.period }.thenByDescending { it.month })
            .map { (section, sectionEntries) ->
                ActivitySectionUi(
                    header = section.header(),
                    items = sectionEntries.map { it.toListItem(showTime = section.period.showsTime) },
                )
            }
    }

    fun toDetail(entry: ActivityLogEntry): ActivityDetailUi? {
        if (!entry.isRenderable()) return null
        val date = formatDateTime(entry.instant())
        val party = entry.interactingParty
        val reportUri = party.supervisoryAuthorityUri?.takeIf { it.isNotBlank() }
        return when (entry.type) {
            ActivityType.PRESENTATION -> ActivityDetailUi.Presentation(
                id = entry.id,
                title = party.name ?: resourceProvider.getString(R.string.request_relying_party_default_name),
                date = date,
                links = listOfNotNull(
                    party.supportUri.toLink(ActivityLinkKind.SUPPORT),
                    party.privacyPolicyUri.toLink(ActivityLinkKind.PRIVACY_POLICY),
                    party.infoUri.toLink(ActivityLinkKind.WEBSITE),
                ),
                reportUri = reportUri,
                outcome = entry.outcome(),
                purpose = entry.purpose.forLocale(locale),
                credentials = entry.credentials.map { it.toSharedCredential() },
            )

            ActivityType.ISSUANCE,
            ActivityType.DELETION,
                -> {
                val credential = entry.credentials.first()
                val deleted = entry.type == ActivityType.DELETION
                ActivityDetailUi.CredentialEvent(
                    id = entry.id,
                    title = credential.eventTitle(deleted),
                    date = date,
                    links = listOfNotNull(
                        party.supportUri.toLink(ActivityLinkKind.SUPPORT),
                        party.privacyPolicyUri.toLink(ActivityLinkKind.PRIVACY_POLICY),
                        party.infoUri.toLink(ActivityLinkKind.WEBSITE),
                    ),
                    reportUri = reportUri,
                    deleted = deleted,
                    isPid = credential.isPid(),
                    card = credential.toCardData(),
                    credential = credential.toSharedCredential(),
                )
            }
        }
    }

    // Persisted data: an issuance or deletion without its credential is skipped, not fatal.
    private fun ActivityLogEntry.isRenderable(): Boolean =
        type == ActivityType.PRESENTATION || credentials.isNotEmpty()

    private fun ActivityLogEntry.outcome(): PresentationOutcomeUi = when {
        result == ActivityResult.COMPLETED -> PresentationOutcomeUi.COMPLETED
        else -> when (rejection) {
            ActivityRejection.BY_USER -> PresentationOutcomeUi.REJECTED_BY_USER
            ActivityRejection.OVERASKING -> PresentationOutcomeUi.OVERASKING
            ActivityRejection.UNKNOWN_SERVICE -> PresentationOutcomeUi.UNKNOWN_SERVICE
            ActivityRejection.BY_SERVICE -> PresentationOutcomeUi.REJECTED_BY_SERVICE
            null -> PresentationOutcomeUi.NOT_COMPLETED
        }
    }

    private fun ActivityCredential.eventTitle(deleted: Boolean): String = when {
        isPid() && deleted -> resourceProvider.getString(R.string.activities_detail_title_pid_issuance_deleted)
        isPid() -> resourceProvider.getString(R.string.activities_detail_title_pid_issuance)
        deleted -> resourceProvider.getString(R.string.activities_detail_title_eaa_issuance_deleted_dev, displayName())
        else -> resourceProvider.getString(R.string.activities_detail_title_eaa_issuance_dev, displayName())
    }

    private fun ActivityLogEntry.toListItem(showTime: Boolean): ActivityListItemUi {
        val instant = instant()
        val whenText = if (showTime) formatTime(instant) else formatDate(instant)
        return when (type) {
            ActivityType.PRESENTATION -> {
                val name = interactingParty.name
                    ?: resourceProvider.getString(R.string.request_relying_party_default_name)
                val what = when {
                    result != ActivityResult.COMPLETED ->
                        resourceProvider.getString(R.string.activities_overview_label_rejected)

                    credentials.distinctCredentialNames().size == 1 -> credentials.first().displayName()
                    else -> resourceProvider.getString(R.string.activities_overview_label_multiple_credentials)
                }
                ActivityListItemUi(
                    id = id,
                    title = name,
                    subtitle = joined(what, whenText),
                    avatar = ActivityAvatarUi.Initials(initials(name)),
                    notCompleted = result != ActivityResult.COMPLETED,
                )
            }

            ActivityType.ISSUANCE,
            ActivityType.DELETION,
                -> {
                val credential = credentials.first()
                ActivityListItemUi(
                    id = id,
                    title = credential.eventTitle(deleted = type == ActivityType.DELETION),
                    subtitle = joined(credential.issuerName(), whenText),
                    avatar = credential.avatar(),
                )
            }
        }
    }

    private fun ActivityCredential.toSharedCredential(): SharedCredentialUi {
        val metadata = issuerMetadata
        return SharedCredentialUi(
            name = displayName(),
            issuer = issuerName(),
            avatar = avatar(),
            claimLabels = presentedClaims.ifEmpty { requestedClaims }.ifEmpty { heldClaims }
                .map { path -> claimLabel(path, metadata) }
                .distinct(),
        )
    }

    private fun ActivityCredential.toCardData(): EaaCardData {
        val display = issuerMetadata?.display.getLocalizedValue(
            userLocale = locale,
            fallback = null,
            localeExtractor = { it.locale },
            valueExtractor = { it },
        )
        return EaaCardData(
            id = documentId,
            description = displayName(),
            name = issuerName(),
            backgroundColor = display?.backgroundColor,
            backgroundImageUri = display?.backgroundImageUri,
            logoUri = display?.logo?.uri,
            validityDate = null,
        )
    }

    private fun claimLabel(path: List<String>, metadata: IssuerMetadata?): String {
        val fallback = path.lastOrNull().orEmpty()
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(locale) }
        val claim = metadata?.claims?.firstOrNull { it.path == path }
            ?: metadata?.claims?.firstOrNull { it.path.lastOrNull() == path.lastOrNull() }
        return claim?.display.getLocalizedClaimName(userLocale = locale, fallback = fallback)
            .ifBlank { fallback }
    }

    private fun ActivityCredential.isPid(): Boolean =
        credentialIdentifier.toDocumentIdentifier().isPid

    private fun ActivityCredential.displayName(): String = if (isPid()) {
        resourceProvider.getString(R.string.pid_inspection_pid_details_title)
    } else {
        issuerMetadata.getLocalizedDocumentName(userLocale = locale, fallback = credentialIdentifier)
    }

    private fun ActivityCredential.issuerName(): String = if (isPid()) {
        resourceProvider.getString(R.string.pid_presentation_data_consent_credential_subline)
    } else {
        issuerMetadata?.issuerDisplay.getLocalizedString(
            userLocale = locale,
            localeExtractor = { it.locale },
            stringExtractor = { it.name },
            fallback = "",
        )
    }

    private fun ActivityCredential.avatar(): ActivityAvatarUi {
        if (isPid()) return ActivityAvatarUi.PidEagle
        val display = issuerMetadata?.display.getLocalizedValue(
            userLocale = locale,
            fallback = null,
            localeExtractor = { it.locale },
            valueExtractor = { it },
        )
        val initials = ActivityAvatarUi.Initials(initials(displayName()), display?.backgroundColor)
        return display?.logo?.uri?.toString()?.let { ActivityAvatarUi.Logo(it, fallback = initials) } ?: initials
    }

    // The PID's mdoc and SD-JWT VC are one credential to the user.
    private fun List<ActivityCredential>.distinctCredentialNames(): Set<String> =
        mapTo(mutableSetOf()) { it.displayName() }

    private fun joined(first: String, second: String): String =
        if (first.isBlank()) second else "$first · $second"

    private fun String?.toLink(kind: ActivityLinkKind): ActivityLinkUi? =
        this?.takeIf { it.isNotBlank() }?.let { ActivityLinkUi(kind, it) }

    private fun ActivityLogEntry.instant(): Instant = Instant.ofEpochMilli(timestampMillis)

    private fun ActivityLogEntry.localDate(): LocalDate = instant().atZone(clock.zone).toLocalDate()

    private fun formatDate(instant: Instant): String = format(instant, DATE_PATTERN)

    private fun formatTime(instant: Instant): String =
        format(instant, if (isGerman()) GERMAN_TIME_PATTERN else TIME_PATTERN)

    private fun formatDateTime(instant: Instant): String = joined(formatDate(instant), formatTime(instant))

    private fun format(instant: Instant, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, locale).withZone(clock.zone).format(instant)

    private fun isGerman(): Boolean = locale.language == Locale.GERMAN.language

    private fun section(date: LocalDate, today: LocalDate): Section {
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        return when {
            daysAgo <= 0 -> Section(Period.TODAY)
            daysAgo == 1L -> Section(Period.YESTERDAY)
            daysAgo <= 7 -> Section(Period.PREVIOUS_7_DAYS)
            daysAgo <= 30 -> Section(Period.PREVIOUS_30_DAYS)
            else -> Section(Period.MONTH, YearMonth.from(date))
        }
    }

    private fun Section.header(): String = month
        ?.let { formatMonth(it) }
        ?: resourceProvider.getString(requireNotNull(period.labelRes))

    private fun formatMonth(month: YearMonth): String =
        DateTimeFormatter.ofPattern(MONTH_PATTERN, locale).format(month).replaceFirstChar { it.titlecase(locale) }

    /** [month] is set only for [Period.MONTH], whose sections are one per calendar month. */
    private data class Section(val period: Period, val month: YearMonth? = null)

    private enum class Period(val labelRes: Int?, val showsTime: Boolean) {
        TODAY(R.string.activities_overview_label_today, showsTime = true),
        YESTERDAY(R.string.activities_overview_label_yesterday, showsTime = true),
        PREVIOUS_7_DAYS(R.string.activities_overview_label_last_week, showsTime = false),
        PREVIOUS_30_DAYS(R.string.activities_overview_label_last_month, showsTime = false),
        MONTH(labelRes = null, showsTime = false),
    }

    private companion object {
        const val DATE_PATTERN = "dd.MM.yyyy"
        const val MONTH_PATTERN = "LLLL yyyy"
        const val TIME_PATTERN = "h:mm a"
        const val GERMAN_TIME_PATTERN = "HH:mm 'Uhr'"
    }
}

fun initials(name: String): String =
    name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "?" }
