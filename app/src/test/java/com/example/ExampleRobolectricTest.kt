package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.database.HobbyDatabaseHelper
import com.example.model.Hobby
import com.example.ui.AddEditHobbyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var dbHelper: HobbyDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        dbHelper = HobbyDatabaseHelper(context)
        // Dọn dẹp sạch CSDL trước mỗi bài test
        val all = dbHelper.getAllHobbies()
        all.forEach { dbHelper.deleteHobby(it.id) }
    }

    @Test
    fun readStringFromContext() {
        val appName = context.getString(R.string.app_name)
        assertTrue(appName == "Sở Thích" || appName == "Hobbies")
    }

    @Test
    fun testLaunchMainActivityEmptyState() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        val hobbies = dbHelper.getAllHobbies()
        assertTrue(hobbies.isEmpty())
    }

    @Test
    fun testAddEditHobbyActivityAddMode() {
        val controller = Robolectric.buildActivity(AddEditHobbyActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        // Nhập thông tin sở thích mới
        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_hobby_name)
            .setText("Nhiếp ảnh đường phố")
        activity.findViewById<android.widget.RatingBar>(R.id.rating_bar_input).rating = 5f
        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_hobby_notes)
            .setText("Chụp ảnh góc phố Hà Nội")

        // Bấm Lưu
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).performClick()

        val hobbies = dbHelper.getAllHobbies()
        assertEquals(1, hobbies.size)
        assertEquals("Nhiếp ảnh đường phố", hobbies[0].name)
        assertEquals(5, hobbies[0].rating)
    }

    @Test
    fun testAddEditHobbyActivityEditMode() {
        // Tạo trước 1 sở thích
        val initialHobby = Hobby(
            name = "Chơi cờ vua",
            category = "Khác",
            rating = 4,
            note = "Chiến thuật phòng thủ Sicilian",
            createdAt = 1000000L
        )
        val id = dbHelper.insertHobby(initialHobby)

        val intent = Intent(context, AddEditHobbyActivity::class.java).apply {
            putExtra(AddEditHobbyActivity.EXTRA_KEY_HOBBY_ID, id)
        }

        val controller = Robolectric.buildActivity(AddEditHobbyActivity::class.java, intent).setup()
        val activity = controller.get()

        // Sửa tên và rating
        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_hobby_name)
            .setText("Chơi cờ vua quốc tế")
        activity.findViewById<android.widget.RatingBar>(R.id.rating_bar_input).rating = 5f

        // Bấm Lưu
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).performClick()

        val updated = dbHelper.getHobbyById(id)
        assertNotNull(updated)
        assertEquals("Chơi cờ vua quốc tế", updated?.name)
        assertEquals(5, updated?.rating)
        assertEquals(1000000L, updated?.createdAt) // Giữ nguyên createdAt
    }

    @Test
    fun testAddEditHobbyValidationEmptyName() {
        val controller = Robolectric.buildActivity(AddEditHobbyActivity::class.java).setup()
        val activity = controller.get()

        // Bấm Lưu khi chưa nhập tên
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).performClick()

        val til = activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_hobby_name)
        assertNotNull(til.error)

        val hobbies = dbHelper.getAllHobbies()
        assertTrue(hobbies.isEmpty())
    }

    @Test
    fun testAddEditHobbyClickHandlers() {
        val controller = Robolectric.buildActivity(AddEditHobbyActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        // Kiểm tra click vào nút chọn danh mục
        val cardCategory = activity.findViewById<android.view.View>(R.id.card_category)
        assertNotNull(cardCategory)
        assertTrue(cardCategory.isClickable)
        cardCategory.performClick()

        // Kiểm tra click vào nút chụp ảnh và thư viện
        val btnTakePhoto = activity.findViewById<android.view.View>(R.id.btn_take_photo)
        assertNotNull(btnTakePhoto)
        assertTrue(btnTakePhoto.isClickable)

        val btnChooseGallery = activity.findViewById<android.view.View>(R.id.btn_choose_gallery)
        assertNotNull(btnChooseGallery)
        assertTrue(btnChooseGallery.isClickable)

        // Bật switch nhắc nhở và kiểm tra nút thay đổi thông báo hàng tuần
        val switchReminder = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_reminder)
        switchReminder.isChecked = true

        val btnChangeWeekly = activity.findViewById<android.view.View>(R.id.btn_change_weekly_reminder)
        assertNotNull(btnChangeWeekly)
        assertTrue(btnChangeWeekly.isClickable)
        btnChangeWeekly.performClick()
    }

    @Test
    fun testImagePickerBottomSheetVisibility() {
        // Trường hợp chưa có ảnh: nút xóa ẩn
        val sheetWithoutImage = com.example.ui.ImagePickerBottomSheet.newInstance(hasImage = false)
        assertNotNull(sheetWithoutImage)
        assertEquals(false, sheetWithoutImage.hasImage)

        // Trường hợp đã có ảnh: nút xóa hiện
        val sheetWithImage = com.example.ui.ImagePickerBottomSheet.newInstance(hasImage = true)
        assertNotNull(sheetWithImage)
        assertEquals(true, sheetWithImage.hasImage)
    }

    @Test
    fun testHobbyPersistenceWithImage() {
        // Tạo file ảnh giả lập trong thư mục filesDir
        val imageFile = java.io.File(context.filesDir, "test_sample.jpg")
        imageFile.writeText("fake image content")

        val hobbyWithImage = Hobby(
            name = "Chụp ảnh Film",
            category = "Khác",
            rating = 5,
            note = "Máy Leica M6",
            imagePath = imageFile.absolutePath,
            createdAt = System.currentTimeMillis()
        )
        val id = dbHelper.insertHobby(hobbyWithImage)
        assertTrue(id > 0)

        val retrieved = dbHelper.getHobbyById(id)
        assertNotNull(retrieved)
        assertEquals(imageFile.absolutePath, retrieved?.imagePath)

        // Test chỉnh sửa và xóa ảnh
        val updatedWithoutImage = retrieved!!.copy(imagePath = null)
        dbHelper.updateHobby(updatedWithoutImage)

        val retrievedAfterUpdate = dbHelper.getHobbyById(id)
        assertNull(retrievedAfterUpdate?.imagePath)

        imageFile.delete()
    }

    @Test
    fun testDatabaseCRUD() {
        // 1. Thêm mới sở thích
        val hobby = Hobby(
            name = "Rừng Na Uy",
            category = "Sách",
            rating = 5,
            note = "Một cuốn tiểu thuyết kinh điển của Haruki Murakami",
            imagePath = "/images/norwegian_wood.jpg",
            audioPath = null,
            reminderTime = 1775000000000L,
            createdAt = System.currentTimeMillis()
        )
        val insertedId = dbHelper.insertHobby(hobby)
        assertTrue(insertedId > 0)

        // 2. Lấy theo ID
        val retrieved = dbHelper.getHobbyById(insertedId)
        assertNotNull(retrieved)
        assertEquals("Rừng Na Uy", retrieved?.name)
        assertEquals("Sách", retrieved?.category)
        assertEquals(5, retrieved?.rating)

        // 3. Cập nhật
        val updatedHobby = retrieved!!.copy(rating = 4, note = "Đã cập nhật ghi chú")
        val rowsAffected = dbHelper.updateHobby(updatedHobby)
        assertEquals(1, rowsAffected)

        val retrievedAfterUpdate = dbHelper.getHobbyById(insertedId)
        assertEquals(4, retrievedAfterUpdate?.rating)
        assertEquals("Đã cập nhật ghi chú", retrievedAfterUpdate?.note)

        // 4. Thống kê theo danh mục
        val counts = dbHelper.countByCategory()
        assertEquals(1, counts["Sách"])

        // 5. Xóa
        val deletedRows = dbHelper.deleteHobby(insertedId)
        assertEquals(1, deletedRows)

        val retrievedAfterDelete = dbHelper.getHobbyById(insertedId)
        assertNull(retrievedAfterDelete)
    }

    @Test
    fun testHobbyDetailActivityDisplay() {
        val hobby = Hobby(
            name = "Trà đạo Nhật Bản",
            category = "Ẩm thực",
            rating = 5,
            note = "Nghệ thuật thưởng trà thanh tịnh",
            createdAt = 1700000000000L
        )
        val id = dbHelper.insertHobby(hobby)

        val intent = Intent(context, com.example.ui.HobbyDetailActivity::class.java).apply {
            putExtra(com.example.ui.HobbyDetailActivity.EXTRA_KEY_HOBBY_ID, id)
        }

        val controller = Robolectric.buildActivity(com.example.ui.HobbyDetailActivity::class.java, intent).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val nameView = activity.findViewById<android.widget.TextView>(R.id.tv_detail_name)
        assertEquals("Trà đạo Nhật Bản", nameView.text.toString())

        val categoryView = activity.findViewById<android.widget.TextView>(R.id.tv_detail_category)
        assertEquals(com.example.utils.CategoryUtils.getCategoryLabel(activity, "Ẩm thực"), categoryView.text.toString())

        val ratingBar = activity.findViewById<android.widget.RatingBar>(R.id.rating_bar_detail)
        assertEquals(5f, ratingBar.rating)
    }

    @Test
    fun testShareBottomSheetCreation() {
        val hobby = Hobby(
            name = "Leo núi Fansipan",
            category = "Du lịch",
            rating = 4,
            note = "Hành trình 3 ngày 2 đêm tuyệt vời"
        )

        val sheet = com.example.ui.ShareBottomSheet.newInstance(hobby)
        assertNotNull(sheet)
    }

    @Test
    fun testQueryHobbiesSearchFilterSort() {
        // Chèn 3 mục kiểm thử
        dbHelper.insertHobby(Hobby(name = "Rừng Na Uy", category = "Sách", rating = 5, createdAt = 100L))
        dbHelper.insertHobby(Hobby(name = "Kind of Blue", category = "Âm nhạc", rating = 5, createdAt = 200L))
        dbHelper.insertHobby(Hobby(name = "Chạy bộ ven hồ", category = "Thể thao", rating = 4, createdAt = 300L))

        // 1. Tìm kiếm theo tên
        val searchByName = dbHelper.queryHobbies("Rừng", null, null)
        assertEquals(1, searchByName.size)
        assertEquals("Rừng Na Uy", searchByName[0].name)

        // 2. Tìm kiếm theo danh mục
        val searchByCategory = dbHelper.queryHobbies("Âm nhạc", null, null)
        assertEquals(1, searchByCategory.size)
        assertEquals("Kind of Blue", searchByCategory[0].name)

        // 3. Lọc theo danh mục cụ thể
        val filterSport = dbHelper.queryHobbies(null, "Thể thao", null)
        assertEquals(1, filterSport.size)
        assertEquals("Chạy bộ ven hồ", filterSport[0].name)

        // 4. Kết hợp tìm kiếm và lọc danh mục
        val combined = dbHelper.queryHobbies("Rừng", "Sách", null)
        assertEquals(1, combined.size)

        val combinedMismatch = dbHelper.queryHobbies("Rừng", "Thể thao", null)
        assertEquals(0, combinedMismatch.size)

        // 5. Sắp xếp theo tên A-Z
        val sortName = dbHelper.queryHobbies(null, null, "name")
        assertEquals("Chạy bộ ven hồ", sortName[0].name)

        // 6. Sắp xếp theo ngày mới nhất (mặc định)
        val sortDate = dbHelper.queryHobbies(null, null, "date")
        assertEquals("Chạy bộ ven hồ", sortDate[0].name)
    }

    @Test
    fun testStatsActivityEmptyState() {
        val controller = Robolectric.buildActivity(com.example.ui.StatsActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val emptyLayout = activity.findViewById<android.view.View>(R.id.layout_empty_stats)
        val scrollContent = activity.findViewById<android.view.View>(R.id.scroll_stats_content)

        assertEquals(android.view.View.VISIBLE, emptyLayout.visibility)
        assertEquals(android.view.View.GONE, scrollContent.visibility)
    }

    @Test
    fun testStatsActivityWithData() {
        dbHelper.insertHobby(Hobby(name = "Đọc sách trinh thám", category = "Sách", rating = 5))
        dbHelper.insertHobby(Hobby(name = "Nghe nhạc Trịnh", category = "Âm nhạc", rating = 5))

        val controller = Robolectric.buildActivity(com.example.ui.StatsActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val emptyLayout = activity.findViewById<android.view.View>(R.id.layout_empty_stats)
        val scrollContent = activity.findViewById<android.view.View>(R.id.scroll_stats_content)

        assertEquals(android.view.View.GONE, emptyLayout.visibility)
        assertEquals(android.view.View.VISIBLE, scrollContent.visibility)

        val breakdownList = activity.findViewById<android.widget.LinearLayout>(R.id.layout_category_breakdown)
        assertEquals(7, breakdownList.childCount) // Đủ 7 danh mục
    }

    @Test
    fun testSplashActivityLaunch() {
        val controller = Robolectric.buildActivity(com.example.ui.SplashActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val titleView = activity.findViewById<android.widget.TextView>(R.id.tv_splash_title)
        assertEquals(activity.getString(R.string.app_name), titleView.text.toString())
    }

    @Test
    fun testSettingsActivityThemeAndSort() {
        val prefHelper = com.example.utils.PreferenceHelper(context)

        val controller = Robolectric.buildActivity(com.example.ui.SettingsActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        // 1. Kiểm tra chọn giao diện Tối
        activity.findViewById<android.view.View>(R.id.card_theme_dark).performClick()
        assertEquals("dark", prefHelper.themeMode)

        // 2. Kiểm tra chọn giao diện Sáng
        activity.findViewById<android.view.View>(R.id.card_theme_light).performClick()
        assertEquals("light", prefHelper.themeMode)

        // 3. Kiểm tra chọn sắp xếp theo tên
        activity.findViewById<android.view.View>(R.id.row_sort_name).performClick()
        assertEquals(com.example.utils.Constants.SORT_BY_NAME, prefHelper.sortMode)

        // 4. Kiểm tra chọn sắp xếp theo đánh giá
        activity.findViewById<android.view.View>(R.id.row_sort_rating).performClick()
        assertEquals(com.example.utils.Constants.SORT_BY_RATING, prefHelper.sortMode)
    }

    @Test
    fun testAudioUtilsFormatDuration() {
        assertEquals("00:00", com.example.utils.AudioUtils.formatDuration(0))
        assertEquals("00:07", com.example.utils.AudioUtils.formatDuration(7000))
        assertEquals("00:23", com.example.utils.AudioUtils.formatDuration(23000))
        assertEquals("01:15", com.example.utils.AudioUtils.formatDuration(75000))
    }

    @Test
    fun testAddEditHobbyAudioIdleState() {
        val controller = Robolectric.buildActivity(com.example.ui.AddEditHobbyActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val idleLayout = activity.findViewById<android.view.View>(R.id.layout_audio_idle)
        val recordingLayout = activity.findViewById<android.view.View>(R.id.layout_audio_recording)
        val recordedLayout = activity.findViewById<android.view.View>(R.id.layout_audio_recorded)

        assertEquals(android.view.View.VISIBLE, idleLayout.visibility)
        assertEquals(android.view.View.GONE, recordingLayout.visibility)
        assertEquals(android.view.View.GONE, recordedLayout.visibility)
    }

    @Test
    fun testHobbyDetailAudioPlayerVisibilityAndDelete() {
        val dummyAudioFile = java.io.File(context.filesDir, "test_recording.mp4")
        dummyAudioFile.writeText("fake audio content for test")

        val hobby = Hobby(
            name = "Thử ghi âm",
            category = "Âm nhạc",
            rating = 5,
            note = "Bản ghi âm guitar",
            audioPath = dummyAudioFile.absolutePath,
            createdAt = System.currentTimeMillis()
        )
        val insertedId = dbHelper.insertHobby(hobby)
        assertTrue(insertedId > 0)

        val intent = Intent(context, com.example.ui.HobbyDetailActivity::class.java).apply {
            putExtra(com.example.ui.HobbyDetailActivity.EXTRA_KEY_HOBBY_ID, insertedId)
        }

        val controller = Robolectric.buildActivity(com.example.ui.HobbyDetailActivity::class.java, intent).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val audioPlayerLayout = activity.findViewById<android.view.View>(R.id.layout_audio_player)
        assertEquals(android.view.View.VISIBLE, audioPlayerLayout.visibility)

        // Kiểm tra xóa sở thích và xóa file âm thanh
        val hobbyToDelete = dbHelper.getHobbyById(insertedId)
        assertNotNull(hobbyToDelete)
        hobbyToDelete?.audioPath?.let { path ->
            val file = java.io.File(path)
            if (file.exists()) file.delete()
        }
        val deleted = dbHelper.deleteHobby(insertedId)
        assertTrue(deleted > 0)

        assertNull(dbHelper.getHobbyById(insertedId))
        assertEquals(false, dummyAudioFile.exists())
    }

    @Test
    fun testNotificationChannelCreated() {
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = notificationManager?.getNotificationChannel(com.example.SoThichApplication.CHANNEL_REMINDERS)
        assertNotNull(channel)
        assertEquals(com.example.SoThichApplication.CHANNEL_REMINDERS, channel?.id)
    }

    @Test
    fun testReminderSchedulerAndReceiver() {
        val hobby = Hobby(
            name = "Chơi cờ vua",
            category = "Khác",
            rating = 5,
            note = "Ván cờ chiều",
            reminderTime = System.currentTimeMillis() + 60000L,
            createdAt = System.currentTimeMillis()
        )
        val insertedId = dbHelper.insertHobby(hobby)
        assertTrue(insertedId > 0)

        // Đặt nhắc nhở
        com.example.utils.ReminderScheduler.scheduleReminder(
            context,
            insertedId,
            hobby.name,
            hobby.reminderTime
        )

        // Kích hoạt ReminderReceiver
        val receiver = com.example.receiver.ReminderReceiver()
        val intent = Intent(context, com.example.receiver.ReminderReceiver::class.java).apply {
            putExtra(com.example.utils.Constants.EXTRA_HOBBY_ID, insertedId)
            putExtra(com.example.utils.ReminderScheduler.EXTRA_HOBBY_NAME, hobby.name)
        }
        receiver.onReceive(context, intent)

        // Kiểm tra thông báo được đăng
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        val notifications = notificationManager.activeNotifications
        assertTrue(notifications.isNotEmpty())

        // Hủy nhắc nhở
        com.example.utils.ReminderScheduler.cancelReminder(context, insertedId)
    }

    @Test
    fun testHobbyDetailReminderVisibility() {
        val futureTime = System.currentTimeMillis() + 3600000L
        val hobbyWithReminder = Hobby(
            name = "Xem phim cổ điển",
            category = "Phim ảnh",
            rating = 5,
            reminderTime = futureTime,
            createdAt = System.currentTimeMillis()
        )
        val insertedId = dbHelper.insertHobby(hobbyWithReminder)
        assertTrue(insertedId > 0)

        val intent = Intent(context, com.example.ui.HobbyDetailActivity::class.java).apply {
            putExtra(com.example.ui.HobbyDetailActivity.EXTRA_KEY_HOBBY_ID, insertedId)
        }

        val controller = Robolectric.buildActivity(com.example.ui.HobbyDetailActivity::class.java, intent).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val reminderLayout = activity.findViewById<android.view.View>(R.id.layout_reminder_info)
        assertEquals(android.view.View.VISIBLE, reminderLayout.visibility)

        val reminderTextView = activity.findViewById<android.widget.TextView>(R.id.tv_detail_reminder)
        val text = reminderTextView.text.toString()
        assertTrue(text.contains("Nhắc lúc") || text.contains("Reminder at"))
    }

    @Test
    fun testLanguageSettingInPreferencesAndSettingsActivity() {
        val prefHelper = com.example.utils.PreferenceHelper(context)

        // 1. Kiểm tra giá trị mặc định là "vi"
        assertEquals("vi", prefHelper.language)

        // 2. Kiểm tra lưu vào SharedPreferences với key "language"
        prefHelper.language = "en"
        assertEquals("en", prefHelper.language)
        val sharedPrefs = context.getSharedPreferences(com.example.utils.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        assertEquals("en", sharedPrefs.getString("language", null))

        // 3. Khởi tạo SettingsActivity và kiểm tra tương tác chuyển ngôn ngữ
        val controller = Robolectric.buildActivity(com.example.ui.SettingsActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val rbVi = activity.findViewById<android.widget.RadioButton>(R.id.rb_lang_vi)
        val rbEn = activity.findViewById<android.widget.RadioButton>(R.id.rb_lang_en)

        // Chọn Tiếng Việt
        activity.findViewById<android.view.View>(R.id.row_lang_vi).performClick()
        assertEquals("vi", prefHelper.language)
        assertTrue(rbVi.isChecked)
        assertEquals(false, rbEn.isChecked)

        // Chọn English
        activity.findViewById<android.view.View>(R.id.row_lang_en).performClick()
        assertEquals("en", prefHelper.language)
        assertTrue(rbEn.isChecked)
        assertEquals(false, rbVi.isChecked)
    }

    @Test
    fun testCategoryUtilsMappingBidirectional() {
        // 1. Kiểm tra ánh xạ từ SQLite (tiếng Việt) sang chuỗi hiển thị
        assertEquals(context.getString(R.string.category_music), com.example.utils.CategoryUtils.getCategoryLabel(context, "Âm nhạc"))
        assertEquals(context.getString(R.string.category_movie), com.example.utils.CategoryUtils.getCategoryLabel(context, "Phim ảnh"))
        assertEquals(context.getString(R.string.category_book), com.example.utils.CategoryUtils.getCategoryLabel(context, "Sách"))
        assertEquals(context.getString(R.string.category_sport), com.example.utils.CategoryUtils.getCategoryLabel(context, "Thể thao"))
        assertEquals(context.getString(R.string.category_travel), com.example.utils.CategoryUtils.getCategoryLabel(context, "Du lịch"))
        assertEquals(context.getString(R.string.category_food), com.example.utils.CategoryUtils.getCategoryLabel(context, "Ẩm thực"))
        assertEquals(context.getString(R.string.category_other), com.example.utils.CategoryUtils.getCategoryLabel(context, "Khác"))

        // 2. Kiểm tra ánh xạ ngược lại từ chuỗi hiển thị tiếng Anh/tiếng Việt sang giá trị SQLite chuẩn (tiếng Việt)
        assertEquals("Âm nhạc", com.example.utils.CategoryUtils.toDbCategory(context, "Music"))
        assertEquals("Phim ảnh", com.example.utils.CategoryUtils.toDbCategory(context, "Movies"))
        assertEquals("Sách", com.example.utils.CategoryUtils.toDbCategory(context, "Books"))
        assertEquals("Thể thao", com.example.utils.CategoryUtils.toDbCategory(context, "Sports"))
        assertEquals("Du lịch", com.example.utils.CategoryUtils.toDbCategory(context, "Travel"))
        assertEquals("Ẩm thực", com.example.utils.CategoryUtils.toDbCategory(context, "Food & Cuisine"))
        assertEquals("Khác", com.example.utils.CategoryUtils.toDbCategory(context, "Other"))

        // 3. Với chuỗi tiếng Việt đã chuẩn thì giữ nguyên
        assertEquals("Âm nhạc", com.example.utils.CategoryUtils.toDbCategory(context, "Âm nhạc"))
        assertEquals("Sách", com.example.utils.CategoryUtils.toDbCategory(context, "Sách"))
    }
}
