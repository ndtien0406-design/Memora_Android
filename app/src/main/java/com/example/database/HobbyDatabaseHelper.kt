package com.example.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.Hobby

/**
 * Quản lý cơ sở dữ liệu SQLite cho bảng `hobbies`.
 * Tuân thủ yêu cầu SQLiteOpenHelper (không dùng Room).
 */
class HobbyDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "hobbies.db"
        const val DATABASE_VERSION = 1

        const val TABLE_HOBBIES = "hobbies"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_CATEGORY = "category"
        const val COLUMN_RATING = "rating"
        const val COLUMN_NOTE = "note"
        const val COLUMN_IMAGE_PATH = "image_path"
        const val COLUMN_AUDIO_PATH = "audio_path"
        const val COLUMN_REMINDER_TIME = "reminder_time"
        const val COLUMN_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_HOBBIES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_CATEGORY TEXT NOT NULL,
                $COLUMN_RATING INTEGER NOT NULL DEFAULT 5,
                $COLUMN_NOTE TEXT,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_AUDIO_PATH TEXT,
                $COLUMN_REMINDER_TIME INTEGER NOT NULL DEFAULT 0,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Tương thích khi nâng cấp CSDL trong tương lai
        if (oldVersion < 2) {
            // Ví dụ: db.execSQL("ALTER TABLE $TABLE_HOBBIES ADD COLUMN ...")
        }
    }

    /**
     * Thêm mới một mục sở thích vào cơ sở dữ liệu.
     * @return id của bản ghi vừa thêm, hoặc -1 nếu thất bại
     */
    fun insertHobby(hobby: Hobby): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, hobby.name)
            put(COLUMN_CATEGORY, hobby.category)
            put(COLUMN_RATING, hobby.rating)
            put(COLUMN_NOTE, hobby.note)
            put(COLUMN_IMAGE_PATH, hobby.imagePath)
            put(COLUMN_AUDIO_PATH, hobby.audioPath)
            put(COLUMN_REMINDER_TIME, hobby.reminderTime)
            put(COLUMN_CREATED_AT, hobby.createdAt)
        }
        return db.insert(TABLE_HOBBIES, null, values)
    }

    /**
     * Cập nhật thông tin mục sở thích.
     * @return Số dòng bị ảnh hưởng (1 nếu thành công)
     */
    fun updateHobby(hobby: Hobby): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, hobby.name)
            put(COLUMN_CATEGORY, hobby.category)
            put(COLUMN_RATING, hobby.rating)
            put(COLUMN_NOTE, hobby.note)
            put(COLUMN_IMAGE_PATH, hobby.imagePath)
            put(COLUMN_AUDIO_PATH, hobby.audioPath)
            put(COLUMN_REMINDER_TIME, hobby.reminderTime)
            put(COLUMN_CREATED_AT, hobby.createdAt)
        }
        return db.update(
            TABLE_HOBBIES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(hobby.id.toString())
        )
    }

    /**
     * Xóa sở thích theo ID.
     * @return Số dòng bị xóa (1 nếu thành công)
     */
    fun deleteHobby(id: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_HOBBIES,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Lấy toàn bộ danh sách sở thích, sắp xếp mặc định theo ngày tạo mới nhất.
     */
    fun getAllHobbies(): List<Hobby> {
        val hobbies = mutableListOf<Hobby>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HOBBIES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                hobbies.add(cursorToHobby(it))
            }
        }
        return hobbies
    }

    /**
     * Lấy chi tiết một mục sở thích theo ID.
     */
    fun getHobbyById(id: Long): Hobby? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HOBBIES,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                cursorToHobby(it)
            } else {
                null
            }
        }
    }

    /**
     * Lấy các sở thích có nhắc nhở còn hiệu lực trong tương lai (dành cho BootReceiver - Bước 11).
     */
    fun getFutureReminders(currentTime: Long): List<Hobby> {
        val hobbies = mutableListOf<Hobby>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HOBBIES,
            null,
            "$COLUMN_REMINDER_TIME > ?",
            arrayOf(currentTime.toString()),
            null,
            null,
            "$COLUMN_REMINDER_TIME ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                hobbies.add(cursorToHobby(it))
            }
        }
        return hobbies
    }

    /**
     * Thống kê số lượng sở thích theo từng danh mục.
     * @return Bản đồ (Category -> Số lượng sở thích)
     */
    fun countByCategory(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val db = readableDatabase
        val query = "SELECT $COLUMN_CATEGORY, COUNT(*) as count FROM $TABLE_HOBBIES GROUP BY $COLUMN_CATEGORY"
        db.rawQuery(query, null).use { cursor ->
            val catCol = cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)
            val countCol = cursor.getColumnIndexOrThrow("count")
            while (cursor.moveToNext()) {
                val category = cursor.getString(catCol)
                val count = cursor.getInt(countCol)
                result[category] = count
            }
        }
        return result
    }

    /**
     * Tìm kiếm và lọc danh sách sở thích kết hợp sắp xếp theo yêu cầu Bước 6.
     * @param keyword Từ khóa tìm kiếm theo tên HOẶC danh mục (LIKE, không phân biệt hoa thường).
     * @param category Lọc theo danh mục cụ thể (bỏ qua nếu null hoặc "Tất cả").
     * @param sortMode Kiểu sắp xếp: "name" (A-Z), "rating" (cao -> thấp), "date" (mới nhất).
     */
    fun queryHobbies(keyword: String?, category: String?, sortMode: String?): List<Hobby> {
        val db = readableDatabase
        val selectionList = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        // 1. Tìm kiếm theo tên HOẶC danh mục
        if (!keyword.isNullOrBlank()) {
            val kw = "%${keyword.trim()}%"
            selectionList.add("($COLUMN_NAME LIKE ? OR $COLUMN_CATEGORY LIKE ?)")
            selectionArgsList.add(kw)
            selectionArgsList.add(kw)
        }

        // 2. Lọc theo danh mục (bỏ qua nếu rỗng hoặc "Tất cả")
        if (!category.isNullOrBlank() && category != "Tất cả") {
            selectionList.add("$COLUMN_CATEGORY = ?")
            selectionArgsList.add(category)
        }

        val selection = if (selectionList.isEmpty()) null else selectionList.joinToString(" AND ")
        val selectionArgs = if (selectionArgsList.isEmpty()) null else selectionArgsList.toTypedArray()

        // 3. Sắp xếp theo sortMode
        val orderBy = when (sortMode) {
            "name" -> "$COLUMN_NAME COLLATE NOCASE ASC"
            "rating" -> "$COLUMN_RATING DESC, $COLUMN_CREATED_AT DESC"
            "date" -> "$COLUMN_CREATED_AT DESC"
            else -> "$COLUMN_CREATED_AT DESC"
        }

        val hobbies = mutableListOf<Hobby>()
        val cursor = db.query(
            TABLE_HOBBIES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        )

        cursor.use {
            while (it.moveToNext()) {
                hobbies.add(cursorToHobby(it))
            }
        }
        return hobbies
    }

    private fun cursorToHobby(cursor: Cursor): Hobby {
        val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(COLUMN_NAME)
        val categoryIndex = cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)
        val ratingIndex = cursor.getColumnIndexOrThrow(COLUMN_RATING)
        val noteIndex = cursor.getColumnIndexOrThrow(COLUMN_NOTE)
        val imagePathIndex = cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)
        val audioPathIndex = cursor.getColumnIndexOrThrow(COLUMN_AUDIO_PATH)
        val reminderTimeIndex = cursor.getColumnIndexOrThrow(COLUMN_REMINDER_TIME)
        val createdAtIndex = cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)

        return Hobby(
            id = cursor.getLong(idIndex),
            name = cursor.getString(nameIndex),
            category = cursor.getString(categoryIndex),
            rating = cursor.getInt(ratingIndex),
            note = if (cursor.isNull(noteIndex)) null else cursor.getString(noteIndex),
            imagePath = if (cursor.isNull(imagePathIndex)) null else cursor.getString(imagePathIndex),
            audioPath = if (cursor.isNull(audioPathIndex)) null else cursor.getString(audioPathIndex),
            reminderTime = cursor.getLong(reminderTimeIndex),
            createdAt = cursor.getLong(createdAtIndex)
        )
    }
}
