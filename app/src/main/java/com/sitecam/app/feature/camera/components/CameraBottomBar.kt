package com.sitecam.app.feature.camera.components

import android.media.MediaActionSound
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sitecam.app.feature.camera.CaptureMode
import com.sitecam.app.feature.gallery.VideoThumbnail
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A single responsive control dock. Zoom, mode and shutter controls live in
 * one ordered layout so vendor aspect ratios, navigation insets and font scale
 * can change spacing without allowing independently-positioned controls to
 * overlap.
 */
@Composable
fun CameraBottomBar(
    latestThumbnailUri: String?,
    latestMediaType: String? = null,
    isCapturing: Boolean,
    captureMode: CaptureMode,
    isRecordingVideo: Boolean,
    recordingDurationSeconds: Int,
    zoomPresets: List<Float>,
    currentZoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
    isLandscape: Boolean = false,
    landscapeBarWidth: Dp = 255.dp,
    onModeChange: (CaptureMode) -> Unit,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    captureAllowed: Boolean = true,
    isBusy: Boolean = false,
    shutterSoundEnabled: Boolean = true,
    thumbnailBounceToken: Long = 0L,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val shutterScale = remember { Animatable(1f) }
    val thumbnailScale = remember { Animatable(1f) }
    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(shutterSound) { onDispose { shutterSound.release() } }

    LaunchedEffect(thumbnailBounceToken) {
        if (thumbnailBounceToken == 0L) return@LaunchedEffect
        thumbnailScale.snapTo(0.86f)
        thumbnailScale.animateTo(1.10f, tween(durationMillis = 110))
        thumbnailScale.animateTo(1f, tween(durationMillis = 90))
    }

    fun fireShutter() {
        // A photo that is still being saved (or a camera that is not ready)
        // must not produce a second, misleading shutter sound. Stopping an
        // active video recording remains allowed while the dock is busy.
        if (isBusy && !isRecordingVideo) return
        if (!captureAllowed && !isRecordingVideo) return
        if (captureMode == CaptureMode.PHOTO && shutterSoundEnabled) {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        }
        scope.launch {
            shutterScale.animateTo(0.90f, tween(45))
            shutterScale.animateTo(1f, tween(70))
        }
        onShutterClick()
    }

    if (isLandscape) {
        LandscapeControlDock(
            modifier = modifier
                .fillMaxHeight()
                .width(landscapeBarWidth)
                .background(Color.Black)
                .clipToBounds(),
            latestThumbnailUri = latestThumbnailUri,
            latestMediaType = latestMediaType,
            captureMode = captureMode,
            isCapturing = isCapturing || (!captureAllowed && !isRecordingVideo),
            isRecordingVideo = isRecordingVideo,
            recordingDurationSeconds = recordingDurationSeconds,
            zoomPresets = zoomPresets,
            currentZoomRatio = currentZoomRatio,
            isBusy = isBusy,
            shutterScale = shutterScale.value,
            thumbnailScale = thumbnailScale.value,
            onZoomSelected = onZoomSelected,
            onModeChange = onModeChange,
            onGalleryClick = onGalleryClick,
            onFlipCameraClick = onFlipCameraClick,
            onShutterClick = ::fireShutter
        )
    } else {
        PortraitControlDock(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds(),
            latestThumbnailUri = latestThumbnailUri,
            latestMediaType = latestMediaType,
            captureMode = captureMode,
            isCapturing = isCapturing || (!captureAllowed && !isRecordingVideo),
            isRecordingVideo = isRecordingVideo,
            recordingDurationSeconds = recordingDurationSeconds,
            zoomPresets = zoomPresets,
            currentZoomRatio = currentZoomRatio,
            isBusy = isBusy,
            shutterScale = shutterScale.value,
            thumbnailScale = thumbnailScale.value,
            onZoomSelected = onZoomSelected,
            onModeChange = onModeChange,
            onGalleryClick = onGalleryClick,
            onFlipCameraClick = onFlipCameraClick,
            onShutterClick = ::fireShutter
        )
    }
}

@Composable
private fun PortraitControlDock(
    modifier: Modifier,
    latestThumbnailUri: String?,
    latestMediaType: String?,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    recordingDurationSeconds: Int,
    zoomPresets: List<Float>,
    currentZoomRatio: Float,
    isBusy: Boolean,
    shutterScale: Float,
    thumbnailScale: Float,
    onZoomSelected: (Float) -> Unit,
    onModeChange: (CaptureMode) -> Unit,
    onGalleryClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    onShutterClick: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        val compact = maxHeight < 205.dp || fontScale >= 1.25f
        val veryCompact = maxHeight < 170.dp || fontScale >= 1.45f
        val verticalPadding = when {
            veryCompact -> 4.dp
            compact -> 7.dp
            else -> 10.dp
        }
        val sectionGap = when {
            veryCompact -> 1.dp
            compact -> 3.dp
            else -> 6.dp
        }
        val shutterSize = when {
            veryCompact -> 56.dp
            compact -> 64.dp
            else -> 72.dp
        }
        val sideButtonSize = if (veryCompact) 40.dp else 46.dp
        val controlsLift = when {
            veryCompact -> 10.dp
            compact -> 22.dp
            else -> 40.dp
        }
        val dockLift = when {
            veryCompact -> 4.dp
            compact -> 6.dp
            else -> 10.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = -dockLift)
                .padding(horizontal = 22.dp, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.offset(y = -controlsLift),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ZoomPillGroup(
                    presets = zoomPresets,
                    currentZoomRatio = currentZoomRatio,
                    onZoomSelected = onZoomSelected,
                    compact = compact
                )

                Spacer(Modifier.height(sectionGap))

                if (isRecordingVideo) {
                    RecordingTimer(recordingDurationSeconds, compact = compact)
                } else {
                    ModeSelector(
                        captureMode = captureMode,
                        vertical = false,
                        compact = compact,
                        enabled = !isBusy,
                        onModeChange = onModeChange
                    )
                }
            }

            Spacer(Modifier.height(sectionGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-8).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalleryButton(
                    uri = latestThumbnailUri,
                    mediaType = latestMediaType,
                    enabled = !isBusy,
                    size = sideButtonSize,
                    scale = thumbnailScale,
                    onClick = onGalleryClick
                )
                ShutterButton(
                    captureMode = captureMode,
                    isCapturing = isCapturing,
                    isRecordingVideo = isRecordingVideo,
                    scale = shutterScale,
                    outerSize = shutterSize,
                    onClick = onShutterClick
                )
                FlipButton(
                    enabled = !isBusy,
                    size = sideButtonSize,
                    onClick = onFlipCameraClick
                )
            }
        }
    }
}

@Composable
private fun LandscapeControlDock(
    modifier: Modifier,
    latestThumbnailUri: String?,
    latestMediaType: String?,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    recordingDurationSeconds: Int,
    zoomPresets: List<Float>,
    currentZoomRatio: Float,
    isBusy: Boolean,
    shutterScale: Float,
    thumbnailScale: Float,
    onZoomSelected: (Float) -> Unit,
    onModeChange: (CaptureMode) -> Unit,
    onGalleryClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    onShutterClick: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        val compact = maxWidth < 235.dp || maxHeight < 360.dp || fontScale >= 1.25f
        val shutterSize = if (compact) 58.dp else 68.dp
        val sideButtonSize = if (compact) 40.dp else 46.dp
        val horizontalPadding = if (compact) 4.dp else 8.dp
        val lensModeGap = if (compact) 2.dp else 4.dp
        // Keep the shutter group's absolute edge distance close to portrait:
        // portrait sits ~100dp above the bottom edge on the connected device,
        // so landscape uses a comparable distance from the right edge.
        val shutterLeftShift = if (compact) 58.dp else 68.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            ZoomPillGroup(
                presets = zoomPresets,
                currentZoomRatio = currentZoomRatio,
                onZoomSelected = onZoomSelected,
                isVertical = true,
                compact = compact
            )

            Spacer(Modifier.width(lensModeGap))

            if (isRecordingVideo) {
                RecordingTimer(recordingDurationSeconds, compact = compact)
            } else {
                ModeSelector(
                    captureMode = captureMode,
                    vertical = true,
                    compact = compact,
                    enabled = !isBusy,
                    onModeChange = onModeChange
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = -shutterLeftShift)
                    .padding(vertical = if (compact) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                GalleryButton(
                    uri = latestThumbnailUri,
                    mediaType = latestMediaType,
                    enabled = !isBusy,
                    size = sideButtonSize,
                    scale = thumbnailScale,
                    onClick = onGalleryClick
                )
                ShutterButton(
                    captureMode = captureMode,
                    isCapturing = isCapturing,
                    isRecordingVideo = isRecordingVideo,
                    scale = shutterScale,
                    outerSize = shutterSize,
                    onClick = onShutterClick
                )
                FlipButton(
                    enabled = !isBusy,
                    size = sideButtonSize,
                    onClick = onFlipCameraClick
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(
    captureMode: CaptureMode,
    vertical: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onModeChange: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = if (compact) 10.dp else 22.dp
    val content: @Composable () -> Unit = {
        ModeLabel("拍照", captureMode == CaptureMode.PHOTO, compact, enabled) { onModeChange(CaptureMode.PHOTO) }
        ModeLabel("录像", captureMode == CaptureMode.VIDEO, compact, enabled) { onModeChange(CaptureMode.VIDEO) }
    }
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
private fun ModeLabel(
    text: String,
    selected: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (selected) EngineeringYellow else Color.White.copy(alpha = 0.62f),
        fontSize = if (compact) 13.sp else 15.sp,
        lineHeight = if (compact) 17.sp else 20.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun ShutterButton(
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    scale: Float,
    outerSize: Dp,
    onClick: () -> Unit
) {
    val video = captureMode == CaptureMode.VIDEO
    val innerSize = outerSize * 0.75f
    val stopSize = outerSize * 0.34f
    Box(
        modifier = Modifier
            .size(outerSize)
            .scale(scale)
            .clip(CircleShape)
            .border(BorderStroke(3.dp, if (video) ErrorRed else Color.White), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(if (video) ErrorRed.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = !isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (video && isRecordingVideo) stopSize else innerSize)
                .clip(if (video && isRecordingVideo) RoundedCornerShape(5.dp) else CircleShape)
                .background(if (video) ErrorRed else Color.White)
        )
    }
}

@Composable
private fun GalleryButton(
    uri: String?,
    mediaType: String?,
    enabled: Boolean,
    size: Dp,
    scale: Float = 1f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171717))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            !uri.isNullOrBlank() && mediaType == "VIDEO" -> VideoThumbnail(uri, Modifier.fillMaxSize())
            !uri.isNullOrBlank() -> AsyncImage(uri, "最新照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(
                Icons.Default.PhotoLibrary,
                "相册",
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
private fun FlipButton(enabled: Boolean, size: Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF171717))
    ) {
        Icon(
            Icons.Default.FlipCameraAndroid,
            "切换镜头",
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
private fun RecordingTimer(seconds: Int, compact: Boolean, modifier: Modifier = Modifier) {
    val text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    Text(
        text = text,
        color = Color.White,
        fontSize = if (compact) 12.sp else 14.sp,
        lineHeight = if (compact) 16.sp else 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(alpha = 0.24f))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 5.dp)
    )
}
