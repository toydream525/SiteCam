@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.feature.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** One playback session, with its output moved between the detail and fullscreen views. */
private class VideoViewBinding(val player: ExoPlayer) {
    private var current: PlayerView? = null
    fun attach(view: PlayerView) {
        if (view === current) return
        current?.keepScreenOn = false
        PlayerView.switchTargetView(player, current, view)
        current = view
        view.keepScreenOn = player.isPlaying
    }
    fun detach(view: PlayerView) {
        view.keepScreenOn = false
        view.player = null
        if (current === view) current = null
    }
    fun playingChanged(playing: Boolean) { current?.keepScreenOn = playing }
    fun release() {
        current?.player = null
        current = null
        player.release()
    }
}

@Composable
fun InlineVideoPlayer(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fullscreen by rememberSaveable(uri) { mutableStateOf(false) }
    val window = context.activity()?.window
    val binding = remember(uri) {
        VideoViewBinding(ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        })
    }
    DisposableEffect(binding) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { binding.playingChanged(isPlaying) }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "视频播放失败，请检查文件或视频格式", Toast.LENGTH_LONG).show()
            }
        }
        binding.player.addListener(listener)
        onDispose {
            binding.player.removeListener(listener)
            binding.release()
        }
    }
    DisposableEffect(binding, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) binding.player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    if (!fullscreen || window == null) {
        VideoSurface(binding, modifier) { fullscreen = it }
    } else {
        Box(modifier.background(Color.Black))
        FullscreenVideo(binding, window) { fullscreen = it }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun createVideoView(context: Context): PlayerView = PlayerView(context).apply {
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    setShowPreviousButton(false)
    setShowNextButton(false)
    setShowRewindButton(false)
    setShowFastForwardButton(false)
    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
}

@Composable
private fun VideoSurface(binding: VideoViewBinding, modifier: Modifier,
    onFullscreenChanged: (Boolean) -> Unit) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { createVideoView(it) },
        onReset = null,
        onRelease = { binding.detach(it) },
        update = { view ->
            // Media3 also reports programmatic icon-state changes through this listener.
            view.setFullscreenButtonClickListener { requested -> onFullscreenChanged(requested) }
            view.setFullscreenButtonState(false)
            binding.attach(view)
        }
    )
}

/** A window-sized native layer avoids the detail pane and dialog measurement limits. */
@Composable
private fun FullscreenVideo(binding: VideoViewBinding, window: Window,
    onFullscreenChanged: (Boolean) -> Unit) {
    DisposableEffect(binding, window) {
        val decor = window.decorView as ViewGroup
        val before = ViewCompat.getRootWindowInsets(decor)
        val statusVisible = before?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        val navigationVisible = before?.isVisible(WindowInsetsCompat.Type.navigationBars()) == true
        val controller = WindowCompat.getInsetsController(window, decor)
        val behavior = controller.systemBarsBehavior
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        val layer = FrameLayout(window.context).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val fullView = createVideoView(window.context).apply {
            setFullscreenButtonClickListener { requested -> onFullscreenChanged(requested) }
            setFullscreenButtonState(true)
        }
        layer.addView(fullView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(layer) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        decor.addView(layer, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(layer)
        binding.attach(fullView)
        onDispose {
            binding.detach(fullView)
            ViewCompat.setOnApplyWindowInsetsListener(layer, null)
            decor.removeView(layer)
            controller.systemBarsBehavior = behavior
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
            else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
