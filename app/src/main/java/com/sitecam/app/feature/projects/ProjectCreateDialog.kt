package com.sitecam.app.feature.projects

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.sitecam.app.core.database.entity.ProjectCategoryEntity
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@Composable
fun ProjectCreateDialog(
    categories: List<ProjectCategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, address: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "建筑") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = "新建工程项目",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Name Field (Required)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) isNameError = false
                    },
                    label = { Text("工程名称（必填）") },
                    isError = isNameError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EngineeringYellow,
                        focusedLabelColor = EngineeringYellow,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isNameError) {
                    Text(
                        text = "工程名称不能为空",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips Selector
                Text(
                    text = "工程类型",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.name == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EngineeringYellow,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.Black.copy(alpha = 0.3f),
                                labelColor = TextPrimaryDark
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Address Field
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("工程地点（可选）") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EngineeringYellow,
                        focusedLabelColor = EngineeringYellow,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("工程备注（可选）") },
                    maxLines = 2,
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
                    if (name.isBlank()) {
                        isNameError = true
                    } else {
                        onConfirm(name, selectedCategory, address, description)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EngineeringYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("创建", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondaryDark)
            }
        }
    )
}
