package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed

/** Top tool shelf matching the measured 102dp MIUI camera shelf. */
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
    isBusy: Boolean = false,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val flashIcon = when (flashMode.uppercase()) {
        "TORCH" -> Icons.Default.FlashOn
        "ON" -> Icons.Default.FlashOn
        "AUTO" -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }
    val flashSelected = flashMode.uppercase() != "OFF"

    if (isLandscape) {
        Column(
            modifier = modifier.fillMaxHeight().background(Color.Black).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolIcon(Icons.Default.Folder, EngineeringYellow, "工程项目：$projectName", !isBusy, onProjectClick)
            ToolIcon(
                flashIcon,
                if (flashSelected) EngineeringYellow else Color.White,
                "闪光灯，长按常亮",
                !isBusy,
                onFlashToggle,
                onFlashLongPress
            )
            ToolIcon(Icons.Default.ReportProblem, if (isQuickIssueMode) ErrorRed else Color.White, "重点问题", !isBusy, onQuickIssueToggle)
            ToolIcon(Icons.Default.Settings, Color.White, "设置", !isBusy, onSettingsClick)
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 154.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1B1B1B))
                    .clickable(enabled = !isBusy, onClick = onProjectClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.Folder, "工程项目", tint = EngineeringYellow, modifier = Modifier.size(20.dp))
                Text(
                    text = projectName.ifBlank { "默认工程项目" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIcon(
                    flashIcon,
                    if (flashSelected) EngineeringYellow else Color.White,
                    "闪光灯，长按常亮",
                    !isBusy,
                    onFlashToggle,
                    onFlashLongPress
                )
                ToolIcon(Icons.Default.ReportProblem, if (isQuickIssueMode) ErrorRed else Color.White, "重点问题", !isBusy, onQuickIssueToggle)
                ToolIcon(Icons.Default.Settings, Color.White, "设置", !isBusy, onSettingsClick)
            }
        }
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
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(46.dp)
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
        Icon(icon, description, tint = tint, modifier = Modifier.size(25.dp))
    }
}
