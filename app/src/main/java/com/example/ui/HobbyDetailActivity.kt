package com.example.ui

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.R
import com.example.database.HobbyDatabaseHelper
import com.example.databinding.ActivityHobbyDetailBinding
import com.example.model.Hobby
import com.example.utils.AudioUtils
import com.example.utils.Constants
import com.example.utils.PreferenceHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình Chi tiết sở thích (Bước 5 + Bước 10: Tích hợp trình phát âm thanh).
 * - Hiển thị ảnh lớn, tên (serif), danh mục, số sao, "Thêm ngày dd/MM/yyyy", ghi chú.
 * - Trình phát âm thanh chỉ hiện khi sở thích có audioPath hợp lệ.
 * - Phát lại bằng MediaPlayer với SeekBar, cập nhật thời gian theo thời gian thực.
 * - Giải phóng MediaPlayer khi rời màn hình (onStop/onDestroy).
 * - Xóa sở thích sẽ xóa cả file ảnh và file ghi âm tương ứng.
 */
class HobbyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHobbyDetailBinding
    private lateinit var dbHelper: HobbyDatabaseHelper
    private lateinit var preferenceHelper: PreferenceHelper

    private var hobbyId: Long = -1L
    private var currentHobby: Hobby? = null

    // Quản lý phát âm thanh
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio: Boolean = false
    private val playerHandler = Handler(Looper.getMainLooper())
    private var playerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        dbHelper = HobbyDatabaseHelper(this)

        binding = ActivityHobbyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hobbyId = if (intent.hasExtra(EXTRA_KEY_HOBBY_ID)) {
            intent.getLongExtra(EXTRA_KEY_HOBBY_ID, -1L)
        } else {
            intent.getLongExtra(Constants.EXTRA_HOBBY_ID, -1L)
        }

        setupToolbar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadHobbyDetail()
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun setupToolbar() {
        binding.toolbarDetail.setNavigationOnClickListener {
            finish()
        }

        binding.btnDetailBack.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        // Nút Sửa: mở AddEditHobbyActivity chế độ Sửa
        binding.btnEditHobby.setOnClickListener {
            currentHobby?.let { hobby ->
                val intent = Intent(this, AddEditHobbyActivity::class.java).apply {
                    putExtra(AddEditHobbyActivity.EXTRA_KEY_HOBBY_ID, hobby.id)
                }
                startActivity(intent)
            }
        }

        // Nút Xóa: hiện hộp thoại xác nhận xóa vĩnh viễn
        binding.btnDeleteHobby.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // Click ảnh để xem full
        binding.ivDetailImage.setOnClickListener {
            currentHobby?.imagePath?.let { path ->
                if (path.isNotBlank() && java.io.File(path).exists()) {
                    showFullScreenImageDialog(path)
                }
            }
        }
    }

    private fun loadHobbyDetail() {
        val hobby = dbHelper.getHobbyById(hobbyId)
        if (hobby == null) {
            // Mục đã bị xóa hoặc không hợp lệ -> đóng màn hình
            finish()
            return
        }

        currentHobby = hobby
        bindHobbyData(hobby)
    }

    private fun bindHobbyData(hobby: Hobby) {
        // Ảnh lớn
        val imagePath = hobby.imagePath
        if (!imagePath.isNullOrBlank()) {
            val bitmap = HobbyAdapter.decodeSampledBitmapFromPath(imagePath, 800, 600)
            if (bitmap != null) {
                binding.ivDetailImage.setImageBitmap(bitmap)
            } else {
                binding.ivDetailImage.setImageResource(R.drawable.ic_hobby_placeholder)
            }
        } else {
            binding.ivDetailImage.setImageResource(R.drawable.ic_hobby_placeholder)
        }

        // Tên, danh mục, đánh giá
        binding.tvDetailName.text = hobby.name
        binding.tvDetailCategory.text = com.example.utils.CategoryUtils.getCategoryLabel(this, hobby.category)
        binding.ratingBarDetail.rating = hobby.rating.toFloat()
        binding.tvDetailRatingNum.text = getString(R.string.detail_rating_format, hobby.rating)

        // Ngày tạo "Thêm ngày dd/MM/yyyy"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date(hobby.createdAt))
        binding.tvDetailCreatedDate.text = getString(R.string.added_date_format, dateString)

        // Ghi chú
        binding.tvDetailNotes.text = if (hobby.note.isNullOrBlank()) {
            getString(R.string.detail_no_notes)
        } else {
            hobby.note
        }

        // Bước 10: Khởi tạo trình phát âm thanh nếu có audioPath
        setupAudioPlayer(hobby.audioPath)

        // Bước 11: Hiển thị dòng nhắc nhở nếu còn hiệu lực trong tương lai
        val reminderTime = hobby.reminderTime
        if (reminderTime > System.currentTimeMillis()) {
            binding.layoutReminderInfo.visibility = View.VISIBLE
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateObj = Date(reminderTime)
            binding.tvDetailReminder.text = getString(
                R.string.reminder_detail_format,
                timeFormat.format(dateObj),
                dateFormat.format(dateObj)
            )
        } else {
            binding.layoutReminderInfo.visibility = View.GONE
        }
    }

    /**
     * Cấu hình trình phát âm thanh với SeekBar và hiển thị thời lượng.
     */
    private fun setupAudioPlayer(audioPath: String?) {
        stopPlayback()

        if (audioPath.isNullOrBlank() || !File(audioPath).exists()) {
            binding.layoutAudioPlayer.visibility = View.GONE
            return
        }

        binding.layoutAudioPlayer.visibility = View.VISIBLE
        val totalDuration = AudioUtils.getAudioDuration(audioPath)
        binding.tvDetailAudioDuration.text = AudioUtils.formatDuration(totalDuration)
        binding.seekbarDetailAudio.max = totalDuration
        binding.seekbarDetailAudio.progress = 0
        binding.btnDetailPlayAudio.setImageResource(R.drawable.ic_play_arrow)

        binding.btnDetailPlayAudio.setOnClickListener {
            togglePlayAudio(audioPath)
        }

        binding.seekbarDetailAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    binding.tvDetailAudioDuration.text = AudioUtils.formatDuration(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayAudio(audioPath: String) {
        if (isPlayingAudio) {
            pauseAudio()
        } else {
            playAudio(audioPath)
        }
    }

    private fun playAudio(audioPath: String) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioPath)
                    prepare()
                    setOnCompletionListener {
                        isPlayingAudio = false
                        binding.btnDetailPlayAudio.setImageResource(R.drawable.ic_play_arrow)
                        binding.seekbarDetailAudio.progress = 0
                        val total = duration
                        binding.tvDetailAudioDuration.text = AudioUtils.formatDuration(total)
                        stopPlayerUpdates()
                    }
                }
                binding.seekbarDetailAudio.max = mediaPlayer?.duration ?: 0
            }

            mediaPlayer?.start()
            isPlayingAudio = true
            binding.btnDetailPlayAudio.setImageResource(R.drawable.ic_pause)
            startPlayerUpdates()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.msg_playback_error), Toast.LENGTH_SHORT).show()
            stopPlayback()
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        isPlayingAudio = false
        binding.btnDetailPlayAudio.setImageResource(R.drawable.ic_play_arrow)
        stopPlayerUpdates()
    }

    private fun stopPlayback() {
        stopPlayerUpdates()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPlayingAudio = false
        binding.btnDetailPlayAudio.setImageResource(R.drawable.ic_play_arrow)
    }

    private fun startPlayerUpdates() {
        stopPlayerUpdates()
        val runnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (isPlayingAudio && player.isPlaying) {
                        val current = player.currentPosition
                        binding.seekbarDetailAudio.progress = current
                        binding.tvDetailAudioDuration.text = AudioUtils.formatDuration(current)
                        playerHandler.postDelayed(this, 100L)
                    }
                }
            }
        }
        playerRunnable = runnable
        playerHandler.post(runnable)
    }

    private fun stopPlayerUpdates() {
        playerRunnable?.let { playerHandler.removeCallbacks(it) }
        playerRunnable = null
    }

    private fun showDeleteConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                performDelete()
            }
            .show()
    }

    private fun performDelete() {
        val hobby = currentHobby ?: return
        stopPlayback()

        // 1. Xóa file ảnh trên ổ đĩa nếu có
        hobby.imagePath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }

        // 2. Bước 10: Xóa file ghi âm trên ổ đĩa nếu có
        hobby.audioPath?.let { path ->
            try {
                val audioFile = File(path)
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (_: Exception) {}
        }

        // 3. Bước 11: Hủy báo thức nhắc nhở nếu có
        com.example.utils.ReminderScheduler.cancelReminder(this, hobby.id)

        // 4. Xóa bản ghi trong SQLite
        val deletedRows = dbHelper.deleteHobby(hobby.id)
        if (deletedRows > 0) {
            Toast.makeText(this, getString(R.string.msg_delete_success), Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, getString(R.string.error_cannot_delete_hobby), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery(imagePath: String) {
        try {
            val sourceFile = java.io.File(imagePath)
            if (!sourceFile.exists()) return

            val resolver = contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Memora_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Memora")
            }

            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }
                Toast.makeText(this, "Đã lưu ảnh vào Thư viện", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFullScreenImageDialog(imagePath: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val ivImage = dialog.findViewById<android.widget.ImageView>(R.id.iv_fullscreen_image)
        val btnClose = dialog.findViewById<android.widget.ImageButton>(R.id.btn_close_fullscreen)
        val btnSave = dialog.findViewById<android.widget.ImageButton>(R.id.btn_save_image)

        val bitmap = HobbyAdapter.decodeSampledBitmapFromPath(imagePath, 1200, 1200)
        ivImage.setImageBitmap(bitmap)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener { 
            saveImageToGallery(imagePath)
        }

        dialog.show()
    }

    companion object {
        const val EXTRA_KEY_HOBBY_ID = "hobby_id"
    }
}
