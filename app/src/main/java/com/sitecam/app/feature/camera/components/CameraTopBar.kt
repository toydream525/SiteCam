package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    publicLenses: List<CameraLensCapability> = emptyList(),
    activeCameraId: String? = null,
    onLensSelected: (CameraLensCapability) -> Unit = {},
    addressRefreshState: String? = null,
    onAddressRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    captureMode: CaptureMode = CaptureMode.PHOTO
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
    // Typography scaling only relaxes widths here; it never shrinks a touch target.
    val fontScale = LocalDensity.current.fontScale

    if (isLandscape) {
        Column(
            modifier = modifier.fillMaxHeight().background(Letterbox).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            ProjectNameTool(projectName, !isBusy, onProjectClick)
            CameraLensSelector(
                publicLenses = publicLenses,
                activeCameraId = activeCameraId,
                enabled = !isBusy,
                onLensSelected = onLensSelected,
                captureMode = captureMode
            )
            if (addressRefreshState != null && onAddressRefreshClick != null) {
                ToolIcon(
                    Icons.Default.Refresh,
                    EngineeringYellow,
                    addressRefreshDescription(addressRefreshState),
                    !isBusy && addressRefreshState != "REFRESHING",
                    onAddressRefreshClick
                )
            }
            ToolIcon(
                flashIcon,
                if (flashSelected) EngineeringYellow else Color.White,
                flashDescription,
                !isBusy,
                onFlashToggle,
                onFlashLongPress
            )
            ToolIcon(Icons.Default.ReportProblem, if (isQuickIssueMode) ErrorRed else Color.White, "重点问题", !isBusy, onQuickIssueToggle)
            OrientationSelector(orientationLabel, !isBusy, onOrientationSelected)
            ToolIcon(Icons.Default.Settings, Color.White, "设置", !isBusy, onSettingsClick)
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().background(Letterbox).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    // 125dp was tuned for 13sp text. Let the capsule widen with the scaled type so
                    // the project name is not ellipsized at large font scales, and keep 48dp as a
                    // floor instead of a fixed height the text cannot outgrow.
                    .widthIn(max = (125f * fontScale.coerceIn(1f, 2f)).dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1B1B1B))
                    .clickable(enabled = !isBusy, onClick = onProjectClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.Folder, "工程项目", tint = EngineeringYellow, modifier = Modifier.size(20.dp))
                Text(
                    text = projectName.ifBlank { "请选择工程包" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraLensSelector(
                    publicLenses = publicLenses,
                    activeCameraId = activeCameraId,
                    enabled = !isBusy,
                    onLensSelected = onLensSelected,
                    captureMode = captureMode
                )
                if (addressRefreshState != null && onAddressRefreshClick != null) {
                    ToolIcon(
                        Icons.Default.Refresh,
                        EngineeringYellow,
                        addressRefreshDescription(addressRefreshState),
                        !isBusy && addressRefreshState != "REFRESHING",
                        onAddressRefreshClick
                    )
                }
                ToolIcon(
                    flashIcon,
                    if (flashSelected) EngineeringYellow else Color.White,
                    flashDescription,
                    !isBusy,
                    onFlashToggle,
                    onFlashLongPress
                )
                ToolIcon(Icons.Default.ReportProblem, if (isQuickIssueMode) ErrorRed else Color.White, "重点问题", !isBusy, onQuickIssueToggle)
                OrientationSelector(orientationLabel, !isBusy, onOrientationSelected)
                ToolIcon(Icons.Default.Settings, Color.White, "设置", !isBusy, onSettingsClick)
            }
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
    // Only independently exposed, rear CameraX groups are selectable here.
    // Physical IDs that Camera2 reports inside a logical group stay evidence
    // of hardware and are deliberately absent from this menu.
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

private fun lensLabel(lens: CameraLensCapability): String = when (lens.role) {
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
    iconSize: androidx.compose.ui.unit.Dp = 25.dp
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(CircleShape)
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
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun OrientationSelector(label: String, enabled: Boolean, onSelect: (CaptureOrientation) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = CaptureOrientation.entries.firstOrNull { it.label == label } ?: CaptureOrientation.AUTO
    Box {
        ToolIcon(
            icon = orientationIcon(current),
            tint = EngineeringYellow,
            description = "拍摄方向：${current.label}",
            enabled = enabled,
            onClick = { expanded = true }
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            containerColor = DarkCard
        ) {
            CaptureOrientation.entries.forEach { mode ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.widthIn(min = 164.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = orientationIcon(mode),
                                contentDescription = mode.label,
                                tint = if (mode == current) EngineeringYellow else Color.White
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                            Text(
                                text = mode.label,
                                color = if (mode == current) EngineeringYellow else Color.White
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            if (mode == current) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                    contentDescription = "当前选择",
                                    tint = EngineeringYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    }
                )
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
