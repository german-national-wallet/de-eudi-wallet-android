package eu.europa.ec.commonfeature.ui.document_details.transformer

import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sprind.wallet.commonfeature.ui.transformer.sdjwt.SdJwtKeys
import org.sprind.wallet.commonfeature.ui.transformer.toListItemData
import java.util.Locale

class DocumentItemToListItemDataSdJwtTest {

    @Test
    fun `toListItemData maps sd-jwt fields into ordered UI items`() {
        val resourceProvider = FakeResourceProvider(locale = Locale.GERMANY)

        val detailsItems = listOf(
            documentItem(SdJwtKeys.FAMILY_NAME, "MUSTERMANN", "Family name"),
            documentItem(SdJwtKeys.GIVEN_NAME, "ERIKA", "Given name(s)"),
            documentItem(SdJwtKeys.FAMILY_BIRTH_NAME, "GABLER", "Birth name"),
            documentItem(SdJwtKeys.BIRTHDATE, "12.Aug.1984", "Date of birth"),
            documentItem(SdJwtKeys.NATIONALITIES, "DE", "Nationality"),
            documentItem(SdJwtKeys.ISSUING_AUTHORITY, "DE", "Issuing authority"),
            documentItem(SdJwtKeys.ISSUING_COUNTRY, "DE", "Issuing country"),

            documentItem(SdJwtKeys.ADDRESS_LOCALITY, "KÖLN", "address.locality"),
            documentItem(SdJwtKeys.ADDRESS_POSTAL_CODE, "51147", "address.postal_code"),
            documentItem(SdJwtKeys.ADDRESS_STREET_ADDRESS, "HEIDESTRAẞE 17", "address.street_address"),
            documentItem(SdJwtKeys.ADDRESS_COUNTRY, "DE", "address.country"),

            documentItem(SdJwtKeys.PLACE_OF_BIRTH_LOCALITY, "BERLIN", "place_of_birth.locality"),
            // value from the credential so flattening is also tested when using the ext function in the transformer
            documentItem(SdJwtKeys.AGE_EQUAL_OR_OVER, "12: true\n" +
                    "14: true\n" +
                    "16: true\n" +
                    "18: true\n" +
                    "21: true\n" +
                    "65: false", "age_equal_or_over"),

            documentItem(SdJwtKeys.ISSUED_AT, "1741132800", "iat"),
            documentItem(SdJwtKeys.EXPIRED, "1742342400", "exp"),
        )

        val result = detailsItems.toListItemData(
            resourceProvider = resourceProvider,
            documentFormat = SdJwtVcFormat("urn:eudi:pid:de:1"),
            documentIdentifier = DocumentIdentifier.SdJwtPid,
        )

        assertThat(
            result.map { it.itemId },
            equalTo(
                listOf(
                    SdJwtKeys.FAMILY_NAME,
                    SdJwtKeys.FAMILY_BIRTH_NAME,
                    SdJwtKeys.GIVEN_NAME,
                    SdJwtKeys.ADDRESS,
                    SdJwtKeys.NATIONALITIES,
                    SdJwtKeys.PLACE_OF_BIRTH,
                    SdJwtKeys.AGE_EQUAL_OR_OVER,
                    SdJwtKeys.ISSUING_AUTHORITY,
                    SdJwtKeys.ISSUING_COUNTRY,
                    SdJwtKeys.ISSUED_AT,
                    SdJwtKeys.EXPIRED,
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

        assertTextValue(result[4], "DEUTSCHLAND")
        assertTextValue(result[5], "BERLIN")

        assertTextValue(
            result[6],
            """
            12 Jahre: Ja
            14 Jahre: Ja
            16 Jahre: Ja
            18 Jahre: Ja
            21 Jahre: Ja
            65 Jahre: Nein
            """.trimIndent()
        )
        assertTextValue(result[7], "DEUTSCHLAND")
        assertTextValue(result[8], "DEUTSCHLAND")
        assertTextValue(result[9], "05.03.2025")
        assertTextValue(result[10], "19.03.2025")
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
        docId: String = "random_docId_for_test",
    ) = DocumentItem(
        elementIdentifier = key,
        value = value,
        readableName = readableName,
        docId = docId,
    )
}