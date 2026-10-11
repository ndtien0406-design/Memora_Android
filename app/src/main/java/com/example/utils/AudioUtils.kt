package com.example.utils

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.Locale

/**
 * Tiện ích hỗ trợ xử lý tệp ghi âm giọng nói (Bước 10).
 */
object AudioUtils {

    /**
     * Định dạng mili-giây thành chuỗi "mm:ss" (ví dụ: 00:23).
     */
    fun formatDuration(millis: Int): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Lấy thời lượng (ms) của file âm thanh từ metadata.
     */
    fun getAudioDuration(path: String?): Int {
        if (path.isNullOrBlank()) return 0
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return 0

        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            timeStr?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
