package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.database.HobbyDatabaseHelper
import com.example.receiver.ReminderReceiver

/**
 * Tiện ích quản lý đặt và hủy báo thức nhắc nhở bằng AlarmManager (Bước 11).
 * - Request code = id sở thích.
 * - Android 12+ kiểm tra canScheduleExactAlarms():
 *   + Có quyền: dùng setExactAndAllowWhileIdle.
 *   + Không có quyền: dùng setAndAllowWhileIdle.
 * - Hủy báo khi tắt nhắc hoặc xóa sở thích.
 */
object ReminderScheduler {

    const val EXTRA_HOBBY_NAME = "extra_hobby_name"

    fun scheduleReminder(
        context: Context,
        hobbyId: Long,
        hobbyName: String,
        reminderTimeMs: Long
    ) {
        if (reminderTimeMs <= System.currentTimeMillis() || hobbyId <= 0L) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(Constants.EXTRA_HOBBY_ID, hobbyId)
            putExtra(EXTRA_HOBBY_NAME, hobbyName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hobbyId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Trường hợp quyền chính xác bị chặn đột xuất
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, hobbyId: Long) {
        if (hobbyId <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hobbyId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun rescheduleAllFutureReminders(context: Context) {
        val dbHelper = HobbyDatabaseHelper(context)
        val futureHobbies = dbHelper.getFutureReminders(System.currentTimeMillis())
        for (hobby in futureHobbies) {
            scheduleReminder(
                context = context,
                hobbyId = hobby.id,
                hobbyName = hobby.name,
                reminderTimeMs = hobby.reminderTime
            )
        }
    }
}
