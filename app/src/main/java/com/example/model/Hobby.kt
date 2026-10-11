package com.example.model

/**
 * Đại diện cho một mục sở thích cá nhân.
 *
 * @property id Khóa chính tự tăng trong SQLite (0 khi tạo mới)
 * @property name Tên sở thích (bắt buộc)
 * @property category Một trong 7 danh mục cố định
 * @property rating Mức độ yêu thích (1 - 5 sao)
 * @property note Ghi chú / cảm nhận cá nhân
 * @property imagePath Đường dẫn tệp ảnh minh họa lưu ở bộ nhớ thiết bị
 * @property audioPath Đường dẫn tệp âm thanh ghi chú
 * @property reminderTime Thời điểm hẹn giờ nhắc (ms), 0 = không đặt nhắc
 * @property createdAt Thời điểm tạo mục sở thích (timestamp ms)
 */
data class Hobby(
    val id: Long = 0L,
    val name: String,
    val category: String,
    val rating: Int = 5,
    val note: String? = null,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val reminderTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
