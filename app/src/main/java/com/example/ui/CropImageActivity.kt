package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.databinding.ActivityCropImageBinding
import com.example.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Màn hình Căn chỉnh khung ảnh (Crop / Adjust Framing) cho hình đại diện sở thích.
 * - Cho phép kéo di chuyển (Pan) và dùng 2 ngón phóng to/thu nhỏ (Pinch-to-zoom).
 * - Cắt ảnh theo khung 4:3 chuẩn xác và lưu ra file ảnh JPEG chất lượng cao.
 */
class CropImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropImageBinding
    private lateinit var preferenceHelper: PreferenceHelper

    private var inputImagePath: String? = null
    private var inputImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        binding = ActivityCropImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inputImagePath = intent.getStringExtra(EXTRA_INPUT_PATH)
        inputImageUri = androidx.core.content.IntentCompat.getParcelableExtra(intent, EXTRA_INPUT_URI, Uri::class.java)

        if (inputImagePath.isNullOrBlank() && inputImageUri == null) {
            Toast.makeText(this, "Không tìm thấy ảnh để căn chỉnh", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        loadImageAsync()
    }

    private fun setupListeners() {
        binding.btnCropBack.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnCropRotate.setOnClickListener {
            binding.cropImageView.rotate90()
        }

        binding.btnCropApply.setOnClickListener {
            saveCroppedImageAsync()
        }
    }

    private fun loadImageAsync() {
        binding.progressLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = loadOptimizedBitmap()
            withContext(Dispatchers.Main) {
                binding.progressLoading.visibility = View.GONE
                if (bitmap != null) {
                    binding.cropImageView.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@CropImageActivity, "Không thể tải ảnh", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun loadOptimizedBitmap(): Bitmap? {
        return try {
            val (width, height) = decodeImageBounds()
            if (width <= 0 || height <= 0) return null

            // Downsample nếu ảnh quá lớn (> 2048px) để tránh OOM
            val maxDimension = 2048
            var inSampleSize = 1
            var halfW = width
            var halfH = height
            while (halfW > maxDimension || halfH > maxDimension) {
                inSampleSize *= 2
                halfW /= 2
                halfH /= 2
            }

            val options = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }

            val decodedBitmap = openImageStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Xoay theo EXIF orientation nếu có
            val orientation = getExifOrientation()
            fixExifOrientation(decodedBitmap, orientation)
        } catch (e: Exception) {
            null
        }
    }

    private fun openImageStream(): InputStream? {
        val path = inputImagePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) return file.inputStream()
        }
        val uri = inputImageUri
        if (uri != null) {
            return contentResolver.openInputStream(uri)
        }
        return null
    }

    private fun decodeImageBounds(): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openImageStream()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return Pair(options.outWidth, options.outHeight)
    }

    private fun getExifOrientation(): Int {
        val path = inputImagePath
        if (!path.isNullOrBlank()) {
            try {
                val exif = ExifInterface(path)
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) {}
        }
        val uri = inputImageUri
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (_: Exception) {}
        }
        return ExifInterface.ORIENTATION_NORMAL
    }

    private fun fixExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun saveCroppedImageAsync() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.btnCropApply.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val croppedBitmap = binding.cropImageView.getCroppedBitmap()
            if (croppedBitmap == null) {
                withContext(Dispatchers.Main) {
                    binding.progressLoading.visibility = View.GONE
                    binding.btnCropApply.isEnabled = true
                    Toast.makeText(this@CropImageActivity, "Lỗi khi cắt ảnh", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val savedFile = saveBitmapToHobbyImages(croppedBitmap)
            withContext(Dispatchers.Main) {
                binding.progressLoading.visibility = View.GONE
                binding.btnCropApply.isEnabled = true

                if (savedFile != null) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_CROPPED_PATH, savedFile.absolutePath)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    Toast.makeText(this@CropImageActivity, "Không thể lưu ảnh đã căn chỉnh", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToHobbyImages(bitmap: Bitmap): File? {
        return try {
            val baseDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val hobbyImagesDir = File(baseDir, "hobby_images").apply { mkdirs() }
            val fileName = "hobby_crop_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(hobbyImagesDir, fileName)

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            destFile
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_INPUT_PATH = "extra_input_path"
        const val EXTRA_INPUT_URI = "extra_input_uri"
        const val EXTRA_CROPPED_PATH = "extra_cropped_path"

        fun createIntent(context: Context, path: String): Intent {
            return Intent(context, CropImageActivity::class.java).apply {
                putExtra(EXTRA_INPUT_PATH, path)
            }
        }

        fun createIntent(context: Context, uri: Uri): Intent {
            return Intent(context, CropImageActivity::class.java).apply {
                putExtra(EXTRA_INPUT_URI, uri)
            }
        }
    }
}
