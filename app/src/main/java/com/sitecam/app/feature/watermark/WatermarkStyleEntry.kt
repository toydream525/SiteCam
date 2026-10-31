package com.sitecam.app.feature.watermark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@Composable
fun WatermarkStyleEntry(currentStyle: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    val foreground = if (dark) TextPrimaryDark else MaterialTheme.colorScheme.onSurface
    val secondary = if (dark) TextSecondaryDark else MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun information(modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("水印样式", color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(currentStyle, color = secondary, fontSize = 14.sp)
        }
    }
    @Composable fun action(modifier: Modifier = Modifier) {
        Surface(modifier.defaultMinSize(minHeight = 48.dp), color = EngineeringYellow, shape = RoundedCornerShape(10.dp)) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("更换样式", color = Color(0xFF231A00), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 328.dp || LocalDensity.current.fontScale >= 1.3f
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            color = if (dark) DarkCard else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFFB88613))) {
            if (stacked) Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                information(); action(Modifier.fillMaxWidth())
            } else Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                information(Modifier.weight(1f)); action()
            }
        }
    }
}
