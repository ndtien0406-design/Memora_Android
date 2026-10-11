package com.example.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.SoThichApplication
import com.example.ui.HobbyDetailActivity
import com.example.utils.Constants
import com.example.utils.ReminderScheduler

/**
 * BroadcastReceiver nhận sự kiện nhắc nhở từ AlarmManager khi đến giờ (Bước 11).
 * - Hiển thị Notification: tiêu đề "Sở Thích", nội dung "Đến giờ: <tên sở thích>".
 * - Bấm vào thông báo mở HobbyDetailActivity của mục đó (PendingIntent, FLAG_IMMUTABLE).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hobbyId = intent.getLongExtra(Constants.EXTRA_HOBBY_ID, -1L)
        val hobbyName = intent.getStringExtra(ReminderScheduler.EXTRA_HOBBY_NAME)
            ?: context.getString(R.string.app_name)

        if (hobbyId <= 0L) return

        val detailIntent = Intent(context, HobbyDetailActivity::class.java).apply {
            putExtra(HobbyDetailActivity.EXTRA_KEY_HOBBY_ID, hobbyId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            hobbyId.toInt(),
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SoThichApplication.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notebook_open)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_content_format, hobbyName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(hobbyId.toInt(), notification)
        } catch (_: SecurityException) {
            // Không có quyền POST_NOTIFICATIONS
        }
    }
}
