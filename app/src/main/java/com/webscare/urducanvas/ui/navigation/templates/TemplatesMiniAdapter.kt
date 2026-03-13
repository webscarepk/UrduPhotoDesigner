package com.webscare.urducanvas.ui.navigation.templates

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
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.LayoutTemplateCategoryBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class TemplatesMiniAdapter(
    private val onClick: (com.webscare.urducanvas.data.model.TemplateEntity, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.webscare.urducanvas.data.model.TemplateEntity, TemplatesMiniAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    inner class VH(val binding: LayoutTemplateCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: com.webscare.urducanvas.data.model.TemplateEntity) {


            binding.isPremium.isVisible = item.is_premium && !item.is_subscribed
            // CLICK
            binding.root.addPressEffect {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val freshItem = currentList[pos]
                    onClick(freshItem, freshItem.is_downloaded)
                }
            }

            // PROGRESS UI FROM ENTITY (truth)
            val downloading = item.is_downloading && !item.is_downloaded

            binding.download.visibility =
                if (downloading || item.is_downloaded) View.GONE else View.VISIBLE

            binding.loading.visibility = if (downloading) View.VISIBLE else View.GONE
//            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE

//            if (downloading) {
//                val p = item.download_progress.coerceIn(0, 100)
//                binding.percentage.text = "$p%"
//            } else {
//                binding.percentage.text = ""
//            }

            // IMAGE
            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url

            Glide.with(binding.root.context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .thumbnail(0.1f)
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.shimmerLayout.hideShimmer()
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: Target<android.graphics.drawable.Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.shimmerLayout.hideShimmer()
                        return false
                    }
                })
                .into(binding.image)
        }

        fun renderProgressFromState(ui: com.webscare.urducanvas.data.model.ProgressUi?) {
            if (ui == null) return

            if (ui.isDownloaded) {
                binding.loading.isVisible = false
//                binding.progressBox.isVisible = false
                binding.download.isVisible = false
                return
            }

            val isBusy = ui.isDownloading
            binding.loading.isVisible = isBusy
//            binding.progressBox.isVisible = isBusy
            binding.download.isVisible = !isBusy

//            if (isBusy) {
//                val p = (ui.progress ?: 0).coerceIn(0, 100)
//                binding.percentage.text = "$p%"
//            }
        }
    }

    override fun onBindViewHolder(h: VH, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val state = payloads.firstOrNull() as? com.webscare.urducanvas.data.model.ProgressUi
            if (state != null) {
                h.renderProgressFromState(state)
            }
        } else {
            super.onBindViewHolder(h, pos, payloads)
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutTemplateCategoryBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    fun updateProgress(templateId: Int, state: com.webscare.urducanvas.data.model.ProgressUi) {
        val idx = currentList.indexOfFirst { it.id == templateId }
        if (idx != -1) notifyItemChanged(idx, state)
    }

    fun updateItem(updated: com.webscare.urducanvas.data.model.TemplateEntity) {
        val newList = currentList.toMutableList()
        val idx = newList.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            newList[idx] = updated
            submitList(newList){
                notifyItemChanged(idx)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<com.webscare.urducanvas.data.model.TemplateEntity>() {
        override fun areItemsTheSame(o: com.webscare.urducanvas.data.model.TemplateEntity, n: com.webscare.urducanvas.data.model.TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: com.webscare.urducanvas.data.model.TemplateEntity, n: com.webscare.urducanvas.data.model.TemplateEntity): Boolean {
            return o.is_downloaded == n.is_downloaded &&
                    o.is_downloading == n.is_downloading &&
                    o.download_progress == n.download_progress
        }
    }
}
