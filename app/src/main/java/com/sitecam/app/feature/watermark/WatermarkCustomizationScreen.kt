@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.sitecam.app.feature.watermark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.ui.theme.EngineeringYellow

@Composable
fun WatermarkCustomizationScreen(viewModel: WatermarkEditorViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val messageContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            android.widget.Toast.makeText(messageContext, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var tab by rememberSaveable { mutableStateOf("内容") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WatermarkFieldEntity?>(null) }
    var styles by remember { mutableStateOf(false) }
    @Composable fun preview(modifier: Modifier) {
        Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color(0xFF1E2830)) {
            WatermarkVisualPreview(state.previewData, Modifier.fillMaxSize())
        }
    }
    @Composable fun controls(modifier: Modifier) {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("内容", "字段", "外观").forEach { label -> FilterChip(tab == label, { tab = label }, label = { Text(label) }) }
            }
            when(tab) {
                "内容" -> {
                    state.fields.forEach { field ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(field.defaultValue, { viewModel.updateFieldValue(field, it) }, label = { Text(field.label) },
                                    modifier = Modifier.weight(1f), enabled = field.isEnabled, minLines = 1, maxLines = 3)
                                Switch(field.isEnabled, { viewModel.toggleField(field) })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("新增字段") }
                        TextButton(onClick = { tab = "字段" }) { Text("管理字段") }
                    }
                }
                "字段" -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = viewModel::restoreFieldPresentation) { Icon(Icons.Default.RestartAlt, null); Text("恢复默认") }
                        TextButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("新增字段") }
                    }
                    state.fields.forEachIndexed { index, field ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(field.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = { editing = field }) { Icon(Icons.Default.Edit, "编辑 ${field.label}") }
                                    Switch(field.isEnabled, { viewModel.toggleField(field) })
                                }
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    IconButton(onClick = { viewModel.moveFieldUp(index) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "上移 ${field.label}") }
                                    IconButton(onClick = { viewModel.moveFieldDown(index) }, enabled = index < state.fields.lastIndex) { Icon(Icons.Default.ArrowDownward, "下移 ${field.label}") }
                                    if (field.fieldKey.startsWith("CUSTOM_")) IconButton(onClick = { viewModel.deleteField(field) }) { Icon(Icons.Default.Delete, "删除 ${field.label}", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                "外观" -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        TextButton(onClick = { styles = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Text("水印样式", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Text(WatermarkStyleCatalog.resolve(state.template?.styleType).name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    WatermarkControl("字号", "%.1f×".format(state.template?.fontSizeScale ?: 1f)) {
                        Slider(state.template?.fontSizeScale ?: 1f, viewModel::updateFontSizeScale, valueRange = .6f..2f, steps = 13)
                    }
                    WatermarkControl("水印宽度", "${kotlin.math.round((state.template?.widthRatio ?: .6f) * 100).toInt()}%") {
                        Slider(state.template?.widthRatio ?: .6f, viewModel::updateWidthRatio, valueRange = .3f..0.9f, steps = 11)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("30%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("90%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    WatermarkControl("不透明度", "${((state.template?.opacity ?: .85f) * 100).toInt()}%") {
                        Slider(state.template?.opacity ?: .85f, viewModel::updateOpacity, valueRange = .3f..1f, steps = 6)
                    }
                    WatermarkControl("位置", "") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TOP_LEFT" to "左上", "TOP_RIGHT" to "右上", "BOTTOM_LEFT" to "左下", "BOTTOM_RIGHT" to "右下").forEach { (key,label) ->
                                FilterChip(state.template?.position == key, { viewModel.updatePosition(key) }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
        }
    }
    Scaffold(modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("水印编辑", fontWeight = FontWeight.Bold) }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, actions = { TextButton(onClick = onNavigateBack) { Text("完成") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val previewHeight = (maxHeight * .32f).coerceIn(100.dp, 240.dp)
            if (maxWidth >= 840.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                preview(Modifier.weight(1f).fillMaxHeight())
                controls(Modifier.width(360.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.fillMaxWidth().height(previewHeight))
                controls(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    if(styles) WatermarkStylePickerDialog(state.template?.styleType ?: "CLASSIC", state.previewData,
        onDismiss = { styles = false }, onConfirm = { viewModel.updateStyleType(it); styles = false })
    if(adding || editing != null) {
        var label by remember(editing, adding) { mutableStateOf(editing?.label ?: "") }
        ModalBottomSheet(onDismissRequest = { adding = false; editing = null }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if(adding) "新增字段" else "编辑字段", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(label, { label = it }, label = { Text("字段名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { adding = false; editing = null }, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = { if(adding) viewModel.addCustomField(label.trim()) else editing?.let { viewModel.updateField(it, label.trim()) }; adding = false; editing = null },
                        enabled = label.isNotBlank(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(contentColor = Color.Black)) { Text(if(adding) "添加" else "保存") }
                }
            }
        }
    }
}
@Composable private fun WatermarkControl(title: String, value: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(value) }
            content()
        }
    }
}
