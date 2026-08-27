package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import java.util.Locale

private val QUICK_ENGINEERING_TAGS = listOf(
    "隐蔽验收", "主体结构", "基础工程", "钢筋绑扎",
    "混凝土浇筑", "防水工程", "安全巡检", "设备安装",
    "回填土方", "现场测绘", "砌体工程", "抹灰工程",
    "消防工程", "管线敷设", "材料进场", "质量整改"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickWatermarkEditSheet(
    activeTemplate: WatermarkTemplateEntity?,
    fields: List<WatermarkFieldEntity>,
    onDismissRequest: () -> Unit,
    onFieldValueChange: (fieldId: Long, newValue: String) -> Unit,
    onFieldToggle: (fieldId: Long, enabled: Boolean) -> Unit,
    onStyleChange: (styleType: String) -> Unit,
    onScaleChange: (scale: Float) -> Unit,
    onOpacityChange: (opacity: Float) -> Unit,
    onAddField: (label: String, defaultValue: String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var focusedFieldId by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EngineeringYellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = EngineeringYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "现场快速修改水印",
                        color = TextPrimaryDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "完成",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "完成",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Template Styles Selector Tabs
            val currentStyle = activeTemplate?.styleType ?: "CLASSIC"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "CLASSIC" to "经典工程",
                    "MINIMAL" to "极简水印",
                    "INFO_BOARD" to "工程信息板"
                ).forEach { (styleKey, styleTitle) ->
                    val isSelected = currentStyle == styleKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStyleChange(styleKey) },
                        label = {
                            Text(
                                text = styleTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EngineeringYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkCard,
                            labelColor = TextSecondaryDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Engineering Quick Presets Chip Bar
            Text(
                text = "⚡ 常用工程部位 / 施工标签（点击快速填入）",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUICK_ENGINEERING_TAGS.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                // Find current focused field or first custom/non-name field
                                val targetField = fields.find { it.id == focusedFieldId }
                                    ?: fields.find { it.label.contains("部位") || it.label.contains("内容") || it.fieldKey.startsWith("CUSTOM") }
                                    ?: fields.firstOrNull()

                                targetField?.let { field ->
                                    val currentVal = field.defaultValue
                                    val updatedVal = if (currentVal.isBlank()) tag else "$currentVal $tag"
                                    onFieldValueChange(field.id, updatedVal)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "+ $tag",
                            color = EngineeringYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Watermark Size Scale Slider (0.6x ~ 2.0x)
            val currentScale = activeTemplate?.fontSizeScale ?: 1.0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字号缩放比例",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
                Text(
                    text = String.format(Locale.US, "%.1f×", currentScale),
                    color = EngineeringYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = currentScale,
                onValueChange = onScaleChange,
                valueRange = 0.6f..2.0f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = EngineeringYellow,
                    activeTrackColor = EngineeringYellow
                )
            )

            // 4. Scrollable Fields Editor List
            Text(
                text = "现场水印字段内容（实时修改）",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fields, key = { it.id }) { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = field.isEnabled,
                            onCheckedChange = { onFieldToggle(field.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EngineeringYellow,
                                checkedTrackColor = EngineeringYellow.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(36.dp)
                        )

                        Text(
                            text = field.label,
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(68.dp)
                        )

                        OutlinedTextField(
                            value = field.defaultValue,
                            onValueChange = { onFieldValueChange(field.id, it) },
                            singleLine = true,
                            enabled = field.isEnabled,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EngineeringYellow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                        )
                    }
                }

                // Add Custom Field Button in sheet
                item {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = EngineeringYellow
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增字段",
                            tint = EngineeringYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "新增现场自定义字段（如：标段、监理单位）",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add Field Dialog inside bottom sheet
    if (showAddDialog) {
        var newLabel by remember { mutableStateOf("") }
        var newDefaultVal by remember { mutableStateOf("") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    text = "新增水印字段",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("字段名称（如：标段 / 监理单位）") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EngineeringYellow),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDefaultVal,
                        onValueChange = { newDefaultVal = it },
                        label = { Text("默认内容（如：一标段，选填）") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EngineeringYellow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLabel.isNotBlank()) {
                            onAddField(newLabel.trim(), newDefaultVal.trim())
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow)
                ) {
                    Text("添加", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            }
        )
    }
}
