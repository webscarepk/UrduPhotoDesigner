package com.example.urduphotodesigner.ui.navigation.fonts

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.databinding.LayoutFontsGridBinding
import com.example.urduphotodesigner.databinding.LayoutFontsRowBinding

class PopularFontsAdapter(
    private val onFontClick: (FontEntity, Boolean) -> Unit,
    private val onDownload: (FontEntity) -> Unit,
    private var isGrid: Boolean = true
) : ListAdapter<FontEntity, RecyclerView.ViewHolder>(Diff()) {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    private val progressById = mutableMapOf<Int, ProgressUi>()

    fun updateProgress(fontId: Int, ui: ProgressUi) {
        val prev = progressById[fontId]
        if (prev == ui) return
        progressById[fontId] = ui
        val pos = currentList.indexOfFirst { it.id == fontId }
        if (pos != -1) notifyItemChanged(pos, ui)
    }

    fun toggleViewType(isGrid: Boolean) {
        this.isGrid = isGrid
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = LayoutFontsGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridVH(binding, onFontClick, onDownload)
        } else {
            val binding = LayoutFontsRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ListVH(binding, onFontClick, onDownload)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val progress = progressById[item.id] ?: ProgressUi(
            progress = item.download_progress,
            isDownloading = item.is_downloading,
            isDownloaded = item.is_downloaded
        )
        when (holder) {
            is GridVH -> holder.bind(item, progress)
            is ListVH -> holder.bind(item, progress)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            (payloads.lastOrNull() as? ProgressUi)?.let {
                when (holder) {
                    is GridVH -> holder.applyProgress(it)
                    is ListVH -> holder.applyProgress(it)
                }
            }
        } else super.onBindViewHolder(holder, position, payloads)
    }

    // ---------------- Grid ViewHolder ----------------
    inner class GridVH(
        private val binding: LayoutFontsGridBinding,
        private val onFontClick: (FontEntity, Boolean) -> Unit,
        private val onDownload: (FontEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FontEntity, progress: ProgressUi) {
            loadImage(item)

            binding.isPremium.isVisible = item.is_premium
            binding.assetName.text = item.font_name
            binding.metaData.text = "${formatSize(item.file_size)}"
            binding.root.addPressEffect { onFontClick(item, item.is_downloaded) }
            binding.download.addPressEffect { onDownload(item) }
            applyProgress(progress)
        }

        private fun loadImage(item: FontEntity) {
            if (item.image_url.isEmpty() && item.font_image?.isNotEmpty() == true) {
                Glide.with(itemView.context).load(item.font_image).into(binding.image)
                binding.shimmerLayout.hideShimmer()
            } else {
                val url = Constants.BASE_URL_GLIDE + item.image_url
                if (url.endsWith(".svg", true)) {
                    Glide.with(itemView.context)
                        .`as`(PictureDrawable::class.java)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(svgListener())
                        .into(binding.image)
                } else {
                    Glide.with(itemView.context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .thumbnail(0.1f)
                        .listener(drawableListener())
                        .into(binding.image)
                }
            }
        }

        private fun svgListener() = object : RequestListener<PictureDrawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<PictureDrawable>, isFirstResource: Boolean
            ) = false.also { binding.shimmerLayout.hideShimmer() }

            override fun onResourceReady(
                resource: PictureDrawable, model: Any, target: Target<PictureDrawable>?, dataSource: DataSource, isFirstResource: Boolean
            ) = false.also { binding.shimmerLayout.hideShimmer() }
        }

        private fun drawableListener() = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean { binding.shimmerLayout.hideShimmer(); return false }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
            ): Boolean { binding.shimmerLayout.hideShimmer(); return false }
        }

        fun applyProgress(ui: ProgressUi) {
            val completed = ui.progress >= 100 || ui.isDownloaded
            val downloading = ui.isDownloading && !completed
            binding.download.visibility = if (downloading || completed) View.GONE else View.VISIBLE
            binding.loading.visibility = if (downloading) View.VISIBLE else View.GONE
//            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE

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

    // ---------------- List ViewHolder ----------------
    inner class ListVH(
        private val binding: LayoutFontsRowBinding,
        private val onFontClick: (FontEntity, Boolean) -> Unit,
        private val onDownload: (FontEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FontEntity, progress: ProgressUi) {
            loadImage(item)

            binding.isPremium.isVisible = item.is_premium
            binding.assetName.text = item.font_name
            binding.metaData.text = "${formatSize(item.file_size)}"
            binding.root.addPressEffect { onFontClick(item, item.is_downloaded) }
            binding.download.addPressEffect { onDownload(item) }
            applyProgress(progress)
        }

        private fun svgListener() = object : RequestListener<PictureDrawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<PictureDrawable>, isFirstResource: Boolean
            ) = false.also { binding.shimmerLayout.hideShimmer() }

            override fun onResourceReady(
                resource: PictureDrawable, model: Any, target: Target<PictureDrawable>?, dataSource: DataSource, isFirstResource: Boolean
            ) = false.also { binding.shimmerLayout.hideShimmer() }
        }

        private fun drawableListener() = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean { binding.shimmerLayout.hideShimmer(); return false }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
            ): Boolean { binding.shimmerLayout.hideShimmer(); return false }
        }

        private fun loadImage(item: FontEntity) {
            if (item.image_url.isEmpty() && item.font_image?.isNotEmpty() == true) {
                Glide.with(itemView.context).load(item.font_image).into(binding.image)
                binding.shimmerLayout.hideShimmer()
            } else {
                val url = Constants.BASE_URL_GLIDE + item.image_url
                if (url.endsWith(".svg", true)) {
                    Glide.with(itemView.context)
                        .`as`(PictureDrawable::class.java)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(svgListener())
                        .into(binding.image)
                } else {
                    Glide.with(itemView.context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .thumbnail(0.1f)
                        .listener(drawableListener())
                        .into(binding.image)
                }
            }
        }

        fun applyProgress(ui: ProgressUi) {
            val completed = ui.progress >= 100 || ui.isDownloaded
            val downloading = ui.isDownloading && !completed
            binding.download.visibility = if (downloading || completed) View.GONE else View.VISIBLE
            binding.loading.visibility = if (downloading) View.VISIBLE else View.GONE
//            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE

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

    private fun formatSize(size: Any?): String {
        if (size == null) return ""
        val bytes = when (size) {
            is String -> size.toLongOrNull() ?: return size
            is Int -> size.toLong()
            is Long -> size
            is Float -> (size * 1024 * 1024).toLong()
            is Double -> (size * 1024 * 1024).toLong()
            else -> return size.toString()
        }
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }

    private class Diff : DiffUtil.ItemCallback<FontEntity>() {
        override fun areItemsTheSame(o: FontEntity, n: FontEntity) = o.id == n.id
        override fun areContentsTheSame(o: FontEntity, n: FontEntity) = o == n
    }
}
