package eu.europa.ec.commonfeature.ui.document_details.transformer

import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.util.Locale

class FakeResourceProvider(
    private val locale: Locale,
) : ResourceProvider {

    override fun getLocale(): Locale = locale
    override fun provideContext(): Context {
        return ApplicationProvider.getApplicationContext()
    }

    override fun provideContentResolver(): ContentResolver {
        return ApplicationProvider.getApplicationContext<Context>().contentResolver
    }

    override fun getString(resId: Int): String = when (resId) {
        R.string.pid_issuance_data_consent_label_name -> "Familienname"
        R.string.pid_issuance_data_consent_label_birth_name -> "Geburtsname"
        R.string.pid_issuance_data_consent_label_first_names -> "Vorname(n)"
        R.string.pid_issuance_data_consent_label_address -> "Anschrift"
        R.string.pid_issuance_data_consent_label_nationality -> "Staatsangehörigkeit"
        R.string.pid_issuance_data_consent_label_birth_date -> "Geburtsdatum"
        R.string.pid_issuance_data_consent_label_place_of_birth -> "Geburtsort"
        R.string.pid_issuance_data_consent_label_age_in_years -> "Alter in Jahren"
        R.string.pid_issuance_data_consent_label_age_birth_year -> "Geburtsjahr"
        R.string.pid_issuance_data_consent_label_age_equal_or_over -> "Alter gleich oder über"
        R.string.pid_issuance_data_consent_label_issuing_authority -> "Behörde"
        R.string.pid_issuance_data_consent_label_issuing_country -> "Ausstellender Staat"
        R.string.pid_issuance_data_consent_label_created_at -> "Erstellt am"
        R.string.pid_issuance_data_consent_label_expire_date -> "Gültig bis"
        R.string.pid_issuance_data_consent_label_age_equal_or_over_yes -> "Ja"
        R.string.pid_issuance_data_consent_label_age_equal_or_over_no -> "Nein"
        R.string.document_details_data_nationality_german -> "Deutsch"
        else -> error("Unhandled string resource id: $resId")
    }

    override fun getStringFromRaw(resId: Int): String {
        return "You are using a fake resource provider, verify your tests"
    }

    override fun getQuantityString(
        resId: Int,
        quantity: Int,
        vararg formatArgs: Any
    ): String {
        return "You are using a fake resource provider, verify your tests"
    }

    override fun getString(resId: Int, vararg formatArgs: Any): String = when (resId) {
        R.string.document_details_data_age_over -> "${formatArgs[0]} Jahre:"
        else -> error("Unhandled formatted string resource id: $resId")
    }

    override fun genericErrorMessage(): String {
        return "You are using a fake resource provider, verify your tests"
    }

    override fun genericNetworkErrorMessage(): String {
        return "Something went wrong"
    }
}