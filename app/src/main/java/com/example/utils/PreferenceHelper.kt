package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Tiện ích hỗ trợ quản lý cấu hình người dùng qua SharedPreferences.
 * - Quản lý giao diện Sáng / Tối / Hệ thống (key "theme_mode").
 * - Quản lý thứ tự sắp xếp (key "sort_mode").
 * - Quản lý đa ngôn ngữ Tiếng Việt / English (key "language") áp dụng qua AppCompatDelegate.setApplicationLocales (Bước 12).
 */
class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: String
        get() = prefs.getString("theme_mode", "light") ?: "light"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var sortMode: String
        get() = prefs.getString("sort_mode", Constants.SORT_BY_DATE) ?: Constants.SORT_BY_DATE
        set(value) = prefs.edit().putString("sort_mode", value).apply()

    var sortOrder: String
        get() = sortMode
        set(value) { sortMode = value }

    var isGridView: Boolean
        get() = prefs.getString(Constants.KEY_VIEW_MODE, "grid") == "grid"
        set(value) = prefs.edit().putString(Constants.KEY_VIEW_MODE, if (value) "grid" else "linear").apply()

    var language: String
        get() = prefs.getString(Constants.KEY_LANGUAGE, "vi") ?: "vi"
        set(value) {
            prefs.edit().putString(Constants.KEY_LANGUAGE, value).apply()
            applyAppLanguage(value)
        }

    fun applyAppTheme() {
        when (themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applyAppLanguage(lang: String = language) {
        val appLocale = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
