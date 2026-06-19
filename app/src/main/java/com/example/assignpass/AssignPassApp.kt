package com.example.assignpass

import android.app.Application
import com.example.assignpass.receiver.ReminderManager

/**
 * 自定义 Application - 用于初始化全局状态
 */
class AssignPassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 创建通知渠道
        ReminderManager.createNotificationChannel(this)
    }
}
