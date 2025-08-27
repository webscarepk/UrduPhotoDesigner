package com.example.urduphotodesigner.ui.navigation.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutTemplatePopularBinding

class PopularTemplatesAdapter(
    private val onClick: (TemplateEntity, Boolean) -> Unit,
    private val progressProvider: (Int) -> ProgressUi?
) : ListAdapter<TemplateEntity, PopularTemplatesAdapter.VH>(Diff()) {

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

    inner class VH(val binding: LayoutTemplatePopularBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TemplateEntity, progress: ProgressUi) {
            binding.shimmerLayout.startShimmer()

            binding.root.strokeColor =
                ContextCompat.getColor(binding.root.context, R.color.appColor)

            applyProgress(progress)

            binding.root.addPressEffect {
                val state = progressProvider(item.id)
                val isDownloaded = state?.isDownloaded == true || item.is_downloaded
                onClick(item, isDownloaded)
            }

            // Thumbnail
            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url

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
                        binding.shimmerLayout.hideShimmer()
                        return false
                    }
                })
                .into(binding.image)
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

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutTemplatePopularBinding.inflate(LayoutInflater.from(p.context), p, false))

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

    override fun getItemId(position: Int) = getItem(position).id.toLong()

    class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
