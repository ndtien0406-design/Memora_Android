package com.example.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.R
import com.example.databinding.ActivitySettingsBinding
import com.example.utils.BottomNavHelper
import com.example.utils.Constants
import com.example.utils.PreferenceHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Màn hình Cài đặt (Bước 8 + Bước 12: Đa ngôn ngữ).
 * - "Giao diện": 3 lựa chọn Sáng / Tối / Theo hệ thống. Lưu vào SharedPreferences (key "theme_mode"),
 *   áp dụng ngay bằng AppCompatDelegate.setDefaultNightMode.
 * - "Sắp xếp mặc định": Tên A–Z / Đánh giá / Ngày thêm, dùng chung key "sort_mode" với Bước 6.
 * - "Ngôn ngữ": Tiếng Việt / English, lưu vào key "language" và áp dụng bằng AppCompatDelegate.setApplicationLocales (Bước 12).
 * - Tích hợp BottomNavigationView dùng chung (layout_bottom_nav).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceHelper: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupThemeSelection()
        setupSortSelection()
        setupLanguageSelection()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái hiển thị
        updateThemeUI(preferenceHelper.themeMode)
        updateSortUI(preferenceHelper.sortMode)
        updateLanguageUI(preferenceHelper.language)
        binding.includedBottomNav.bottomNavView.selectedItemId = R.id.nav_settings
    }



    private fun setupThemeSelection() {
        updateThemeUI(preferenceHelper.themeMode)

        binding.cardThemeLight.setOnClickListener {
            selectTheme("light", AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding.cardThemeDark.setOnClickListener {
            selectTheme("dark", AppCompatDelegate.MODE_NIGHT_YES)
        }

        binding.cardThemeSystem.setOnClickListener {
            selectTheme("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun selectTheme(mode: String, nightMode: Int) {
        if (preferenceHelper.themeMode == mode) return

        preferenceHelper.themeMode = mode
        updateThemeUI(mode)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun updateThemeUI(currentMode: String) {
        val accentColor = ContextCompat.getColor(this, R.color.color_accent)
        val borderColor = ContextCompat.getColor(this, R.color.border)
        val textSecondaryColor = ContextCompat.getColor(this, R.color.color_text_secondary)

        fun applyCardState(
            card: MaterialCardView,
            icon: ImageView,
            text: TextView,
            isSelected: Boolean
        ) {
            if (isSelected) {
                card.strokeColor = accentColor
                card.strokeWidth = (1.8f * resources.displayMetrics.density).toInt()
                icon.imageTintList = ColorStateList.valueOf(accentColor)
                text.setTextColor(accentColor)
                text.setTypeface(text.typeface, Typeface.BOLD)
            } else {
                card.strokeColor = borderColor
                card.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                icon.imageTintList = ColorStateList.valueOf(textSecondaryColor)
                text.setTextColor(textSecondaryColor)
                text.setTypeface(text.typeface, Typeface.NORMAL)
            }
        }

        applyCardState(binding.cardThemeLight, binding.ivThemeLight, binding.tvThemeLight, currentMode == "light")
        applyCardState(binding.cardThemeDark, binding.ivThemeDark, binding.tvThemeDark, currentMode == "dark")
        applyCardState(binding.cardThemeSystem, binding.ivThemeSystem, binding.tvThemeSystem, currentMode == "system")
    }

    private fun setupSortSelection() {
        updateSortUI(preferenceHelper.sortMode)

        binding.rowSortName.setOnClickListener {
            selectSort(Constants.SORT_BY_NAME)
        }

        binding.rowSortRating.setOnClickListener {
            selectSort(Constants.SORT_BY_RATING)
        }

        binding.rowSortDate.setOnClickListener {
            selectSort(Constants.SORT_BY_DATE)
        }
    }

    private fun selectSort(sortMode: String) {
        preferenceHelper.sortMode = sortMode
        updateSortUI(sortMode)
    }

    private fun updateSortUI(currentSort: String) {
        binding.rbSortName.isChecked = (currentSort == Constants.SORT_BY_NAME)
        binding.rbSortRating.isChecked = (currentSort == Constants.SORT_BY_RATING)
        binding.rbSortDate.isChecked = (currentSort == Constants.SORT_BY_DATE)
    }

    // =========================================================================
    // BƯỚC 12: QUẢN LÝ ĐA NGÔN NGỮ (TIẾNG VIỆT / ENGLISH)
    // =========================================================================

    private fun setupLanguageSelection() {
        updateLanguageUI(preferenceHelper.language)

        binding.rowLangVi.setOnClickListener {
            selectLanguage("vi")
        }

        binding.rowLangEn.setOnClickListener {
            selectLanguage("en")
        }
    }

    private fun selectLanguage(langCode: String) {
        if (preferenceHelper.language == langCode) return
        preferenceHelper.language = langCode
        updateLanguageUI(langCode)
    }

    private fun updateLanguageUI(currentLang: String) {
        binding.rbLangVi.isChecked = (currentLang == "vi")
        binding.rbLangEn.isChecked = (currentLang == "en")
    }

    private fun setupBottomNav() {
        BottomNavHelper.setupBottomNav(
            activity = this,
            bottomNavView = binding.includedBottomNav.bottomNavView,
            currentTabId = R.id.nav_settings
        )
    }
}
