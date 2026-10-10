package com.sitecam.app.feature.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.TextButton
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
import com.sitecam.app.ui.theme.PageBackground as DarkBackground
import com.sitecam.app.ui.theme.PageCard as DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.PageText as TextPrimaryDark
import com.sitecam.app.ui.theme.PageMuted as TextSecondaryDark

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
    val tableOfContentsScrollState = rememberScrollState()
    val chapterScrollState = rememberScrollState()

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

    LaunchedEffect(selectedSection, showTableOfContents) {
        if (!showTableOfContents) chapterScrollState.scrollTo(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "使用指南",
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
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(paddingValues)) {
                    val wide = maxWidth >= 840.dp
                    androidx.activity.compose.BackHandler(!wide && !showTableOfContents) { showTableOfContents = true }
                    @Composable fun directory(pane:Modifier) {
                        Column(pane.verticalScroll(tableOfContentsScrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("章节目录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            guideSections.forEachIndexed { index, section ->
                                Card(Modifier.fillMaxWidth().clickable { selectedSection = index; showTableOfContents = false }, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                                    Text("${index + 1}  ${section.title}", Modifier.padding(16.dp), color = if(index == selectedSection) EngineeringYellow else TextPrimaryDark, fontSize = 16.sp)
                                }
                            }
                            OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) { Text("重新查看功能指引") }
                        }
                    }
                    @Composable fun chapter(pane:Modifier) {
                        androidx.compose.foundation.layout.Box(pane, contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight().verticalScroll(chapterScrollState).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if(!wide) TextButton(onClick = { showTableOfContents = true }) { Text("‹ 返回目录") }
                            selected?.let { section ->
                                Text(section.title, color = EngineeringYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                section.lines.forEach { line -> Text(line, color = TextPrimaryDark, fontSize = 17.sp, lineHeight = 27.sp, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                        }
                    }
                    if(wide) Row(Modifier.fillMaxSize()) {
                        directory(Modifier.width(320.dp).fillMaxHeight())
                        chapter(Modifier.weight(1f).fillMaxHeight())
                    } else if(showTableOfContents) directory(Modifier.fillMaxSize()) else chapter(Modifier.fillMaxSize())
                }
            }
        }
    }
}
