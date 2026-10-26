@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.camera.CaptureOrientation
import com.sitecam.app.core.camera.CameraLensCapability
import com.sitecam.app.core.camera.CameraLensRole
import com.sitecam.app.feature.camera.CaptureMode
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.Letterbox

/** Top tool shelf matching the measured 56dp camera shelf. */
@Composable
fun CameraTopBar(
    projectName: String,
    flashMode: String,
    isQuickIssueMode: Boolean,
    onProjectClick: () -> Unit,
    onFlashToggle: () -> Unit,
    onFlashLongPress: () -> Unit,
    onQuickIssueToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    orientationLabel: String = "自动",
    onOrientationSelected: (CaptureOrientation) -> Unit = {},
    isBusy: Boolean = false,
    isLandscape: Boolean = false,
    verticalTools: Boolean = false,
    projectOnly: Boolean = false,
    transparentControls: Boolean = false,
    publicLenses: List<CameraLensCapability> = emptyList(),
    activeCameraId: String? = null,
    onLensSelected: (CameraLensCapability) -> Unit = {},
    addressRefreshState: String? = null,
    onAddressRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    captureMode: CaptureMode = CaptureMode.PHOTO,
    burstEnabled: Boolean = false,
    onBurstToggle: () -> Unit = {}
) {
    val flashIcon = when (flashMode.uppercase()) {
        "TORCH" -> Icons.Default.FlashlightOn
        "ON" -> Icons.Default.FlashOn
        "AUTO" -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }
    val flashSelected = flashMode.uppercase() != "OFF"
    val flashDescription = when (flashMode.uppercase()) {
        "TORCH" -> "闪光灯，常亮"
        "ON" -> "闪光灯，开启"
        "AUTO" -> "闪光灯，自动"
        else -> "闪光灯，关闭"
    }
    val plateAlpha = if (transparentControls) .42f else .82f
    BoxWithConstraints(modifier.fillMaxWidth()
        .background(if (isLandscape) Color.Transparent else Letterbox)
        .padding(horizontal = if (verticalTools) 4.dp else 8.dp)) {
        val shelfWidth = maxWidth
        val project: @Composable (Modifier) -> Unit = { placement ->
            Row(placement.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                .background(Letterbox.copy(alpha = plateAlpha)).clickable(enabled = !isBusy, onClick = onProjectClick)
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(projectName.ifBlank { "选择工程" }, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        val actions: @Composable () -> Unit = {
            ToolIcon(flashIcon, if (flashSelected) EngineeringYellow else Color.White,
                flashDescription, !isBusy, onFlashToggle, onFlashLongPress, boxSize = 48.dp, backgroundAlpha = plateAlpha)
            ToolIcon(Icons.Default.BurstMode, if (burstEnabled) EngineeringYellow else Color.White,
                if (burstEnabled) "连拍，开启" else "连拍，关闭", !isBusy && captureMode == CaptureMode.PHOTO,
                onBurstToggle, boxSize = 48.dp, backgroundAlpha = plateAlpha)
            OrientationSelector(orientationLabel, !isBusy, onOrientationSelected, plateAlpha,
                menuTowardPreview = verticalTools)
            ToolIcon(Icons.Default.Settings, Color.White, "设置", !isBusy, onSettingsClick,
                boxSize = 48.dp, backgroundAlpha = plateAlpha)
            if (verticalTools && addressRefreshState != null && onAddressRefreshClick != null) {
                IconButton(onClick = onAddressRefreshClick, enabled = !isBusy && addressRefreshState != "REFRESHING",
                    modifier = Modifier.size(48.dp)) {
                    if (addressRefreshState == "REFRESHING") CircularProgressIndicator(Modifier.size(24.dp),
                        color = EngineeringYellow, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "刷新地址", tint = EngineeringYellow)
                }
            }
        }
        if (projectOnly) {
            project(Modifier.widthIn(max = minOf(220.dp, shelfWidth)))
        } else if (verticalTools) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly) { actions() }
        } else if (maxWidth < (352f + 120f * (LocalDensity.current.fontScale.coerceAtLeast(1f) - 1f)).dp) {
            Column(Modifier.fillMaxWidth()) {
                project(Modifier.widthIn(max = minOf(220.dp, shelfWidth)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            project(Modifier.widthIn(max = minOf(220.dp, shelfWidth - 192.dp)))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

/**
 * Entry point for all camera profiles, including the narrow cover display
 * where the normal top shelf is intentionally absent.
 */
@Composable
fun CameraLensSelector(
    publicLenses: List<CameraLensCapability>,
    activeCameraId: String?,
    enabled: Boolean,
    onLensSelected: (CameraLensCapability) -> Unit,
    compact: Boolean = false,
    captureMode: CaptureMode = CaptureMode.PHOTO
) {
    // Public rear groups and supported physical-selector candidates are
    // selectable. Metadata-only hardware remains absent from this menu.
    val options = publicLenses
        .filter { isLensSelectableInMode(it, captureMode) }
        .distinctBy { it.cameraId }
    if (options.size <= 1) return

    var expanded by remember { mutableStateOf(false) }
    val active = options.firstOrNull { it.cameraId == activeCameraId }
    Box {
        ToolIcon(
            icon = Icons.Default.FlipCameraAndroid,
            tint = if (active != null) EngineeringYellow else Color.White,
            description = "镜头：${active?.let(::lensLabel) ?: "选择"}",
            enabled = enabled,
            onClick = { expanded = true },
            boxSize = if (compact) 44.dp else 46.dp,
            iconSize = if (compact) 23.dp else 25.dp
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            containerColor = DarkCard
        ) {
            options.forEach { lens ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.widthIn(min = 190.dp)) {
                            Text(
                                text = lensLabel(lens),
                                color = if (lens.cameraId == activeCameraId) EngineeringYellow else Color.White,
                                fontWeight = if (lens.cameraId == activeCameraId) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = lensDetail(lens, captureMode),
                                color = Color.White.copy(alpha = .68f),
                                fontSize = 11.sp
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onLensSelected(lens)
                    }
                )
            }
        }
    }
}

/** A lens is offered only when the current capture mode can use it. */
internal fun isLensSelectableInMode(
    lens: CameraLensCapability,
    captureMode: CaptureMode
): Boolean = lens.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK &&
    lens.appAccessible &&
    lens.photoBindable &&
    (captureMode != CaptureMode.VIDEO ||
        lens.videoBindingVerification != com.sitecam.app.core.camera.CameraBindingVerification.VERIFIED ||
        lens.videoBindable)

internal fun lensLabel(lens: CameraLensCapability): String = when (lens.role) {
    CameraLensRole.WIDE -> "广角"
    CameraLensRole.MAIN -> "主摄"
    CameraLensRole.TELE -> "长焦"
    CameraLensRole.UNKNOWN -> "镜头"
}

internal fun lensDetail(lens: CameraLensCapability, captureMode: CaptureMode): String = buildString {
    fun appendDetail(value: String) {
        if (isNotEmpty()) append(" · ")
        append(value)
    }

    lens.equivalentZoomRatio
        ?.takeIf { it.isFinite() && it > 0f }
        ?.let { ratio ->
            val format = if (ratio < 1f) "约%.2f×" else "约%.1f×"
            appendDetail(String.format(java.util.Locale.US, format, ratio))
        }
    when (captureMode) {
        CaptureMode.PHOTO -> appendDetail(
            if (lens.photoBindingVerification == com.sitecam.app.core.camera.CameraBindingVerification.VERIFIED) {
                "可拍照"
            } else {
                "拍照待确认"
            }
        )

        CaptureMode.VIDEO -> appendDetail(
            when {
                lens.videoBindingVerification == com.sitecam.app.core.camera.CameraBindingVerification.VERIFIED && lens.videoBindable -> "可录像"
                lens.videoBindingVerification == com.sitecam.app.core.camera.CameraBindingVerification.VERIFIED -> "录像不可用"
                else -> "录像待确认"
            }
        )
    }
}

private fun addressRefreshDescription(state: String): String = when (state) {
    "REFRESHING" -> "地址刷新中"
    "FAILED_PERMISSION" -> "定位未开启，重试地址"
    "FAILED_LOCATION_DISABLED" -> "系统定位已关闭，打开设置"
    "FAILED_LOCATION" -> "定位不可用，重试地址"
    "FAILED_ADDRESS" -> "地址服务失败，重试"
    else -> "刷新地址"
}

@Composable
private fun ProjectNameTool(
    projectName: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp, max = 102.dp)
            .heightIn(min = 58.dp, max = 78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "工程项目：$projectName",
            tint = EngineeringYellow,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = projectName.ifBlank { "请选择工程包" },
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    boxSize: androidx.compose.ui.unit.Dp = 46.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    loading: Boolean = false,
    darkBackground: Boolean = true,
    backgroundAlpha: Float = .72f
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(if (darkBackground) DarkCard.copy(alpha = backgroundAlpha) else Color.Transparent)
            .semantics { contentDescription = description }
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
        if (loading) CircularProgressIndicator(modifier = Modifier.size(iconSize), color = tint, strokeWidth = 2.dp)
        else Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun OrientationSelector(label: String, enabled: Boolean, onSelect: (CaptureOrientation) -> Unit,
    backgroundAlpha: Float, menuTowardPreview: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val current = CaptureOrientation.entries.firstOrNull { it.label == label } ?: CaptureOrientation.AUTO
    Box {
        ToolIcon(icon = Icons.Default.ScreenRotation,
            tint = if (expanded || current != CaptureOrientation.AUTO) EngineeringYellow else Color.White,
            description = "拍摄方向：${current.label}", enabled = enabled,
            onClick = { expanded = true }, boxSize = 48.dp, backgroundAlpha = backgroundAlpha)
        androidx.compose.material3.DropdownMenu(
            expanded = expanded && enabled, onDismissRequest = { expanded = false },
            containerColor = DarkCard,
            offset = androidx.compose.ui.unit.DpOffset((if (menuTowardPreview) 56 else -160).dp, 0.dp)
        ) {
            val modes = listOf(CaptureOrientation.AUTO, CaptureOrientation.PORTRAIT,
                CaptureOrientation.LANDSCAPE_LEFT, CaptureOrientation.LANDSCAPE_RIGHT)
            modes.chunked(2).forEach { row ->
                Row(Modifier.widthIn(min = 208.dp, max = 208.dp)) {
                    row.forEach { mode ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(orientationIcon(mode), null, Modifier.size(24.dp),
                                        tint = if (mode == current) EngineeringYellow else Color.White)
                                    Text(mode.label, color = if (mode == current) EngineeringYellow else Color.White, fontSize = 14.sp)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                                .background(if (mode == current) EngineeringYellow.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(8.dp)),
                            onClick = { expanded = false; onSelect(mode) }
                        )
                    }
                }
            }
        }
    }
}

private fun orientationIcon(mode: CaptureOrientation): ImageVector = when (mode) {
    CaptureOrientation.AUTO -> androidx.compose.material.icons.Icons.Default.ScreenRotation
    CaptureOrientation.PORTRAIT -> androidx.compose.material.icons.Icons.Default.StayCurrentPortrait
    CaptureOrientation.LANDSCAPE_LEFT -> androidx.compose.material.icons.Icons.Default.RotateLeft
    CaptureOrientation.LANDSCAPE_RIGHT -> androidx.compose.material.icons.Icons.Default.RotateRight
}
