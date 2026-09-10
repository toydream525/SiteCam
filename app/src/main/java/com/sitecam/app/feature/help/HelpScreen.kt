package com.sitecam.app.feature.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var sections by remember { mutableStateOf<List<GuideSection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableIntStateOf(0) }
    var showTableOfContents by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }

    fun loadGuide() {
        sections = null
        error = null
        retryKey += 1
    }

    LaunchedEffect(context, retryKey) {
        if (sections == null) {
            UserGuideRepository(context.applicationContext).loadSections()
                .onSuccess { sections = it }
                .onFailure { error = it.message ?: "帮助内容暂时不可用" }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "使用技巧与教程",
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
                            tint = TextPrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        when {
            sections == null && error == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = EngineeringYellow)
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error ?: "帮助内容暂时不可用",
                        color = TextPrimaryDark,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = ::loadGuide) {
                        Text(text = "重新读取")
                    }
                }
            }

            else -> {
                val guideSections = sections.orEmpty()
                val selected = guideSections.getOrNull(selectedSection)
                    ?: guideSections.firstOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showTableOfContents) {
                        Text(
                            text = "目录",
                            color = TextPrimaryDark,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        guideSections.forEachIndexed { index, section ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSection = index
                                        showTableOfContents = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCard)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = EngineeringYellow,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = section.title,
                                        color = TextPrimaryDark,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showTableOfContents = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "返回目录", fontSize = 16.sp)
                        }
                        selected?.let { section ->
                            Text(
                                text = section.title,
                                color = EngineeringYellow,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            section.lines.forEach { line ->
                                Text(
                                    text = line,
                                    color = TextPrimaryDark,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onReplayOnboarding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "重新查看功能指引", fontSize = 16.sp)
                    }
                    Text(
                        text = "指南内容随应用离线提供，现场没有网络也可以查看。",
                        color = TextSecondaryDark,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}
