package com.sitecam.app.feature.watermark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkCustomizationScreen(
    viewModel: WatermarkEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<WatermarkFieldEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.template?.name ?: "水印"} 样式与字段定制",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Preview Section
            item {
                Text(
                    text = "实时效果预览",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2830))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WatermarkPreviewCanvas(
                            watermarkData = uiState.previewData,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. Styling Controls (Size, Opacity, Position)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "样式调整",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Font size scale slider
                        val scale = uiState.template?.fontSizeScale ?: 1.0f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("字号缩放比例", color = TextSecondaryDark, fontSize = 13.sp)
                            Text(String.format(Locale.US, "%.1f×", scale), color = EngineeringYellow, fontSize = 13.sp)
                        }
                        Slider(
                            value = scale,
                            onValueChange = { viewModel.updateFontSizeScale(it) },
                            valueRange = 0.6f..2.0f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = EngineeringYellow,
                                activeTrackColor = EngineeringYellow
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Opacity slider
                        val opacity = uiState.template?.opacity ?: 0.85f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("背景卡片不透明度", color = TextSecondaryDark, fontSize = 13.sp)
                            Text("${(opacity * 100).toInt()}%", color = EngineeringYellow, fontSize = 13.sp)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { viewModel.updateOpacity(it) },
                            valueRange = 0.3f..1.0f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = EngineeringYellow,
                                activeTrackColor = EngineeringYellow
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Position selector
                        Text("水印位置", color = TextSecondaryDark, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val currentPos = uiState.template?.position ?: "BOTTOM_LEFT"
                            listOf(
                                "BOTTOM_LEFT" to "左下角",
                                "BOTTOM_RIGHT" to "右下角",
                                "TOP_LEFT" to "左上角",
                                "TOP_RIGHT" to "右上角"
                            ).forEach { (posKey, posLabel) ->
                                FilterChip(
                                    selected = currentPos == posKey,
                                    onClick = { viewModel.updatePosition(posKey) },
                                    label = { Text(posLabel, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EngineeringYellow,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.Black.copy(alpha = 0.3f),
                                        labelColor = TextPrimaryDark
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Fields Management Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "水印字段管理与排序",
                        color = TextSecondaryDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { showAddFieldDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Text("新增字段", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Fields List
            itemsIndexed(uiState.fields, key = { _, item -> item.id }) { index, field ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (field.isEnabled) DarkCard else DarkCard.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.label,
                                color = if (field.isEnabled) TextPrimaryDark else TextSecondaryDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (field.defaultValue.isNotBlank()) {
                                Text(
                                    text = "默认: ${field.defaultValue}",
                                    color = TextSecondaryDark,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Action Controls (Up, Down, Edit, Toggle)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (index > 0) {
                                IconButton(
                                    onClick = { viewModel.moveFieldUp(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "上移", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < uiState.fields.size - 1) {
                                IconButton(
                                    onClick = { viewModel.moveFieldDown(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "下移", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { editingField = field },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = EngineeringYellow, modifier = Modifier.size(16.dp))
                            }
                            if (field.fieldKey.startsWith("CUSTOM_")) {
                                IconButton(
                                    onClick = { viewModel.deleteField(field) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                }
                            }

                            Switch(
                                checked = field.isEnabled,
                                onCheckedChange = { viewModel.toggleField(field) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EngineeringYellow,
                                    checkedTrackColor = EngineeringYellow.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add Custom Field Dialog
    if (showAddFieldDialog) {
        var newLabel by remember { mutableStateOf("") }
        var newDefaultVal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            containerColor = DarkCard,
            title = { Text("新增工程水印字段", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
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
                        label = { Text("默认内容（如：一标段）") },
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
                            viewModel.addCustomField(newLabel, newDefaultVal)
                            showAddFieldDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow)
                ) {
                    Text("添加", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            }
        )
    }

    // Edit Field Dialog
    editingField?.let { field ->
        var editLabel by remember(field) { mutableStateOf(field.label) }
        var editDefaultVal by remember(field) { mutableStateOf(field.defaultValue) }

        AlertDialog(
            onDismissRequest = { editingField = null },
            containerColor = DarkCard,
            title = { Text("编辑水印字段", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("字段标签") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EngineeringYellow),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDefaultVal,
                        onValueChange = { editDefaultVal = it },
                        label = { Text("默认内容") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EngineeringYellow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editLabel.isNotBlank()) {
                            viewModel.updateField(field, editLabel, editDefaultVal)
                            editingField = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow)
                ) {
                    Text("保存", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("取消", color = TextSecondaryDark)
                }
            }
        )
    }
}
