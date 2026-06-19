package com.example.assignpass.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 作业数据模型
 */
data class Assignment(
    val id: String = UUID.randomUUID().toString(),
    val platform: String = "",          // 作业平台
    val subject: String = "",           // 学科
    val name: String = "",              // 作业名称
    val deadline: Long? = null,         // 截止时间 (timestamp in millis)
    val notes: String = "",             // 备注
    val reminderEnabled: Boolean = false, // 是否开启提醒
    val reminderMinutesBefore: Int = 30,  // 提前多少分钟提醒 (默认30分钟)
    val isCompleted: Boolean = false,     // 是否已完成
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * JSON 持久化工具 - 管理作业列表的存储和读取
 */
class AssignmentStorage(private val gson: Gson) {

    /**
     * 将作业列表保存为 JSON 字符串
     */
    fun toJson(assignments: List<Assignment>): String {
        return gson.toJson(assignments)
    }

    /**
     * 从 JSON 字符串读取作业列表
     */
    fun fromJson(json: String): List<Assignment> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Assignment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val FILE_NAME = "assignments.json"
    }
}
