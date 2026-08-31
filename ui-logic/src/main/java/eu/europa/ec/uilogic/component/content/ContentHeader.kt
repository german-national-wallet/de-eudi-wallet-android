package eu.europa.ec.uilogic.component.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIconAndTextData
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.TextLengthPreviewProvider
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.clickableNoRipple

/**
 * Data class representing the configuration for a content header.
 * This header typically displays information like app icon, name, description,
 * and potentially relying party details.
 *
 * @property appIconAndTextData Data for displaying the app icon and text.
 * @property description A descriptive text for the content.
 * @property descriptionTextConfig Configuration for the appearance of the description text.
 * @property purposeText The main title or heading text. If purpose text exist, a new section will be displayed
 * @property purposeTextConfig Configuration for the appearance of the main text.
 * @property relyingPartyData Data for displaying information about the relying party, if applicable.
 * @property displayDetailsButton Whether to display a button to view details.
 * @property detailsButtonAction Action to perform when the button is clicked.
 */
data class ContentHeaderConfig(
    val appIconAndTextData: AppIconAndTextData = AppIconAndTextData(),
    val title: String? = null,
    val subTitle: String? = null,
    val titleTextConfig: TextConfig? = null,
    val description: String? = null,
    val descriptionTextConfig: TextConfig? = null,
    val purposeText: String? = null,
    val purposeTextConfig: TextConfig? = null,
    val importantDataTitleConfig: TextConfig? = null,
    val importantDataBodyConfig: TextConfig? = null,
    val relyingPartyData: RelyingPartyData? = null,
    val importantInformationAction: () -> Unit = {},
)

/**
 * Composable function that displays the content header for the screen.
 *
 * This function displays the app icon and text, description, main text, and relying party information
 * based on the provided [ContentHeaderConfig].
 *
 * @param modifier Modifier used to adjust the layout of the header.
 * @param config Configuration object containing data for the header content.
 */
@Composable
fun ContentHeader(
    modifier: Modifier = Modifier,
    config: ContentHeaderConfig,
) {
    val commonTextAlign = TextAlign.Left

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        with(config) {

            // title
            title?.let { title ->
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SPACING_SMALL.dp)
                        .padding(bottom = SPACING_LARGE.dp),
                    text = title,
                    textConfig = purposeTextConfig ?: TextConfig(
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = commonTextAlign,
                        maxLines = 5,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                )
            }

            subTitle?.let {
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SPACING_SMALL.dp)
                        .padding(bottom = SPACING_LARGE.dp),
                    text = subTitle,
                    textConfig = purposeTextConfig ?: TextConfig(
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = commonTextAlign,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }


            description?.let { safeDescription ->
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SPACING_SMALL.dp)
                        .padding(bottom = SPACING_LARGE.dp),
                    text = safeDescription,
                    textConfig = descriptionTextConfig ?: TextConfig(
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = commonTextAlign,
                        maxLines = Int.MAX_VALUE,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            purposeText?.let {
                // Purpose section
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                    text = stringResource(R.string.request_header_purpose_title),
                    textConfig = purposeTextConfig ?: TextConfig(
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = commonTextAlign,
                    )
                )
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SPACING_LARGE.dp),
                    text = purposeText,
                    textConfig = purposeTextConfig ?: TextConfig(
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = commonTextAlign,
                    )
                )
                // important data
                WrapText(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(R.string.request_header_important_data_title),
                    textConfig = importantDataTitleConfig ?: TextConfig(
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = commonTextAlign,
                    )
                )

                TextAndIcon(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SPACING_MEDIUM.dp)
                        .align(alignment = Alignment.CenterHorizontally)
                        .clickableNoRipple(enabled = true, onClick = importantInformationAction),
                    textValue = stringResource(R.string.pid_presentation_data_consent_title),
                    textConfig = importantDataBodyConfig ?: TextConfig(
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = commonTextAlign,
                    ),
                    rightIconData = IconData(
                        resourceId = R.drawable.ic_info,
                        contentDescriptionId = R.string.content_description_info_icon
                    ),
                    customTint = MaterialTheme.colorScheme.onSurface,
                    horizontalArrangement = Arrangement.Absolute.Left,
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentHeaderPreview(
    @PreviewParameter(TextLengthPreviewProvider::class) text: String,
) {
    PreviewTheme {
        ContentHeader(
            config = ContentHeaderConfig(
                appIconAndTextData = AppIconAndTextData(
                    appIcon = AppIcons.LogoPlain,
                    appText = AppIcons.LogoText,
                ),
                purposeText = "Title: $text",
                relyingPartyData = RelyingPartyData(
                    isVerified = true,
                    name = "Relying Party Name: $text",
                    description = "Relying Party Description: $text",
                ),
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentHeaderRpInfoPreview(
    @PreviewParameter(TextLengthPreviewProvider::class) text: String,
) {
    PreviewTheme {
        ContentHeader(
            config = ContentHeaderConfig(
                title = stringResource(R.string.pid_presentation_rp_info_title),
                subTitle = text,
                description = stringResource(R.string.pid_presentation_rp_info_paragraph_1) + "\n\n" + stringResource(
                    R.string.pid_presentation_rp_info_paragraph_2
                ),
            )
        )
    }
}