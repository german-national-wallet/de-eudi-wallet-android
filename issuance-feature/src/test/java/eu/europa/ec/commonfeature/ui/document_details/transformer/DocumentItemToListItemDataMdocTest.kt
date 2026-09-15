package eu.europa.ec.commonfeature.ui.document_details.transformer

import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sprind.wallet.commonfeature.ui.transformer.mdoc.MdocKeys
import org.sprind.wallet.commonfeature.ui.transformer.toListItemData
import java.util.Locale

class DocumentItemToListItemDataMdocTest {

    @Test
    fun `toListItemData maps mdoc fields into ordered UI items`() {
        val resourceProvider = FakeResourceProvider(locale = Locale.GERMAN)

        val detailsItems = listOf(
            documentItem(MdocKeys.FAMILY_NAME, "MUSTERMANN", "Family name"),
            documentItem(MdocKeys.ISSUING_AUTHORITY, "DE", "Issuing authority"),
            documentItem(MdocKeys.RESIDENT_COUNTRY, "DE", "Resident country"),
            documentItem(MdocKeys.RESIDENT_POSTAL_CODE, "51147", "Resident Postal Code"),
            documentItem(MdocKeys.FAMILY_NAME_BIRTH, "GABLER", "Birth name"),
            documentItem(MdocKeys.RESIDENT_CITY, "KÖLN", "Resident city"),
            documentItem(MdocKeys.BIRTH_DATE, "12.Aug.1984", "Date of birth"),
            documentItem(MdocKeys.ISSUANCE_DATE, "09.Mar.2026", "Issuing date"),
            documentItem(MdocKeys.ISSUING_COUNTRY, "DE", "Issuing country"),
            documentItem(MdocKeys.GIVEN_NAME, "ERIKA", "Given name(s)"),
            documentItem(MdocKeys.NATIONALITY, "DE", "Nationality"),
            documentItem(MdocKeys.EXPIRY_DATE, "23.Mar.2026", "Valid until"),
            documentItem(MdocKeys.BIRTH_PLACE, "locality: BERLIN", "birth_place"),
            documentItem(MdocKeys.RESIDENT_STREET, "HEIDESTRAẞE 17", "Resident Street"),
            documentItem(MdocKeys.SOURCE_DOCUMENT_TYPE, "ID", "Document type"),
            documentItem(MdocKeys.ageOver(18), "true", "Age over 18"),
            documentItem(MdocKeys.ageOver(21), "true", "Age over 21"),
            documentItem(MdocKeys.ageOver(12), "true", "Age over 12"),
            documentItem(MdocKeys.ageOver(65), "false", "Age over 65"),
            documentItem(MdocKeys.ageOver(14), "true", "Age over 14"),
            documentItem(MdocKeys.ageOver(16), "true", "Age over 16"),
        )

        val result = detailsItems.toListItemData(
            resourceProvider = resourceProvider,
            documentFormat = MsoMdocFormat("eu.europa.ec.eudi.pid.1"),
            documentIdentifier = DocumentIdentifier.MdocPid,
        )

        assertThat(
            result.map { it.itemId },
            equalTo(
                listOf(
                    "family_name",
                    "family_name_birth",
                    "given_name",
                    MdocKeys.ADDRESS,
                    MdocKeys.NATIONALITY,
                    MdocKeys.BIRTH_DATE,
                    MdocKeys.BIRTH_PLACE,
                    MdocKeys.AGE_EQUAL_OR_OVER,
                    MdocKeys.ISSUING_AUTHORITY,
                    MdocKeys.ISSUING_COUNTRY,
                    MdocKeys.ISSUANCE_DATE,
                    MdocKeys.EXPIRY_DATE,
                )
            )
        )

        assertThat(
            result.map { it.overlineText },
            equalTo(
                listOf(
                    "Familienname",
                    "Geburtsname",
                    "Vorname(n)",
                    "Anschrift",
                    "Staatsangehörigkeit",
                    "Geburtsdatum",
                    "Geburtsort",
                    "Alter gleich oder über",
                    "Behörde",
                    "Ausstellender Staat",
                    "Erstellt am",
                    "Gültig bis",
                )
            )
        )

        assertTextValue(result[0], "MUSTERMANN")
        assertTextValue(result[1], "GABLER")
        assertTextValue(result[2], "ERIKA")

        assertTextValue(
            result[3],
            """
            HEIDESTRAẞE 17
            51147 KÖLN
            DEUTSCHLAND
            """.trimIndent()
        )

        assertTextValue(result[4], "Deutsch")
        assertTextValue(result[5], "12.Aug.1984")
        assertTextValue(result[6], "BERLIN")

        assertTextValue(
            result[7],
            """
            12 Jahre: Ja
            14 Jahre: Ja
            16 Jahre: Ja
            18 Jahre: Ja
            21 Jahre: Ja
            65 Jahre: Nein
            """.trimIndent()
        )

        assertTextValue(result[8], "DEUTSCHLAND")
        assertTextValue(result[9], "DEUTSCHLAND")
        assertTextValue(result[10], "09.Mar.2026")
        assertTextValue(result[11], "23.Mar.2026")
    }

    private fun assertTextValue(item: ListItemData, expected: String) {
        assertThat(item.mainContentData, instanceOf(ListItemMainContentData.Text::class.java))
        val content = item.mainContentData as ListItemMainContentData.Text
        assertThat(content.text, equalTo(expected))
    }

    private fun documentItem(
        key: String,
        value: String,
        readableName: String,
        docId: String = "random_test_id",
    ) = DocumentItem(
        elementIdentifier = key,
        value = value,
        readableName = readableName,
        docId = docId,
    )
}