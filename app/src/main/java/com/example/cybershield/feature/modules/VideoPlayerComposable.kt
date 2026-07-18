package com.example.cybershield.feature.modules

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
    // Fired on any playback failure (bad URL, network error, unsupported
    // codec, expired Storage token, etc). Previously nothing overrode
    // Player.Listener.onPlayerError, so failures were silent — the user
    // just saw a frozen/black player with no explanation and no retry path.
    onPlaybackError: (String) -> Unit = {},
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
                playWhenReady = true
            }
        }

    // Seek when saved position arrives (may be async-loaded from DB)
    LaunchedEffect(savedPosition) {
        if (savedPosition > 0L) player.seekTo(savedPosition)
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

    // Listen for video ended / error events
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        onVideoEnded()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onPlaybackError(error.message ?: "Video playback failed.")
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Whether the player was actually playing (not just present) the moment
    // we backgrounded it — distinct from whether the *user* had paused it
    // themselves beforehand. Without this, ON_RESUME unconditionally called
    // player.play(), so a user who deliberately paused, switched away, and
    // came back would find the video auto-resuming against their wishes.
    var wasPlayingBeforeBackground by remember { mutableStateOf(true) }

    // Lifecycle — pause on background, resume on foreground (only if it was
    // actually playing before), release on exit
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        // Capture intent before pause() flips isPlaying to false.
                        wasPlayingBeforeBackground = player.isPlaying
                        // Save position when going to background
                        onPositionChanged(player.currentPosition)
                        player.pause()
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        if (wasPlayingBeforeBackground) player.play()
                    }

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
