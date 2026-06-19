package com.example.assignpass.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.assignpass.data.Assignment

/**
 * 提醒管理器 - 负责创建通知渠道、调度和取消提醒
 */
object ReminderManager {

    /**
     * 初始化通知渠道
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                "作业提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "作业截止提醒通知"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 调度提醒
     */
    fun scheduleReminder(context: Context, assignment: Assignment) {
        if (!assignment.reminderEnabled || assignment.deadline == null) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = assignment.deadline - (assignment.reminderMinutesBefore * 60 * 1000L)

        // 如果提醒时间已过，不设置提醒
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ASSIGNMENT_ID, assignment.id)
            putExtra(ReminderReceiver.EXTRA_ASSIGNMENT_NAME, assignment.name)
            putExtra(ReminderReceiver.EXTRA_PLATFORM, assignment.platform)
            putExtra(ReminderReceiver.EXTRA_SUBJECT, assignment.subject)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            assignment.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 set() 设置一次性提醒（不需要精确时间，使用 SET 以省电）
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    /**
     * 取消提醒
     */
    fun cancelReminder(context: Context, assignmentId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            assignmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
