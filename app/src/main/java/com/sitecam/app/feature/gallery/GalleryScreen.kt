package com.sitecam.app.feature.gallery

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryScreen(viewModel: GalleryViewModel, onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit, modifier: Modifier = Modifier, selectedMediaId: Long? = null) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var filters by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    BackHandler(selecting) { selecting = false; viewModel.clearSelection() }
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
    // Android 10/11+ deletes media the app does not own only after this system request.
    // The system dialog can recreate the activity, and the approved retry needs
    // the same ids afterwards.
    var pendingDeleteIds by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode == android.app.Activity.RESULT_OK) viewModel.retryDeleteAfterConsent(pendingDeleteIds)
        else Toast.makeText(context, "已取消删除", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { event ->
        when(event) {
            is GalleryUiEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            is GalleryUiEvent.PendingDeleteConsent -> {
                pendingDeleteIds = event.itemIds
                deleteConsent.launch(androidx.activity.result.IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    } }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(state.currentProject?.name ?: "全部工程相册") },
        // Distinct from PhotoDetailScreen's back action: on a wide split screen both are on screen
        // at once, and they do different things (leave the gallery vs. close the preview pane).
        navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出相册") } },
        actions = { TextButton(onClick = { projectPicker = true }) { Text("工程") } }) },
        bottomBar = {
            if(selecting) Surface(tonalElevation = 3.dp) {
                FlowRow(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { export = true }, enabled = state.checkedIds.isNotEmpty() && !state.isLoading) { Text("导出") }
                    TextButton(onClick = { viewModel.shareSelected(context) }, enabled = state.checkedIds.isNotEmpty() && !state.isLoading) { Text("分享") }
                    TextButton(onClick = { movePicker = true }, enabled = state.checkedIds.isNotEmpty() && !state.isLoading) { Text("移入工程") }
                    TextButton(onClick = { delete = true }, enabled = state.checkedIds.isNotEmpty() && !state.isLoading) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if(selecting) {
                    Text("已选 ${state.checkedIds.size} 项", Modifier.padding(8.dp))
                    TextButton(onClick = viewModel::selectAll) { Text("全选") }
                    TextButton(onClick = { selecting = false; viewModel.clearSelection() }) { Text("取消") }
                } else {
                    listOf(GalleryFilter.ALL to "全部", GalleryFilter.TODAY to "今天", GalleryFilter.ISSUES_ONLY to "问题").forEach { (filter, label) ->
                        FilterChip(state.selectedFilter == filter, { viewModel.setFilter(filter) }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                    TextButton(onClick = { filters = true }) { Text("筛选") }
                    TextButton(onClick = { selecting = true }) { Text("选择") }
                }
            }
            Text("${state.mediaItems.size} 项 · 照片 ${state.mediaItems.count { it.mediaType == "PHOTO" }} · 视频 ${state.mediaItems.count { it.mediaType == "VIDEO" }}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
            if(state.isLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress, Modifier.padding(8.dp)) }
            val groups = state.mediaItems.groupBy { Instant.ofEpochMilli(it.captureTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
            LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if(groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("没有符合条件的照片或视频", Modifier.padding(24.dp)) }
                groups.forEach { (day, media) ->
                    item(key = "day_$day", span = { GridItemSpan(maxLineSpan) }) { Text("$day · ${media.size} 项", Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall) }
                    items(media, key = { it.id }) { item ->
                        Card(Modifier.border(if (item.id == selectedMediaId) 2.dp else 0.dp, if (item.id == selectedMediaId) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent).combinedClickable(enabled = !state.isLoading, onClick = {
                            if(!selecting) onNavigateToDetail(item.id) else viewModel.toggleChecked(item.id)
                        }, onLongClick = { selecting = true; viewModel.toggleChecked(item.id) })) {
                            Box {
                                if(item.mediaType == "VIDEO") VideoThumbnail(item.contentUri, Modifier.fillMaxWidth().aspectRatio(1f))
                                else AsyncImage(model = item.contentUri, contentDescription = item.fileName, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                                if(selecting) Checkbox(item.id in state.checkedIds, { viewModel.toggleChecked(item.id) }, Modifier.align(Alignment.TopEnd))
                                if(item.isIssue) Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp), shape = MaterialTheme.shapes.small) {
                                    Text("问题", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                        }
                    }
                }
            }
        }
    }
    if(filters) ModalBottomSheet(onDismissRequest = { filters = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("筛选", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { dates = true }) { Text(if(state.dateRange == CaptureDateRange()) "拍摄日期" else "${state.dateRange.start ?: "不限"} 至 ${state.dateRange.endInclusive ?: "不限"}") }
            Text("工程类别", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.category == null, { viewModel.setCategory(null) }, label = { Text("全部") })
                state.projects.map { it.categoryName }.distinct().forEach { category -> FilterChip(state.category == category, { viewModel.setCategory(category) }, label = { Text(category) }) }
            }
            Text("问题状态", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.status == null, { viewModel.setStatus(null) }, label = { Text("全部") })
                listOf("PENDING" to "待处理", "IN_PROGRESS" to "处理中", "COMPLETED" to "已完成").forEach { (value, label) -> FilterChip(state.status == value, { viewModel.setStatus(value) }, label = { Text(label) }) }
            }
            Text("严重程度", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(state.severity == null, { viewModel.setSeverity(null) }, label = { Text("全部") })
                listOf("NORMAL" to "一般", "IMPORTANT" to "重要", "CRITICAL" to "严重").forEach { (value, label) -> FilterChip(state.severity == value, { viewModel.setSeverity(value) }, label = { Text(label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.setDateRange(CaptureDateRange()); viewModel.setCategory(null); viewModel.setStatus(null); viewModel.setSeverity(null) }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(onClick = { filters = false }, modifier = Modifier.weight(1f)) { Text("完成") }
            }
        }
    }
    if(projectPicker || movePicker) AlertDialog(onDismissRequest = { projectPicker = false; movePicker = false },
        title = { Text(if(movePicker) "移入工程" else "选择工程") }, text = {
            LazyColumn {
                if(!movePicker) item { TextButton(onClick = { viewModel.setProject(null); projectPicker = false }) { Text("全部工程") } }
                items(state.projects, key = { it.id }) { project -> TextButton(onClick = {
                    if(movePicker) viewModel.moveSelected(project.id) else viewModel.setProject(project.id)
                    projectPicker = false; movePicker = false
                }) { Text("${project.name} · ${project.categoryName}${if(project.isCaptureLocked) " · 拍摄锁定" else ""}${if(project.isArchived) " · 已归档" else ""}") } }
            }
        }, confirmButton = { TextButton(onClick = { projectPicker = false; movePicker = false }) { Text("取消") } })
    if(dates) DateRangeDialog(state.dateRange, onDismiss = { dates = false }, onConfirm = { viewModel.setDateRange(it); dates = false })
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除 ${state.checkedIds.size} 项照片或视频？") },
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
private fun DateRangeDialog(initial: CaptureDateRange, onDismiss: () -> Unit, onConfirm: (CaptureDateRange) -> Unit) {
    val first = initial.start
    val last = initial.endInclusive
    val initialMode = when {
        first == null && last == null -> "MONTH"
        first == null || last == null -> "TEXT"
        first == last -> "DAY"
        first.dayOfYear == 1 && last == first.withMonth(12).withDayOfMonth(31) -> "YEAR"
        first.dayOfMonth == 1 && last == java.time.YearMonth.from(first).atEndOfMonth() -> "MONTH"
        else -> "RANGE"
    }
    var mode by remember { mutableStateOf(initialMode) }
    var year by remember { mutableStateOf((first ?: last ?: java.time.LocalDate.now()).year) }
    var month by remember { mutableStateOf((first ?: last ?: java.time.LocalDate.now()).monthValue) }
    var start by remember { mutableStateOf(first?.toString().orEmpty()) }
    var end by remember { mutableStateOf(last?.toString().orEmpty()) }
    var error by remember { mutableStateOf("") }
    fun millis(value: java.time.LocalDate?) = value?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val dayState = rememberDatePickerState(initialSelectedDateMillis = millis(first))
    val rangeState = rememberDateRangePickerState(initialSelectedStartDateMillis = millis(first), initialSelectedEndDateMillis = if(first != null) millis(last) else null)
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
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if(mode == "DAY") DatePicker(dayState, Modifier.verticalScroll(rememberScrollState()))
                else DateRangePicker(rangeState, Modifier.heightIn(max = maxHeight))
            }
        }
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("拍摄日期筛选") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
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
