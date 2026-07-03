package com.webscare.urducanvas.ui.editor.export

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.startShimmerSoft
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.PremiumAssetItem
import com.webscare.urducanvas.databinding.ItemPremiumAssetBinding

class PremiumAssetsAdapter(
    private val items: List<PremiumAssetItem>,
    private val localFonts: List<FontEntity> // your existing font list
) : RecyclerView.Adapter<PremiumAssetsAdapter.VH>() {

    class VH(val binding: ItemPremiumAssetBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            ItemPremiumAssetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val isDarkMode = context.isDarkModeEnabled()
        if (isDarkMode && (item.type == ElementType.TEXT || item.applyWhiteTintInDarkMode)) {
            holder.binding.image.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            holder.binding.image.clearColorFilter()
        }

        holder.binding.shimmerLayout.startShimmerSoft(isDarkMode)

        when (item.type) {

            // ✅ TEXT — load font thumbnail
            ElementType.TEXT -> {
                val font = localFonts.find { it.id.toString() == item.fontId }

                when {
                    font?.image_url?.isNotEmpty() == true -> {
                        val url = Constants.BASE_URL_GLIDE + font.image_url
                        if (isSvgUrl(url)) {
                            Glide.with(context)
                                .`as`(PictureDrawable::class.java)
                                .load(url)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .listener(object : RequestListener<PictureDrawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<PictureDrawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        holder.binding.shimmerLayout.hideShimmer()
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: PictureDrawable,
                                        model: Any,
                                        target: Target<PictureDrawable?>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        holder.binding.shimmerLayout.hideShimmer()
                                        return false
                                    }
                                })
                                .into(holder.binding.image)
                        } else {
                            Glide.with(context)
                                .load(url)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .thumbnail(0.1f)
                                .listener(object : RequestListener<Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<Drawable?>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        holder.binding.shimmerLayout.hideShimmer()
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: Drawable,
                                        model: Any,
                                        target: Target<Drawable?>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        holder.binding.shimmerLayout.hideShimmer()
                                        return false
                                    }
                                })
                                .into(holder.binding.image)
                        }
                    }

                    // Fallback
                    else -> {
                        holder.binding.image.setImageResource(R.drawable.ic_font_thumbnail)
                        holder.binding.shimmerLayout.hideShimmer()
                    }
                }
            }

            // ✅ IMAGE / STICKER — load base64
            else -> {
                if (!item.bitmapData.isNullOrEmpty()) {
                    holder.binding.image.setImageBitmap(ImageProcessor.base64ToBitmap(item.bitmapData))
                    holder.binding.shimmerLayout.hideShimmer()
                } else {
                    holder.binding.image.setImageResource(R.drawable.ic_images)
                    holder.binding.shimmerLayout.hideShimmer()
                }
            }
        }
    }

    private fun isSvgUrl(url: String) = url.endsWith(".svg", ignoreCase = true)
}