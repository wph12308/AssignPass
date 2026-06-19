package com.example.assignpass.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.assignpass.MainActivity
import com.example.assignpass.R

/**
 * 提醒广播接收器 - 接收 AlarmManager 触发的提醒
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val assignmentId = intent.getStringExtra(EXTRA_ASSIGNMENT_ID) ?: return
        val assignmentName = intent.getStringExtra(EXTRA_ASSIGNMENT_NAME) ?: "作业"
        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: ""
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""

        val title = "作业提醒"
        val body = if (platform.isNotEmpty() && subject.isNotEmpty()) {
            "$platform · $subject · $assignmentName"
        } else {
            assignmentName
        }

        showNotification(context, assignmentId, title, body)
    }

    private fun showNotification(context: Context, assignmentId: String, title: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 点击通知打开主界面
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, assignmentId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(assignmentId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "assignment_reminder"
        const val EXTRA_ASSIGNMENT_ID = "assignment_id"
        const val EXTRA_ASSIGNMENT_NAME = "assignment_name"
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_SUBJECT = "subject"
    }
}
