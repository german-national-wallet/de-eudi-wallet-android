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

import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.sprind.wallet.corelogic.activity.ActivityCredential
import org.sprind.wallet.corelogic.activity.ActivityLogEntry
import org.sprind.wallet.corelogic.activity.ActivityRejection
import org.sprind.wallet.corelogic.activity.ActivityResult
import org.sprind.wallet.corelogic.activity.ActivityType
import org.sprind.wallet.corelogic.activity.InteractingParty
import org.sprind.wallet.corelogic.model.LocalizedTextDomain
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityAvatarUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityDetailUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityLinkKind
import org.sprind.wallet.dashboardfeature.ui.activities.model.PresentationOutcomeUi
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ActivityUiMapperTest {

    private val now = Instant.parse("2026-05-10T12:00:00Z")

    private val resourceProvider = mock<ResourceProvider> {
        on { getLocale() } doReturn Locale.ENGLISH
        on { getString(any()) } doAnswer { STRINGS.getValue(it.getArgument(0)) }
        on { getString(any(), anyVararg()) } doAnswer { invocation ->
            STRINGS.getValue(invocation.getArgument(0)).format(*invocation.arguments.drop(1).toTypedArray())
        }
    }

    private val mapper = ActivityUiMapper(resourceProvider, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `entries are grouped into sections in chronological order`() {
        val sections = mapper.toSections(
            listOf(
                presentation("today", now.minusSeconds(3_600)),
                presentation("yesterday", now.minusSeconds(DAY)),
                presentation("previous 7 days", now.minusSeconds(7 * DAY)),
                presentation("previous 30 days", now.minusSeconds(30 * DAY)),
                presentation("march", now.minusSeconds(60 * DAY)),
                presentation("february", now.minusSeconds(90 * DAY)),
                presentation("also march", now.minusSeconds(45 * DAY)),
            )
        )

        assertEquals(
            listOf("Today", "Yesterday", "Previous 7 days", "Previous 30 days", "March 2026", "February 2026"),
            sections.map { it.header },
        )
        assertEquals(listOf("march", "also march"), sections[4].items.map { it.id })
    }

    @Test
    fun `a presentation shows the verifier, its initials and the credential with the time`() {
        val item = mapper.toSections(listOf(presentation("1", now.minusSeconds(3_600))))
            .single().items.single()

        assertEquals("Rewe", item.title)
        assertEquals("Digital ID · 11:00 AM", item.subtitle)
        assertEquals(ActivityAvatarUi.Initials("RE"), item.avatar)
    }

    @Test
    fun `the PID in both formats counts as one credential`() {
        val entry = presentation("1", now).copy(
            credentials = listOf(pid(PID_MDOC), pid(PID_SD_JWT)),
        )

        val item = mapper.toSections(listOf(entry)).single().items.single()

        assertEquals("Digital ID · 12:00 PM", item.subtitle)
    }

    @Test
    fun `different credentials are summarised as multiple`() {
        val entry = presentation("1", now).copy(credentials = listOf(pid(PID_SD_JWT), eaa()))

        val item = mapper.toSections(listOf(entry)).single().items.single()

        assertEquals("Multiple credentials · 12:00 PM", item.subtitle)
    }

    @Test
    fun `an issuance older than yesterday shows the issuer and the date`() {
        val entry = ActivityLogEntry(
            id = "1",
            timestampMillis = now.minusSeconds(3 * DAY).toEpochMilli(),
            type = ActivityType.ISSUANCE,
            result = ActivityResult.COMPLETED,
            interactingParty = InteractingParty(name = "Stadt Köln", isTrusted = true),
            credentials = listOf(eaa()),
        )

        val item = mapper.toSections(listOf(entry)).single().items.single()

        assertEquals("Sozialpass added", item.title)
        assertEquals("Stadt Köln · 07.05.2026", item.subtitle)
    }

    @Test
    fun `the detail labels shared claims from the snapshot`() {
        val entry = presentation("1", now).copy(
            credentials = listOf(
                eaa().copy(
                    requestedClaims = listOf(listOf("family_name"), listOf("birth_date")),
                    presentedClaims = listOf(listOf("family_name")),
                )
            ),
        )

        val detail = mapper.toDetail(entry) as ActivityDetailUi.Presentation

        assertEquals(PresentationOutcomeUi.COMPLETED, detail.outcome)
        assertEquals(listOf("Family name"), detail.credentials.single().claimLabels)
        assertEquals(listOf(ActivityLinkKind.PRIVACY_POLICY), detail.links.map { it.kind })
    }

    @Test
    fun `a rejected presentation keeps who rejected it and lists the requested claims`() {
        val rejections = mapOf(
            ActivityRejection.BY_USER to PresentationOutcomeUi.REJECTED_BY_USER,
            ActivityRejection.OVERASKING to PresentationOutcomeUi.OVERASKING,
            ActivityRejection.UNKNOWN_SERVICE to PresentationOutcomeUi.UNKNOWN_SERVICE,
            ActivityRejection.BY_SERVICE to PresentationOutcomeUi.REJECTED_BY_SERVICE,
            null to PresentationOutcomeUi.NOT_COMPLETED,
        )
        rejections.forEach { (rejection, outcome) ->
            val entry = presentation("1", now).copy(
                result = ActivityResult.NOT_COMPLETED,
                rejection = rejection,
                credentials = listOf(eaa().copy(requestedClaims = listOf(listOf("family_name")))),
            )

            val detail = mapper.toDetail(entry) as ActivityDetailUi.Presentation

            assertEquals(outcome, detail.outcome)
            assertEquals(listOf("Family name"), detail.credentials.single().claimLabels)
        }
    }

    @Test
    fun `every presentation that did not complete is listed as rejected`() {
        val rejected = presentation("1", now).copy(
            result = ActivityResult.NOT_COMPLETED,
            rejection = ActivityRejection.OVERASKING,
        )
        val interrupted = presentation("2", now).copy(result = ActivityResult.NOT_COMPLETED)

        val items = mapper.toSections(listOf(rejected, interrupted)).single().items

        assertEquals(listOf("Rejected · 12:00 PM", "Rejected · 12:00 PM"), items.map { it.subtitle })
        assertTrue(items.all { it.notCompleted })
    }

    @Test
    fun `the registered purpose is shown in the user's language`() {
        val entry = presentation("1", now).copy(
            purpose = listOf(
                LocalizedTextDomain(language = "de", value = "Altersprüfung"),
                LocalizedTextDomain(language = "en", value = "Age check"),
            ),
        )

        val detail = mapper.toDetail(entry) as ActivityDetailUi.Presentation

        assertEquals("Age check", detail.purpose)
    }

    @Test
    fun `an issuance shows the added credential and the claims it held`() {
        val entry = credentialEvent(ActivityType.ISSUANCE)

        val detail = mapper.toDetail(entry) as ActivityDetailUi.CredentialEvent

        assertEquals("Sozialpass added", detail.title)
        assertFalse(detail.deleted)
        assertEquals("Sozialpass", detail.card.description)
        assertEquals(listOf("Family name"), detail.credential.claimLabels)
    }

    @Test
    fun `a deletion is listed and detailed as deleted`() {
        val entry = credentialEvent(ActivityType.DELETION)

        val item = mapper.toSections(listOf(entry)).single().items.single()
        val detail = mapper.toDetail(entry) as ActivityDetailUi.CredentialEvent

        assertEquals("Sozialpass deleted", item.title)
        assertEquals("Sozialpass deleted", detail.title)
        assertTrue(detail.deleted)
        assertEquals(listOf("Family name"), detail.credential.claimLabels)
    }

    @Test
    fun `a deleted PID uses the digital ID's own title`() {
        val entry = credentialEvent(ActivityType.DELETION).copy(credentials = listOf(pid(PID_SD_JWT)))

        val detail = mapper.toDetail(entry) as ActivityDetailUi.CredentialEvent

        assertEquals("Digital ID deleted", detail.title)
        assertTrue(detail.isPid)
    }

    @Test
    fun `the PID always shows the eagle, whatever its metadata`() {
        val entry = credentialEvent(ActivityType.ISSUANCE).copy(credentials = listOf(pid(PID_SD_JWT)))

        val item = mapper.toSections(listOf(entry)).single().items.single()
        val detail = mapper.toDetail(entry) as ActivityDetailUi.CredentialEvent

        assertEquals(ActivityAvatarUi.PidEagle, item.avatar)
        assertEquals(ActivityAvatarUi.PidEagle, detail.credential.avatar)
    }

    @Test
    fun `a credential event without its credential is skipped instead of failing the list`() {
        val broken = credentialEvent(ActivityType.ISSUANCE).copy(id = "broken", credentials = emptyList())
        val entries = listOf(broken, presentation("2", now))

        val items = mapper.toSections(entries).flatMap { it.items }

        assertEquals(listOf("2"), items.map { it.id })
        assertNull(mapper.toDetail(broken))
    }

    private fun credentialEvent(type: ActivityType) = ActivityLogEntry(
        id = "1",
        timestampMillis = now.toEpochMilli(),
        type = type,
        result = ActivityResult.COMPLETED,
        interactingParty = InteractingParty(name = "Stadt Köln", isTrusted = true),
        credentials = listOf(eaa().copy(heldClaims = listOf(listOf("family_name")))),
    )

    private fun presentation(id: String, at: Instant) = ActivityLogEntry(
        id = id,
        timestampMillis = at.toEpochMilli(),
        type = ActivityType.PRESENTATION,
        result = ActivityResult.COMPLETED,
        interactingParty = InteractingParty(
            name = "Rewe",
            isTrusted = true,
            privacyPolicyUri = "https://rewe.example/privacy",
        ),
        credentials = listOf(pid(PID_SD_JWT)),
    )

    private fun pid(credentialIdentifier: String) = ActivityCredential(
        documentId = credentialIdentifier,
        credentialIdentifier = credentialIdentifier,
    )

    private fun eaa() = ActivityCredential(
        documentId = "eaa",
        credentialIdentifier = "urn:eaa:sozialpass",
        issuerMetadataJson = IssuerMetadata(
            documentConfigurationIdentifier = "sozialpass",
            display = listOf(IssuerMetadata.Display(name = "Sozialpass", locale = Locale.ENGLISH)),
            claims = listOf(
                IssuerMetadata.Claim(
                    path = listOf("family_name"),
                    display = listOf(IssuerMetadata.Claim.Display("Family name", Locale.ENGLISH)),
                )
            ),
            credentialIssuerIdentifier = "https://stadt.koeln",
            issuerDisplay = listOf(IssuerMetadata.IssuerDisplay("Stadt Köln", Locale.ENGLISH)),
        ).toJson(),
    )

    private companion object {
        const val DAY = 86_400L
        const val PID_MDOC = "eu.europa.ec.eudi.pid.1"
        const val PID_SD_JWT = "urn:eudi:pid:de:1"

        val STRINGS = mapOf(
            R.string.activities_overview_label_today to "Today",
            R.string.activities_overview_label_yesterday to "Yesterday",
            R.string.activities_overview_label_last_week to "Previous 7 days",
            R.string.activities_overview_label_last_month to "Previous 30 days",
            R.string.activities_overview_label_multiple_credentials to "Multiple credentials",
            R.string.activities_overview_label_rejected to "Rejected",
            R.string.activities_detail_title_eaa_issuance_dev to "%1\$s added",
            R.string.activities_detail_title_eaa_issuance_deleted_dev to "%1\$s deleted",
            R.string.activities_detail_title_pid_issuance to "Digital ID created",
            R.string.activities_detail_title_pid_issuance_deleted to "Digital ID deleted",
            R.string.pid_inspection_pid_details_title to "Digital ID",
            R.string.pid_presentation_data_consent_credential_subline to "Federal Republic of Germany",
            R.string.request_relying_party_default_name to "Relying party",
        )
    }
}
