package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM

/**
 * A note beside the content it qualifies, optionally led by a mark such as a warning.
 *
 * The mark and the note are announced as one phrase, so the note is never read without knowing what
 * kind of note it is.
 */
@Composable
fun Banner(
    modifier: Modifier = Modifier,
    body: String,
    icon: IconData? = null,
) {
    BannerCard(modifier = modifier, icon = icon) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun Banner(
    modifier: Modifier = Modifier,
    body: AnnotatedString,
    icon: IconData? = null,
) {
    BannerCard(modifier = modifier, icon = icon) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BannerCard(
    modifier: Modifier,
    icon: IconData?,
    body: @Composable () -> Unit,
) {
    WrapCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(SPACING_MEDIUM.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            // The mark sits with the first line, not in the middle of a wrapped note.
            verticalAlignment = Alignment.Top,
        ) {
            icon?.let {
                WrapIcon(
                    iconData = it,
                    modifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                    customTint = MaterialTheme.colorScheme.onSurface,
                )
            }
            body()
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSuccessMessageCardPreview() {
    PreviewTheme {
        Banner(
            body = "Your documents have been securely presented and saved."
        )
    }
}