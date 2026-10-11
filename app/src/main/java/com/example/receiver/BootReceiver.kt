package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.utils.ReminderScheduler

/**
 * BroadcastReceiver nhận sự kiện khởi động thiết bị hoàn tất (BOOT_COMPLETED) (Bước 11).
 * Đọc tất cả các sở thích có thời gian nhắc nhở trong tương lai và đặt lại báo thức với AlarmManager.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            ReminderScheduler.rescheduleAllFutureReminders(context)
        }
    }
}
