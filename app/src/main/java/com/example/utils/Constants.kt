package com.example.utils

/**
 * Các hằng số định nghĩa chung cho ứng dụng Sở Thích.
 */
object Constants {
    // 7 danh mục mặc định theo đúng thứ tự SPEC
    val CATEGORIES = listOf(
        "Âm nhạc",
        "Phim ảnh",
        "Sách",
        "Thể thao",
        "Du lịch",
        "Ẩm thực",
        "Khác"
    )

    // Khóa Intent Extra
    const val EXTRA_HOBBY_ID = "extra_hobby_id"
    const val EXTRA_IS_EDIT_MODE = "extra_is_edit_mode"
    const val EXTRA_IMAGE_PATH = "extra_image_path"
    const val EXTRA_CATEGORY = "extra_category"

    // Khóa SharedPreferences
    const val PREFS_NAME = "hobby_prefs"
    const val KEY_THEME_MODE = "pref_theme_mode" // "light", "dark", "system"
    const val KEY_VIEW_MODE = "pref_view_mode"   // "grid", "linear"
    const val KEY_LANGUAGE = "language"          // "vi", "en" (Bước 12)

    // Giá trị sắp xếp
    const val SORT_BY_DATE = "date"
    const val SORT_BY_NAME = "name"
    const val SORT_BY_RATING = "rating"
}
