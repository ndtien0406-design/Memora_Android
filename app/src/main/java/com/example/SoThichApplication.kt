package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.utils.PreferenceHelper

/**
 * Lớp Application tùy chỉnh cho ứng dụng Sở Thích.
 * - Đọc cấu hình theme_mode và áp dụng ngay khi mở app.
 * - Tạo NotificationChannel "reminders" khi khởi động app (Bước 11).
 */
class SoThichApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()
        preferenceHelper.applyAppLanguage()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_reminders_desc)
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}
