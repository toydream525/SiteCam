@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sitecam.app.feature.camera.components

import android.media.MediaActionSound
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sitecam.app.feature.camera.CaptureMode
import com.sitecam.app.feature.camera.smallCoverAddressFallbackSlot
import com.sitecam.app.feature.gallery.VideoThumbnail
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.Letterbox
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A single responsive control dock. Zoom, mode and shutter controls live in
 * one ordered layout so vendor aspect ratios, navigation insets and font scale
 * can change spacing without allowing independently-positioned controls to
 * overlap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraBottomBar(
    latestThumbnailUri: String?,
    latestMediaType: String? = null,
    isCapturing: Boolean,
    captureMode: CaptureMode,
    isRecordingVideo: Boolean,
    isBurstCapturing: Boolean = false,
    recordingDurationSeconds: Int,
    zoomPresets: List<Float>,
    currentZoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
    isLandscape: Boolean = false,
    landscapeBarWidth: Dp = 255.dp,
    compactGroup: Boolean = false,
    smallCover: Boolean = false,
    phoneGripSpace: Boolean = false,
    onPortraitMinimumHeightChanged: (Dp) -> Unit = {},
    onModeChange: (CaptureMode) -> Unit,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    onFlipCameraLongClick: (() -> Unit)? = null,
    captureAllowed: Boolean = true,
    isBusy: Boolean = false,
    shutterSoundEnabled: Boolean = true,
    thumbnailBounceToken: Long = 0L,
    addressRefreshFallbackState: String? = null,
    onAddressRefreshFallbackClick: (() -> Unit)? = null,
    onWatermarkClick: () -> Unit = {},
    onIssueClick: () -> Unit = {},
    issueActive: Boolean = false,
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
        if (isBusy && !isRecordingVideo && !isBurstCapturing) return
        if (!captureAllowed && !isRecordingVideo && !isBurstCapturing) return
        if (captureMode == CaptureMode.PHOTO && !isBurstCapturing && shutterSoundEnabled) {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        }
        scope.launch {
            shutterScale.animateTo(0.90f, tween(45))
            shutterScale.animateTo(1f, tween(70))
        }
        onShutterClick()
    }

    if (isLandscape || smallCover) {
        LandscapeControlDock(
            compactGroup = compactGroup,
            phoneGripSpace = phoneGripSpace,
            addressRefreshState = addressRefreshFallbackState,
            onAddressRefresh = onAddressRefreshFallbackClick,
            modifier = modifier
                .testTag("camera-controls")
                .fillMaxHeight()
                .width(if (smallCover) 56.dp else landscapeBarWidth)
                .background(Letterbox)
                .clipToBounds(),
            latestThumbnailUri = latestThumbnailUri,
            latestMediaType = latestMediaType,
            captureMode = captureMode,
            isCapturing = isCapturing || (!captureAllowed && !isRecordingVideo),
            isRecordingVideo = isRecordingVideo,
            isBurstCapturing = isBurstCapturing,
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
            onFlipCameraLongClick = onFlipCameraLongClick,
            onShutterClick = ::fireShutter,
            onWatermarkClick = onWatermarkClick, onIssueClick = onIssueClick, issueActive = issueActive
        )
    } else {
        PortraitControlDock(
            phoneGripSpace = phoneGripSpace,
            onMinimumHeightChanged = onPortraitMinimumHeightChanged,
            addressRefreshState = addressRefreshFallbackState,
            onAddressRefresh = onAddressRefreshFallbackClick,
            modifier = modifier
                .fillMaxSize()
                .background(Letterbox)
                .clipToBounds(),
            latestThumbnailUri = latestThumbnailUri,
            latestMediaType = latestMediaType,
            captureMode = captureMode,
            isCapturing = isCapturing || (!captureAllowed && !isRecordingVideo),
            isRecordingVideo = isRecordingVideo,
            isBurstCapturing = isBurstCapturing,
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
            onFlipCameraLongClick = onFlipCameraLongClick,
            onShutterClick = ::fireShutter,
            onWatermarkClick = onWatermarkClick, onIssueClick = onIssueClick, issueActive = issueActive
        )
    }
}

private fun addressRefreshFallbackStateDescription(state: String): String = when (state) {
    "REFRESHING" -> "地址刷新中"
    "FAILED_PERMISSION" -> "定位未开启"
    "FAILED_LOCATION" -> "定位不可用"
    "FAILED_ADDRESS" -> "地址服务失败"
    else -> "刷新地址"
}

@Composable
private fun PortraitControlDock(
    phoneGripSpace: Boolean,
    onMinimumHeightChanged: (Dp) -> Unit,
    addressRefreshState: String?,
    onAddressRefresh: (() -> Unit)?,
    modifier: Modifier,
    latestThumbnailUri: String?,
    latestMediaType: String?,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    isBurstCapturing: Boolean = false,
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
    onFlipCameraLongClick: (() -> Unit)?,
    onShutterClick: () -> Unit,
    onWatermarkClick: () -> Unit,
    onIssueClick: () -> Unit,
    issueActive: Boolean
 ) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val oneRow = maxWidth.value - 40f >= 126f * fontScale + 90f
        val verticalModes = maxWidth.value - 40f < 56f * fontScale + 58f
        val verticalPadding = if (phoneGripSpace) 2.dp else 6.dp
        val gap = if (phoneGripSpace) 4.dp else 8.dp
        val shutterSize = 72.dp
        val gripHeight = 64.dp
        var naturalControlsHeight by remember { mutableStateOf(0) }
        LaunchedEffect(naturalControlsHeight, phoneGripSpace, maxWidth, maxHeight, fontScale, density.density) {
            if (phoneGripSpace && naturalControlsHeight > 0) {
                val fixedHeight = with(density) {
                    verticalPadding.roundToPx() * 2 + gap.roundToPx() * 2 +
                        shutterSize.roundToPx() + gripHeight.roundToPx()
                }
                onMinimumHeightChanged(with(density) { (naturalControlsHeight + fixedHeight + 1).toDp() })
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                // Measure inside the scroll viewport, where the text is
                // unconstrained. Clear its inherited minimum height too,
                // so reporting the viewport cannot grow the dock in a loop.
                .then(if (phoneGripSpace) Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { naturalControlsHeight = it.height } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                DockZoomControls(zoomPresets, currentZoomRatio, onZoomSelected, addressRefreshState, onAddressRefresh, isBusy)
                if (!oneRow) {
                    if (isRecordingVideo) RecordingTimer(recordingDurationSeconds, true)
                    else ModeSelector(captureMode, verticalModes, true, !isBusy, onModeChange)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CameraAction("水印", Icons.Default.WaterDrop, !isBusy, false, onWatermarkClick)
                    if (oneRow) {
                        if (isRecordingVideo) RecordingTimer(recordingDurationSeconds, true)
                        else ModeSelector(captureMode, false, true, !isBusy, onModeChange)
                    }
                    CameraAction("记问题", Icons.Default.EditNote, !isBusy, issueActive, onIssueClick)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GalleryButton(latestThumbnailUri, latestMediaType, !isBusy, 48.dp, thumbnailScale, onGalleryClick)
                ShutterButton(captureMode, isCapturing, isRecordingVideo, isBurstCapturing, shutterScale, shutterSize, onShutterClick)
                FlipButton(!isBusy, 48.dp, onFlipCameraClick, onFlipCameraLongClick)
            }
            if (phoneGripSpace) Spacer(Modifier.height(gripHeight))
        }
    }
}

@Composable
private fun LandscapeControlDock(
    compactGroup: Boolean,
    phoneGripSpace: Boolean,
    addressRefreshState: String?,
    onAddressRefresh: (() -> Unit)?,
    modifier: Modifier,
    latestThumbnailUri: String?,
    latestMediaType: String?,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    isBurstCapturing: Boolean = false,
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
    onFlipCameraLongClick: (() -> Unit)?,
    onShutterClick: () -> Unit,
    onWatermarkClick: () -> Unit,
    onIssueClick: () -> Unit,
    issueActive: Boolean
 ) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val modeVertical = maxWidth.value - 16f < 56f * fontScale + 56f
        val narrow = maxWidth < 112.dp
        val shutter = minOf(maxWidth * .72f, if(maxHeight < 500.dp) 80.dp else 100.dp).coerceAtLeast(48.dp)
        val phonePanelHeight = ((maxHeight - shutter - 32.dp) / 2).coerceAtLeast(0.dp)
        if (phoneGripSpace) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = phonePanelHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRecordingVideo) RecordingTimer(recordingDurationSeconds, false)
                    else Row(Modifier.fillMaxWidth()) {
                        LandscapeModeLabel("拍照", captureMode == CaptureMode.PHOTO, !isBusy,
                            Modifier.weight(1f)) { onModeChange(CaptureMode.PHOTO) }
                        LandscapeModeLabel("录像", captureMode == CaptureMode.VIDEO, !isBusy,
                            Modifier.weight(1f)) { onModeChange(CaptureMode.VIDEO) }
                    }
                    if (zoomPresets.isNotEmpty())
                        DockZoomControls(zoomPresets, currentZoomRatio, onZoomSelected, null, null, isBusy)
                    Spacer(Modifier.weight(1f))
                    GalleryButton(latestThumbnailUri, latestMediaType, !isBusy, 48.dp, thumbnailScale, onGalleryClick)
                    Spacer(Modifier.weight(1f))
                }
                ShutterButton(captureMode, isCapturing, isRecordingVideo, isBurstCapturing, shutterScale, shutter, onShutterClick)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = phonePanelHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    FlipButton(!isBusy, 48.dp, onFlipCameraClick, onFlipCameraLongClick)
                    if (onAddressRefresh != null && addressRefreshState != null)
                        AddressRefreshDockButton(addressRefreshState, onAddressRefresh, isBusy)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CameraAction("水印", Icons.Default.WaterDrop, !isBusy, false, onWatermarkClick)
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CameraAction("记问题", Icons.Default.EditNote, !isBusy, issueActive, onIssueClick)
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxSize().padding(horizontal = if(narrow) 2.dp else 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                if (!narrow) DockZoomControls(zoomPresets, currentZoomRatio, onZoomSelected, addressRefreshState, onAddressRefresh, isBusy)
                else if (onAddressRefresh != null && addressRefreshState != null) {
                    AddressRefreshDockButton(addressRefreshState, onAddressRefresh, isBusy)
                }
                if (isRecordingVideo) RecordingTimer(recordingDurationSeconds, false)
                else ModeSelector(captureMode, modeVertical, true, !isBusy, onModeChange)
                GalleryButton(latestThumbnailUri, latestMediaType, !isBusy, if(narrow) 48.dp else 64.dp, thumbnailScale, onGalleryClick)
            }
            ShutterButton(captureMode, isCapturing, isRecordingVideo, isBurstCapturing, shutterScale, shutter, onShutterClick)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                FlipButton(!isBusy, 48.dp, onFlipCameraClick, onFlipCameraLongClick)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CameraAction("水印", Icons.Default.WaterDrop, !isBusy, false, onWatermarkClick)
                    CameraAction("记问题", Icons.Default.EditNote, !isBusy, issueActive, onIssueClick)
                }
            }
        }
    }
}

@Composable
private fun LandscapeModeLabel(text: String, selected: Boolean, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Text(text, color = if (selected) EngineeringYellow else Color.White,
            fontSize = 16.sp, maxLines = 1, fontWeight = FontWeight.Medium)
        Box(Modifier.size(5.dp).background(if (selected) EngineeringYellow else Color.Transparent, CircleShape))
    }
}

@Composable
private fun DockZoomControls(presets: List<Float>, current: Float, onZoom: (Float) -> Unit,
    refreshState: String?, onRefresh: (() -> Unit)?, isBusy: Boolean) {
    Row(Modifier.widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        ZoomPillGroup(presets, current, onZoom, compact = true, modifier = Modifier.weight(1f, fill = false))
        if (refreshState != null && onRefresh != null) AddressRefreshDockButton(refreshState, onRefresh, isBusy)
    }
}

@Composable
private fun AddressRefreshDockButton(state: String, onRefresh: () -> Unit, isBusy: Boolean) {
    IconButton(onClick = onRefresh, enabled = !isBusy && state != "REFRESHING", modifier = Modifier.size(48.dp)) {
        if (state == "REFRESHING") CircularProgressIndicator(Modifier.size(24.dp), color = EngineeringYellow, strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, "刷新地址", tint = EngineeringYellow)
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
    Box(Modifier.heightIn(min = 48.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(if (selected) EngineeringYellow else Color.Transparent)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = if(compact) 12.dp else 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.Black else Color.White,
            fontSize = if(compact) 14.sp else 16.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShutterButton(
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecordingVideo: Boolean,
    isBurstCapturing: Boolean = false,
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
            .testTag("camera-shutter")
            .semantics { contentDescription = if (isBurstCapturing) "停止连拍" else if (video) "录像快门" else "拍照快门" }
            .scale(scale)
            .clip(CircleShape)
            .border(BorderStroke(3.dp, if (video) ErrorRed else Color.White), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(if (video) ErrorRed.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = !isCapturing || isBurstCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isBurstCapturing || video && isRecordingVideo) stopSize else innerSize)
                .clip(if (isBurstCapturing || video && isRecordingVideo) RoundedCornerShape(5.dp) else CircleShape)
                .background(if (video) ErrorRed else Color.White)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GalleryButton(
    uri: String?,
    mediaType: String?,
    enabled: Boolean,
    size: Dp,
    scale: Float = 1f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171717))
            // Merge the thumbnail/icon inside: TalkBack must see one "相册" node, not a second
            // "最新照片"/"视频" node stacked on top of it.
            .semantics(mergeDescendants = true) {
                if (onLongClick != null) {
                    contentDescription = "相册，长按打开更多动作"
                }
            }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                }
            ),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlipButton(enabled: Boolean, size: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF171717))
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick,
            onLongClickLabel = if (onLongClick != null) "选择镜头" else null), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.FlipCameraAndroid, "切换镜头", tint = Color.White.copy(alpha = if(enabled) 1f else .38f),
            modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RecordingTimer(seconds: Int, compact: Boolean, modifier: Modifier = Modifier) {
    val text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(alpha = 0.24f))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun CameraAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean,
    active: Boolean, onClick: () -> Unit, large: Boolean = false) {
    Column(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, Modifier.size(24.dp), tint = (if (active) EngineeringYellow else Color.White).copy(alpha = if(enabled) 1f else .38f))
        Text(label, color = (if (active) EngineeringYellow else Color.White).copy(alpha = if(enabled) 1f else .38f), fontSize = if(large) 15.sp else 14.sp)
    }
}
