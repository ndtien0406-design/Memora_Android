package com.example.utils

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.R
import com.example.ui.SettingsActivity
import com.example.ui.StatsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Tiện ích hỗ trợ đồng bộ và điều hướng BottomNavigationView 3 tab:
 * - Sở thích (MainActivity)
 * - Thống kê (StatsActivity)
 * - Cài đặt (SettingsActivity)
 * Tắt hiệu ứng chuyển cảnh khi chuyển tab theo yêu cầu Bước 8.
 */
object BottomNavHelper {

    fun setupBottomNav(
        activity: Activity,
        bottomNavView: BottomNavigationView,
        currentTabId: Int
    ) {
        bottomNavView.selectedItemId = currentTabId
        bottomNavView.setOnItemSelectedListener { item ->
            if (item.itemId == currentTabId) {
                return@setOnItemSelectedListener true
            }

            val targetClass = when (item.itemId) {
                R.id.nav_hobbies -> MainActivity::class.java
                R.id.nav_stats -> StatsActivity::class.java
                R.id.nav_settings -> SettingsActivity::class.java
                else -> return@setOnItemSelectedListener false
            }

            val intent = Intent(activity, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            activity.startActivity(intent)

            // Chuyển tab mượt mà: Fade + Scale nhẹ (tab_enter / tab_exit)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.tab_enter, R.anim.tab_exit)
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.tab_enter, R.anim.tab_exit)
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(R.anim.tab_enter, R.anim.tab_exit)
            }
            false
        }
    }
}
