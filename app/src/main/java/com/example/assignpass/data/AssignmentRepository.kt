package com.example.assignpass.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 作业仓库 - 负责作业数据的增删改查和文件读写
 */
class AssignmentRepository(private val context: Context) {

    private val gson = Gson()
    private val storage = AssignmentStorage(gson)

    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()

    private val _subjectHistory = MutableStateFlow<List<String>>(emptyList())
    val subjectHistory: StateFlow<List<String>> = _subjectHistory.asStateFlow()

    private val assignmentsFile: File
        get() = File(context.filesDir, AssignmentStorage.FILE_NAME)

    private val subjectHistoryFile: File
        get() = File(context.filesDir, "subject_history.json")

    init {
        loadFromFile()
        loadSubjectHistory()
    }

    // ========== 作业读写 ==========

    private fun loadFromFile() {
        try {
            if (assignmentsFile.exists()) {
                val json = assignmentsFile.readText()
                _assignments.value = storage.fromJson(json)
            }
        } catch (e: Exception) {
            _assignments.value = emptyList()
        }
    }

    private suspend fun saveToFile() = withContext(Dispatchers.IO) {
        val json = storage.toJson(_assignments.value)
        assignmentsFile.writeText(json)
    }

    fun getAllAssignments(): List<Assignment> {
        return _assignments.value.sortedBy { it.deadline ?: Long.MAX_VALUE }
    }

    fun getAssignmentById(id: String): Assignment? {
        return _assignments.value.find { it.id == id }
    }

    suspend fun addAssignment(assignment: Assignment) {
        val current = _assignments.value.toMutableList()
        current.add(assignment)
        _assignments.value = current
        saveToFile()
        // 保存学科到历史
        addSubjectToHistory(assignment.subject)
    }

    suspend fun updateAssignment(assignment: Assignment) {
        val current = _assignments.value.toMutableList()
        val index = current.indexOfFirst { it.id == assignment.id }
        if (index != -1) {
            current[index] = assignment.copy(updatedAt = System.currentTimeMillis())
            _assignments.value = current
            saveToFile()
            // 保存学科到历史
            addSubjectToHistory(assignment.subject)
        }
    }

    suspend fun deleteAssignment(id: String) {
        val current = _assignments.value.toMutableList()
        current.removeAll { it.id == id }
        _assignments.value = current
        saveToFile()
    }

    suspend fun markAsCompleted(id: String) {
        val current = _assignments.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(
                isCompleted = true,
                updatedAt = System.currentTimeMillis()
            )
            _assignments.value = current
            saveToFile()
        }
    }

    // ========== 学科历史 ==========

    private fun loadSubjectHistory() {
        try {
            if (subjectHistoryFile.exists()) {
                val json = subjectHistoryFile.readText()
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(json, type) ?: emptyList()
                _subjectHistory.value = list
            }
        } catch (e: Exception) {
            _subjectHistory.value = emptyList()
        }
    }

    private suspend fun saveSubjectHistory() = withContext(Dispatchers.IO) {
        val json = gson.toJson(_subjectHistory.value)
        subjectHistoryFile.writeText(json)
    }

    private suspend fun addSubjectToHistory(subject: String) {
        if (subject.isBlank()) return
        val current = _subjectHistory.value.toMutableList()
        // 去重：如果已存在则移除旧位置，添加到最前面
        current.removeAll { it == subject }
        current.add(0, subject)
        // 限制最多保存 20 条
        val trimmed = current.take(20)
        _subjectHistory.value = trimmed
        saveSubjectHistory()
    }
}
