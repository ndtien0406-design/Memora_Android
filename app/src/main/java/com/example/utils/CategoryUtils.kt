package com.example.utils

import android.content.Context
import com.example.R

/**
 * Tiện ích ánh xạ danh mục giữa giá trị lưu trong CSDL (tiếng Việt chuẩn)
 * và nhãn hiển thị đa ngôn ngữ (Tiếng Việt / English) theo yêu cầu Bước 12.
 */
object CategoryUtils {

    // 7 danh mục gốc tiếng Việt lưu trong SQLite theo SPEC
    val DB_CATEGORIES = listOf(
        "Âm nhạc",
        "Phim ảnh",
        "Sách",
        "Thể thao",
        "Du lịch",
        "Ẩm thực",
        "Khác"
    )

    /**
     * Ánh xạ từ giá trị tiếng Việt lưu trong SQLite sang chuỗi hiển thị theo ngôn ngữ hiện tại của app.
     */
    fun getCategoryLabel(context: Context, dbCategory: String?): String {
        if (dbCategory.isNullOrBlank()) return ""
        return when (dbCategory.trim()) {
            "Âm nhạc", "Music" -> context.getString(R.string.category_music)
            "Phim ảnh", "Movies", "Movie" -> context.getString(R.string.category_movie)
            "Sách", "Books", "Book" -> context.getString(R.string.category_book)
            "Thể thao", "Sports", "Sport" -> context.getString(R.string.category_sport)
            "Du lịch", "Travel" -> context.getString(R.string.category_travel)
            "Ẩm thực", "Food", "Food & Cuisine" -> context.getString(R.string.category_food)
            "Khác", "Other" -> context.getString(R.string.category_other)
            "Tất cả", "All" -> context.getString(R.string.category_all)
            else -> dbCategory
        }
    }

    /**
     * Ánh xạ ngược lại từ chuỗi hiển thị người dùng chọn trên giao diện về giá trị tiếng Việt chuẩn để lưu SQLite.
     */
    fun toDbCategory(context: Context, displayLabel: String?): String {
        if (displayLabel.isNullOrBlank()) return "Khác"

        val music = context.getString(R.string.category_music)
        val movies = context.getString(R.string.category_movie)
        val books = context.getString(R.string.category_book)
        val sports = context.getString(R.string.category_sport)
        val travel = context.getString(R.string.category_travel)
        val food = context.getString(R.string.category_food)
        val other = context.getString(R.string.category_other)
        val all = context.getString(R.string.category_all)

        return when (displayLabel.trim()) {
            music, "Âm nhạc", "Music" -> "Âm nhạc"
            movies, "Phim ảnh", "Movies", "Movie" -> "Phim ảnh"
            books, "Sách", "Books", "Book" -> "Sách"
            sports, "Thể thao", "Sports", "Sport" -> "Thể thao"
            travel, "Du lịch", "Travel" -> "Du lịch"
            food, "Ẩm thực", "Food", "Food & Cuisine" -> "Ẩm thực"
            other, "Khác", "Other" -> "Khác"
            all, "Tất cả", "All" -> "Tất cả"
            else -> displayLabel
        }
    }

    /**
     * Trả về danh sách nhãn hiển thị của 7 danh mục theo ngôn ngữ hiện tại.
     */
    fun getDisplayCategories(context: Context): List<String> {
        return DB_CATEGORIES.map { getCategoryLabel(context, it) }
    }
}
