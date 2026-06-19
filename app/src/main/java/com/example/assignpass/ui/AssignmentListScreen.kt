package com.example.assignpass.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignpass.data.Assignment
import java.text.SimpleDateFormat
import java.util.*

/**
 * 作业列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentListScreen(
    viewModel: AssignmentViewModel,
    onAddAssignment: () -> Unit,
    onEditAssignment: (Assignment) -> Unit
) {
    val assignments by viewModel.assignments.collectAsState()
    val pendingAssignments = remember(assignments) {
        assignments.filter { !it.isCompleted }.sortedBy { it.deadline ?: Long.MAX_VALUE }
    }
    val completedAssignments = remember(assignments) {
        assignments.filter { it.isCompleted }.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AssignPass",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAssignment,
                icon = { Icon(Icons.Default.Add, contentDescription = "添加") },
                text = { Text("添加作业") }
            )
        }
    ) { padding ->
        if (assignments.isEmpty()) {
            // 空状态
            EmptyState(modifier = Modifier
                .fillMaxSize()
                .padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 待完成
                if (pendingAssignments.isNotEmpty()) {
                    item(key = "section_pending") {
                        Text(
                            text = "待完成",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(pendingAssignments, key = { it.id }) { assignment ->
                        AssignmentCard(
                            assignment = assignment,
                            onEdit = { onEditAssignment(assignment) },
                            onDelete = { viewModel.deleteAssignment(assignment) },
                            onComplete = { viewModel.markAssignmentAsCompleted(assignment) }
                        )
                    }
                }

                // 已完成
                if (completedAssignments.isNotEmpty()) {
                    item(key = "section_completed") {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "已完成",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(completedAssignments, key = { "completed_${it.id}" }) { assignment ->
                        AssignmentCard(
                            assignment = assignment,
                            onEdit = null,
                            onDelete = { viewModel.deleteAssignment(assignment) },
                            onComplete = null
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个作业卡片
 */
@Composable
private fun AssignmentCard(
    assignment: Assignment,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onComplete: (() -> Unit)?
) {
    val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE)
    val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.CHINESE)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isOverdue = !assignment.isCompleted && assignment.deadline != null && assignment.deadline < System.currentTimeMillis()
    val cardAlpha = if (assignment.isCompleted) 0.45f else 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardAlpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (assignment.isCompleted) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 第一行：平台 + 学科标签 + 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 平台标签
                    if (assignment.platform.isNotBlank()) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(assignment.platform, fontSize = 12.sp) }
                        )
                    }
                    // 学科标签
                    if (assignment.subject.isNotBlank()) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(assignment.subject, fontSize = 12.sp) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 完成按钮（仅待办作业显示）
                    if (onComplete != null) {
                        TextButton(
                            onClick = onComplete,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "完成",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // 已完成的勾选标记
                    if (assignment.isCompleted) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 提醒图标（仅待办作业显示）
                    if (assignment.reminderEnabled && !assignment.isCompleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "已设置提醒",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 作业名称（已完成加删除线）
            Text(
                text = assignment.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (assignment.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (assignment.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 截止时间
            if (assignment.deadline != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "截止: ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(assignment.deadline)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOverdue)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " (${dayOfWeekFormat.format(Date(assignment.deadline))})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOverdue) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "已超期",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 备注（如果有）
            if (assignment.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = assignment.notes,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (assignment.isCompleted)
                        "完成于 ${SimpleDateFormat("MM/dd", Locale.CHINESE).format(Date(assignment.updatedAt))}"
                    else
                        "创建于 ${SimpleDateFormat("MM/dd", Locale.CHINESE).format(Date(assignment.createdAt))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${assignment.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 空状态占位
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📝",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有作业",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮添加第一个作业",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
