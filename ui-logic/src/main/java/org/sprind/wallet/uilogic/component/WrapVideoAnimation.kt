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

package org.sprind.wallet.uilogic.component

import android.content.ContentResolver
import android.graphics.Color
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// ExoPlayer and PlayerView are marked unstable by Media3; review API changes during upgrades:
// https://developer.android.com/media/media3/exoplayer/migration-guide#unstableapi
@OptIn(UnstableApi::class)
@Composable
fun WrapVideoAnimation(
    @RawRes video: Int,
    @DrawableRes placeholder: Int,
    isPlaying: Boolean = true,
) {
    val context = LocalContext.current
    var hasRenderedFirstFrame by remember(video) { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    LaunchedEffect(video) {
        hasRenderedFirstFrame = false
        player.setMediaItem(
            MediaItem.fromUri(
                Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.packageName).path(video.toString()).build()
            )
        )
        player.prepare()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying) {
        if (isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // The static WebP prevents a black flash while the video player prepares its first frame.
        Image(
            painter = painterResource(placeholder),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopCenter
        )

        AndroidView(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Reveal the player only after it has rendered a frame over the placeholder.
                .graphicsLayer { alpha = if (hasRenderedFirstFrame) 1f else 0f },
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(Color.TRANSPARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = player },
        )
    }
}