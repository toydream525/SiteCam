package com.sitecam.app.feature.watermark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkStyle
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

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
    var selectedStyleId by remember(currentStyleId) { mutableStateOf(currentStyleId) }
    val selectedStyle = WatermarkStyleCatalog.resolve(selectedStyleId)
    val selectedPreview = previewData.copy(styleType = selectedStyle.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = "选择水印样式",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Keep the dialog body below the title and action row so those actions remain visible
                // even when the device is short in landscape. The grid itself stays scrollable.
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val bodyBudget = (screenHeight - 196.dp).coerceAtLeast(144.dp)
                val compact = bodyBudget < 340.dp
                val veryCompact = bodyBudget < 240.dp
                val previewHeight = when {
                    veryCompact -> 52.dp
                    compact -> 80.dp
                    else -> 124.dp
                }
                val titleLines = if (veryCompact) 1 else 2
                val descriptionLines = if (compact) 1 else 2
                val fixedContentHeight = previewHeight +
                    8.dp + (16.dp * titleLines) +
                    (4.dp + 13.dp * descriptionLines) + 8.dp
                val gridHeight = (bodyBudget - fixedContentHeight).coerceAtLeast(48.dp)
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF202A31))
                    ) {
                        WatermarkPreviewCanvas(
                            watermarkData = selectedPreview,
                            modifier = Modifier.fillMaxWidth().height(previewHeight)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = selectedStyle.name,
                        color = EngineeringYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp,
                        maxLines = titleLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedStyle.description,
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = descriptionLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(WatermarkStyleCatalog.styles, key = { it.id }) { style ->
                            WatermarkStyleChoiceCard(
                                style = style,
                                selected = style.id == selectedStyle.id,
                                compact = compact,
                                veryCompact = veryCompact,
                                onClick = { selectedStyleId = style.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStyle.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EngineeringYellow,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("使用此样式", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondaryDark)
            ) {
                Text("取消")
            }
        }
    )
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
            color = if (selected) EngineeringYellow else Color.White.copy(alpha = 0.12f)
        ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF293139))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbnailHeight)
                    .background(Color(0xFF202A31)),
                contentAlignment = Alignment.Center
            ) {
                WatermarkPreviewCanvas(
                    watermarkData = STYLE_PICKER_SAMPLE_DATA.copy(styleType = style.id),
                    modifier = Modifier.fillMaxWidth().height(thumbnailHeight)
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = textPadding)) {
                Text(
                    text = style.name,
                    color = if (selected) EngineeringYellow else TextPrimaryDark,
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 15.sp else 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        text = style.description,
                        color = TextSecondaryDark,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
