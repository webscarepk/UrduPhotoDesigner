package com.webscare.urducanvas.ui.navigation.templates

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.LayoutTemplateItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class TemplatesAdapter(
    private val onTemplateSelected: (com.webscare.urducanvas.data.model.TemplateEntity, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.webscare.urducanvas.data.model.TemplateEntity, TemplatesAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = getItem(position).id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutTemplateItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(
            binding = binding,
            onClick = ::onItemClick,
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    private fun onItemClick(item: com.webscare.urducanvas.data.model.TemplateEntity) {
        onTemplateSelected(item, item.is_downloaded)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val state = payloads.firstOrNull() as? com.webscare.urducanvas.data.model.ProgressUi
            if (state != null) {
                holder.applyProgress(state)

                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun updateProgress(templateId: Int, state: com.webscare.urducanvas.data.model.ProgressUi) {
        val idx = currentList.indexOfFirst { it.id == templateId }
        if (idx != -1) {
            notifyItemChanged(idx, state)
        }
    }

    class VH(
        private val binding: LayoutTemplateItemBinding,
        private val onClick: (com.webscare.urducanvas.data.model.TemplateEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: com.webscare.urducanvas.data.model.TemplateEntity) {

            val width = item.canvas_width
            val height = item.canvas_height

            if (width > 0 && height > 0) {
                val ratio = "$width:$height"

                val params = binding.imageContainer.layoutParams as ConstraintLayout.LayoutParams
                params.dimensionRatio = ratio
                binding.imageContainer.layoutParams = params
            }

            binding.isPremium.isVisible = item.is_premium && !item.is_subscribed

            binding.download.addPressEffect {
                val currentItem = (bindingAdapter as TemplatesAdapter)
                    .currentList[bindingAdapterPosition]
                onClick(currentItem)
            }

            binding.fontCard.addPressEffect {
                val currentItem = (bindingAdapter as TemplatesAdapter)
                    .currentList[bindingAdapterPosition]
                onClick(currentItem)
            }

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
                            binding.download.isVisible = !item.is_downloaded && !item.is_downloading
                            binding.loading.isVisible = item.is_downloading && !item.is_downloaded
                            return false
                        }
                    }).into(binding.template)

                binding.shimmerLayout.startShimmer()
            }
        }

        fun applyProgress(state: com.webscare.urducanvas.data.model.ProgressUi) {
            binding.apply {
                val downloading = state.isDownloading && !state.isDownloaded

                loading.isVisible = downloading
                binding.download.isVisible = !(downloading || state.isDownloaded)
            }
        }
    }

    private class Diff : DiffUtil.ItemCallback<com.webscare.urducanvas.data.model.TemplateEntity>() {
        override fun areItemsTheSame(o: com.webscare.urducanvas.data.model.TemplateEntity, n: com.webscare.urducanvas.data.model.TemplateEntity) = o.id == n.id

        override fun areContentsTheSame(o: com.webscare.urducanvas.data.model.TemplateEntity, n: com.webscare.urducanvas.data.model.TemplateEntity): Boolean {
            return o.id == n.id &&
                    o.is_downloaded == n.is_downloaded &&
                    o.is_downloading == n.is_downloading &&
                    o.download_progress == n.download_progress
        }

        // Payload generator to prevent full rebind
        override fun getChangePayload(oldItem: com.webscare.urducanvas.data.model.TemplateEntity, newItem: com.webscare.urducanvas.data.model.TemplateEntity): Any? {
            if (oldItem.download_progress != newItem.download_progress ||
                oldItem.is_downloading != newItem.is_downloading ||
                oldItem.is_downloaded != newItem.is_downloaded) {
                return _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
                    newItem.download_progress,
                    newItem.is_downloading,
                    newItem.is_downloaded
                )
            }
            return super.getChangePayload(oldItem, newItem)
        }
    }
}