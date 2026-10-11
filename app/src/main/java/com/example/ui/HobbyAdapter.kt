package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.ItemHobbyBinding
import com.example.model.Hobby
import java.io.File

/**
 * Adapter hiển thị danh sách mục sở thích trong RecyclerView (dùng chung cho cả Linear và Grid).
 * Hỗ trợ nạp ảnh từ `imagePath` bằng BitmapFactory với kỹ thuật thu nhỏ tỉ lệ (inSampleSize),
 * nếu rỗng hoặc lỗi sẽ hiển thị ảnh vector mặc định.
 */
class HobbyAdapter(
    private val onItemClick: ((Hobby) -> Unit)? = null
) : ListAdapter<Hobby, HobbyAdapter.HobbyViewHolder>(HobbyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HobbyViewHolder {
        val binding = ItemHobbyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HobbyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HobbyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HobbyViewHolder(private val binding: ItemHobbyBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hobby: Hobby) {
            binding.tvHobbyName.text = hobby.name
            binding.tvHobbyCategory.text = com.example.utils.CategoryUtils.getCategoryLabel(binding.root.context, hobby.category)
            binding.ratingBarHobby.rating = hobby.rating.toFloat()

            // Hiển thị ngày thêm và trích dẫn ghi chú theo thiết kế Image 1
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            binding.tvHobbyDate.text = dateFormat.format(java.util.Date(hobby.createdAt))

            binding.tvHobbyNote.visibility = android.view.View.GONE

            // Xử lý nạp ảnh từ đường dẫn tệp
            val imagePath = hobby.imagePath
            if (!imagePath.isNullOrBlank()) {
                val bitmap = decodeSampledBitmapFromPath(imagePath, 400, 300)
                if (bitmap != null) {
                    binding.ivHobbyImage.setImageBitmap(bitmap)
                } else {
                    binding.ivHobbyImage.setImageResource(R.drawable.ic_hobby_placeholder)
                }
            } else {
                binding.ivHobbyImage.setImageResource(R.drawable.ic_hobby_placeholder)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(hobby)
            }
        }
    }

    private class HobbyDiffCallback : DiffUtil.ItemCallback<Hobby>() {
        override fun areItemsTheSame(oldItem: Hobby, newItem: Hobby): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Hobby, newItem: Hobby): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        /**
         * Giải mã ảnh từ file đường dẫn với kích thước thu nhỏ phù hợp nhằm tối ưu bộ nhớ.
         */
        fun decodeSampledBitmapFromPath(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null

            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)
            } catch (e: Exception) {
                null
            }
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
