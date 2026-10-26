package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
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
import androidx.compose.material3.TextButton
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
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.feature.watermark.WatermarkStylePickerDialog
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
    previewData: WatermarkData,
    onDismissRequest: () -> Unit,
    onFieldValueChange: (fieldId: Long, newValue: String) -> Unit,
    onFieldToggle: (fieldId: Long, enabled: Boolean) -> Unit,
    onStyleChange: (styleType: String) -> Unit,
    onScaleChange: (scale: Float) -> Unit,
    onWidthChange: (ratio: Float) -> Unit,
    onOpacityChange: (opacity: Float) -> Unit,
    onAddField: (label: String, defaultValue: String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var tab by remember { mutableStateOf("内容") }
    val projectFocus = remember { FocusRequester() }
    val fieldListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var projectFocusPending by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(projectFocusPending, tab) {
        if (projectFocusPending && tab == "内容") {
            val index = fields.indexOfFirst { it.fieldKey == BuiltInWatermarkFieldKeys.PROJECT_NAME }
            if (index >= 0) {
                fieldListState.scrollToItem(index + 2)
                kotlinx.coroutines.delay(100)
                projectFocus.requestFocus()
            }
            projectFocusPending = false
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var focusedFieldId by remember { mutableStateOf<Long?>(null) }
    var showStylePicker by remember { mutableStateOf(false) }
    val currentStyle = WatermarkStyleCatalog.resolve(activeTemplate?.styleType)
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = DarkBackground, dragHandle = null) {
        Column(Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .9f)).imePadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("水印编辑", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) { Text("完成", color = EngineeringYellow) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("内容", "外观").forEach { label ->
                    androidx.compose.material3.FilterChip(tab == label, { tab = label }, label = { Text(label, color = TextPrimaryDark) })
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = fieldListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if(tab == "内容") {
                    item {
                        val projectField = fields.firstOrNull { it.fieldKey == BuiltInWatermarkFieldKeys.PROJECT_NAME }
                        TextButton(onClick = {
                            if (projectField != null) {
                                if (!projectField.isEnabled) onFieldToggle(projectField.id, true)
                                projectFocusPending = true
                            }
                        }, enabled = projectField != null) { Text("工程名称", color = EngineeringYellow) }
                    }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QUICK_ENGINEERING_TAGS.forEach { tag -> TextButton(onClick = {
                            val field = fields.firstOrNull { it.id == focusedFieldId } ?: fields.firstOrNull { it.isEnabled }
                            field?.let { onFieldValueChange(it.id, listOf(it.defaultValue, tag).filter(String::isNotBlank).joinToString(" ")) }
                        }) { Text(tag, color = EngineeringYellow) } }
                    } }
                    items(fields, key = { it.id }) { field ->
                        Column(Modifier.fillMaxWidth().background(DarkCard, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(field.label, color = TextPrimaryDark, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Switch(field.isEnabled, { onFieldToggle(field.id, it) })
                            }
                            OutlinedTextField(field.defaultValue, { onFieldValueChange(field.id, it) }, label = { Text(field.label) }, enabled = field.isEnabled,
                                modifier = Modifier.fillMaxWidth().then(if (field.fieldKey == BuiltInWatermarkFieldKeys.PROJECT_NAME) Modifier.focusRequester(projectFocus) else Modifier).onFocusChanged { if(it.isFocused) focusedFieldId = field.id }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimaryDark, unfocusedTextColor = TextPrimaryDark))
                        }
                    }
                    item { TextButton(onClick = { showAddDialog = true }) { Text("添加字段", color = EngineeringYellow) } }
                } else {
                    item { TextButton(onClick = { showStylePicker = true }) { Text("${currentStyle.name} ›", color = EngineeringYellow) } }
                    item { Text("字号 ${String.format(Locale.ROOT, "%.1f", activeTemplate?.fontSizeScale ?: 1f)}×", color = TextPrimaryDark)
                        Slider(activeTemplate?.fontSizeScale ?: 1f, onScaleChange, valueRange = .6f..2f) }
                    item { Text("水印宽度 ${((activeTemplate?.widthRatio ?: .6f) * 100).toInt()}%", color = TextPrimaryDark)
                        Slider(activeTemplate?.widthRatio ?: .6f, onWidthChange, valueRange = .3f..0.9f, steps = 11) }
                    item { Text("不透明度 ${((activeTemplate?.opacity ?: .85f) * 100).toInt()}%", color = TextPrimaryDark)
                        Slider(activeTemplate?.opacity ?: .85f, onOpacityChange, valueRange = .3f..1f) }
                }
            }
        }
    }
    if(showStylePicker) WatermarkStylePickerDialog(currentStyle.id, previewData, onDismiss = { showStylePicker = false }, onConfirm = { onStyleChange(it); showStylePicker = false })
    if(showAddDialog) {
        var label by remember { mutableStateOf("") }; var value by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { showAddDialog = false }, containerColor = DarkCard,
            title = { Text("添加字段", color = TextPrimaryDark) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(label, { label = it }, label = { Text("字段名称") })
                    OutlinedTextField(value, { value = it }, label = { Text("字段内容") })
                }
            }, confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onAddField(label.trim(), value); showAddDialog = false }) { Text("添加") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } })
    }
}
