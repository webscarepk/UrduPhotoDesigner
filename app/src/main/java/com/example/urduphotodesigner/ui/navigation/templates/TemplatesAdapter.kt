package com.example.urduphotodesigner.ui.navigation.templates

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutTemplateItemBinding
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class TemplatesAdapter(
    private val onTemplateSelected: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<TemplateEntity, TemplatesAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    data class ProgressUi(val progress: Int, val isDownloading: Boolean, val isDownloaded: Boolean)
    private val dimsByUrl = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val progressById = mutableMapOf<Int, ProgressUi>()

    fun updateProgress(templateId: Int, ui: ProgressUi) {
        val prev = progressById[templateId]
        if (prev == ui) return
        progressById[templateId] = ui
        val pos = currentList.indexOfFirst { it.id == templateId }
        if (pos != -1) notifyItemChanged(pos, ui)
    }

    override fun getItemId(position: Int) = getItem(position).id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutTemplateItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.template.apply {
            adjustViewBounds = true
        }

        return VH(
            binding = binding,
            onClick = ::onItemClick,
            dimsByUrl
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(
            item = item,
            progress = progressById[item.id] ?: ProgressUi(
                progress = item.download_progress,
                isDownloading = item.is_downloading,
                isDownloaded = item.is_downloaded
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val last = payloads.last()
            when (last) {
                is ProgressUi -> holder.applyProgress(last)
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)

        // Optional: wide "banner" images span both columns for nicer composition.
        val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
        val item = getItem(pos)
        val url = item.thumbnail_url ?: return
        val dims = dimsByUrl[url] ?: return
        val isBanner = dims.first >= (dims.second * 1.7) // width ≥ 1.7 * height
        val lp = holder.itemView.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            lp.isFullSpan = isBanner
            holder.itemView.layoutParams = lp
        }
    }

    private fun onItemClick(item: TemplateEntity) {
        onTemplateSelected(item, item.is_downloaded)
    }

    class VH(
        private val binding: LayoutTemplateItemBinding,
        private val onClick: (TemplateEntity) -> Unit,
        private val dimsByUrl: ConcurrentHashMap<String, Pair<Int, Int>>
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateEntity, progress: ProgressUi) {
            itemView.addPressEffect {
                onClick(item)
            }
            // 2) Progress UI
            applyProgress(progress)

            val url = item.thumbnail_url.orEmpty()
            val cachedDims = dimsByUrl[url]
            applyDimensionRatio(cachedDims)

            // 3) Load bitmap to learn real ratio exactly once per URL.
            if (url.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .asBitmap()
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .thumbnail(0.1f)
                    .dontTransform()
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.shimmerLayout.hideShimmer()
                            return false
                        }

                        override fun onResourceReady(
                            res: Bitmap,
                            model: Any,
                            target: Target<Bitmap>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            binding.shimmerLayout.hideShimmer()
                            val w = max(1, res.width)
                            val h = max(1, res.height)
                            if (!dimsByUrl.containsKey(url)) {
                                dimsByUrl[url] = w to h
                                applyDimensionRatio(w to h)

                                // Ask SGLM to rebalance spans after this item’s height is known.
                                (itemView.parent as? RecyclerView)?.post {
                                    ((itemView.parent as? RecyclerView)?.layoutManager as?
                                            StaggeredGridLayoutManager)?.invalidateSpanAssignments()
                                }
                            }
                            return false
                        }
                    })
                    .into(binding.template)

                binding.shimmerLayout.startShimmer()
            }
        }

        fun applyProgress(ui: ProgressUi) {
            val downloading = ui.isDownloading && !ui.isDownloaded
            binding.download.visibility =
                if (downloading || ui.isDownloaded) View.GONE else View.VISIBLE
            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.percentage.visibility = if (downloading) View.VISIBLE else View.GONE

            if (downloading) {
                val pct = ui.progress.coerceIn(0, 100)
                binding.progressBar.progress = pct
                binding.percentage.text = "$pct%"
            } else {
                binding.progressBar.progress = 0
                binding.percentage.text = ""
            }
        }

        private fun applyDimensionRatio(dims: Pair<Int, Int>?) {
            val lp = binding.template.layoutParams as? ConstraintLayout.LayoutParams ?: return
            lp.dimensionRatio = when (dims) {
                null -> "1:1" // safe fallback, avoids massive relayout when first loading
                else -> "${dims.first}:${dims.second}"
            }
            binding.template.layoutParams = lp
        }
    }

    private class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
