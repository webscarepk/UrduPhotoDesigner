package com.webscare.urducanvas.ui.editor.panels.background.backgrounds

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.LayoutImagesItemBinding
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class ImagesAdapter(
    private val onImageSelected: (Bitmap, com.webscare.urducanvas.data.model.ImageEntity) -> Unit
) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    private val images = mutableListOf<com.webscare.urducanvas.data.model.ImageEntity>()

    fun submitList(newList: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        images.clear()
        images.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding =
            LayoutImagesItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(private val binding: LayoutImagesItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentDrawable: Drawable? = null

        fun bind(image: com.webscare.urducanvas.data.model.ImageEntity) {
            binding.shimmerLayout.startShimmer()

            if (image.is_selected) {
                binding.root.strokeWidth = 4
                binding.root.setCardBackgroundColor(Color.WHITE)
                binding.root.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
                binding.root.setCardBackgroundColor(Color.WHITE)
            }

            binding.isPremium.isVisible = image.is_premium
            binding.root.addPressEffect {
                if (image.bitmapData != null) {
                    val bitmap = _root_ide_package_.com.webscare.urducanvas.common.utils.ImageProcessor.filePathToBitmap(image.bitmapData!!)
                    onImageSelected(bitmap!!, image)
                } else {
                    // If bitmapData is empty, load image from URL
                    val url = _root_ide_package_.com.webscare.urducanvas.common.utils.Constants.BASE_URL_GLIDE + image.file_url
                    Glide.with(binding.root.context)
                        .asBitmap()
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            override fun onResourceReady(
                                bitmap: Bitmap,
                                transition: Transition<in Bitmap>?
                            ) {
                                // this is guaranteed to be the full-size image
                                onImageSelected(bitmap, image)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) { /* no-op */
                            }
                        })
                }
            }

            // Glide image loading logic: If bitmapData is empty, load from the URL
            val url = _root_ide_package_.com.webscare.urducanvas.common.utils.Constants.BASE_URL_GLIDE + image.file_url
            if (image.bitmapData != null) {
                // Decode Base64 string to Bitmap for loading directly if bitmapData exists
                // Decode Base64 string to Bitmap
                Glide.with(itemView.context)
                    .load(image.bitmapData)
                    .into(binding.image)

                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.setShimmer(null)
            } else {
                // Glide to load the image from the URL if no bitmapData
                Glide.with(binding.root.context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .thumbnail(0.1f)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("GlideDebug", "Image load failed: ${e?.message}")
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.setShimmer(null)
                            currentDrawable = null
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("GlideDebug", "Image loaded successfully from: $url")
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.setShimmer(null)
                            currentDrawable = resource
                            return false
                        }
                    })
                    .into(binding.image)
            }
        }
    }
}
