package com.example.assignpass.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignpass.data.Assignment
import com.example.assignpass.data.AssignmentRepository
import com.example.assignpass.ocr.OcrManager
import com.example.assignpass.ocr.OcrParsedResult
import com.example.assignpass.ocr.OcrResultParser
import com.example.assignpass.ocr.OcrTextLine
import com.example.assignpass.receiver.ReminderManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 截图识别 UI 状态
 */
sealed class ScreenshotUiState {
    data object Idle : ScreenshotUiState()
    data object Processing : ScreenshotUiState()
    data class Success(val message: String) : ScreenshotUiState()
    data class Error(val message: String) : ScreenshotUiState()
}

/**
 * 作业管理 ViewModel
 */
class AssignmentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AssignmentRepository(application)
    private val ocrManager = OcrManager()
    private val ocrResultParser = OcrResultParser()

    /** 所有作业列表 */
    val assignments: StateFlow<List<Assignment>> = repository.assignments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 学科历史记录 */
    val subjectHistory: StateFlow<List<String>> = repository.subjectHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 截图识别状态 */
    var screenshotState by mutableStateOf<ScreenshotUiState>(ScreenshotUiState.Idle)
        private set

    /** 当前选中的作业（编辑时使用） */
    var editingAssignment by mutableStateOf<Assignment?>(null)
        private set

    /** 是否正在显示添加/编辑页面 */
    var isShowingForm by mutableStateOf(false)
        private set

    /** 当前正在编辑的作业副本 */
    var currentFormAssignment by mutableStateOf(Assignment())
        private set

    /** 是否正在编辑已有作业（而非新建） */
    var isEditing by mutableStateOf(false)
        private set

    // ========== 导航操作 ==========

    fun startAddAssignment() {
        currentFormAssignment = Assignment()
        isEditing = false
        editingAssignment = null
        isShowingForm = true
    }

    fun startEditAssignment(assignment: Assignment) {
        currentFormAssignment = assignment.copy()
        isEditing = true
        editingAssignment = assignment
        isShowingForm = true
    }

    fun cancelForm() {
        isShowingForm = false
        editingAssignment = null
        currentFormAssignment = Assignment()
        isEditing = false
        screenshotState = ScreenshotUiState.Idle
    }

    // ========== 表单字段更新 ==========

    fun updatePlatform(value: String) {
        currentFormAssignment = currentFormAssignment.copy(platform = value)
    }

    fun updateSubject(value: String) {
        currentFormAssignment = currentFormAssignment.copy(subject = value)
    }

    fun updateName(value: String) {
        currentFormAssignment = currentFormAssignment.copy(name = value)
    }

    fun updateDeadline(timestamp: Long?) {
        currentFormAssignment = currentFormAssignment.copy(deadline = timestamp)
    }

    fun updateNotes(value: String) {
        currentFormAssignment = currentFormAssignment.copy(notes = value)
    }

    fun updateReminderEnabled(enabled: Boolean) {
        currentFormAssignment = currentFormAssignment.copy(reminderEnabled = enabled)
    }

    fun updateReminderMinutesBefore(minutes: Int) {
        currentFormAssignment = currentFormAssignment.copy(reminderMinutesBefore = minutes)
    }

    // ========== 表单验证 ==========

    fun validateForm(): String? {
        if (currentFormAssignment.name.isBlank()) return "请输入作业名称"
        if (currentFormAssignment.platform.isBlank()) return "请选择作业平台"
        if (currentFormAssignment.subject.isBlank()) return "请输入学科"
        if (currentFormAssignment.deadline == null) return "请设置截止时间"
        return null
    }

    // ========== CRUD 操作 ==========

    fun saveAssignment() {
        viewModelScope.launch {
            if (isEditing && editingAssignment != null) {
                repository.updateAssignment(currentFormAssignment)
                ReminderManager.cancelReminder(getApplication(), editingAssignment!!.id)
                if (currentFormAssignment.reminderEnabled) {
                    ReminderManager.scheduleReminder(getApplication(), currentFormAssignment)
                }
            } else {
                repository.addAssignment(currentFormAssignment)
                if (currentFormAssignment.reminderEnabled) {
                    ReminderManager.scheduleReminder(getApplication(), currentFormAssignment)
                }
            }
            cancelForm()
        }
    }

    fun deleteAssignment(assignment: Assignment) {
        viewModelScope.launch {
            repository.deleteAssignment(assignment.id)
            ReminderManager.cancelReminder(getApplication(), assignment.id)
        }
    }

    /**
     * 将作业标记为已完成
     */
    fun markAssignmentAsCompleted(assignment: Assignment) {
        viewModelScope.launch {
            repository.markAsCompleted(assignment.id)
            ReminderManager.cancelReminder(getApplication(), assignment.id)
        }
    }

    // ========== 截图识别 ==========

    /**
     * 用户选择/拍摄了图片，执行 OCR 识别并填充表单
     * @param uri 图片 URI
     */
    fun onImageCaptured(uri: android.net.Uri) {
        screenshotState = ScreenshotUiState.Processing

        viewModelScope.launch {
            val result = ocrManager.recognizeText(getApplication(), uri)

            result.fold(
                onSuccess = { ocrLines ->
                    if (ocrLines.isEmpty()) {
                        screenshotState = ScreenshotUiState.Error(
                            "未识别到文字，请确保截图清晰且包含作业信息"
                        )
                        return@launch
                    }

                    val parsed = ocrResultParser.parse(ocrLines)

                    if (parsed.isEmpty) {
                        // OCR 有文字但解析不出结构化字段，将原始文本放入备注
                        val rawText = ocrLines.joinToString("\n") { it.text }
                        updateNotes(rawText)
                        screenshotState = ScreenshotUiState.Success(
                            "已识别到文字，但未能自动解析。请参考备注栏中的识别结果手动填写"
                        )
                    } else {
                        applyOcrResult(parsed)
                        val fields = buildString {
                            if (parsed.platform.isNotBlank()) append("平台 ")
                            if (parsed.subject.isNotBlank()) append("学科 ")
                            if (parsed.name.isNotBlank()) append("作业 ")
                            if (parsed.deadline != null) append("截止时间 ")
                        }
                        screenshotState = ScreenshotUiState.Success(
                            "已自动识别并填写：$fields"
                        )
                    }
                },
                onFailure = { exception ->
                    val msg = when {
                        exception.message?.contains("Network") == true -> "网络错误，请检查连接"
                        exception.message?.contains("Time") == true -> "识别超时，请重试"
                        exception.message?.contains("File") == true -> "图片文件无法读取，请重试"
                        else -> "识别失败：${exception.localizedMessage ?: "未知错误"}"
                    }
                    screenshotState = ScreenshotUiState.Error(msg)
                }
            )
        }
    }

    /**
     * 清除截图状态（错误关闭 / 成功自动消失后调用）
     */
    fun clearScreenshotState() {
        screenshotState = ScreenshotUiState.Idle
    }

    /**
     * 将解析结果应用到表单字段
     */
    private fun applyOcrResult(result: OcrParsedResult) {
        if (result.platform.isNotBlank()) updatePlatform(result.platform)
        if (result.subject.isNotBlank()) updateSubject(result.subject)
        if (result.name.isNotBlank()) updateName(result.name)
        if (result.deadline != null) updateDeadline(result.deadline)
        // 备注：合并解析出的备注和识别出的其他文本
        val notes = buildString {
            if (result.notes.isNotBlank()) append(result.notes)
        }
        updateNotes(notes)
    }

    override fun onCleared() {
        super.onCleared()
        ocrManager.close()
    }
}
