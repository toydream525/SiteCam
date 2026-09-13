package com.sitecam.app.feature.projects

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.export.ExportChoiceDialog
import com.sitecam.app.core.export.ExportOptions
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkBorder
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.DarkSurface
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.SuccessGreen
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import com.sitecam.app.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGallery: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var editor by remember { mutableStateOf<ProjectEntity?>(null) }
    var create by remember { mutableStateOf(false) }
    var deleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var exportIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var folderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var folderOptions by remember { mutableStateOf(ExportOptions()) }
    var categoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var batchMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val projectListState = rememberLazyListState()
    var pendingCreatedProjectId by remember { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && folderIds.isNotEmpty()) {
            viewModel.exportProjectsFolder(context, folderIds, uri, folderOptions)
        }
        folderIds = emptySet()
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(viewModel) {
        viewModel.projectCreated.collect {
            create = false
            pendingCreatedProjectId = it
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.projectSelected.collect { onNavigateBack() }
    }
    LaunchedEffect(viewModel) {
        viewModel.undoEvent.collect { undo ->
            val result = snackbarHostState.showSnackbar(
                message = undo.message,
                actionLabel = "撤销",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoProjectMutation(undo)
            }
        }
    }

    // Wait for Room to publish the row before scrolling, so creation also
    // works while the user was partway down a long project list.
    LaunchedEffect(pendingCreatedProjectId, state.projects) {
        val newId = pendingCreatedProjectId ?: return@LaunchedEffect
        if (state.projects.firstOrNull()?.project?.id == newId) {
            projectListState.animateScrollToItem(0)
            pendingCreatedProjectId = null
        }
    }

    BackHandler(enabled = batchMode) {
        viewModel.clearSelection()
        batchMode = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "工程包",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "${state.projects.size} 个工程",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (batchMode) {
                            viewModel.clearSelection()
                            batchMode = false
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimaryDark
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { create = true },
                        enabled = !state.isExporting,
                        colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                    ) {
                        Text("新建", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimaryDark,
                    navigationIconContentColor = TextPrimaryDark,
                    actionIconContentColor = EngineeringYellow
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                label = { Text("搜索工程 / 线路") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = EngineeringYellow)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EngineeringYellow,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = EngineeringYellow,
                    unfocusedLabelColor = TextSecondaryDark,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    cursorColor = EngineeringYellow,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("ACTIVE" to "进行中", "ARCHIVED" to "已归档", "ALL" to "全部").forEach { (value, label) ->
                    FilterChip(
                        selected = state.archiveFilter == value,
                        onClick = { viewModel.setArchiveFilter(value) },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurface,
                            labelColor = TextSecondaryDark,
                            selectedContainerColor = EngineeringYellow,
                            selectedLabelColor = Color.Black
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.archiveFilter == value,
                            borderColor = DarkBorder,
                            selectedBorderColor = EngineeringYellow,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp
                        )
                    )
                }
            }

            if (!batchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { sortMenu = true },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "排序 · ${state.sort.label}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false },
                            containerColor = DarkCard
                        ) {
                            ProjectSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.sort == sort) "✓ ${sort.label}" else sort.label,
                                            color = if (state.sort == sort) EngineeringYellow else TextPrimaryDark,
                                            fontSize = 13.sp,
                                            lineHeight = 16.sp
                                        )
                                    },
                                    onClick = {
                                        sortMenu = false
                                        viewModel.setSort(sort, state.ascending)
                                    }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.setSort(state.sort, !state.ascending) },
                        modifier = Modifier.size(width = 54.dp, height = 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (state.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (state.ascending) "升序" else "降序",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.clearSelection()
                            batchMode = true
                        },
                        enabled = !state.isExporting,
                        modifier = Modifier.size(width = 82.dp, height = 38.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                    ) {
                        Text("批量管理", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("批量管理", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "已选 ${state.checkedIds.size} 项 · 全选作用于当前筛选",
                            color = TextSecondaryDark,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = {
                                viewModel.clearSelection()
                                batchMode = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                        ) {
                            Text("取消")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.selectAll() },
                            enabled = !state.isExporting && state.projects.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 5.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                        ) { Text("全选", maxLines = 1) }
                        TextButton(
                            onClick = { viewModel.clearSelection() },
                            enabled = !state.isExporting && state.checkedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 5.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondaryDark)
                        ) { Text("清空", maxLines = 1) }
                        TextButton(
                            onClick = {
                                exportIds = state.checkedIds
                                viewModel.clearSelection()
                                batchMode = false
                            },
                            enabled = !state.isExporting && state.checkedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 5.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                        ) { Text("导出", maxLines = 1) }
                        Box(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { batchMenu = true },
                                enabled = !state.isExporting && state.checkedIds.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 5.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                            ) {
                                Text("更多", maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = batchMenu,
                                onDismissRequest = { batchMenu = false },
                                containerColor = DarkCard
                            ) {
                                DropdownMenuItem(
                                    text = { MoreMenuText("批量分类", "给选中的工程统一设置类别") },
                                    onClick = {
                                        batchMenu = false
                                        categoryIds = state.checkedIds
                                        viewModel.clearSelection()
                                        batchMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { MoreMenuText("归档", "把选中的工程移入归档") },
                                    onClick = {
                                        batchMenu = false
                                        viewModel.batchChange(state.checkedIds, archived = true)
                                        batchMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { MoreMenuText("恢复", "把选中的工程放回进行中") },
                                    onClick = {
                                        batchMenu = false
                                        viewModel.batchChange(state.checkedIds, archived = false)
                                        batchMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { MoreMenuText("锁定拍摄", "暂停选中工程继续拍摄录像") },
                                    onClick = {
                                        batchMenu = false
                                        viewModel.batchChange(state.checkedIds, locked = true)
                                        batchMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { MoreMenuText("解锁拍摄", "允许选中工程继续拍摄录像") },
                                    onClick = {
                                        batchMenu = false
                                        viewModel.batchChange(state.checkedIds, locked = false)
                                        batchMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { MoreMenuText("删除工程", "删除工程及其照片、视频和编辑成品", ErrorRed) },
                                    onClick = {
                                        batchMenu = false
                                        deleteIds = state.checkedIds
                                        viewModel.clearSelection()
                                        batchMode = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.isExporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    color = EngineeringYellow,
                    trackColor = DarkBorder
                )
                Text(
                    text = state.exportProgress,
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            LazyColumn(
                state = projectListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.projects.isEmpty()) {
                    item {
                        EmptyProjectsState()
                    }
                }
                items(state.projects, key = { it.project.id }) { row ->
                    val project = row.project
                    var menu by remember(project.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 128.dp)
                            .combinedClickable(
                                onClick = {
                                    if (batchMode) viewModel.toggleChecked(project.id)
                                    else onNavigateToGallery(project.id)
                                },
                                onLongClick = {
                                    batchMode = true
                                    viewModel.toggleChecked(project.id)
                                }
                            ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 128.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (batchMode) {
                                Checkbox(
                                    checked = project.id in state.checkedIds,
                                    onCheckedChange = { viewModel.toggleChecked(project.id) },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = EngineeringYellow,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = TextSecondaryDark
                                    ),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    // The 48dp MoreVert hit area leaves 16dp below its 16dp text.
                                    // Add that space above the normal-mode column so centering moves
                                    // the visible content down about 8dp and balances both card edges.
                                    .padding(top = if (batchMode) 0.dp else 16.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = project.name,
                                        color = TextPrimaryDark,
                                        fontSize = 16.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = !row.isSelected)
                                    )
                                    if (row.isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        CurrentProjectBadge()
                                    }
                                    if (project.isCaptureLocked) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "已锁定拍摄",
                                            tint = WarningYellow,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(17.dp)
                                        )
                                    }
                                    if (project.isArchived) {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = "已归档",
                                            tint = TextSecondaryDark,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(17.dp)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProjectPill(
                                        text = project.categoryName.ifBlank { "未分类" },
                                        tint = EngineeringYellow
                                    )
                                    if (project.routeName.isNotBlank()) {
                                        Spacer(Modifier.size(4.dp))
                                        Text(
                                            text = "线路 · ${project.routeName}",
                                            color = TextSecondaryDark,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "拍摄日期",
                                        tint = EngineeringYellow,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = row.statistics.dateLabel(),
                                        color = TextSecondaryDark,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProjectCountSummary(
                                        photos = row.statistics.photos,
                                        videos = row.statistics.videos,
                                        issues = row.statistics.issues,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!batchMode) {
                                        Box {
                                            IconButton(
                                                onClick = { menu = true },
                                                enabled = !state.isExporting,
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "更多工程操作",
                                                    tint = TextSecondaryDark,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menu,
                                                onDismissRequest = { menu = false },
                                                containerColor = DarkCard
                                            ) {
                                                if (!row.isSelected) {
                                                    DropdownMenuItem(
                                                        enabled = !state.isExporting,
                                                        text = {
                                                            MoreMenuText("设为拍摄工程", "将后续拍摄保存到此工程")
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Folder, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            menu = false
                                                            viewModel.selectProject(project.id)
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        MoreMenuText(
                                                            if (project.isCaptureLocked) "解锁拍摄" else "锁定拍摄",
                                                            if (project.isCaptureLocked) "允许这个工程继续拍摄录像" else "暂停这个工程继续拍摄录像"
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Lock, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        menu = false
                                                        viewModel.batchChange(setOf(project.id), locked = !project.isCaptureLocked)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        MoreMenuText(
                                                            if (project.isArchived) "恢复工程" else "归档工程",
                                                            if (project.isArchived) "放回进行中的工程包" else "暂时收起这个工程包"
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Archive, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        menu = false
                                                        viewModel.batchChange(setOf(project.id), archived = !project.isArchived)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        MoreMenuText("删除工程", "删除前请先导出需要留档的照片和视频", ErrorRed)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                                                    },
                                                    onClick = {
                                                        menu = false
                                                        deleteIds = setOf(project.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (!batchMode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.batchChange(
                                                    setOf(project.id),
                                                    locked = !project.isCaptureLocked
                                                )
                                            },
                                            enabled = !state.isExporting,
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (project.isCaptureLocked) WarningYellow else TextSecondaryDark
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.size(2.dp))
                                            Text(
                                                if (project.isCaptureLocked) "解锁拍摄" else "锁定拍摄",
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                viewModel.batchChange(
                                                    setOf(project.id),
                                                    archived = !project.isArchived
                                                )
                                            },
                                            enabled = !state.isExporting,
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondaryDark)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Archive,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.size(2.dp))
                                            Text(
                                                if (project.isArchived) "恢复工程" else "归档工程",
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            if (!batchMode) {
                                VerticalDivider(
                                    modifier = Modifier
                                        .height(88.dp)
                                        .padding(vertical = 4.dp),
                                    thickness = 1.dp,
                                    color = DarkBorder.copy(alpha = 0.65f)
                                )
                                        Column(
                                            modifier = Modifier.width(82.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    if (row.isSelected) onNavigateBack()
                                                    else viewModel.selectProject(project.id)
                                                },
                                                enabled = !state.isExporting && !state.isSwitchingProject,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = EngineeringYellow,
                                                    disabledContentColor = TextSecondaryDark.copy(alpha = 0.45f)
                                                )
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(Modifier.size(2.dp))
                                                Text(
                                                    when {
                                                        row.isSelected -> "返回相机"
                                                        state.switchingProjectId == project.id -> "切换中…"
                                                        else -> "切换"
                                                    },
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            TextButton(
                                        onClick = { editor = project },
                                        enabled = !state.isExporting,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = TextSecondaryDark,
                                            disabledContentColor = TextSecondaryDark.copy(alpha = 0.45f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.size(2.dp))
                                        Text("编辑", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                                    }
                                    TextButton(
                                        onClick = { exportIds = setOf(project.id) },
                                        enabled = !state.isExporting,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = EngineeringYellow,
                                            disabledContentColor = EngineeringYellow.copy(alpha = 0.45f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.size(2.dp))
                                        Text("导出", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (create || editor != null) {
        ProjectEditor(
            project = editor,
            categories = state.categories.map { it.name },
            onDismiss = { create = false; editor = null }
        ) { name, route, category, address, description ->
            val current = editor
            if (current == null) {
                viewModel.createProject(name, category, address, description, route)
            } else {
                viewModel.editProject(current, name, route, category, address, description)
                editor = null
            }
        }
    }

    if (categoryIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { categoryIds = emptySet() },
            containerColor = DarkCard,
            title = { Text("批量分类", color = TextPrimaryDark) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(state.categories) { category ->
                        TextButton(
                            onClick = {
                                viewModel.batchChange(categoryIds, category = category.name)
                                categoryIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = TextPrimaryDark)
                        ) {
                            Text(category.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { categoryIds = emptySet() },
                    colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
                ) { Text("取消") }
            }
        )
    }

    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteIds = emptySet() },
            containerColor = DarkCard,
            title = { Text("删除 ${deleteIds.size} 个工程？", color = TextPrimaryDark) },
            text = {
                val rows = state.projects.filter { it.project.id in deleteIds }
                Text(
                    "同时删除照片或视频共 ${rows.sumOf { it.statistics.photos + it.statistics.videos }} 项，并删除关联问题和编辑成品。无法撤销。未能删除的照片或视频会保留，并提示原因。",
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProjects(deleteIds)
                        deleteIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteIds = emptySet() },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondaryDark)
                ) { Text("取消") }
            }
        )
    }

    if (exportIds.isNotEmpty()) {
        ExportChoiceDialog(
            onDismiss = { exportIds = emptySet() },
            onZip = {
                viewModel.exportProjectsZip(context, exportIds, it)
                exportIds = emptySet()
            },
            onFolder = {
                folderIds = exportIds
                folderOptions = it
                exportIds = emptySet()
                runCatching { picker.launch(viewModel.initialExportTreeUri(context)) }
                    .onFailure { Toast.makeText(context, "无法打开目录选择器", Toast.LENGTH_LONG).show() }
            }
        )
    }
}

@Composable
private fun EmptyProjectsState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = EngineeringYellow, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("没有符合条件的工程包", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            Text("可以新建工程，或调整搜索和筛选条件", color = TextSecondaryDark, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProjectPill(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CurrentProjectBadge() {
    Surface(
        color = SuccessGreen.copy(alpha = 0.15f),
        contentColor = SuccessGreen,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "当前拍摄工程",
                modifier = Modifier.size(13.dp)
            )
            Text("当前", fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ProjectCountSummary(
    photos: Int,
    videos: Int,
    issues: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ProjectCount(label = "照片", value = photos)
        Text("·", color = TextSecondaryDark, fontSize = 12.sp, lineHeight = 16.sp)
        ProjectCount(label = "视频", value = videos)
        Text("·", color = TextSecondaryDark, fontSize = 12.sp, lineHeight = 16.sp)
        ProjectCount(label = "问题", value = issues)
    }
}

@Composable
private fun ProjectCount(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextSecondaryDark, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        Text(value.toString(), color = TextPrimaryDark, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
    }
}

@Composable
private fun MoreMenuText(title: String, description: String, titleColor: Color = TextPrimaryDark) {
    Column {
        Text(title, color = titleColor, fontSize = 14.sp, lineHeight = 18.sp)
        Text(description, color = TextSecondaryDark, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun ProjectEditor(
    project: ProjectEntity?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(project?.name.orEmpty()) }
    var route by remember { mutableStateOf(project?.routeName.orEmpty()) }
    var category by remember { mutableStateOf(project?.categoryName ?: categories.firstOrNull().orEmpty()) }
    var address by remember { mutableStateOf(project?.address.orEmpty()) }
    var description by remember { mutableStateOf(project?.description.orEmpty()) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EngineeringYellow,
        unfocusedBorderColor = DarkBorder,
        focusedLabelColor = EngineeringYellow,
        unfocusedLabelColor = TextSecondaryDark,
        focusedTextColor = TextPrimaryDark,
        unfocusedTextColor = TextPrimaryDark,
        cursorColor = EngineeringYellow,
        focusedContainerColor = DarkSurface,
        unfocusedContainerColor = DarkSurface
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = if (project == null) "新建工程包" else "编辑工程包",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("工程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it },
                    label = { Text("线路名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { value ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(value, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EngineeringYellow,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextPrimaryDark
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("工程地点（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("工程备注（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, route, category, address, description) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = EngineeringYellow)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondaryDark)
            ) { Text("取消") }
        }
    )
}
