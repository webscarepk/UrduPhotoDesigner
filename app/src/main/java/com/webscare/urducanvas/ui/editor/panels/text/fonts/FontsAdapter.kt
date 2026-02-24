package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.databinding.LayoutFontItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class FontsAdapter(
    private val onFontSelected: (com.webscare.urducanvas.data.model.FontEntity, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.webscare.urducanvas.data.model.FontEntity, FontsAdapter.FontViewHolder>(DiffCallback()) {

    var selectedFontId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                old?.let { oldId ->
                    val oldPos = currentList.indexOfFirst { it.id.toString() == oldId }
                    if (oldPos != -1) notifyItemChanged(oldPos)
                }
                value?.let { newId ->
                    val newPos = currentList.indexOfFirst { it.id.toString() == newId }
                    if (newPos != -1) notifyItemChanged(newPos)
                }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding =
            LayoutFontItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FontViewHolder(private val binding: LayoutFontItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(font: com.webscare.urducanvas.data.model.FontEntity) {
            val isSelected = font.id.toString() == selectedFontId

            binding.shimmerLayout.startShimmer()
            binding.root.strokeWidth = if (isSelected) 2 else 0
            binding.root.strokeColor = ContextCompat.getColor(
                binding.root.context,
                R.color.appColor
            )

            binding.isPremium.isVisible = font.is_premium
            // Download UI
            binding.download.visibility =
                if (font.is_downloaded || font.is_downloading) View.GONE else View.VISIBLE

            binding.loading.visibility = if (font.is_downloading) View.VISIBLE else View.GONE
//            binding.progressBar.visibility = if (font.is_downloading) View.VISIBLE else View.GONE

            binding.root.addPressEffect {
                selectedFontId = font.id.toString()
                if (font.is_downloaded) {
                    onFontSelected(font, true)
                } else {
                    onFontSelected(font, false)
                }
            }

            // Check if the font_image is not empty and image_url is empty
            if (font.image_url.isEmpty()) {
                if (font.font_image?.isNotEmpty() == true){
                    Glide.with(itemView.context)
                        .load(font.font_image)
                        .into(binding.font)
                    binding.shimmerLayout.hideShimmer()
                }else{
                    binding.font.setImageResource(R.drawable.ic_font_thumbnail)
                    binding.shimmerLayout.hideShimmer()
                }
            } else {
                // Load font preview using Glide (from image_url)
                val url = Constants.BASE_URL_GLIDE + font.image_url
                if (isSvgUrl(url)) {
                    Glide.with(binding.root.context)
                        .`as`(PictureDrawable::class.java)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(object : RequestListener<PictureDrawable> {
                            override fun onLoadFailed(
                                e: GlideException?, model: Any?, target: Target<PictureDrawable>, isFirstResource: Boolean
                            ) = false.also { binding.shimmerLayout.hideShimmer() }

                            override fun onResourceReady(
                                resource: PictureDrawable,
                                model: Any,
                                target: Target<PictureDrawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ) = false.also { binding.shimmerLayout.hideShimmer() }
                        })
                        .into(binding.font)

                }else{
                    Glide.with(binding.root.context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .thumbnail(0.1f)
                        .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.hideShimmer()
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable, model: Any,
                                target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                binding.shimmerLayout.hideShimmer()
                                return false
                            }
                        })
                        .into(binding.font)
                }
            }
        }
    }

    private fun isSvgUrl(raw: String): Boolean {
        val q = raw.substringBefore('#').substringBefore('?').lowercase()
        return q.endsWith(".svg")
    }

    class DiffCallback : DiffUtil.ItemCallback<com.webscare.urducanvas.data.model.FontEntity>() {
        override fun areItemsTheSame(oldItem: com.webscare.urducanvas.data.model.FontEntity, newItem: com.webscare.urducanvas.data.model.FontEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: com.webscare.urducanvas.data.model.FontEntity, newItem: com.webscare.urducanvas.data.model.FontEntity) =
            oldItem == newItem
    }
}