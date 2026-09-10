package com.sitecam.app.feature.issue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import com.sitecam.app.ui.theme.WarningYellow

@Composable
fun IssueDialog(
    mediaId: Long,
    onDismiss: () -> Unit,
    existingIssue: com.sitecam.app.core.database.entity.IssueEntity? = null,
    onRemove: (() -> Unit)? = null,
    onConfirm: (title: String, severity: String, description: String, status: String) -> Unit
) {
    var title by remember(mediaId) { mutableStateOf(existingIssue?.title.orEmpty()) }
    var severity by remember { mutableStateOf(existingIssue?.severity ?: "NORMAL") } // NORMAL, IMPORTANT, CRITICAL
    var description by remember(mediaId) { mutableStateOf(existingIssue?.description.orEmpty()) }
    var status by remember(mediaId) { mutableStateOf(existingIssue?.status ?: "PENDING") }
    var isTitleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = "标记工程现场问题",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) isTitleError = false
                    },
                    label = { Text("问题名称（如：临边未设防护栏）") },
                    isError = isTitleError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EngineeringYellow,
                        focusedLabelColor = EngineeringYellow,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTitleError) {
                    Text(
                        text = "问题名称不能为空",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Severity selector
                Text(
                    text = "严重等级",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = severity == "NORMAL",
                        onClick = { severity = "NORMAL" },
                        label = { Text("一般") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EngineeringYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            labelColor = TextPrimaryDark
                        )
                    )
                    FilterChip(
                        selected = severity == "IMPORTANT",
                        onClick = { severity = "IMPORTANT" },
                        label = { Text("重要") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarningYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            labelColor = TextPrimaryDark
                        )
                    )
                    FilterChip(
                        selected = severity == "CRITICAL",
                        onClick = { severity = "CRITICAL" },
                        label = { Text("严重") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ErrorRed,
                            selectedLabelColor = Color.White,
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            labelColor = TextPrimaryDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("PENDING" to "待处理", "IN_PROGRESS" to "处理中", "COMPLETED" to "已完成").forEach { (value, label) ->
                        FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) })
                    }
                }
                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("问题原因 / 整改要求 / 备注") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EngineeringYellow,
                        focusedLabelColor = EngineeringYellow,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        isTitleError = true
                    } else {
                        onConfirm(title.trim(), severity, description.trim(), status)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("保存问题记录", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (existingIssue != null && onRemove != null) TextButton(onClick = onRemove) { Text("取消问题", color = ErrorRed) }
                TextButton(onClick = onDismiss) { Text("取消", color = TextSecondaryDark) }
            }
        }
    )
}

@Composable
fun QuickIssueDialog(
    mediaId: Long,
    onDismiss: () -> Unit,
    onConfirm: (title: String, severity: String, description: String) -> Unit
) = IssueDialog(mediaId = mediaId, onDismiss = onDismiss,
    onConfirm = { title, severity, description, _ -> onConfirm(title, severity, description) })
