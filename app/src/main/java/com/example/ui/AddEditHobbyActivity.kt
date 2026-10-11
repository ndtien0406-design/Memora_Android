package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.example.R
import com.example.database.HobbyDatabaseHelper
import com.example.databinding.ActivityAddEditHobbyBinding
import com.example.model.Hobby
import com.example.utils.AudioUtils
import com.example.utils.CategoryUtils
import com.example.utils.Constants
import com.example.utils.PreferenceHelper
import com.example.utils.ReminderScheduler
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Màn hình Thêm hoặc Sửa sở thích (Bước 4 + Bước 10 + Bước 11).
 * Bước 11: Nhắc nhở bằng Notification + AlarmManager:
 * - Switch Bật/Tắt nhắc nhở. Khi bật hiện 2 ô: Ngày (MaterialDatePicker) và Giờ (MaterialTimePicker 24h).
 * - Xin quyền POST_NOTIFICATIONS trên Android 13+ khi bật nhắc nhở lần đầu.
 * - Kiểm tra thời điểm phải ở tương lai, nếu không thì báo lỗi.
 * - Đặt báo thức với AlarmManager khi lưu, hủy báo khi tắt nhắc hoặc xóa.
 */
class AddEditHobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditHobbyBinding
    private lateinit var dbHelper: HobbyDatabaseHelper
    private lateinit var preferenceHelper: PreferenceHelper

    private var hobbyId: Long = -1L
    private var isEditMode: Boolean = false
    private var existingHobby: Hobby? = null

    // Dữ liệu form
    private var currentCategory: String = ""
    private var currentRating: Int = 5
    private var currentImagePath: String? = null
    private var initialImagePath: String? = null
    private var tempCameraFilePath: String? = null

    // Dữ liệu ghi âm giọng nói (Bước 10)
    private var currentAudioPath: String? = null
    private var initialAudioPath: String? = null
    private var isAudioPermissionDenied: Boolean = false

    // MediaRecorder
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording: Boolean = false
    private var recordingFile: File? = null
    private var recordingSeconds: Int = 0
    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingRunnable: Runnable? = null
    private var blinkAnimation: AlphaAnimation? = null

    // MediaPlayer
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio: Boolean = false
    private val playerHandler = Handler(Looper.getMainLooper())
    private var playerRunnable: Runnable? = null

    // Dữ liệu nhắc nhở (Bước 11)
    private var isReminderEnabled: Boolean = false
    private var reminderCalendar: Calendar = Calendar.getInstance()
    private var selectedReminderTimeMs: Long = 0L
    private var initialReminderTimeMs: Long = 0L

    // Launcher xin quyền Camera
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dispatchTakePicture()
        } else {
            Toast.makeText(
                this,
                getString(R.string.msg_camera_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Launcher xin quyền Record Audio (Bước 10)
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isAudioPermissionDenied = false
            startRecording()
        } else {
            isAudioPermissionDenied = true
            Toast.makeText(
                this,
                getString(R.string.msg_record_permission_denied),
                Toast.LENGTH_LONG
            ).show()
            updateAudioStateUI()
        }
    }

    // Launcher xin quyền POST_NOTIFICATIONS (Bước 11 - Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                getString(R.string.msg_notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Launcher chụp ảnh bằng Camera
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            processCameraCapturedPhoto()
        }
    }

    // Launcher chọn ảnh từ Thư viện
    private val pickGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { processGalleryPhoto(it) }
    }

    // Launcher mở màn hình Căn chỉnh khung ảnh (Crop)
    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        tempCameraFilePath?.let { tempPath ->
            try { File(tempPath).delete() } catch (_: Exception) {}
            tempCameraFilePath = null
        }

        if (result.resultCode == RESULT_OK) {
            val croppedPath = result.data?.getStringExtra(CropImageActivity.EXTRA_CROPPED_PATH)
            if (!croppedPath.isNullOrBlank()) {
                setCurrentImage(croppedPath)
                Toast.makeText(this, getString(R.string.msg_photo_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        dbHelper = HobbyDatabaseHelper(this)

        binding = ActivityAddEditHobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCategoryDropdown()
        setupRatingBar()
        setupImagePicker()
        setupAudioRecorder()
        setupReminderPicker()
        setupListeners()

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        } else {
            initFromIntent()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        stopPlayback()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleCancel()
        }
    }

    private fun setupCategoryDropdown() {
        val categories = resources.getStringArray(R.array.category_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)

        if (currentCategory.isEmpty() && categories.isNotEmpty()) {
            currentCategory = CategoryUtils.DB_CATEGORIES[0]
        }
        val initialLabel = CategoryUtils.getCategoryLabel(this, currentCategory)
        binding.actvCategory.setText(initialLabel, false)
        updateCategoryIcon(currentCategory)

        binding.actvCategory.setOnItemClickListener { _, _, position, _ ->
            val selectedDisplay = categories[position]
            currentCategory = CategoryUtils.toDbCategory(this, selectedDisplay)
            updateCategoryIcon(currentCategory)
        }

        val openCategoryPicker = {
            showCategorySelectionDialog()
        }
        binding.cardCategory.setOnClickListener { openCategoryPicker() }
        binding.layoutCategoryContainer.setOnClickListener { openCategoryPicker() }
        binding.actvCategory.setOnClickListener { openCategoryPicker() }
        binding.ivCategoryDropdownArrow.setOnClickListener { openCategoryPicker() }
    }

    private fun showCategorySelectionDialog() {
        val categories = resources.getStringArray(R.array.category_array)
        val currentIndex = categories.indexOfFirst {
            CategoryUtils.toDbCategory(this, it) == currentCategory
        }.let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_category_storage)
            .setSingleChoiceItems(categories, currentIndex) { dialog, which ->
                val selectedDisplay = categories[which]
                currentCategory = CategoryUtils.toDbCategory(this, selectedDisplay)
                binding.actvCategory.setText(selectedDisplay, false)
                updateCategoryIcon(currentCategory)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateCategoryIcon(category: String) {
        val iconRes = when (CategoryUtils.toDbCategory(this, category)) {
            "Âm nhạc" -> R.drawable.ic_music
            "Phim ảnh" -> R.drawable.ic_play_arrow
            "Sách" -> R.drawable.ic_book
            "Du lịch" -> R.drawable.ic_bookmark
            else -> R.drawable.ic_category_shapes
        }
        binding.ivCategoryIcon.setImageResource(iconRes)
    }

    private fun setupRatingBar() {
        binding.ratingBarInput.setOnRatingBarChangeListener { _, rating, _ ->
            val validatedRating = if (rating < 1f) 1 else rating.toInt()
            if (rating < 1f) {
                binding.ratingBarInput.rating = 1f
            }
            currentRating = validatedRating
            binding.tvRatingValue.text = getString(R.string.rating_format, currentRating)
        }
        binding.tvRatingValue.text = getString(R.string.rating_format, currentRating)
    }

    private fun setupImagePicker() {
        binding.cardImagePicker.setOnClickListener {
            showImagePickerBottomSheet()
        }
        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndCapture()
        }
        binding.btnChooseGallery.setOnClickListener {
            pickGalleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnChangePhoto.setOnClickListener {
            showImagePickerBottomSheet()
        }
        binding.layoutSelectedImage.setOnClickListener {
            showImagePickerBottomSheet()
        }
    }

    private fun showImagePickerBottomSheet() {
        val bottomSheet = ImagePickerBottomSheet.newInstance(hasImage = !currentImagePath.isNullOrBlank())
        bottomSheet.onTakePhotoListener = {
            checkCameraPermissionAndCapture()
        }
        bottomSheet.onPickGalleryListener = {
            pickGalleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        bottomSheet.onAdjustFrameListener = {
            currentImagePath?.let { path ->
                if (File(path).exists()) {
                    cropImageLauncher.launch(CropImageActivity.createIntent(this, path))
                }
            }
        }
        bottomSheet.onRemovePhotoListener = {
            setCurrentImage(null)
            Toast.makeText(this, getString(R.string.msg_photo_removed), Toast.LENGTH_SHORT).show()
        }
        bottomSheet.show(supportFragmentManager, ImagePickerBottomSheet.TAG)
    }

    private fun checkCameraPermissionAndCapture() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                dispatchTakePicture()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_title_camera_permission)
                    .setMessage(R.string.dialog_msg_camera_permission)
                    .setPositiveButton(R.string.btn_permission_allow) { _, _ ->
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton(R.string.btn_permission_later, null)
                    .show()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun dispatchTakePicture() {
        try {
            val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val tempDir = File(baseDir, "temp_captures").apply { mkdirs() }
            val tempFile = File.createTempFile("capture_${System.currentTimeMillis()}_", ".jpg", tempDir)
            tempCameraFilePath = tempFile.absolutePath

            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                tempFile
            )
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_cannot_open_camera), Toast.LENGTH_SHORT).show()
        }
    }

    private fun processCameraCapturedPhoto() {
        val tempPath = tempCameraFilePath ?: return
        val tempFile = File(tempPath)
        if (tempFile.exists() && tempFile.length() > 0) {
            cropImageLauncher.launch(CropImageActivity.createIntent(this, tempPath))
        }
    }

    private fun processGalleryPhoto(uri: Uri) {
        try {
            cropImageLauncher.launch(CropImageActivity.createIntent(this, uri))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_cannot_read_gallery), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveInputStreamToPermanentStorage(inputStream: InputStream): String? {
        return try {
            val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val hobbyImagesDir = File(baseDir, "hobby_images").apply { mkdirs() }
            val fileName = "hobby_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(hobbyImagesDir, fileName)

            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun saveImageToPermanentStorage(file: File): String? {
        return try {
            file.inputStream().use { input ->
                saveInputStreamToPermanentStorage(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun setCurrentImage(path: String?) {
        currentImagePath = path
        if (!path.isNullOrBlank()) {
            val bitmap = HobbyAdapter.decodeSampledBitmapFromPath(path, 600, 450)
            if (bitmap != null) {
                binding.ivSelectedImage.setImageBitmap(bitmap)
                binding.layoutSelectedImage.visibility = View.VISIBLE
                binding.layoutEmptyImage.visibility = View.GONE
            } else {
                binding.layoutSelectedImage.visibility = View.GONE
                binding.layoutEmptyImage.visibility = View.VISIBLE
            }
        } else {
            binding.ivSelectedImage.setImageDrawable(null)
            binding.layoutSelectedImage.visibility = View.GONE
            binding.layoutEmptyImage.visibility = View.VISIBLE
        }
    }

    // =========================================================================
    // BƯỚC 10: XỬ LÝ GHI ÂM VÀ PHÁT LẠI GIỌNG NÓI
    // =========================================================================

    private fun setupAudioRecorder() {
        binding.layoutAudioIdle.setOnClickListener {
            checkAudioPermissionAndRecord()
        }

        binding.btnStopRecording.setOnClickListener {
            stopRecording()
        }

        binding.btnPlayAudio.setOnClickListener {
            currentAudioPath?.let { path ->
                if (isPlayingAudio) {
                    pauseAudio()
                } else {
                    playAudio(path)
                }
            }
        }

        binding.btnDeleteAudio.setOnClickListener {
            deleteRecordedAudio()
        }

        binding.seekbarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    binding.tvAudioDuration.text = AudioUtils.formatDuration(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateAudioStateUI()
    }

    private fun checkAudioPermissionAndRecord() {
        if (isAudioPermissionDenied) {
            Toast.makeText(this, getString(R.string.msg_record_permission_denied), Toast.LENGTH_SHORT).show()
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.label_voice_note)
                    .setMessage(R.string.msg_record_permission_rationale)
                    .setPositiveButton(R.string.btn_permission_allow) { _, _ ->
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .setNegativeButton(R.string.btn_permission_later, null)
                    .show()
            }
            else -> {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        try {
            stopPlayback()

            val musicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            val audioDir = File(musicDir, "hobby_audio").apply { mkdirs() }
            val audioFile = File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4")
            recordingFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(audioFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            recordingSeconds = 0

            binding.layoutAudioIdle.visibility = View.GONE
            binding.layoutAudioRecording.visibility = View.VISIBLE
            binding.layoutAudioRecorded.visibility = View.GONE
            binding.tvRecordingStatus.text = getString(R.string.recording_format, AudioUtils.formatDuration(0))

            val blink = AlphaAnimation(1f, 0.2f).apply {
                duration = 500
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            blinkAnimation = blink
            binding.viewRecordingDot.startAnimation(blink)

            startRecordingTimer()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.msg_recording_error), Toast.LENGTH_SHORT).show()
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
            recordingFile?.delete()
            recordingFile = null
            updateAudioStateUI()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        stopRecordingTimer()
        binding.viewRecordingDot.clearAnimation()
        blinkAnimation = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        recordingFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                if (!currentAudioPath.isNullOrBlank() && currentAudioPath != initialAudioPath) {
                    try {
                        val prev = File(currentAudioPath!!)
                        if (prev.exists()) prev.delete()
                    } catch (_: Exception) {}
                }
                currentAudioPath = file.absolutePath
            } else {
                file.delete()
            }
        }
        recordingFile = null

        updateAudioStateUI()
    }

    private fun startRecordingTimer() {
        stopRecordingTimer()
        val runnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordingSeconds++
                    val timeStr = AudioUtils.formatDuration(recordingSeconds * 1000)
                    binding.tvRecordingStatus.text = getString(R.string.recording_format, timeStr)
                    recordingHandler.postDelayed(this, 1000L)
                }
            }
        }
        recordingRunnable = runnable
        recordingHandler.postDelayed(runnable, 1000L)
    }

    private fun stopRecordingTimer() {
        recordingRunnable?.let { recordingHandler.removeCallbacks(it) }
        recordingRunnable = null
    }

    private fun updateAudioStateUI() {
        if (isRecording) {
            binding.layoutAudioIdle.visibility = View.GONE
            binding.layoutAudioRecording.visibility = View.VISIBLE
            binding.layoutAudioRecorded.visibility = View.GONE
            return
        }

        val path = currentAudioPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            binding.layoutAudioIdle.visibility = View.GONE
            binding.layoutAudioRecording.visibility = View.GONE
            binding.layoutAudioRecorded.visibility = View.VISIBLE

            val duration = AudioUtils.getAudioDuration(path)
            binding.tvAudioDuration.text = AudioUtils.formatDuration(duration)
            binding.seekbarAudio.max = duration
            binding.seekbarAudio.progress = 0
            binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
        } else {
            binding.layoutAudioIdle.visibility = View.VISIBLE
            binding.layoutAudioRecording.visibility = View.GONE
            binding.layoutAudioRecorded.visibility = View.GONE

            if (isAudioPermissionDenied) {
                binding.layoutAudioIdle.alpha = 0.5f
                binding.tvTapToRecord.text = getString(R.string.msg_record_permission_denied)
            } else {
                binding.layoutAudioIdle.alpha = 1.0f
                binding.tvTapToRecord.text = getString(R.string.btn_tap_to_record)
            }
        }
    }

    private fun playAudio(path: String) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener {
                        isPlayingAudio = false
                        binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
                        binding.seekbarAudio.progress = 0
                        val total = duration
                        binding.tvAudioDuration.text = AudioUtils.formatDuration(total)
                        stopPlayerUpdates()
                    }
                }
                binding.seekbarAudio.max = mediaPlayer?.duration ?: 0
            }

            mediaPlayer?.start()
            isPlayingAudio = true
            binding.btnPlayAudio.setImageResource(R.drawable.ic_pause)
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
        binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
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
        binding.btnPlayAudio.setImageResource(R.drawable.ic_play_arrow)
    }

    private fun startPlayerUpdates() {
        stopPlayerUpdates()
        val runnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (isPlayingAudio && player.isPlaying) {
                        val current = player.currentPosition
                        binding.seekbarAudio.progress = current
                        binding.tvAudioDuration.text = AudioUtils.formatDuration(current)
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

    private fun deleteRecordedAudio() {
        stopPlayback()
        val path = currentAudioPath
        if (!path.isNullOrBlank()) {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        currentAudioPath = null
        Toast.makeText(this, getString(R.string.msg_audio_deleted), Toast.LENGTH_SHORT).show()
        updateAudioStateUI()
    }

    // =========================================================================
    // BƯỚC 11: XỬ LÝ NHẮC NHỞ (NOTIFICATION + ALARMMANAGER)
    // =========================================================================

    private fun setupReminderPicker() {
        binding.layoutReminderHeader.setOnClickListener {
            binding.switchReminder.toggle()
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            isReminderEnabled = isChecked
            if (isChecked) {
                binding.cardReminder.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_surface_variant))
                // Xin quyền POST_NOTIFICATIONS trên Android 13+ khi bật lần đầu
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                binding.layoutReminderDatetime.visibility = View.VISIBLE
                if (selectedReminderTimeMs <= System.currentTimeMillis()) {
                    // Mặc định thiết lập ngày mai lúc 20:00 (hoặc hôm nay nếu 20:00 chưa qua)
                    val cal = Calendar.getInstance()
                    if (cal.get(Calendar.HOUR_OF_DAY) >= 20) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    cal.set(Calendar.HOUR_OF_DAY, 20)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    reminderCalendar = cal
                    selectedReminderTimeMs = cal.timeInMillis
                }
                updateReminderDateTimeUI()
            } else {
                binding.cardReminder.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_surface))
                binding.layoutReminderDatetime.visibility = View.GONE
                selectedReminderTimeMs = 0L
            }
        }

        binding.btnPickDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnPickTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnChangeWeeklyReminder.setOnClickListener {
            showWeeklyReminderChangeDialog()
        }
    }

    private fun showWeeklyReminderChangeDialog() {
        val days = arrayOf(
            getString(R.string.day_monday),
            getString(R.string.day_tuesday),
            getString(R.string.day_wednesday),
            getString(R.string.day_thursday),
            getString(R.string.day_friday),
            getString(R.string.day_saturday),
            getString(R.string.day_sunday)
        )
        val dayConstants = intArrayOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val currentDay = reminderCalendar.get(Calendar.DAY_OF_WEEK)
        val currentDayIndex = dayConstants.indexOf(currentDay).let {
            if (it >= 0) it else 4 // Mặc định Thứ Sáu
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_select_weekly_day)
            .setSingleChoiceItems(days, currentDayIndex) { dialog, which ->
                val targetDay = dayConstants[which]
                val currentDow = reminderCalendar.get(Calendar.DAY_OF_WEEK)
                val diff = (targetDay - currentDow + 7) % 7
                if (diff != 0) {
                    reminderCalendar.add(Calendar.DAY_OF_YEAR, diff)
                }
                if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
                    reminderCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                selectedReminderTimeMs = reminderCalendar.timeInMillis
                updateReminderDateTimeUI()
                dialog.dismiss()
                showTimePicker()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.title_pick_date))
            .setSelection(reminderCalendar.timeInMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selectionUtcMillis
            }
            reminderCalendar.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            reminderCalendar.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            reminderCalendar.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            selectedReminderTimeMs = reminderCalendar.timeInMillis
            updateReminderDateTimeUI()
        }
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun showTimePicker() {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText(getString(R.string.title_pick_time))
            .setPositiveButtonText(getString(R.string.btn_pick_time_positive))
            .setNegativeButtonText(getString(R.string.btn_pick_time_negative))
            .setHour(reminderCalendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(reminderCalendar.get(Calendar.MINUTE))
            .build()

        timePicker.addOnPositiveButtonClickListener {
            reminderCalendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            reminderCalendar.set(Calendar.MINUTE, timePicker.minute)
            reminderCalendar.set(Calendar.SECOND, 0)
            reminderCalendar.set(Calendar.MILLISECOND, 0)
            if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
                reminderCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            selectedReminderTimeMs = reminderCalendar.timeInMillis
            updateReminderDateTimeUI()
        }
        timePicker.show(supportFragmentManager, "MATERIAL_TIME_PICKER")
    }

    private fun updateReminderDateTimeUI() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvReminderDate.text = dateFormat.format(reminderCalendar.time)
        binding.tvReminderTime.text = timeFormat.format(reminderCalendar.time)

        val dayOfWeekStr = when (reminderCalendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> getString(R.string.day_monday)
            Calendar.TUESDAY -> getString(R.string.day_tuesday)
            Calendar.WEDNESDAY -> getString(R.string.day_wednesday)
            Calendar.THURSDAY -> getString(R.string.day_thursday)
            Calendar.FRIDAY -> getString(R.string.day_friday)
            Calendar.SATURDAY -> getString(R.string.day_saturday)
            Calendar.SUNDAY -> getString(R.string.day_sunday)
            else -> getString(R.string.day_friday)
        }
        val timeStr = timeFormat.format(reminderCalendar.time)
        binding.tvWeeklyReminderText.text = getString(R.string.format_weekly_reminder_detail, dayOfWeekStr, timeStr)
    }

    private fun setupListeners() {
        binding.etHobbyName.doAfterTextChanged {
            binding.tilHobbyName.error = null
        }

        binding.btnCancel.setOnClickListener {
            handleCancel()
        }

        binding.btnSave.setOnClickListener {
            saveHobby()
        }
    }

    private fun handleCancel() {
        stopPlayback()
        if (isRecording) {
            stopRecording()
        }
        if (!currentAudioPath.isNullOrBlank() && currentAudioPath != initialAudioPath) {
            try {
                val f = File(currentAudioPath!!)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        finish()
    }

    private fun initFromIntent() {
        hobbyId = if (intent.hasExtra(EXTRA_KEY_HOBBY_ID)) {
            intent.getLongExtra(EXTRA_KEY_HOBBY_ID, -1L)
        } else {
            intent.getLongExtra(Constants.EXTRA_HOBBY_ID, -1L)
        }

        isEditMode = hobbyId > 0L

        if (isEditMode) {
            binding.toolbar.title = getString(R.string.title_edit_hobby)
            existingHobby = dbHelper.getHobbyById(hobbyId)

            existingHobby?.let { hobby ->
                binding.etHobbyName.setText(hobby.name)
                currentCategory = hobby.category
                binding.actvCategory.setText(CategoryUtils.getCategoryLabel(this, currentCategory), false)
                updateCategoryIcon(currentCategory)

                currentRating = if (hobby.rating >= 1) hobby.rating else 1
                binding.ratingBarInput.rating = currentRating.toFloat()
                binding.tvRatingValue.text = getString(R.string.rating_format, currentRating)

                binding.etHobbyNotes.setText(hobby.note ?: "")

                // Nạp ảnh cũ
                initialImagePath = hobby.imagePath
                setCurrentImage(hobby.imagePath)

                // Nạp ghi âm cũ (Bước 10)
                initialAudioPath = hobby.audioPath
                currentAudioPath = hobby.audioPath
                updateAudioStateUI()

                // Nạp nhắc nhở cũ (Bước 11)
                initialReminderTimeMs = hobby.reminderTime
                selectedReminderTimeMs = hobby.reminderTime
                if (hobby.reminderTime > 0L) {
                    binding.switchReminder.isChecked = true
                    isReminderEnabled = true
                    binding.layoutReminderDatetime.visibility = View.VISIBLE
                    reminderCalendar.timeInMillis = hobby.reminderTime
                    updateReminderDateTimeUI()
                } else {
                    binding.switchReminder.isChecked = false
                    isReminderEnabled = false
                    binding.layoutReminderDatetime.visibility = View.GONE
                }
            } ?: run {
                Toast.makeText(this, getString(R.string.error_hobby_not_found), Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            binding.toolbar.title = getString(R.string.title_add_hobby)
            currentRating = 5
            binding.ratingBarInput.rating = 5f
            binding.tvRatingValue.text = getString(R.string.rating_format, 5)
            setCurrentImage(null)
            initialAudioPath = null
            currentAudioPath = null
            updateAudioStateUI()

            initialReminderTimeMs = 0L
            selectedReminderTimeMs = 0L
            binding.switchReminder.isChecked = false
            isReminderEnabled = false
            binding.layoutReminderDatetime.visibility = View.GONE

            // Kiểm tra danh mục được chọn sẵn từ gợi ý (Phương án A)
            val presetCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: intent.getStringExtra(Constants.EXTRA_CATEGORY)
            if (!presetCategory.isNullOrBlank()) {
                currentCategory = CategoryUtils.toDbCategory(this, presetCategory)
                val displayLabel = CategoryUtils.getCategoryLabel(this, currentCategory)
                binding.actvCategory.setText(displayLabel, false)
                updateCategoryIcon(currentCategory)
                binding.etHobbyName.requestFocus()
            }
        }
    }

    private fun saveHobby() {
        stopPlayback()
        if (isRecording) {
            stopRecording()
        }

        val name = binding.etHobbyName.text?.toString()?.trim().orEmpty()
        val rawCategory = currentCategory.ifEmpty {
            binding.actvCategory.text?.toString()?.trim().orEmpty()
        }
        val category = CategoryUtils.toDbCategory(this, rawCategory)
        val note = binding.etHobbyNotes.text?.toString()?.trim()?.ifEmpty { null }
        val rating = currentRating

        // 1. Validate: tên không rỗng
        if (name.isBlank()) {
            binding.tilHobbyName.error = getString(R.string.error_name_required)
            binding.etHobbyName.requestFocus()
            return
        }

        // 2. Validate: rating >= 1
        if (rating < 1) {
            Toast.makeText(this, getString(R.string.error_rating_min), Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Validate nhắc nhở: thời điểm phải ở tương lai nếu bật switch (Bước 11)
        val finalReminderTime = if (binding.switchReminder.isChecked) {
            val scheduledTime = reminderCalendar.timeInMillis
            if (scheduledTime <= System.currentTimeMillis()) {
                Toast.makeText(this, getString(R.string.error_reminder_past), Toast.LENGTH_SHORT).show()
                return
            }
            scheduledTime
        } else {
            0L
        }

        if (isEditMode) {
            val oldHobby = existingHobby
            val updatedHobby = Hobby(
                id = hobbyId,
                name = name,
                category = category,
                rating = rating,
                note = note,
                imagePath = currentImagePath,
                audioPath = currentAudioPath,
                reminderTime = finalReminderTime,
                createdAt = oldHobby?.createdAt ?: System.currentTimeMillis()
            )

            val rowsAffected = dbHelper.updateHobby(updatedHobby)
            if (rowsAffected > 0) {
                // Xử lý báo thức nhắc nhở (Bước 11)
                if (finalReminderTime > 0L) {
                    ReminderScheduler.scheduleReminder(this, hobbyId, name, finalReminderTime)
                } else {
                    ReminderScheduler.cancelReminder(this, hobbyId)
                }

                // Nếu đổi hoặc xóa ảnh: xóa file ảnh cũ khỏi đĩa
                if (!initialImagePath.isNullOrBlank() && initialImagePath != currentImagePath) {
                    try {
                        val oldFile = File(initialImagePath!!)
                        if (oldFile.exists()) oldFile.delete()
                    } catch (_: Exception) {}
                }

                // Nếu đổi hoặc xóa ghi âm: xóa file ghi âm cũ khỏi đĩa (Bước 10)
                if (!initialAudioPath.isNullOrBlank() && initialAudioPath != currentAudioPath) {
                    try {
                        val oldAudio = File(initialAudioPath!!)
                        if (oldAudio.exists()) oldAudio.delete()
                    } catch (_: Exception) {}
                }

                Toast.makeText(this, getString(R.string.msg_update_success), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.error_cannot_update_hobby), Toast.LENGTH_SHORT).show()
            }
        } else {
            val newHobby = Hobby(
                name = name,
                category = category,
                rating = rating,
                note = note,
                imagePath = currentImagePath,
                audioPath = currentAudioPath,
                reminderTime = finalReminderTime,
                createdAt = System.currentTimeMillis()
            )

            val insertedId = dbHelper.insertHobby(newHobby)
            if (insertedId > 0L) {
                // Đặt báo thức nhắc nhở nếu có (Bước 11)
                if (finalReminderTime > 0L) {
                    ReminderScheduler.scheduleReminder(this, insertedId, name, finalReminderTime)
                }

                Toast.makeText(this, getString(R.string.msg_add_success), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, getString(R.string.error_cannot_add_hobby), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("state_hobby_id", hobbyId)
        outState.putBoolean("state_is_edit_mode", isEditMode)
        outState.putString("state_category", currentCategory)
        outState.putInt("state_rating", currentRating)
        outState.putString("state_image_path", currentImagePath)
        outState.putString("state_audio_path", currentAudioPath)
        outState.putString("state_initial_audio_path", initialAudioPath)
        outState.putBoolean("state_audio_perm_denied", isAudioPermissionDenied)

        // Lưu trạng thái nhắc nhở (Bước 11)
        outState.putBoolean("state_reminder_enabled", isReminderEnabled)
        outState.putLong("state_reminder_time", reminderCalendar.timeInMillis)
        outState.putLong("state_initial_reminder_time", initialReminderTimeMs)
    }

    private fun restoreInstanceState(savedInstanceState: Bundle) {
        hobbyId = savedInstanceState.getLong("state_hobby_id", -1L)
        isEditMode = savedInstanceState.getBoolean("state_is_edit_mode", false)
        currentCategory = savedInstanceState.getString("state_category", "")
        currentRating = savedInstanceState.getInt("state_rating", 5)
        currentImagePath = savedInstanceState.getString("state_image_path")
        currentAudioPath = savedInstanceState.getString("state_audio_path")
        initialAudioPath = savedInstanceState.getString("state_initial_audio_path")
        isAudioPermissionDenied = savedInstanceState.getBoolean("state_audio_perm_denied", false)

        binding.toolbar.title = if (isEditMode) {
            getString(R.string.title_edit_hobby)
        } else {
            getString(R.string.title_add_hobby)
        }

        if (currentCategory.isNotEmpty()) {
            binding.actvCategory.setText(CategoryUtils.getCategoryLabel(this, currentCategory), false)
            updateCategoryIcon(currentCategory)
        }
        binding.ratingBarInput.rating = currentRating.toFloat()
        binding.tvRatingValue.text = getString(R.string.rating_format, currentRating)
        setCurrentImage(currentImagePath)
        updateAudioStateUI()

        // Khôi phục trạng thái nhắc nhở (Bước 11)
        isReminderEnabled = savedInstanceState.getBoolean("state_reminder_enabled", false)
        val savedTime = savedInstanceState.getLong("state_reminder_time", 0L)
        initialReminderTimeMs = savedInstanceState.getLong("state_initial_reminder_time", 0L)
        selectedReminderTimeMs = savedTime
        binding.switchReminder.isChecked = isReminderEnabled
        if (isReminderEnabled && savedTime > 0L) {
            reminderCalendar.timeInMillis = savedTime
            binding.layoutReminderDatetime.visibility = View.VISIBLE
            updateReminderDateTimeUI()
        } else {
            binding.layoutReminderDatetime.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_KEY_HOBBY_ID = "hobby_id"
        const val EXTRA_CATEGORY = Constants.EXTRA_CATEGORY
    }
}
