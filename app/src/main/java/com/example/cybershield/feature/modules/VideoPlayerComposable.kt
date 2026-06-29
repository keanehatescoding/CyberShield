package com.example.cybershield.feature.modules

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoPlayerComposable(
    videoUrl: String,
    savedPosition: Long,
    playbackSpeed: Float,
    onVideoEnded: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create ExoPlayer once — survives recomposition
    val player =
        remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                prepare()
                // Seek to saved position before playing
                if (savedPosition > 0L) seekTo(savedPosition)
                playWhenReady = true
            }
        }

    // Apply playback speed when it changes
    LaunchedEffect(playbackSpeed) {
        player.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Save position every 5 seconds while playing
    LaunchedEffect(player) {
        while (isActive) {
            delay(5_000L.milliseconds)
            if (player.isPlaying) {
                onPositionChanged(player.currentPosition)
            }
        }
    }

    // Listen for video ended event
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        onVideoEnded()
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Lifecycle — pause on background, resume on foreground, release on exit
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        // Save position when going to background
                        onPositionChanged(player.currentPosition)
                        player.pause()
                    }

                    Lifecycle.Event.ON_RESUME -> player.play()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onPositionChanged(player.currentPosition) // save on exit
            player.release()
        }
    }

    // Bridge ExoPlayer's PlayerView into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                // Show built-in playback controls
                useController = true
            }
        },
        modifier = modifier,
    )
}
