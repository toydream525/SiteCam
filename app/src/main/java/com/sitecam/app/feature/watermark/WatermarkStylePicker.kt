package com.sitecam.app.feature.watermark

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import com.sitecam.app.core.layout.AdaptivePaneLayout
import com.sitecam.app.core.layout.adaptivePaneArrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkStyle
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.ui.theme.PageCard as DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.SiteCamTheme
import com.sitecam.app.ui.theme.PageText as TextPrimaryDark
import com.sitecam.app.ui.theme.PageMuted as TextSecondaryDark

private val STYLE_PICKER_SAMPLE_DATA = WatermarkData(
    projectName = "滨江路雨污分流改造",
    categoryName = "市政工程",
    addressText = "吉林市·交行花园",
    latitude = 43.8267,
    longitude = 126.5677,
    userName = "施工员",
    styleType = "CLASSIC"
)

/**
 * Shared two-column style chooser used by Settings' editor and the camera quick sheet.
 * Choosing a card only changes the local candidate; persistence happens on confirmation.
 */
@Composable
fun WatermarkStylePickerDialog(
    currentStyleId: String,
    previewData: WatermarkData,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedStyleId by rememberSaveable(currentStyleId) { mutableStateOf(currentStyleId) }
    val choicesState = rememberLazyGridState()
    val selectedStyle = WatermarkStyleCatalog.resolve(selectedStyleId)
    val selectedPreview = previewData.copy(styleType = selectedStyle.id)

    val density = LocalDensity.current
    SiteCamTheme(updateSystemBars = false) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        // Compose 1.7's custom-width path replaces the system's measure specs
        // with full-screen dimensions. Keep platform measurement and size
        // this window explicitly so cutouts and system bars constrain it.
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = true)) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            onDispose { }
        }
        Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } }) {
            AdaptivePaneLayout(allowTwoPane = false, primary = {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = DarkCard,
                        modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth().heightIn(max = 840.dp).fillMaxHeight()
                            .pointerInput(Unit) { detectTapGestures { } }) {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                            val wide = adaptivePaneArrangement(maxWidth.value, maxHeight.value, density.fontScale,
                                primaryMinWidth = 320f, secondaryMinWidth = 360f).split
                            val short = maxHeight < 440.dp
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("选择水印样式", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                @Composable fun preview(modifier: Modifier) {
                                    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF202A31)), contentAlignment = Alignment.Center) {
                                            WatermarkVisualPreview(selectedPreview, Modifier.fillMaxWidth().fillMaxHeight())
                                        }
                                        Text(selectedStyle.name, color = EngineeringYellow, fontSize = 16.sp)
                                    }
                                }
                                @Composable fun choices(modifier: Modifier) {
                                    val minimumCardWidth = (144f + 96f * (density.fontScale.coerceAtLeast(1f) - 1f)).dp
                                    LazyVerticalGrid(GridCells.Adaptive(minimumCardWidth), modifier, state = choicesState,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(WatermarkStyleCatalog.styles, key = { it.id }) { style ->
                                            WatermarkStyleChoiceCard(style, style.id == selectedStyle.id, compact = short, veryCompact = false,
                                                onClick = { selectedStyleId = style.id })
                                        }
                                    }
                                }
                                if (wide) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    preview(Modifier.weight(1f).fillMaxHeight())
                                    choices(Modifier.weight(1f).fillMaxHeight())
                                } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (!short) preview(Modifier.fillMaxWidth().height(144.dp))
                                    choices(Modifier.fillMaxWidth().weight(1f))
                                }
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消", color = TextSecondaryDark) }
                                    Button(onClick = { onConfirm(selectedStyle.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = Color.Black)) { Text("使用此样式") }
                                }
                            }
                        }
                    }
                }
            })
        }
    }
    }
}

@Composable
private fun WatermarkStyleChoiceCard(
    style: WatermarkStyle,
    selected: Boolean,
    compact: Boolean,
    veryCompact: Boolean,
    onClick: () -> Unit
) {
    val thumbnailHeight = when {
        veryCompact -> 36.dp
        compact -> 40.dp
        else -> 82.dp
    }
    val textPadding = when {
        veryCompact -> 2.dp
        compact -> 4.dp
        else -> 6.dp
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) EngineeringYellow else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbnailHeight)
                    .background(Color(0xFF202A31)),
                contentAlignment = Alignment.Center
            ) {
                WatermarkVisualPreview(
                    data = STYLE_PICKER_SAMPLE_DATA.copy(styleType = style.id),
                    modifier = Modifier.fillMaxWidth().height(thumbnailHeight)
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = textPadding)) {
                Text(
                    text = style.name,
                    color = if (selected) EngineeringYellow else TextPrimaryDark,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

            }
        }
    }
}
