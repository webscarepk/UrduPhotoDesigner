package com.webscare.urducanvas.ui.navigation.home

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.databinding.LayoutPopularFontItemBinding

class FontsAdapter(
    private val onFontClick: (FontEntity, Boolean) -> Unit,
    private val onDownload: (FontEntity) -> Unit
) : androidx.recyclerview.widget.ListAdapter<FontEntity, FontsAdapter.VH>(
    Diff()
) {

    private val progressById = mutableMapOf<Int, ProgressUi>()

    fun updateProgress(fontId: Int, ui: ProgressUi) {
        val prev = progressById[fontId]
        if (prev == ui) return
        progressById[fontId] = ui
        val pos = currentList.indexOfFirst { it.id == fontId }
        if (pos != -1) notifyItemChanged(pos, ui)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutPopularFontItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onFontClick, onDownload)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(
            item, progressById[item.id] ?: ProgressUi(
                progress = item.download_progress,
                isDownloading = item.is_downloading,
                isDownloaded = item.is_downloaded
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            (payloads.lastOrNull() as? ProgressUi)?.let {
                holder.applyProgress(
                    it
                )
            }
        } else super.onBindViewHolder(holder, position, payloads)
    }

    class VH(
        private val binding: LayoutPopularFontItemBinding,
        private val onFontClick: (FontEntity, Boolean) -> Unit,
        private val onDownload: (FontEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: FontEntity,
            progress: ProgressUi
        ) {
            val isDarkMode = itemView.context.isDarkModeEnabled()
            if (isDarkMode) {
                binding.image.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                binding.image.clearColorFilter()
            }
            // Preview (if you have thumbnail url or local file)
            if (item.image_url.isEmpty() && item.font_image?.isNotEmpty() == true) {
                // Parse font_image from Base64 to Bitmap
                Glide.with(itemView.context).load(item.font_image)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            if (itemView.context.isDarkModeEnabled()) {
                                binding.image.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                            }
                            return false
                        }
                    })
                    .into(binding.image)
                binding.shimmerLayout.hideShimmer()
            } else {
                // Load font preview using Glide (from image_url)
                val url =
                    Constants.BASE_URL_GLIDE + item.image_url
                if (isSvgUrl(url)) {
                    com.webscare.urducanvas.common.utils.SvgLoader.load(
                        url = url,
                        imageView = binding.image,
                        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                        cachedXml = null,
                        maxPx = 512,
                        applyWhiteTint = isDarkMode
                    ) { _, _ ->
                        binding.shimmerLayout.hideShimmer()
                    }
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
                                android.util.Log.e("FontsAdapter", "Image load failed | url: $model | error: ${e?.message}")
                                e?.logRootCauses("FontsAdapter_RootCause")
                                binding.shimmerLayout.hideShimmer()
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                if (binding.root.context.isDarkModeEnabled()) {
                                    binding.image.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                                }
                                binding.shimmerLayout.hideShimmer()
                                return false
                            }
                        }).into(binding.image)
                }
            }
            // Card click → use font (if downloaded)
            binding.root.addPressEffect {
                onFontClick(item, item.is_downloaded)
            }

            // Download button → trigger font download
            binding.download.addPressEffect { onDownload(item) }

            binding.isPremium.isVisible = item.is_premium && !item.is_subscribed
            applyProgress(progress)
        }

        private fun isSvgUrl(raw: String): Boolean {
            val q = raw.substringBefore('#').substringBefore('?').lowercase()
            return q.endsWith(".svg")
        }

        fun applyProgress(ui: ProgressUi) {
            val completed = ui.progress >= 100 || ui.isDownloaded
            val downloading = ui.isDownloading && !completed

            binding.download.visibility = if (downloading || completed) View.GONE else View.VISIBLE
//            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.loading.visibility = if (downloading) View.VISIBLE else View.GONE

//            if (downloading) {
//                val pct = ui.progress.coerceIn(0, 100)
//                binding.progressBar.progress = pct
//                binding.percentage.text = "$pct%"
//            } else {
//                binding.progressBar.progress = 0
//                binding.percentage.text = ""
//            }
        }
    }

    private class Diff : DiffUtil.ItemCallback<FontEntity>() {
        override fun areItemsTheSame(o: FontEntity, n: FontEntity) = o.id == n.id
        override fun areContentsTheSame(o: FontEntity, n: FontEntity) = o == n
    }
}
