package com.sitecam.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@Composable
internal fun SiteCamEasterEggDialog(
    onDismiss: () -> Unit,
    onOpenStar: () -> Unit,
    onOpenIssue: () -> Unit,
    onCopyProjectLink: () -> Unit,
    onOpenWebsite: () -> Unit
) {
    val visibilityState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibilityState.targetState = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visibleState = visibilityState,
                enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(.92f)
                        .widthIn(max = 460.dp)
                        .heightIn(max = maxHeight * .9f),
                    shape = RoundedCornerShape(26.dp),
                    color = DarkCard,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ViewfinderStarlight()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "咔嚓，发现彩蛋！",
                            color = TextPrimaryDark,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "小小星光暗房 · ninjaaqua",
                            color = EngineeringYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "https://github.com/toydream525/SiteCam",
                            color = TextSecondaryDark,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            softWrap = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "感谢你使用 SiteCam。如果它帮到了你，欢迎去 GitHub 点个 Star；遇到问题或有新想法，也欢迎提个 Issue。",
                            color = TextSecondaryDark,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = onOpenStar,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EngineeringYellow,
                                contentColor = DarkBackground
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("去点 Star", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenIssue,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("提 Issue")
                        }
                        TextButton(onClick = onCopyProjectLink, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("复制项目链接")
                        }
                        TextButton(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("访问官网")
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewfinderStarlight() {
    Box(
        modifier = Modifier
            .size(width = 148.dp, height = 104.dp)
            .background(DarkBackground.copy(alpha = .72f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 15.dp.toPx()
            val corner = 17.dp.toPx()
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val color = EngineeringYellow.copy(alpha = .84f)
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            listOf(
                Offset(left, top) to Offset(left + corner, top),
                Offset(left, top) to Offset(left, top + corner),
                Offset(right - corner, top) to Offset(right, top),
                Offset(right, top) to Offset(right, top + corner),
                Offset(left, bottom - corner) to Offset(left, bottom),
                Offset(left, bottom) to Offset(left + corner, bottom),
                Offset(right - corner, bottom) to Offset(right, bottom),
                Offset(right, bottom - corner) to Offset(right, bottom)
            ).forEach { (start, end) -> drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round) }
            drawCircle(color.copy(alpha = .28f), radius = 34.dp.toPx(), style = stroke)
        }
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = EngineeringYellow,
            modifier = Modifier.size(36.dp)
        )
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = EngineeringYellow,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset((-9).dp, 5.dp)
                .size(17.dp)
        )
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = .72f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(10.dp, (-6).dp)
                .size(11.dp)
        )
    }
}
