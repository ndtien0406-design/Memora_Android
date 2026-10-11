package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.example.R
import com.example.databinding.BottomSheetShareBinding
import com.example.model.Hobby
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

/**
 * BottomSheetDialogFragment cho tính năng chia sẻ sở thích.
 * - Tóm tắt: ảnh nhỏ, tên, danh mục, số sao.
 * - 2 công tắc: "Kèm ảnh" và "Kèm ghi chú".
 * - Khối "Nội dung sẽ gửi" cập nhật tức thì khi chuyển đổi công tắc.
 * - Bấm "Chia sẻ": Gửi qua Intent.ACTION_SEND (hỗ trợ đính kèm ảnh FileProvider).
 */
class ShareBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetShareBinding? = null
    private val binding get() = _binding!!

    private var hobby: Hobby? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val item = hobby ?: run {
            dismiss()
            return
        }

        bindHobbySummary(item)
        setupSwitches(item)
        updatePreviewContent(item)

        binding.btnShareCancel.setOnClickListener {
            dismiss()
        }

        binding.btnShareConfirm.setOnClickListener {
            performShare(item)
            dismiss()
        }
    }

    private fun bindHobbySummary(item: Hobby) {
        binding.tvShareName.text = item.name
        binding.tvShareCategory.text = com.example.utils.CategoryUtils.getCategoryLabel(requireContext(), item.category)
        binding.tvShareRating.text = getString(R.string.rating_format, item.rating)

        // Hiển thị ảnh nhỏ
        val imagePath = item.imagePath
        if (!imagePath.isNullOrBlank()) {
            val bitmap = HobbyAdapter.decodeSampledBitmapFromPath(imagePath, 160, 160)
            if (bitmap != null) {
                binding.ivShareThumb.setImageBitmap(bitmap)
            } else {
                binding.ivShareThumb.setImageResource(R.drawable.ic_hobby_placeholder)
            }
        } else {
            binding.ivShareThumb.setImageResource(R.drawable.ic_hobby_placeholder)
        }
    }

    private fun setupSwitches(item: Hobby) {
        val hasImage = !item.imagePath.isNullOrBlank() && File(item.imagePath).exists()
        val hasNote = !item.note.isNullOrBlank()

        // Công tắc ảnh: chỉ bật được khi có ảnh thật
        binding.switchIncludeImage.isEnabled = hasImage
        binding.switchIncludeImage.isChecked = hasImage

        // Công tắc ghi chú: mặc định bật nếu có ghi chú
        binding.switchIncludeNote.isEnabled = hasNote
        binding.switchIncludeNote.isChecked = hasNote

        binding.switchIncludeImage.setOnCheckedChangeListener { _, _ ->
            updatePreviewContent(item)
        }

        binding.switchIncludeNote.setOnCheckedChangeListener { _, _ ->
            updatePreviewContent(item)
        }
    }

    /**
     * Cập nhật nội dung xem trước theo đúng định dạng:
     * <tên>
     * Danh mục: <danh mục>
     * Đánh giá: <n>/5 sao
     * Ghi chú: <ghi chú> (chỉ khi bật "Kèm ghi chú")
     */
    private fun buildShareText(item: Hobby): String {
        val sb = StringBuilder()
        sb.append(item.name).append("\n")
        sb.append(getString(R.string.share_format_category, com.example.utils.CategoryUtils.getCategoryLabel(requireContext(), item.category))).append("\n")
        sb.append(getString(R.string.share_format_rating, item.rating))

        if (binding.switchIncludeNote.isChecked && !item.note.isNullOrBlank()) {
            sb.append("\n").append(getString(R.string.share_format_note, item.note))
        }

        return sb.toString()
    }

    private fun updatePreviewContent(item: Hobby) {
        binding.tvPreviewContent.text = buildShareText(item)
    }

    /**
     * Thực hiện gửi Intent chia sẻ (ACTION_SEND)
     */
    private fun performShare(item: Hobby) {
        val context = context ?: return
        val shareText = buildShareText(item)
        val includeImage = binding.switchIncludeImage.isChecked && !item.imagePath.isNullOrBlank()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)

            if (includeImage) {
                val imageFile = File(item.imagePath!!)
                if (imageFile.exists()) {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        imageFile
                    )
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            } else {
                type = "text/plain"
            }
        }

        val chooser = Intent.createChooser(shareIntent, getString(R.string.share_via))
        startActivity(chooser)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ShareBottomSheet"

        fun newInstance(hobby: Hobby): ShareBottomSheet {
            return ShareBottomSheet().apply {
                this.hobby = hobby
            }
        }
    }
}
