package com.example.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.databinding.BottomSheetImagePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheetDialogFragment hiển thị tùy chọn thêm/đổi/xóa ảnh:
 * 1. Chụp ảnh
 * 2. Chọn từ thư viện
 * 3. Xóa ảnh (chỉ hiển thị khi đã có ảnh)
 */
class ImagePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImagePickerBinding? = null
    private val binding get() = _binding!!

    var hasImage: Boolean = false
    var onTakePhotoListener: (() -> Unit)? = null
    var onPickGalleryListener: (() -> Unit)? = null
    var onRemovePhotoListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImagePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dòng xóa ảnh chỉ hiện khi đã có ảnh
        binding.btnActionRemovePhoto.visibility = if (hasImage) View.VISIBLE else View.GONE

        binding.btnActionCamera.setOnClickListener {
            dismiss()
            onTakePhotoListener?.invoke()
        }

        binding.btnActionGallery.setOnClickListener {
            dismiss()
            onPickGalleryListener?.invoke()
        }

        binding.btnActionRemovePhoto.setOnClickListener {
            dismiss()
            onRemovePhotoListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImagePickerBottomSheet"

        fun newInstance(hasImage: Boolean): ImagePickerBottomSheet {
            return ImagePickerBottomSheet().apply {
                this.hasImage = hasImage
            }
        }
    }
}
