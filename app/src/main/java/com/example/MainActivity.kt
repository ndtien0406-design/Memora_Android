package com.example

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.database.HobbyDatabaseHelper
import com.example.databinding.ActivityMainBinding
import com.example.ui.AddEditHobbyActivity
import com.example.ui.HobbyAdapter
import com.example.ui.HobbyDetailActivity
import com.example.ui.SortFilterBottomSheet
import com.example.ui.StatsActivity
import com.example.utils.Constants
import com.example.utils.PreferenceHelper

/**
 * Màn hình chính của ứng dụng Sở Thích.
 * Bước 6: Tìm kiếm theo thời gian thực (debounce ~250ms), lọc theo chip danh mục,
 * sắp xếp qua BottomSheet và lưu vào SharedPreferences (key "sort_mode").
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var dbHelper: HobbyDatabaseHelper
    private lateinit var hobbyAdapter: HobbyAdapter

    private var isGridView: Boolean = false
    private var currentSortMode: String = Constants.SORT_BY_DATE
    private var selectedCategory: String = "Tất cả"

    // Handler hỗ trợ debounce 250ms cho ô tìm kiếm
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // Danh sách chip giao diện trên MainActivity
    private val categoryChips by lazy {
        listOf(
            binding.chipAll to "Tất cả",
            binding.chipMusic to "Âm nhạc",
            binding.chipMovies to "Phim ảnh",
            binding.chipBooks to "Sách",
            binding.chipSports to "Thể thao",
            binding.chipTravel to "Du lịch",
            binding.chipFood to "Ẩm thực",
            binding.chipOther to "Khác"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        preferenceHelper.applyAppTheme()

        dbHelper = HobbyDatabaseHelper(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isGridView = preferenceHelper.isGridView
        // Đọc lại kiểu sắp xếp đã lưu từ SharedPreferences (key "sort_mode")
        currentSortMode = preferenceHelper.sortMode

        setupRecyclerView()
        setupSearchAndFilters()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        currentSortMode = preferenceHelper.sortMode
        binding.includedBottomNav.bottomNavView.selectedItemId = R.id.nav_hobbies
        loadHobbiesData()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    private fun setupRecyclerView() {
        hobbyAdapter = HobbyAdapter { hobby ->
            val intent = Intent(this, HobbyDetailActivity::class.java).apply {
                putExtra(HobbyDetailActivity.EXTRA_KEY_HOBBY_ID, hobby.id)
            }
            startActivity(intent)
        }

        binding.rvHobbies.adapter = hobbyAdapter
        applyLayoutManager()
    }

    private fun applyLayoutManager() {
        if (isGridView) {
            binding.rvHobbies.layoutManager = GridLayoutManager(this, 2)
        } else {
            binding.rvHobbies.layoutManager = LinearLayoutManager(this)
        }
    }

    private fun setupSearchAndFilters() {
        // 1. Tìm kiếm với debounce ~250ms
        binding.etSearch.doAfterTextChanged {
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            val runnable = Runnable {
                loadHobbiesData()
            }
            searchRunnable = runnable
            searchHandler.postDelayed(runnable, 250L)
        }

        // 2. Lọc theo chip danh mục
        categoryChips.forEach { (chipView, categoryName) ->
            chipView.setOnClickListener {
                selectCategory(categoryName)
            }
        }
    }

    private fun selectCategory(categoryName: String) {
        selectedCategory = categoryName
        updateCategoryChipsUI()
        loadHobbiesData()
    }

    private fun updateCategoryChipsUI() {
        categoryChips.forEach { (chipView, name) ->
            if (name == selectedCategory) {
                chipView.setBackgroundResource(R.drawable.bg_chip_selected)
                chipView.setTextColor(getColor(R.color.color_on_accent))
                chipView.setTypeface(chipView.typeface, android.graphics.Typeface.BOLD)
            } else {
                chipView.setBackgroundResource(R.drawable.bg_chip_unselected)
                chipView.setTextColor(getColor(R.color.color_text_primary))
                chipView.setTypeface(chipView.typeface, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun setupListeners() {
        // Removed btnViewMode listener

        // Nút Sắp xếp mở BottomSheetDialogFragment "Sắp xếp và lọc"
        binding.btnSort.setOnClickListener {
            openSortFilterBottomSheet()
        }

        // FAB dấu cộng mở chế độ Thêm
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AddEditHobbyActivity::class.java)
            startActivity(intent)
        }

        // Nút Thêm ở khối trạng thái rỗng
        binding.btnAddFirstHobby.setOnClickListener {
            val intent = Intent(this, AddEditHobbyActivity::class.java)
            startActivity(intent)
        }

        // Gợi ý danh mục: Thẻ Sách (Phương án A - mở form với danh mục Sách chọn sẵn)
        binding.cardSuggestionBooks.setOnClickListener {
            val intent = Intent(this, AddEditHobbyActivity::class.java).apply {
                putExtra(AddEditHobbyActivity.EXTRA_CATEGORY, "Sách")
            }
            startActivity(intent)
        }

        // Gợi ý danh mục: Thẻ Âm nhạc (Phương án A - mở form với danh mục Âm nhạc chọn sẵn)
        binding.cardSuggestionMusic.setOnClickListener {
            val intent = Intent(this, AddEditHobbyActivity::class.java).apply {
                putExtra(AddEditHobbyActivity.EXTRA_CATEGORY, "Âm nhạc")
            }
            startActivity(intent)
        }

        // Nút Xóa bộ lọc khi không tìm thấy kết quả
        binding.btnClearFilters.setOnClickListener {
            binding.etSearch.setText("")
            selectCategory("Tất cả")
        }

        // Thanh điều hướng đáy dùng chung (layout_bottom_nav)
        com.example.utils.BottomNavHelper.setupBottomNav(
            activity = this,
            bottomNavView = binding.includedBottomNav.bottomNavView,
            currentTabId = R.id.nav_hobbies
        )
    }

    private fun openSortFilterBottomSheet() {
        val bottomSheet = SortFilterBottomSheet.newInstance(
            sortMode = currentSortMode,
            category = selectedCategory
        )
        bottomSheet.onApplyListener = { sortMode, category ->
            currentSortMode = sortMode
            // Lưu kiểu sắp xếp vào SharedPreferences (key "sort_mode")
            preferenceHelper.sortMode = sortMode

            selectedCategory = category
            updateCategoryChipsUI()
            loadHobbiesData()
        }
        bottomSheet.show(supportFragmentManager, SortFilterBottomSheet.TAG)
    }

    /**
     * Nạp dữ liệu với tìm kiếm, lọc và sắp xếp.
     */
    private fun loadHobbiesData() {
        val keyword = binding.etSearch.text?.toString()?.trim()

        // Truy vấn CSDL bằng queryHobbies
        val results = dbHelper.queryHobbies(
            keyword = keyword,
            category = selectedCategory,
            sortMode = currentSortMode
        )

        val totalDbCount = dbHelper.getAllHobbies().size

        hobbyAdapter.submitList(results)

        when {
            // Trường hợp 1: Cơ sở dữ liệu hoàn toàn chưa có mục nào
            totalDbCount == 0 -> {
                binding.rvHobbies.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutNoSearchResults.visibility = View.GONE
            }
            // Trường hợp 2: Có mục trong CSDL nhưng tìm kiếm / lọc không có kết quả
            results.isEmpty() -> {
                binding.rvHobbies.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutNoSearchResults.visibility = View.VISIBLE
            }
            // Trường hợp 3: Có kết quả hiển thị
            else -> {
                binding.rvHobbies.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutNoSearchResults.visibility = View.GONE
            }
        }
    }
}
