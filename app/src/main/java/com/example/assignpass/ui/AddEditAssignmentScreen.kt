package com.example.assignpass.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 添加/编辑作业表单页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditAssignmentScreen(
    viewModel: AssignmentViewModel,
    onNavigateBack: () -> Unit
) {
    val assignment = viewModel.currentFormAssignment
    val subjectHistory by viewModel.subjectHistory.collectAsState()
    val screenshotState by viewModel::screenshotState

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // 错误时自动滚动到顶部
    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            scrollState.animateScrollTo(0)
        }
    }

    // 截图相关 UI 状态
    var showMethodPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val predefinedPlatforms = remember {
        listOf(
            "mis课程平台", "中国大学MOOC", "雨课堂", "U校园", "学堂在线",
            "知到", "学习通", "步道乐跑", "智慧树"
        )
    }
    var platformIsCustom by remember {
        mutableStateOf(assignment.platform.isNotEmpty() && assignment.platform !in predefinedPlatforms)
    }

    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)

    // ========== ActivityResultLaunchers ==========

    // 相册选择 — 选择后直接进行 OCR 识别
    // （用户可在系统的相册应用中自行裁剪图片后再选择）
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImageCaptured(it) }
    }

    // 成功状态 2 秒后自动恢复 Idle
    if (screenshotState is ScreenshotUiState.Success) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearScreenshotState()
        }
    }

    // ========== 界面 ==========

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditing) "编辑作业" else "添加作业",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { onNavigateBack() }) {
                        Text("取消")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val error = viewModel.validateForm()
                            if (error != null) {
                                errorMessage = error
                            } else {
                                errorMessage = null
                                viewModel.saveAssignment()
                            }
                        }
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // 错误提示
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp
                    )
                }
            }

            // ===== 上传截图一键添加 =====
            when (screenshotState) {
                is ScreenshotUiState.Idle -> {
                    OcrIdleCard(onClick = { showMethodPicker = true })
                }
                is ScreenshotUiState.Processing -> {
                    OcrProcessingCard()
                }
                is ScreenshotUiState.Error -> {
                    OcrErrorCard(
                        message = (screenshotState as ScreenshotUiState.Error).message,
                        onRetry = { showMethodPicker = true },
                        onDismiss = { viewModel.clearScreenshotState() }
                    )
                }
                is ScreenshotUiState.Success -> {
                    OcrSuccessCard(
                        message = (screenshotState as ScreenshotUiState.Success).message
                    )
                }
            }

            // ===== 作业平台 =====
            SectionLabel("作业平台 *")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedPlatforms.forEach { platform ->
                    FilterChip(
                        selected = assignment.platform == platform,
                        onClick = {
                            viewModel.updatePlatform(platform)
                            platformIsCustom = false
                        },
                        label = { Text(platform, fontSize = 13.sp) }
                    )
                }
                FilterChip(
                    selected = platformIsCustom,
                    onClick = {
                        platformIsCustom = true
                        if (assignment.platform.isEmpty() || assignment.platform in predefinedPlatforms) {
                            viewModel.updatePlatform("")
                        }
                    },
                    label = { Text("其他", fontSize = 13.sp) },
                    leadingIcon = if (platformIsCustom) {
                        { Text("✏️", fontSize = 11.sp) }
                    } else null
                )
            }
            if (platformIsCustom) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = assignment.platform,
                    onValueChange = { viewModel.updatePlatform(it) },
                    placeholder = { Text("请输入自定义平台名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 学科 =====
            SectionLabel("学科 *")
            OutlinedTextField(
                value = assignment.subject,
                onValueChange = { viewModel.updateSubject(it) },
                placeholder = { Text("请输入学科名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (subjectHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "曾使用过的学科",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subjectHistory) { subject ->
                        AssistChip(
                            onClick = { viewModel.updateSubject(subject) },
                            label = { Text(subject, fontSize = 13.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 作业名称 =====
            SectionLabel("作业名称 *")
            OutlinedTextField(
                value = assignment.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = { Text("请输入作业名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 截止时间 =====
            SectionLabel("截止时间 *")
            OutlinedTextField(
                value = if (assignment.deadline != null) {
                    "${dateFormat.format(Date(assignment.deadline))} ${timeFormat.format(Date(assignment.deadline))}"
                } else {
                    ""
                },
                onValueChange = {},
                placeholder = { Text("点击选择截止时间") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("选择")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 备注 =====
            SectionLabel("备注")
            OutlinedTextField(
                value = assignment.notes,
                onValueChange = { viewModel.updateNotes(it) },
                placeholder = { Text("添加备注信息（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 提醒设置 =====
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("提醒设置")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开启提醒", fontSize = 16.sp)
                Switch(
                    checked = assignment.reminderEnabled,
                    onCheckedChange = { viewModel.updateReminderEnabled(it) }
                )
            }

            if (assignment.reminderEnabled) {
                Text("提前提醒时间", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                val reminderOptions = listOf(
                    10 to "10 分钟",
                    15 to "15 分钟",
                    30 to "30 分钟",
                    60 to "1 小时",
                    120 to "2 小时",
                    1440 to "1 天",
                    2880 to "2 天"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reminderOptions.forEach { (minutes, label) ->
                        FilterChip(
                            selected = assignment.reminderMinutesBefore == minutes,
                            onClick = { viewModel.updateReminderMinutesBefore(minutes) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ========== 底部选择菜单 ==========
    if (showMethodPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMethodPicker = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "从相册选择作业截图",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                Text(
                    text = "选择截图后可以裁剪，提高识别准确率",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 从相册选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showMethodPicker = false
                            getContentLauncher.launch("image/*")
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("从相册选择", fontSize = 16.sp)
                }
            }
        }
    }

    // ========== 日期选择器对话框 ==========
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = assignment.deadline ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            val cal = Calendar.getInstance().apply {
                                if (assignment.deadline != null) timeInMillis = assignment.deadline
                                timeInMillis = dateMillis
                            }
                            viewModel.updateDeadline(cal.timeInMillis)
                        }
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ========== 时间选择器对话框 ==========
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            assignment.deadline?.let { timeInMillis = it }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            assignment.deadline?.let { timeInMillis = it }
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        viewModel.updateDeadline(cal.timeInMillis)
                        showTimePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}

// ==================== 截图卡片子组件 ====================

@Composable
private fun OcrIdleCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "上传截图一键添加",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "从相册选择作业截图，自动识别填表",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "测试阶段，填充准确度有限",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "上传截图",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun OcrProcessingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text("正在识别截图中...", fontSize = 14.sp)
            Text(
                "请稍候",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OcrErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚠️ 识别失败",
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun OcrSuccessCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✅", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "识别成功！",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== 工具组件 ====================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

