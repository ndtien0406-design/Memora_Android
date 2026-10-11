package com.example.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.R
import com.example.database.HobbyDatabaseHelper
import com.example.databinding.ActivityStatsBinding
import com.example.databinding.ItemCategoryStatBinding
import com.example.utils.PreferenceHelper
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Màn hình Thống kê sở thích (Bước 7).
 * - Sử dụng thư viện MPAndroidChart.
 * - Khớp chính xác thiết kế: PieChart donut, số tổng ở giữa cùng "Tổng số sở thích".
 * - Nút phân đoạn "Tròn | Cột" bo tròn mềm mại.
 * - Danh sách 7 danh mục chi tiết với số lượng ("X sở thích"), tỷ lệ phần trăm và thanh đo mỏng.
 * - Đầy đủ thanh điều hướng dưới và hỗ trợ chế độ Dark Mode.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var dbHelper: HobbyDatabaseHelper
    private lateinit var preferenceHelper: PreferenceHelper

    private var isPieChartMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        dbHelper = HobbyDatabaseHelper(this)

        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSegmentedControl()
        setupTopActions()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        binding.includedBottomNav.bottomNavView.selectedItemId = R.id.nav_stats
        loadStatsData()
    }

    private fun setupTopActions() {
        // Nút Chia sẻ
        binding.btnStatsShare.setOnClickListener {
            val total = dbHelper.getAllHobbies().size
            val shareText = getString(R.string.stats_share_format, total)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }

        // Nút Thông tin đã bị xóa

        // Nút Thêm ở khối rỗng
        binding.btnStatsAddHobby.setOnClickListener {
            val intent = Intent(this, AddEditHobbyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupSegmentedControl() {
        updateSegmentedUI()

        binding.btnSegmentPie.setOnClickListener {
            if (!isPieChartMode) {
                isPieChartMode = true
                updateSegmentedUI()
            }
        }

        binding.btnSegmentBar.setOnClickListener {
            if (isPieChartMode) {
                isPieChartMode = false
                updateSegmentedUI()
            }
        }
    }

    private fun updateSegmentedUI() {
        if (isPieChartMode) {
            binding.btnSegmentPie.setBackgroundResource(R.drawable.bg_segmented_selected)
            binding.btnSegmentPie.setTextColor(ContextCompat.getColor(this, R.color.color_on_accent))

            binding.btnSegmentBar.background = null
            binding.btnSegmentBar.setTextColor(ContextCompat.getColor(this, R.color.color_text_secondary))

            binding.pieChart.visibility = View.VISIBLE
            binding.barChart.visibility = View.GONE
        } else {
            binding.btnSegmentBar.setBackgroundResource(R.drawable.bg_segmented_selected)
            binding.btnSegmentBar.setTextColor(ContextCompat.getColor(this, R.color.color_on_accent))

            binding.btnSegmentPie.background = null
            binding.btnSegmentPie.setTextColor(ContextCompat.getColor(this, R.color.color_text_secondary))

            binding.pieChart.visibility = View.GONE
            binding.barChart.visibility = View.VISIBLE
        }
    }

    private fun setupBottomNav() {
        com.example.utils.BottomNavHelper.setupBottomNav(
            activity = this,
            bottomNavView = binding.includedBottomNav.bottomNavView,
            currentTabId = R.id.nav_stats
        )
    }

    private fun loadStatsData() {
        val categoryCounts = dbHelper.countByCategory()

        // Tạo danh sách 7 danh mục theo đúng thứ tự SPEC, hiển thị nhãn theo ngôn ngữ hiện tại
        val categoryStats = com.example.utils.CategoryUtils.DB_CATEGORIES.map { dbCategory ->
            val displayLabel = com.example.utils.CategoryUtils.getCategoryLabel(this, dbCategory)
            displayLabel to (categoryCounts[dbCategory] ?: 0)
        }

        val totalCount = categoryStats.sumOf { it.second }

        if (totalCount == 0) {
            binding.layoutEmptyStats.visibility = View.VISIBLE
            binding.scrollStatsContent.visibility = View.GONE
            return
        }

        binding.layoutEmptyStats.visibility = View.GONE
        binding.scrollStatsContent.visibility = View.VISIBLE

        binding.tvCategoryBadge.text = getString(R.string.category_count_badge, categoryStats.size)

        // Bảng màu sắc khớp chính xác ảnh đính kèm
        val categoryColors = listOf(
            ContextCompat.getColor(this, R.color.color_cat_music),   // Âm nhạc (Cam hổ phách)
            ContextCompat.getColor(this, R.color.color_cat_movies),  // Phim ảnh (Xanh phiến thạch đậm)
            ContextCompat.getColor(this, R.color.color_cat_books),   // Sách (Màu đất nung)
            ContextCompat.getColor(this, R.color.color_cat_sports),  // Thể thao (Xanh dương đậm)
            ContextCompat.getColor(this, R.color.color_cat_travel),  // Du lịch (Xanh lá rực)
            ContextCompat.getColor(this, R.color.color_cat_food),    // Ẩm thực (Đỏ hồng thẫm)
            ContextCompat.getColor(this, R.color.color_cat_other)    // Khác (Xám ấm)
        )

        val textColor = ContextCompat.getColor(this, R.color.color_text_primary)
        val secondaryTextColor = ContextCompat.getColor(this, R.color.color_text_secondary)
        val surfaceColor = ContextCompat.getColor(this, R.color.color_surface)
        val borderColor = ContextCompat.getColor(this, R.color.border)

        // 1. Cấu hình PieChart (Donut)
        setupPieChart(categoryStats, totalCount, categoryColors, textColor, secondaryTextColor, surfaceColor)

        // 2. Cấu hình BarChart
        setupBarChart(categoryStats, categoryColors, textColor, secondaryTextColor, borderColor)

        // 3. Danh sách phân bố chi tiết bên dưới
        populateCategoryBreakdown(categoryStats, totalCount, categoryColors)
    }

    private fun setupPieChart(
        categoryStats: List<Pair<String, Int>>,
        totalCount: Int,
        categoryColors: List<Int>,
        textColor: Int,
        secondaryTextColor: Int,
        surfaceColor: Int
    ) {
        val pieChart = binding.pieChart
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(surfaceColor)
        pieChart.holeRadius = 66f
        pieChart.transparentCircleRadius = 70f
        pieChart.setTransparentCircleColor(surfaceColor)
        pieChart.setDrawEntryLabels(false)

        // Tâm donut: Số to nổi bật ở trên, "Tổng số sở thích" ở dưới giống ảnh
        val numberString = totalCount.toString()
        val subtitleString = getString(R.string.total_hobbies_label)
        val fullCenterText = "$numberString\n$subtitleString"

        val spannable = SpannableString(fullCenterText)
        spannable.setSpan(RelativeSizeSpan(2.2f), 0, numberString.length, 0)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, numberString.length, 0)
        spannable.setSpan(ForegroundColorSpan(textColor), 0, numberString.length, 0)

        val labelStart = numberString.length + 1
        spannable.setSpan(RelativeSizeSpan(0.85f), labelStart, fullCenterText.length, 0)
        spannable.setSpan(ForegroundColorSpan(secondaryTextColor), labelStart, fullCenterText.length, 0)

        pieChart.centerText = spannable
        pieChart.setCenterTextSize(14f)

        val gilroyFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.svn_gilroy)
        if (gilroyFont != null) {
            pieChart.setCenterTextTypeface(gilroyFont)
        }

        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        categoryStats.forEachIndexed { index, pair ->
            if (pair.second > 0) {
                entries.add(PieEntry(pair.second.toFloat(), pair.first))
                colors.add(categoryColors[index])
            }
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(false)
            sliceSpace = 2.5f
        }

        pieChart.data = PieData(dataSet)
        pieChart.animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutCubic)
        pieChart.invalidate()
    }

    private fun setupBarChart(
        categoryStats: List<Pair<String, Int>>,
        categoryColors: List<Int>,
        textColor: Int,
        secondaryTextColor: Int,
        borderColor: Int
    ) {
        val barChart = binding.barChart
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)

        val gilroyFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.svn_gilroy)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = secondaryTextColor
        xAxis.textSize = 9.5f
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(categoryStats.map { it.first })
        if (gilroyFont != null) {
            xAxis.typeface = gilroyFont
        }

        val leftAxis = barChart.axisLeft
        leftAxis.textColor = secondaryTextColor
        leftAxis.textSize = 10f
        leftAxis.gridColor = borderColor
        leftAxis.axisMinimum = 0f
        leftAxis.granularity = 1f
        if (gilroyFont != null) {
            leftAxis.typeface = gilroyFont
        }

        barChart.axisRight.isEnabled = false

        val entries = ArrayList<BarEntry>()
        categoryStats.forEachIndexed { index, pair ->
            entries.add(BarEntry(index.toFloat(), pair.second.toFloat()))
        }

        val dataSet = BarDataSet(entries, "").apply {
            colors = categoryColors
            valueTextColor = textColor
            valueTextSize = 10.5f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) value.toInt().toString() else ""
                }
            }
        }

        barChart.data = BarData(dataSet)
        barChart.animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutCubic)
        barChart.invalidate()
    }

    private fun populateCategoryBreakdown(
        categoryStats: List<Pair<String, Int>>,
        totalCount: Int,
        categoryColors: List<Int>
    ) {
        binding.layoutCategoryBreakdown.removeAllViews()
        val inflater = LayoutInflater.from(this)

        categoryStats.forEachIndexed { index, (categoryName, count) ->
            val itemBinding = ItemCategoryStatBinding.inflate(inflater, binding.layoutCategoryBreakdown, false)

            val color = categoryColors[index]
            val percentage = if (totalCount > 0) (count.toDouble() * 100.0) / totalCount.toDouble() else 0.0

            itemBinding.viewCategoryColor.backgroundTintList = ColorStateList.valueOf(color)
            itemBinding.tvCategoryName.text = categoryName
            itemBinding.tvCategoryCount.text = getString(R.string.hobbies_count_format, count)
            itemBinding.tvCategoryPercentage.text = String.format(Locale.US, "%.1f%%", percentage)

            itemBinding.progressCategoryRatio.max = 1000
            itemBinding.progressCategoryRatio.progress = (percentage * 10).toInt()
            itemBinding.progressCategoryRatio.progressTintList = ColorStateList.valueOf(color)

            binding.layoutCategoryBreakdown.addView(itemBinding.root)
        }
    }
}
