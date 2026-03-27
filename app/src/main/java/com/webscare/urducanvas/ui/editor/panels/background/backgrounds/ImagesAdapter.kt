package com.webscare.urducanvas.ui.editor.panels.background.backgrounds

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.LayoutImagesItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class ImagesAdapter(
    private val onImageSelected: (Bitmap?, PictureDrawable?, svgXml: String?, ImageEntity) -> Unit
) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    private val images = mutableListOf<ImageEntity>()

    fun submitList(newList: List<ImageEntity>) {
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

        fun bind(image: ImageEntity) {
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

            binding.isPremium.isVisible = image.is_premium && !image.is_subscribed

            val url = Constants.BASE_URL_GLIDE + image.file_url

            binding.root.addPressEffect {
                if (image.bitmapData != null) {
                    if (image.file_name.endsWith(".svg", ignoreCase = true)) {
                        val svgXml = image.bitmapData!!
                        val svg = com.caverock.androidsvg.SVG.getFromString(svgXml)
                        val drawable = PictureDrawable(svg.renderToPicture())
                        onImageSelected(null, drawable, svgXml, image)
                    } else {
                        val bitmap = ImageProcessor.filePathToBitmap(image.bitmapData!!)
                        onImageSelected(bitmap!!, null, null, image)
                    }
                } else {
                        if (url.endsWith(".svg", true)) {

                            val glideTarget = object : CustomTarget<PictureDrawable>() {
                                override fun onResourceReady(
                                    resource: PictureDrawable,
                                    transition: Transition<in PictureDrawable>?
                                ) {
                                    binding.loading.isVisible = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val svgXml: String? = try {
                                            val request = okhttp3.Request.Builder().url(url).build()
                                            OkHttpClient().newCall(request).execute().use { response ->
                                                response.body.string()
                                            }
                                        } catch (e: Exception) {
                                            null
                                        }

                                        withContext(Dispatchers.Main) {
                                            binding.loading.isVisible = false
                                            // Pass svgXml — Fragment will cache it to Room
                                            onImageSelected(null, resource, svgXml, image)
                                        }
                                    }
                                }
                                override fun onLoadCleared(placeholder: Drawable?) {}
                            }

                            Glide.with(binding.root.context)
                                .`as`(PictureDrawable::class.java)
                                .load(url)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .into(glideTarget)

                        } else {
                            Glide.with(binding.root.context).asBitmap().load(url)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(
                                        bitmap: Bitmap,
                                        transition: Transition<in Bitmap>?
                                    ) {
                                        onImageSelected(bitmap, null, null, image)
                                    }
                                    override fun onLoadCleared(placeholder: Drawable?) {}
                                })
                        }
                    }
                }

            val isPng = image.file_name.endsWith(".png", ignoreCase = true) || image.file_name.endsWith(".svg", ignoreCase = true)
            binding.image.scaleType = if (isPng) android.widget.ImageView.ScaleType.FIT_CENTER else android.widget.ImageView.ScaleType.CENTER_CROP

            if (image.bitmapData != null) {
                if (image.file_name.endsWith(".svg", ignoreCase = true)) {
                    // bitmapData is raw SVG XML — render it directly, no Glide
                    try {
                        val svg = com.caverock.androidsvg.SVG.getFromString(image.bitmapData)
                        val drawable = PictureDrawable(svg.renderToPicture())
                        binding.image.setImageDrawable(drawable)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to render cached SVG: ${e.message}")
                    }
                } else {
                    // bitmapData is a file path — load normally with Glide
                    Glide.with(itemView.context).load(image.bitmapData).into(binding.image)
                }

                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.setShimmer(null)

            } else {
                if (url.endsWith(".svg", true)) {

                    Glide.with(binding.root.context).`as`(PictureDrawable::class.java).load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(object : RequestListener<PictureDrawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<PictureDrawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.stopShimmer()
                                binding.shimmerLayout.setShimmer(null)
                                return true
                            }

                            override fun onResourceReady(
                                resource: PictureDrawable,
                                model: Any,
                                target: Target<PictureDrawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.stopShimmer()
                                binding.shimmerLayout.setShimmer(null)
                                return false
                            }
                        }).into(binding.image)

                } else {

                    Glide.with(binding.root.context).load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL).thumbnail(0.1f)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.stopShimmer()
                                binding.shimmerLayout.setShimmer(null)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.stopShimmer()
                                binding.shimmerLayout.setShimmer(null)
                                return false
                            }
                        }).into(binding.image)
                }
            }
        }
    }
}
