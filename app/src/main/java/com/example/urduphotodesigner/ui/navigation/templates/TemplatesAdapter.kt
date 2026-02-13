package com.example.urduphotodesigner.ui.navigation.templates

import android.graphics.Bitmap
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
        holder.bind(getItem(position))
    }

    private fun onItemClick(item: TemplateEntity) {
        onTemplateSelected(item, item.is_downloaded)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val state = payloads.firstOrNull() as? ProgressUi
            if (state != null) {
                holder.applyProgress(state)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun updateProgress(templateId: Int, state: ProgressUi) {
        val idx = currentList.indexOfFirst { it.id == templateId }
        if (idx != -1) {
            notifyItemChanged(idx, state)
        }
    }

    class VH(
        private val binding: LayoutTemplateItemBinding,
        private val onClick: (TemplateEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateEntity) {
            binding.template.adjustViewBounds = true
            binding.isPremium.isVisible = item.is_premium

            binding.download.visibility = View.GONE
            binding.download.addPressEffect { onClick(item) }
            binding.fontCard.addPressEffect { onClick(item) }

            // Load Image
            val downloading = item.is_downloading && !item.is_downloaded

            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url
            if (url.isNotEmpty()) {
                binding.shimmerLayout.startShimmer()
                binding.shimmerLayout.isVisible = true

                Glide.with(binding.root.context).asBitmap().load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL).thumbnail(0.1f)
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?, m: Any?, t: Target<Bitmap>, isFirst: Boolean
                        ): Boolean {
                            binding.shimmerLayout.hideShimmer()
                            return false
                        }

                        override fun onResourceReady(
                            res: Bitmap, m: Any, t: Target<Bitmap>?, d: DataSource, isFirst: Boolean
                        ): Boolean {
                            binding.shimmerLayout.hideShimmer()

                            if (!item.is_downloaded && !downloading) {
                                binding.download.visibility = View.VISIBLE
                            }
                            return false
                        }
                    }).into(binding.template)

                binding.shimmerLayout.startShimmer()

                val currentState = ProgressUi(
                    progress = item.download_progress,
                    isDownloading = item.is_downloading,
                    isDownloaded = item.is_downloaded
                )
                applyProgress(currentState)
            }
        }

        // --- Exact Logic from Popular Adapter ---
        fun applyProgress(state: ProgressUi) {
            binding.apply {
                val downloading = state.isDownloading && !state.isDownloaded

                progressBox.isVisible = downloading

                if (downloading) {
                    val p = state.progress.coerceIn(0, 100)
                    progressBar.progress = p
                    percentage.text = "$p%"
                } else {
                    progressBar.progress = 0
                    percentage.text = ""
                }
            }
        }
    }

    private class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id

        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity): Boolean {
            return o.id == n.id &&
                    o.is_downloaded == n.is_downloaded &&
                    o.is_downloading == n.is_downloading &&
                    o.download_progress == n.download_progress
        }

        // Payload generator to prevent full rebind
        override fun getChangePayload(oldItem: TemplateEntity, newItem: TemplateEntity): Any? {
            if (oldItem.download_progress != newItem.download_progress ||
                oldItem.is_downloading != newItem.is_downloading ||
                oldItem.is_downloaded != newItem.is_downloaded) {
                return ProgressUi(newItem.download_progress, newItem.is_downloading, newItem.is_downloaded)
            }
            return super.getChangePayload(oldItem, newItem)
        }
    }
}