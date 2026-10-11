package com.example.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View chuyên biệt cho việc căn chỉnh vị trí khung ảnh (Pan & Zoom Crop).
 * - Tỉ lệ khung: 4:3 (phù hợp với card danh sách và chi tiết sở thích).
 * - Hỗ trợ thao tác 1 ngón kéo di chuyển, 2 ngón phóng to / thu nhỏ.
 * - Giới hạn biên tự động để ảnh luôn lấp đầy khung crop, không bị khoảng trống.
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val currentMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    // Khung crop hình chữ nhật (mặc định 4:3, có thể chuyển sang 1:1, 16:9, 3:4)
    val cropRect = RectF()
    private var aspectRatio: Float = 4f / 3f

    fun setAspectRatio(ratio: Float) {
        if (Math.abs(aspectRatio - ratio) < 0.001f) return
        aspectRatio = ratio
        if (width > 0 && height > 0) {
            calculateCropRect(width, height)
            if (sourceBitmap != null) {
                initMatrix()
            }
            invalidate()
        }
    }

    fun getAspectRatio(): Float = aspectRatio

    // Sơn phủ viền mờ bên ngoài
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#B3000000") // 70% đen
        style = Paint.Style.FILL
    }

    // Sơn viền khung crop
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Sơn đường lưới tam phân (Rule of thirds)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Sơn góc viền nhấn (Corner accents)
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C25E3B") // Màu terracotta thương hiệu
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val clipPath = Path()

    // Điều khiển cử chỉ
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val currentScale = getCurrentScale()
            val minScale = calculateMinScale()

            // Giới hạn scale tối thiểu để ảnh không nhỏ hơn khung crop, tối đa 5x
            val targetScale = (currentScale * scaleFactor).coerceIn(minScale, minScale * 5f)
            val actualFactor = targetScale / currentScale

            currentMatrix.postScale(actualFactor, actualFactor, detector.focusX, detector.focusY)
            checkBounds()
            invalidate()
            return true
        }
    })

    fun setImageBitmap(bitmap: Bitmap?) {
        sourceBitmap = bitmap
        if (width > 0 && height > 0) {
            initMatrix()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateCropRect(w, h)
        if (sourceBitmap != null) {
            initMatrix()
        }
    }

    private fun calculateCropRect(viewWidth: Int, viewHeight: Int) {
        val marginHorizontal = 32f * resources.displayMetrics.density
        val availableWidth = (viewWidth - marginHorizontal * 2).coerceAtLeast(100f)
        var cropW = availableWidth
        var cropH = cropW / aspectRatio

        val marginVertical = 80f * resources.displayMetrics.density
        val availableHeight = viewHeight - marginVertical * 2
        if (cropH > availableHeight) {
            cropH = availableHeight
            cropW = cropH * aspectRatio
        }

        val left = (viewWidth - cropW) / 2f
        val top = (viewHeight - cropH) / 2f
        cropRect.set(left, top, left + cropW, top + cropH)
    }

    private fun initMatrix() {
        val bm = sourceBitmap ?: return
        if (cropRect.isEmpty) return

        currentMatrix.reset()
        val minScale = calculateMinScale()
        currentMatrix.postScale(minScale, minScale)

        // Căn giữa ảnh vào khung crop ban đầu
        val scaledW = bm.width * minScale
        val scaledH = bm.height * minScale
        val dx = cropRect.centerX() - (scaledW / 2f)
        val dy = cropRect.centerY() - (scaledH / 2f)
        currentMatrix.postTranslate(dx, dy)

        checkBounds()
    }

    private fun calculateMinScale(): Float {
        val bm = sourceBitmap ?: return 1f
        if (cropRect.isEmpty) return 1f
        val scaleX = cropRect.width() / bm.width.toFloat()
        val scaleY = cropRect.height() / bm.height.toFloat()
        return max(scaleX, scaleY)
    }

    private fun getCurrentScale(): Float {
        currentMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun getImageRect(): RectF {
        val bm = sourceBitmap ?: return RectF()
        val rect = RectF(0f, 0f, bm.width.toFloat(), bm.height.toFloat())
        currentMatrix.mapRect(rect)
        return rect
    }

    private fun checkBounds() {
        val bm = sourceBitmap ?: return
        val imgRect = getImageRect()
        var dx = 0f
        var dy = 0f

        if (imgRect.width() >= cropRect.width()) {
            if (imgRect.left > cropRect.left) {
                dx = cropRect.left - imgRect.left
            } else if (imgRect.right < cropRect.right) {
                dx = cropRect.right - imgRect.right
            }
        } else {
            dx = cropRect.centerX() - imgRect.centerX()
        }

        if (imgRect.height() >= cropRect.height()) {
            if (imgRect.top > cropRect.top) {
                dy = cropRect.top - imgRect.top
            } else if (imgRect.bottom < cropRect.bottom) {
                dy = cropRect.bottom - imgRect.bottom
            }
        } else {
            dy = cropRect.centerY() - imgRect.centerY()
        }

        currentMatrix.postTranslate(dx, dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    currentMatrix.postTranslate(dx, dy)
                    checkBounds()
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Vẽ Bitmap theo Matrix
        sourceBitmap?.let { bm ->
            canvas.drawBitmap(bm, currentMatrix, null)
        }

        if (cropRect.isEmpty) return

        // 2. Vẽ màn tối phủ xung quanh khung crop
        clipPath.reset()
        clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        clipPath.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(clipPath, overlayPaint)

        // 3. Vẽ viền khung crop
        canvas.drawRect(cropRect, borderPaint)

        // 4. Vẽ đường lưới 1/3 (Rule of thirds)
        val oneThirdW = cropRect.width() / 3f
        val oneThirdH = cropRect.height() / 3f

        canvas.drawLine(cropRect.left + oneThirdW, cropRect.top, cropRect.left + oneThirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + oneThirdW * 2, cropRect.top, cropRect.left + oneThirdW * 2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH, cropRect.right, cropRect.top + oneThirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH * 2, cropRect.right, cropRect.top + oneThirdH * 2, gridPaint)

        // 5. Vẽ 4 góc nhấn viền
        val cornerLen = 20f * resources.displayMetrics.density
        // Góc trên-trái
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cornerLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cornerLen, cornerPaint)
        // Góc trên-phải
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right - cornerLen, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cornerLen, cornerPaint)
        // Góc dưới-trái
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cornerLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left, cropRect.bottom - cornerLen, cornerPaint)
        // Góc dưới-phải
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right - cornerLen, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right, cropRect.bottom - cornerLen, cornerPaint)
    }

    /**
     * Xoay ảnh 90 độ theo chiều kim đồng hồ
     */
    fun rotate90() {
        val bm = sourceBitmap ?: return
        val rotateMatrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, rotateMatrix, true)
        sourceBitmap = rotated
        initMatrix()
        invalidate()
    }

    /**
     * Xuất ra Bitmap chính xác theo vùng khung crop hiện tại
     */
    fun getCroppedBitmap(): Bitmap? {
        val bm = sourceBitmap ?: return null
        if (cropRect.isEmpty) return null

        val inverseMatrix = Matrix()
        if (!currentMatrix.invert(inverseMatrix)) return null

        val mappedRect = RectF(cropRect)
        inverseMatrix.mapRect(mappedRect)

        val srcX = mappedRect.left.coerceAtLeast(0f).toInt()
        val srcY = mappedRect.top.coerceAtLeast(0f).toInt()
        val srcRight = mappedRect.right.coerceAtMost(bm.width.toFloat()).toInt()
        val srcBottom = mappedRect.bottom.coerceAtMost(bm.height.toFloat()).toInt()

        val cropWidth = (srcRight - srcX).coerceAtLeast(1)
        val cropHeight = (srcBottom - srcY).coerceAtLeast(1)

        return try {
            Bitmap.createBitmap(bm, srcX, srcY, cropWidth, cropHeight)
        } catch (e: Exception) {
            null
        }
    }
}
