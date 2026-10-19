package com.sitecam.app.feature.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sitecam.app.core.watermark.model.*
import com.sitecam.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoImportScreen(controller: PhotoImportController, state: PhotoImportState) {
    SiteCamTheme(darkTheme = true, updateSystemBars = false) {
        BackHandler { controller.dismiss() }
        Column(Modifier.fillMaxSize().background(DarkBackground).imePadding()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            TopAppBar(title = { Text("添加水印", color = Color.White) }, modifier = Modifier.fillMaxWidth(),
                navigationIcon = { IconButton(controller::dismiss, enabled = !state.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground))
            @Composable fun preview(modifier: Modifier) {
                Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                    AsyncImage(state.preview, "水印预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (state.loading) CircularProgressIndicator(color = EngineeringYellow)
                }
            }
            @Composable fun fields(modifier: Modifier) {
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("工程　${state.project.name}", color = Color.White)
                    var tab by remember { mutableStateOf(0) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("水印内容", "字段", "外观").forEachIndexed { index, title ->
                            TextButton({ tab = index }) { Text(title, color = if (tab == index) EngineeringYellow else Color.White) }
                        }
                    }
                    if (tab == 0) {
                        OutlinedTextField(state.timeText, controller::timeChanged,
                            label = { Text(if (state.metadata.captureTimestamp == null) "导入时间" else "拍摄时间") },
                            isError = state.timeError, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        (state.data.fieldOrder ?: BuiltInWatermarkFieldKeys.all.toList()).filter { it in BuiltInWatermarkFieldKeys.all && it != BuiltInWatermarkFieldKeys.DATE_TIME }.forEach { key ->
                            val value = if (key == BuiltInWatermarkFieldKeys.ADDRESS) state.data.addressText
                                else state.data.systemValueOverrides[key] ?: state.data.builtInValue(key).orEmpty()
                            OutlinedTextField(value, { text -> controller.update { data ->
                                if (key == BuiltInWatermarkFieldKeys.ADDRESS) data.copy(addressText = text)
                                else data.copy(systemValueOverrides = data.systemValueOverrides + (key to text))
                            } }, label = { Text(state.data.fieldLabels[key] ?: BuiltInWatermarkFieldKeys.defaultLabels[key].orEmpty()) },
                                modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                        }
                        state.data.customFields.forEach { field ->
                            OutlinedTextField(field.value, { text -> controller.update { data -> data.copy(customFields = data.customFields.map { if (it.key == field.key) it.copy(value = text) else it }) } },
                                label = { Text(field.label) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                        }
                    } else if (tab == 1) {
                        BuiltInWatermarkFieldKeys.all.forEach { key ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(key in state.data.enabledSystemFields, { enabled -> controller.update { data -> data.copy(enabledSystemFields = if (enabled) data.enabledSystemFields + key else data.enabledSystemFields - key) } }, enabled = !state.saving)
                                Text(state.data.fieldLabels[key] ?: BuiltInWatermarkFieldKeys.defaultLabels[key].orEmpty(), color = Color.White)
                            }
                        }
                        state.data.customFields.forEach { field -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(field.isEnabled, { enabled -> controller.update { data -> data.copy(customFields = data.customFields.map { if (it.key == field.key) it.copy(isEnabled = enabled) else it }) } }, enabled = !state.saving)
                            Text(field.label, color = Color.White)
                        } }
                    } else {
                        Text("水印样式", color = Color.White)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WatermarkStyleCatalog.styles.forEach { style -> FilterChip(state.data.styleType == style.id,
                                { controller.update { it.copy(styleType = style.id) } }, label = { Text(style.name) }, enabled = !state.saving) }
                        }
                        Text("字号", color = Color.White)
                        Slider(state.data.fontSizeScale, { value -> controller.update { it.copy(fontSizeScale = value) } }, valueRange = .6f..1.8f, enabled = !state.saving)
                        Text("水印宽度　${((state.data.widthRatio ?: .6f) * 100).roundToInt()}%", color = Color.White)
                        Slider(state.data.widthRatio ?: .6f, { value -> controller.update { it.copy(widthRatio = (value * 20).roundToInt() / 20f) } }, valueRange = 0.3f..0.9f, steps = 11, enabled = !state.saving)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("30%", color = Color.White); Text("90%", color = Color.White) }
                        Text("不透明度", color = Color.White)
                        Slider(state.data.opacity, { value -> controller.update { it.copy(opacity = value) } }, valueRange = .2f..1f, enabled = !state.saving)
                        FlowRow { listOf("TOP_LEFT" to "左上", "TOP_RIGHT" to "右上", "BOTTOM_LEFT" to "左下", "BOTTOM_RIGHT" to "右下").forEach { (position, title) ->
                            FilterChip(state.data.position == position, { controller.update { it.copy(position = position) } }, label = { Text(title) }, enabled = !state.saving, modifier = Modifier.padding(end = 6.dp))
                        } }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 840.dp) Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight()); fields(Modifier.width(360.dp).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth().weight(.45f)); fields(Modifier.fillMaxWidth().weight(.55f))
                }
            }
            Surface(color = DarkBackground) {
                Button(controller::save, enabled = !state.saving && !state.loading && !state.timeError && (state.preview != null || state.previewStale),
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).heightIn(min = 52.dp)) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp) else Text("保存")
                }
            }
        }
    }
}
