package com.example.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.R
import com.example.databinding.BottomSheetSortFilterBinding
import com.example.utils.Constants
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheetDialogFragment "Sắp xếp và lọc".
 * - RadioButton: Tên (A-Z), Đánh giá (cao đến thấp), Ngày thêm (mới nhất).
 * - Chip danh mục: Tất cả và 7 danh mục.
 * - Nút "Đặt lại" và "Áp dụng".
 */
class SortFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSortFilterBinding? = null
    private val binding get() = _binding!!

    var initialSortMode: String = Constants.SORT_BY_DATE
    var initialCategory: String = "Tất cả"

    var onApplyListener: ((sortMode: String, category: String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSortFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khởi tạo trạng thái ban đầu của RadioButton Sắp xếp
        when (initialSortMode) {
            Constants.SORT_BY_NAME -> binding.rbSortName.isChecked = true
            Constants.SORT_BY_RATING -> binding.rbSortRating.isChecked = true
            else -> binding.rbSortDate.isChecked = true
        }

        // Khởi tạo trạng thái Chip Danh mục
        selectCategoryChip(initialCategory)

        // Nút Đặt lại
        binding.btnFilterReset.setOnClickListener {
            binding.rbSortDate.isChecked = true
            selectCategoryChip("Tất cả")
        }

        // Nút Áp dụng
        binding.btnFilterApply.setOnClickListener {
            val selectedSort = when {
                binding.rbSortName.isChecked -> Constants.SORT_BY_NAME
                binding.rbSortRating.isChecked -> Constants.SORT_BY_RATING
                else -> Constants.SORT_BY_DATE
            }

            val selectedCategory = getSelectedCategoryChip()
            onApplyListener?.invoke(selectedSort, selectedCategory)
            dismiss()
        }
    }

    private fun selectCategoryChip(category: String) {
        when (category) {
            "Âm nhạc" -> binding.chipDialogMusic.isChecked = true
            "Phim ảnh" -> binding.chipDialogMovies.isChecked = true
            "Sách" -> binding.chipDialogBooks.isChecked = true
            "Thể thao" -> binding.chipDialogSports.isChecked = true
            "Du lịch" -> binding.chipDialogTravel.isChecked = true
            "Ẩm thực" -> binding.chipDialogFood.isChecked = true
            "Khác" -> binding.chipDialogOther.isChecked = true
            else -> binding.chipDialogAll.isChecked = true
        }
    }

    private fun getSelectedCategoryChip(): String {
        return when {
            binding.chipDialogMusic.isChecked -> "Âm nhạc"
            binding.chipDialogMovies.isChecked -> "Phim ảnh"
            binding.chipDialogBooks.isChecked -> "Sách"
            binding.chipDialogSports.isChecked -> "Thể thao"
            binding.chipDialogTravel.isChecked -> "Du lịch"
            binding.chipDialogFood.isChecked -> "Ẩm thực"
            binding.chipDialogOther.isChecked -> "Khác"
            else -> "Tất cả"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SortFilterBottomSheet"

        fun newInstance(sortMode: String, category: String): SortFilterBottomSheet {
            return SortFilterBottomSheet().apply {
                this.initialSortMode = sortMode
                this.initialCategory = category
            }
        }
    }
}
