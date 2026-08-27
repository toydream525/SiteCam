package com.sitecam.app.feature.projects

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGallery: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var exportOptionsProjectId by remember { mutableStateOf<Long?>(null) }
    var folderExportProjectId by remember { mutableStateOf<Long?>(null) }

    val exportTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val projectId = folderExportProjectId
        folderExportProjectId = null
        if (uri != null) {
            viewModel.persistExportTreeUri(context, uri)
            if (projectId != null) viewModel.exportProjectFolder(context, projectId, uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.projectCreated.collect {
            showCreateDialog = false
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "工程项目管理",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                actions = {
                    IconButton(onClick = {
                        exportTreeLauncher.launch(viewModel.initialExportTreeUri(context))
                    }) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "选择/管理导出目录",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!uiState.isExporting) showCreateDialog = true },
                containerColor = EngineeringYellow,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建工程")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无工程项目，点击右下角按钮新建！",
                        color = TextSecondaryDark,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.projects, key = { it.project.id }) { item ->
                        val project = item.project
                        val isSelected = item.isSelected

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            border = if (isSelected) BorderStroke(2.dp, EngineeringYellow) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                // Clickable Header to select project
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectProject(project.id)
                                            onNavigateBack()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = project.name,
                                                color = TextPrimaryDark,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(EngineeringYellow.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = project.categoryName,
                                                    color = EngineeringYellow,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "当前选中",
                                                tint = EngineeringYellow,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (project.address.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = TextSecondaryDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = project.address,
                                                color = TextSecondaryDark,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (project.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = project.description,
                                            color = TextSecondaryDark.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Button actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Export ZIP Button
                                    Button(
                                        onClick = { if (!uiState.isExporting) exportOptionsProjectId = project.id },
                                        enabled = !uiState.isExporting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EngineeringYellow.copy(alpha = 0.2f),
                                            contentColor = EngineeringYellow
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "导出工程包",
                                            tint = EngineeringYellow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text(
                                            text = "导出工程包",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(8.dp))

                                    IconButton(
                                        onClick = { projectToDelete = project },
                                        enabled = !uiState.isExporting,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除工程",
                                            tint = ErrorRed
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(4.dp))

                                    // Gallery Button
                                    Button(
                                        onClick = { onNavigateToGallery(project.id) },
                                        enabled = !uiState.isExporting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.15f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "查看相册",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text(
                                            text = "进入相册",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Exporting progress overlay
            if (uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = EngineeringYellow)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.exportProgress.ifBlank { "正在打包工程文件与清单..." },
                                color = TextPrimaryDark,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    exportOptionsProjectId?.let { projectId ->
        AlertDialog(
            onDismissRequest = { exportOptionsProjectId = null },
            title = { Text("选择导出方式", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = { Text("华为/鸿蒙设备可选择不压缩文件夹，普通设备可选择压缩包。", color = TextSecondaryDark) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportOptionsProjectId = null
                    viewModel.exportProjectZip(context, projectId)
                }) { Text("压缩包", color = EngineeringYellow) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportOptionsProjectId = null
                    folderExportProjectId = projectId
                    runCatching {
                        exportTreeLauncher.launch(viewModel.initialExportTreeUri(context))
                    }.onFailure {
                        Toast.makeText(context, "设备不支持目录选择器", Toast.LENGTH_LONG).show()
                        folderExportProjectId = null
                    }
                }) { Text("不压缩文件夹", color = EngineeringYellow) }
            },
            containerColor = DarkCard
        )
    }

    if (showCreateDialog) {
        ProjectCreateDialog(
            categories = uiState.categories,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, cat, addr, desc ->
                viewModel.createProject(name, cat, addr, desc)
            }
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("删除工程？", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = { Text("将删除“${project.name}”及其照片、视频和标注成品。此操作不可撤销；删除失败的媒体会保留索引并汇总提示。", color = TextSecondaryDark) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    projectToDelete = null
                    viewModel.deleteProject(project)
                }) { Text("删除工程", color = ErrorRed) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { projectToDelete = null }) {
                    Text("取消", color = TextSecondaryDark)
                }
            },
            containerColor = DarkCard
        )
    }
}
