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

package eu.europa.ec.uilogic.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import eu.europa.ec.resourceslogic.R

/**
 * Data class to be used when we want to display an Icon.
 * @param resourceId The id of the icon. Can be null
 * @param contentDescriptionId The id of its content description.
 * @param imageVector The [ImageVector] of the icon, null by default.
 * @throws IllegalArgumentException If both [resourceId] AND [imageVector] are null.
 */
@Stable
data class IconData(
    @DrawableRes val resourceId: Int?,
    @StringRes val contentDescriptionId: Int,
    val imageVector: ImageVector? = null,
) {
    init {
        require(
            resourceId == null && imageVector != null
                    || resourceId != null && imageVector == null
                    || resourceId != null && imageVector != null
        ) {
            "An Icon should at least have a valid resourceId or a valid imageVector."
        }
    }
}

/**
 * A Singleton object responsible for providing access to all the app's Icons.
 */
object AppIcons {

    val ArrowBack: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_arrow_back_icon,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
    )

    val ArrowRight: IconData = IconData(
        resourceId = R.drawable.ic_arrow_right,
        contentDescriptionId = R.string.content_description_arrow_right_icon,
        imageVector = null,
    )

    val ArrowForward: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_arrow_forward_icon,
        imageVector = Icons.AutoMirrored.Filled.ArrowForward
    )

    val Close: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_close_icon,
        imageVector = Icons.Filled.Close
    )

    val Share: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_share_icon,
        imageVector = Icons.Filled.Share
    )

    val Key: IconData = IconData(
        resourceId = R.drawable.ic_key,
        contentDescriptionId = R.string.content_description_key_icon,
        imageVector = null
    )

    val Lock: IconData = IconData(
        resourceId = R.drawable.ic_lock,
        contentDescriptionId = R.string.content_description_lock_icon,
        imageVector = null
    )

    val CopyContent: IconData = IconData(
        resourceId = R.drawable.ic_copy_content,
        contentDescriptionId = R.string.content_description_copy,
        imageVector = null
    )

    val VerticalMore: IconData = IconData(
        resourceId = R.drawable.ic_more,
        contentDescriptionId = R.string.content_description_more_vert_icon,
        imageVector = null
    )

    val Warning: IconData = IconData(
        resourceId = R.drawable.ic_warning,
        contentDescriptionId = R.string.content_description_warning_icon,
        imageVector = null
    )

    val Error: IconData = IconData(
        resourceId = R.drawable.ic_error,
        contentDescriptionId = R.string.content_description_error_icon,
        imageVector = null
    )

    val TouchId: IconData = IconData(
        resourceId = R.drawable.ic_touch_id,
        contentDescriptionId = R.string.content_description_touch_id_icon,
        imageVector = null
    )

    val QR: IconData = IconData(
        resourceId = R.drawable.ic_qr,
        contentDescriptionId = R.string.content_description_qr_icon,
        imageVector = null
    )

    val User: IconData = IconData(
        resourceId = R.drawable.ic_user,
        contentDescriptionId = R.string.content_description_user_icon,
        imageVector = null
    )

    val Id: IconData = IconData(
        resourceId = R.drawable.ic_id,
        contentDescriptionId = R.string.content_description_id_icon,
        imageVector = null
    )

    val LogoPlain: IconData = IconData(
        resourceId = R.drawable.ic_logo_plain,
        contentDescriptionId = R.string.content_description_logo_plain_icon,
        imageVector = null
    )

    val LogoText: IconData = IconData(
        resourceId = R.drawable.ic_logo_text,
        contentDescriptionId = R.string.content_description_logo_text_icon,
        imageVector = null
    )

    val KeyboardArrowDown: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_arrow_down_icon,
        imageVector = Icons.Default.KeyboardArrowDown
    )

    val KeyboardArrowUp: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_arrow_up_icon,
        imageVector = Icons.Default.KeyboardArrowUp
    )

    val Visibility: IconData = IconData(
        resourceId = R.drawable.ic_visibility_on,
        contentDescriptionId = R.string.content_description_visibility_icon,
        imageVector = null
    )

    val VisibilityOff: IconData = IconData(
        resourceId = R.drawable.ic_visibility_off,
        contentDescriptionId = R.string.content_description_visibility_off_icon,
        imageVector = null
    )

    val Add: IconData = IconData(
        resourceId = R.drawable.ic_add,
        contentDescriptionId = R.string.content_description_add_icon,
        imageVector = null
    )

    val Edit: IconData = IconData(
        resourceId = R.drawable.ic_edit,
        contentDescriptionId = R.string.content_description_edit_icon,
        imageVector = null
    )

    val Sign: IconData = IconData(
        resourceId = R.drawable.ic_sign_document,
        contentDescriptionId = R.string.content_description_edit_icon,
        imageVector = null
    )

    val Verified: IconData = IconData(
        resourceId = R.drawable.ic_verified,
        contentDescriptionId = R.string.content_description_verified_icon,
        imageVector = null
    )

    val Message: IconData = IconData(
        resourceId = R.drawable.ic_message,
        contentDescriptionId = R.string.content_description_message_icon,
        imageVector = null
    )

    val ClockTimer: IconData = IconData(
        resourceId = R.drawable.ic_clock_timer,
        contentDescriptionId = R.string.content_description_clock_timer_icon,
        imageVector = null
    )

    val OpenNew: IconData = IconData(
        resourceId = R.drawable.ic_open_new,
        contentDescriptionId = R.string.content_description_open_new_icon,
        imageVector = null
    )

    val KeyboardArrowRight: IconData = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_arrow_right_icon,
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
    )

    val HandleBar: IconData = IconData(
        resourceId = R.drawable.ic_handle_bar,
        contentDescriptionId = R.string.content_description_handle_bar_icon,
        imageVector = null
    )

    val Search: IconData = IconData(
        resourceId = R.drawable.ic_search,
        contentDescriptionId = R.string.content_description_search_icon,
        imageVector = null
    )

    val PresentDocumentInPerson: IconData = IconData(
        resourceId = R.drawable.ic_present_document_same_device,
        contentDescriptionId = R.string.content_description_present_document_same_device_icon,
        imageVector = null
    )

    val PresentDocumentOnline: IconData = IconData(
        resourceId = R.drawable.ic_present_document_cross_device,
        contentDescriptionId = R.string.content_description_present_document_cross_device_icon,
        imageVector = null
    )

    val Documents: IconData = IconData(
        resourceId = R.drawable.ic_documents,
        contentDescriptionId = R.string.content_description_documents_icon,
        imageVector = null
    )

    val Filters: IconData = IconData(
        resourceId = R.drawable.ic_filters,
        contentDescriptionId = R.string.content_description_filters_icon,
        imageVector = null
    )

    val Home: IconData = IconData(
        resourceId = R.drawable.ic_home,
        contentDescriptionId = R.string.content_description_home_icon,
        imageVector = null
    )

    val Menu: IconData = IconData(
        resourceId = R.drawable.ic_menu,
        contentDescriptionId = R.string.content_description_menu_icon,
        imageVector = null
    )

    val Contract: IconData = IconData(
        resourceId = R.drawable.ic_contract,
        contentDescriptionId = R.string.content_description_signature_icon,
        imageVector = null
    )

    val InProgress: IconData = IconData(
        resourceId = R.drawable.ic_in_progress,
        contentDescriptionId = R.string.content_description_in_progress_icon,
        imageVector = null
    )

    val Transactions: IconData = IconData(
        resourceId = R.drawable.ic_transactions,
        contentDescriptionId = R.string.content_description_transactions_icon,
        imageVector = null
    )

    val WalletActivated: IconData = IconData(
        resourceId = R.drawable.ic_wallet_activated,
        contentDescriptionId = R.string.content_description_wallet_activated_icon,
        imageVector = null
    )

    val Info: IconData = IconData(
        resourceId = R.drawable.ic_info,
        contentDescriptionId = R.string.content_description_info_icon,
        imageVector = null
    )

    val IdCards: IconData = IconData(
        resourceId = R.drawable.ic_authenticate_id_cards,
        contentDescriptionId = R.string.content_description_issuer_icon,
        imageVector = null
    )

    val Check: IconData = IconData(
        resourceId = R.drawable.ic_check,
        contentDescriptionId = R.string.content_description_check,
        imageVector = null
    )
    val Certificate: IconData = IconData(
        resourceId = R.drawable.certificate,
        contentDescriptionId = R.string.content_description_certified_icon,
        imageVector = null
    )
    val Signature: IconData = IconData(
        resourceId = R.drawable.ic_sign_document,
        contentDescriptionId = R.string.content_description_signature_icon,
        imageVector = null
    )
    val Building: IconData = IconData(
        resourceId = R.drawable.building,
        contentDescriptionId = R.string.content_description_building_icon,
        imageVector = null
    )
    val Calendar: IconData = IconData(
        resourceId = R.drawable.calendar,
        contentDescriptionId = R.string.content_description_available_until,
        imageVector = null
    )
    val Globe: IconData = IconData(
        resourceId = R.drawable.globe,
        contentDescriptionId = R.string.content_description_globe,
        imageVector = null
    )

    val PlayArrow: IconData = IconData(
        resourceId = R.drawable.ic_arrow_right,
        contentDescriptionId = R.string.content_description_play_arrow_icon,
        imageVector = null
    )

    val Apartment = IconData(
        resourceId = R.drawable.ic_apartment,
        contentDescriptionId = R.string.content_description_apartment_icon,
        imageVector = null
    )

    val Event = IconData(
        resourceId = R.drawable.ic_event,
        contentDescriptionId = R.string.content_description_event_icon,
        imageVector = null
    )

    val History = IconData(
        resourceId = R.drawable.ic_history,
        contentDescriptionId = R.string.content_description_history_icon,
        imageVector = null
    )

    val UserBw = IconData(
        resourceId = R.drawable.ic_user_bw,
        contentDescriptionId = R.string.content_description_user_bw_icon,
        imageVector = null
    )

    val Card = IconData(
        resourceId = R.drawable.ic_card,
        contentDescriptionId = R.string.content_description_card_icon,
        imageVector = null
    )

    val NoPAOnDevice = IconData(
        resourceId = R.drawable.no_pa_on_device,
        contentDescriptionId = R.string.content_description_unlock_phone_icon,
        imageVector = null
    )

    val OnboardingSampleIdCard = IconData(
        resourceId = R.drawable.onboarding_card,
        contentDescriptionId = R.string.card_onboarding_image_description,
        imageVector = null
    )

    val OnboardingWithEidAndPin = IconData(
        resourceId = R.drawable.onboarding_eid_pin_logo,
        contentDescriptionId = R.string.onboarding_eid_pin_logo_description,
        imageVector = null
    )

    val ArrowOutward = IconData(
        resourceId = R.drawable.arrow_outward,
        contentDescriptionId = R.string.content_description_arrow_outward,
        imageVector = null
    )

    val EidCanShow = IconData(
        resourceId = R.drawable.eid_can_show_icon,
        contentDescriptionId = R.string.content_description_eid_can_show,
        imageVector = null
    )

    val NoDocumentAvailable = IconData(
        resourceId = R.drawable.ic_no_document_available,
        contentDescriptionId = R.string.content_description_atom_credential_icon,
        imageVector = null
    )

    val Iphone = IconData(
        resourceId = R.drawable.ic_iphone,
        contentDescriptionId = R.string.content_description_iphone,
        imageVector = null
    )

    val PhoneSecurity = IconData(
        resourceId = R.drawable.ic_phone_security,
        contentDescriptionId = R.string.content_description_phone_security,
        imageVector = null
    )

    val Blocked = IconData(
        resourceId = R.drawable.ic_blocked,
        contentDescriptionId = R.string.content_description_blocked,
        imageVector = null
    )

    val WarningHex = IconData(
        resourceId = R.drawable.ic_warning_hex,
        contentDescriptionId = R.string.content_description_warning,
    )

    val PhoneVector = IconData(
        resourceId = R.drawable.ic_phone_vector,
        contentDescriptionId = R.string.content_description_phone_vector,
        imageVector = null
    )

    val NfcNotActivated = IconData(
        resourceId = R.drawable.nfc_not_activated,
        contentDescriptionId = R.string.content_description_nfc_not_activated,
        imageVector = null
    )

    val InfoFilled = IconData(
        resourceId = R.drawable.ic_info_filled,
        contentDescriptionId = R.string.content_description_image_or_placeholder_icon,
        imageVector = null
    )
    val Balance = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_institution,
        imageVector = Icons.Outlined.Balance
    )
    val Location = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_location,
        imageVector = Icons.Outlined.LocationOn
    )
    val EmailGlobe = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_email,
        imageVector = Icons.Default.Language
    )
    val Shield = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_verified_user,
        imageVector = Icons.Outlined.VerifiedUser
    )
    val Store = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_validity,
        imageVector = Icons.Outlined.Storefront
    )

    val LetterTransportPin = IconData(
        resourceId = R.drawable.transport_pin_letter,
        contentDescriptionId = R.string.content_description_transport_pin_letter,
        imageVector = null
    )

    val LetterHighlightingTransportPin = IconData(
        resourceId = R.drawable.letter_highlighting_transport_pin,
        contentDescriptionId = R.string.content_description_letter_highlighting_transport_pin,
        imageVector = null
    )

    val EidPinSet = IconData(
        resourceId = R.drawable.ic_eid_pin_set,
        contentDescriptionId = R.string.content_description_eid_pin_set,
        imageVector = null
    )

    val PinBlocked = IconData(
        resourceId = R.drawable.ic_pin_blocked,
        contentDescriptionId = R.string.content_description_error_pin,
        imageVector = null
    )

    val Copy = IconData(
        resourceId = R.drawable.ic_copy,
        contentDescriptionId = R.string.content_description_copy,
        imageVector = null,
    )

    val Help = IconData(
        resourceId = R.drawable.ic_help,
        contentDescriptionId = R.string.content_description_help,
        imageVector = null,
    )

    /**
     * The long "continue" arrow the card-shaped navigation buttons carry. It is a monochrome glyph
     * with a baked-in fill, so it has to be drawn through [eu.europa.ec.uilogic.component.wrap.WrapIcon]
     * with a tint to follow the theme.
     */
    val ArrowRightLong = IconData(
        resourceId = R.drawable.ic_arrow_right_long,
        contentDescriptionId = R.string.content_description_arrow_right_icon,
        imageVector = null,
    )

    /**
     * The eID-function mark. Its gradient carries the brand, so draw it with
     * [eu.europa.ec.uilogic.component.wrap.WrapImage] and never tint it.
     */
    val EidLogo = IconData(
        resourceId = R.drawable.eid_logo,
        contentDescriptionId = R.string.content_description_eid_logo,
        imageVector = null,
    )

    val NationalIdCard = IconData(
        resourceId = R.drawable.personal_ausweis,
        contentDescriptionId = R.string.content_description_national_id_card,
        imageVector = null,
    )

    val ResidencePermitCard = IconData(
        resourceId = R.drawable.aufenthaltstitel_image,
        contentDescriptionId = R.string.content_description_residence_permit_card,
        imageVector = null,
    )

    /** A handset, for the actions that place a call. */
    val Call = IconData(
        resourceId = null,
        contentDescriptionId = R.string.content_description_call_icon,
        imageVector = Icons.Filled.Call,
    )

    /** The two ID documents stacked behind the card PIN pad, used by the card PIN question. */
    val StackedIdCardsWithPin = IconData(
        resourceId = R.drawable.stacked_cards_new,
        contentDescriptionId = R.string.content_description_stacked_id_cards_with_pin,
        imageVector = null,
    )

    /** The citizen's office cityscape shown when the card PIN can only be set on site. */
    val CitizenOffice = IconData(
        resourceId = R.drawable.citizen_office,
        contentDescriptionId = R.string.content_description_citizen_office,
        imageVector = null,
    )

    /** Pin letter stacked illustration */
    val PinLetterStack = IconData(
        resourceId = R.drawable.pin_letters_stacked,
        contentDescriptionId = R.string.content_description_pin_letter_stacked,
        imageVector = null,
    )

    /** The contactless mark, shown while the device waits for a card to be held against it. */
    val Contactless = IconData(
        resourceId = R.drawable.ic_contactless,
        contentDescriptionId = R.string.content_description_nfc_icon,
        imageVector = null,
    )

    /** Circled checkmark that reports a step of the flow as done. */
    val SuccessCheckmark = IconData(
        resourceId = R.drawable.ic_success_checkmark,
        contentDescriptionId = R.string.content_description_success_icon,
        imageVector = null,
    )

    /** Circled cross that reports a card read as failed. */
    val ScanError = IconData(
        resourceId = R.drawable.ic_scan_error,
        contentDescriptionId = R.string.content_description_error_icon,
        imageVector = null,
    )
}
