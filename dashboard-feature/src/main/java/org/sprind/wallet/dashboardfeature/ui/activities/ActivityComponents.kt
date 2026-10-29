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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityAvatarUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityListItemUi
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.extension.regular

@Composable
internal fun ActivityAvatar(
    avatar: ActivityAvatarUi,
    modifier: Modifier = Modifier,
    size: Dp = AVATAR_SIZE,
) {
    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)
        .clearAndSetSemantics { }
    when (avatar) {
        is ActivityAvatarUi.Initials -> {
            val background = parseCssColor(avatar.backgroundColor, MaterialTheme.colorScheme.surfaceContainerHigh)
            Box(
                modifier = avatarModifier.background(background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatar.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (avatar.backgroundColor == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        background.contentColor()
                    },
                )
            }
        }

        ActivityAvatarUi.PidEagle -> Image(
            painter = painterResource(R.drawable.pid_card_eagle),
            contentDescription = null,
            modifier = avatarModifier,
            contentScale = ContentScale.Fit,
        )

        is ActivityAvatarUi.Logo -> {
            var failed by remember(avatar.uri) { mutableStateOf(false) }
            val background = avatar.fallback.backgroundColor
            if (failed) {
                ActivityAvatar(avatar = avatar.fallback, modifier = modifier, size = size)
            } else if (background == null) {
                WrapAsyncImage(
                    source = avatar.uri,
                    modifier = avatarModifier,
                    onError = { failed = true },
                )
            } else {
                Box(
                    modifier = avatarModifier.background(parseCssColor(background, Color.Transparent)),
                    contentAlignment = Alignment.Center,
                ) {
                    WrapAsyncImage(
                        source = avatar.uri,
                        modifier = Modifier.padding(LOGO_INSET),
                        contentScale = ContentScale.Fit,
                        onError = { failed = true },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActivityRow(
    item: ActivityListItemUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ROW_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityAvatar(avatar = item.avatar)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitleColor = if (item.notCompleted) ThemeColors.warningOutline else ThemeColors.onBackgroundVariant
            Row(
                horizontalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.notCompleted) {
                    WrapIcon(
                        iconData = AppIcons.Warning,
                        modifier = Modifier.size(SUBTITLE_ICON_SIZE),
                        customTint = subtitleColor,
                    )
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium.regular(),
                    color = subtitleColor,
                )
            }
        }
        Box(
            modifier = Modifier.size(SIZE_EXTRA_LARGE.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            WrapIcon(
                iconData = AppIcons.ArrowRightLong,
                modifier = Modifier.size(ARROW_SIZE),
                customTint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A secondary button that leaves the screen for a page, as the trailing mark says. */
@Composable
internal fun ExternalLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WrapButton(
        modifier = modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(type = ButtonType.SECONDARY, onClick = onClick),
    ) {
        TextAndIcon(
            textValue = text,
            textConfig = TextConfig(
                style = ThemeTextStyles.onSecondaryButton,
                color = ThemeColors.onSecondaryButton,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            ),
            rightIconData = AppIcons.OpenNew,
            iconModifier = Modifier
                .padding(start = SPACING_SMALL.dp)
                .size(EXTERNAL_LINK_ICON_SIZE),
            customTint = ThemeColors.onSecondaryButton,
        )
    }
}

internal fun Color.contentColor(): Color = if (luminance() > 0.5f) Color.Black else Color.White

internal val AVATAR_SIZE = 40.dp
private val ROW_VERTICAL_PADDING = 10.dp
private val LOGO_INSET = 6.dp
private val SUBTITLE_ICON_SIZE = 14.dp
private val ARROW_SIZE = 20.dp
private val EXTERNAL_LINK_ICON_SIZE = 18.dp
