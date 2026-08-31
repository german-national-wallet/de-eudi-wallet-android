package eu.europa.ec.uilogic.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

@Composable
fun BulletPointText(text: String) {
    val bulletColor = MaterialTheme.colorScheme.onSurface

    Row(Modifier.padding(SPACING_EXTRA_SMALL.dp)) {

        Canvas(
            modifier = Modifier
                .padding(
                    top = SPACING_SMALL.dp,
                    bottom = SPACING_SMALL.dp,
                    end = SPACING_SMALL.dp,
                    start = SPACING_EXTRA_SMALL.dp
                )
                .size(SPACING_EXTRA_SMALL.dp)
        ) {
            drawCircle(bulletColor)
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun BulletPointText(text: AnnotatedString) {
    val bulletColor = MaterialTheme.colorScheme.onSurface

    Row(Modifier.padding(SPACING_EXTRA_SMALL.dp)) {

        Canvas(
            modifier = Modifier
                .padding(
                    top = SPACING_SMALL.dp,
                    bottom = SPACING_SMALL.dp,
                    end = SPACING_SMALL.dp,
                    start = SPACING_EXTRA_SMALL.dp
                )
                .size(SPACING_EXTRA_SMALL.dp)
        ) {
            drawCircle(bulletColor)
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}