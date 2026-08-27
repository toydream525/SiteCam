package com.sitecam.app.feature.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GalleryUiEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.currentProject != null) {
                                "${uiState.currentProject!!.name} - 相册"
                            } else "工程相册",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "共 ${uiState.mediaItems.count { it.mediaType == "PHOTO" }} 张照片 · ${uiState.mediaItems.count { it.mediaType == "VIDEO" }} 个视频",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == GalleryFilter.ALL,
                    onClick = { viewModel.setFilter(GalleryFilter.ALL) },
                    label = { Text("全部") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EngineeringYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        labelColor = TextPrimaryDark
                    )
                )
                FilterChip(
                    selected = uiState.selectedFilter == GalleryFilter.TODAY,
                    onClick = { viewModel.setFilter(GalleryFilter.TODAY) },
                    label = { Text("今天") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EngineeringYellow,
                        selectedLabelColor = Color.Black,
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        labelColor = TextPrimaryDark
                    )
                )
                FilterChip(
                    selected = uiState.selectedFilter == GalleryFilter.ISSUES_ONLY,
                    onClick = { viewModel.setFilter(GalleryFilter.ISSUES_ONLY) },
                    label = { Text("现场问题") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorRed,
                        selectedLabelColor = Color.White,
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        labelColor = TextPrimaryDark
                    )
                )
            }

            if (uiState.mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无媒体",
                        color = TextSecondaryDark,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.mediaItems, key = { it.id }) { item ->
                        val issue = uiState.issueByMediaId[item.id]
                        Box(
                            modifier = Modifier
                                .aspectRatio(1.0f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onNavigateToDetail(item.id) }
                        ) {
                            if (item.mediaType == "VIDEO") {
                                VideoThumbnail(item.contentUri, Modifier.fillMaxSize())
                            } else {
                                AsyncImage(
                                    model = item.contentUri,
                                    contentDescription = item.fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Timestamp Badge
                            val timeStr = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
                                .format(Date(item.captureTimestamp))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = timeStr,
                                    color = Color.White,
                                    fontSize = 9.sp
                                )
                            }

                            // Issue Badge
                            if (item.isIssue || issue != null) {
                                val severityColor = when (issue?.severity) {
                                    "CRITICAL" -> ErrorRed
                                    "IMPORTANT" -> com.sitecam.app.ui.theme.WarningYellow
                                    else -> EngineeringYellow
                                }
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .widthIn(max = 132.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(severityColor.copy(alpha = 0.94f))
                                            .padding(horizontal = 5.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReportProblem,
                                            contentDescription = "现场问题",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = when (issue?.severity) {
                                                "CRITICAL" -> "严重"
                                                "IMPORTANT" -> "重要"
                                                else -> "一般"
                                            },
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 3.dp)
                                        )
                                    }
                                    issue?.title?.takeIf { it.isNotBlank() }?.let { title ->
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.72f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
