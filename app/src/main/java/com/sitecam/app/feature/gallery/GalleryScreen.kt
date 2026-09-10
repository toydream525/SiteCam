package com.sitecam.app.feature.gallery

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.export.*
import com.sitecam.app.feature.issue.IssueDialog
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(viewModel: GalleryViewModel, onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var projectPicker by remember { mutableStateOf(false) }
    var movePicker by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var issueItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    var exportOptions by remember { mutableStateOf(ExportOptions()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri != null) viewModel.exportSelected(context, exportOptions, uri)
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> if(event is GalleryUiEvent.Message) Toast.makeText(context, event.text, Toast.LENGTH_LONG).show() } }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = { TopAppBar(title = { Text(state.currentProject?.name ?: "全部工程相册") },
        navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = { TextButton(onClick = { projectPicker = true }) { Text("切换工程") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(GalleryFilter.ALL to "全部", GalleryFilter.TODAY to "今天", GalleryFilter.ISSUES_ONLY to "问题").forEach { (filter, label) ->
                    FilterChip(state.selectedFilter == filter, { viewModel.setFilter(filter) }, label = { Text(label) })
                }
                FilterChip(state.dateRange != CaptureDateRange(), { dates = true }, label = { Text("日期 / 范围") })
                if(state.dateRange != CaptureDateRange()) TextButton(onClick = { viewModel.setDateRange(CaptureDateRange()) }) { Text("清除日期") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.category == null, { viewModel.setCategory(null) }, label = { Text("全部类别") })
                state.projects.map { it.categoryName }.distinct().forEach { category -> FilterChip(state.category == category,
                    { viewModel.setCategory(category) }, label = { Text(category) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.status == null && state.severity == null, { viewModel.setStatus(null); viewModel.setSeverity(null) }, label = { Text("全部问题状态") })
                listOf("PENDING" to "待处理", "IN_PROGRESS" to "处理中", "COMPLETED" to "已完成").forEach { (value, label) ->
                    FilterChip(state.status == value, { viewModel.setStatus(if(state.status == value) null else value) }, label = { Text(label) }) }
                listOf("NORMAL" to "一般", "IMPORTANT" to "重要", "CRITICAL" to "严重").forEach { (value, label) ->
                    FilterChip(state.severity == value, { viewModel.setSeverity(if(state.severity == value) null else value) }, label = { Text(label) }) }
            }
            Text("${state.mediaItems.size} 项 · 照片 ${state.mediaItems.count { it.mediaType == "PHOTO" }} / 视频 ${state.mediaItems.count { it.mediaType == "VIDEO" }}", Modifier.padding(horizontal = 12.dp))
            if(state.dateRange != CaptureDateRange()) Text("${state.dateRange.start ?: "不限"} 至 ${state.dateRange.endInclusive ?: "不限"}", Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = viewModel::selectAll, enabled = !state.isLoading) { Text("全选当前筛选") }
                if(state.checkedIds.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearSelection, enabled = !state.isLoading) { Text("取消 ${state.checkedIds.size} 项") }
                    TextButton(onClick = { export = true }, enabled = !state.isLoading) { Text("导出") }
                    Box {
                        TextButton(onClick = { batchMenu = true }, enabled = !state.isLoading) { Text("更多 ▾") }
                        DropdownMenu(expanded = batchMenu, onDismissRequest = { batchMenu = false }) {
                            DropdownMenuItem(text = { Text("移入工程") }, onClick = { batchMenu = false; movePicker = true })
                            DropdownMenuItem(text = { Text("分享") }, onClick = { batchMenu = false; viewModel.shareSelected(context) })
                            DropdownMenuItem(text = { Text("删除") }, onClick = { batchMenu = false; delete = true })
                        }
                    }
                }
            }
            if(state.isLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress, Modifier.padding(8.dp)) }
            val groups = state.mediaItems.groupBy { Instant.ofEpochMilli(it.captureTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if(groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("没有符合条件的照片或视频", Modifier.padding(24.dp)) }
                groups.forEach { (day, media) ->
                    item(key = "day_$day", span = { GridItemSpan(maxLineSpan) }) { Text("$day · ${media.size} 项", Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall) }
                    items(media, key = { it.id }) { item ->
                        Card(Modifier.combinedClickable(enabled = !state.isLoading, onClick = {
                            if(state.checkedIds.isEmpty()) onNavigateToDetail(item.id) else viewModel.toggleChecked(item.id)
                        }, onLongClick = { viewModel.toggleChecked(item.id) })) {
                            Box {
                                if(item.mediaType == "VIDEO") VideoThumbnail(item.contentUri, Modifier.fillMaxWidth().aspectRatio(1f))
                                else AsyncImage(model = item.contentUri, contentDescription = item.fileName, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                                if(item.id in state.checkedIds) Checkbox(true, { viewModel.toggleChecked(item.id) }, Modifier.align(Alignment.TopEnd))
                            }
                            Text(day.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                            Text("${if(item.mediaType == "VIDEO") "视频" else "照片"} · ${state.projects.firstOrNull { it.id == item.projectId }?.name.orEmpty()}",
                                maxLines = 1, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                            TextButton(onClick = { issueItem = item }, enabled = !state.isLoading, contentPadding = PaddingValues(4.dp)) {
                                Text(if(item.isIssue) when(state.issueByMediaId[item.id]?.status) { "COMPLETED" -> "问题 · 已完成"; "IN_PROGRESS" -> "问题 · 处理中"; else -> "问题 · 待处理" } else "标记问题", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if(projectPicker || movePicker) AlertDialog(onDismissRequest = { projectPicker = false; movePicker = false },
        title = { Text(if(movePicker) "移入工程（含锁定工程）" else "选择工程") }, text = {
            LazyColumn {
                if(!movePicker) item { TextButton(onClick = { viewModel.setProject(null); projectPicker = false }) { Text("全部工程") } }
                items(state.projects, key = { it.id }) { project -> TextButton(onClick = {
                    if(movePicker) viewModel.moveSelected(project.id) else viewModel.setProject(project.id)
                    projectPicker = false; movePicker = false
                }) { Text("${project.name} · ${project.categoryName}${if(project.isCaptureLocked) " · 拍摄锁定" else ""}${if(project.isArchived) " · 已归档" else ""}") } }
            }
        }, confirmButton = { TextButton(onClick = { projectPicker = false; movePicker = false }) { Text("取消") } })
    if(dates) DateRangeDialog(onDismiss = { dates = false }, onConfirm = { viewModel.setDateRange(it); dates = false })
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除 ${state.checkedIds.size} 项照片或视频？") },
        text = { Text("同时删除关联问题和编辑成品，无法撤销。未能删除的照片或视频会保留，并提示原因。") },
        confirmButton = { TextButton(onClick = { delete = false; viewModel.deleteSelected() }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
    if(export) ExportChoiceDialog(onDismiss = { export = false }, onZip = { export = false; viewModel.exportSelected(context, it) },
        onFolder = { exportOptions = it; export = false; runCatching { picker.launch(null) }.onFailure { Toast.makeText(context, "无法打开目录选择器", Toast.LENGTH_LONG).show() } })
    issueItem?.let { item -> IssueDialog(item.id, onDismiss = { issueItem = null }, existingIssue = state.issueByMediaId[item.id],
        onRemove = { viewModel.removeIssue(item.id); issueItem = null }, onConfirm = { title, severity, description, status ->
            viewModel.saveIssue(item, title, severity, description, status); issueItem = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (CaptureDateRange) -> Unit) {
    var mode by remember { mutableStateOf("MONTH") }
    var year by remember { mutableStateOf(java.time.LocalDate.now().year) }
    var month by remember { mutableStateOf(java.time.LocalDate.now().monthValue) }
    var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    val dayState = rememberDatePickerState()
    val rangeState = rememberDateRangePickerState()
    fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    if(mode == "DAY" || mode == "RANGE") {
        DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
            TextButton(enabled = if(mode == "DAY") dayState.selectedDateMillis != null else rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
                onClick = {
                    if(mode == "DAY") dayState.selectedDateMillis?.let { onConfirm(CaptureDateRange(date(it),date(it))) }
                    else { val a = rangeState.selectedStartDateMillis; val b = rangeState.selectedEndDateMillis
                        if(a != null && b != null) onConfirm(CaptureDateRange(date(a),date(b))) }
                }) { Text("应用") }
        }, dismissButton = { TextButton(onClick = { mode = "MONTH" }) { Text("返回选择方式") } }) {
            if(mode == "DAY") DatePicker(dayState) else DateRangePicker(rangeState, Modifier.height(500.dp))
        }
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("拍摄日期筛选") }, text = {
        Column {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("YEAR" to "年份", "MONTH" to "月份", "DAY" to "单日", "RANGE" to "范围", "TEXT" to "输入").forEach { (value,label) ->
                    FilterChip(mode == value, { mode = value }, label = { Text(label) })
                }
            }
            if(mode == "YEAR" || mode == "MONTH") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { year-- }) { Text("上一年") }
                    Text("${year}年", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { year++ }) { Text("下一年") }
                }
                if(mode == "MONTH") {
                    (0..3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        (1..3).forEach { column -> val value = row * 3 + column
                            FilterChip(month == value, { month = value }, label = { Text("${value}月") })
                        }
                    } }
                }
            } else {
                Text("输入年份 2026、月份 2026-09 或日期 2026-09-08。结束留空表示同一年、月或日。")
                OutlinedTextField(start, { start = it }, label = { Text("开始 / 年份 / 月份 / 日期") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("结束（包含当天）") }, singleLine = true)
                if(error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = { TextButton(onClick = {
        when(mode) {
            "YEAR" -> onConfirm(CaptureDateRange(java.time.LocalDate.of(year,1,1),java.time.LocalDate.of(year,12,31)))
            "MONTH" -> { val selected = java.time.YearMonth.of(year,month); onConfirm(CaptureDateRange(selected.atDay(1),selected.atEndOfMonth())) }
            else -> runCatching { CaptureDateRange.parse(start.trim(), end.trim()) }.onSuccess(onConfirm).onFailure { error = it.message ?: "日期格式不正确" }
        }
    }) { Text("应用") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
