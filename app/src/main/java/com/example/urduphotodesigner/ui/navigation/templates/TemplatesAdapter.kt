package com.example.urduphotodesigner.ui.navigation.templates

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutTemplateItemBinding

class TemplatesAdapter(
    private val onTemplateSelected: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<TemplateEntity, TemplatesAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

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

    private fun onItemClick(item: TemplateEntity) {
        onTemplateSelected(item, item.is_downloaded)
    }

    class VH(
        private val binding: LayoutTemplateItemBinding,
        private val onClick: (TemplateEntity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateEntity, progress: ProgressUi) {
            binding.download.addPressEffect {
                onClick(item)
            }

            applyProgress(progress)
            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url

            if (url.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .asBitmap()
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .thumbnail(0.1f)
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
    }

    private class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
