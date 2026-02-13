package com.example.urduphotodesigner.ui.navigation.templates

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
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutTemplateCategoryBinding

class TemplatesMiniAdapter(
    private val onClick: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<TemplateEntity, TemplatesMiniAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    inner class VH(val binding: LayoutTemplateCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TemplateEntity) {


            binding.isPremium.isVisible = item.is_premium
            // CLICK
            binding.root.addPressEffect {
                onClick(item, item.is_downloaded)
            }

            // PROGRESS UI FROM ENTITY (truth)
            val downloading = item.is_downloading && !item.is_downloaded

            binding.download.visibility =
                if (downloading || item.is_downloaded) View.GONE else View.VISIBLE

            binding.progressBox.visibility =
                if (downloading) View.VISIBLE else View.GONE

            if (downloading) {
                val p = item.download_progress.coerceIn(0, 100)
                binding.percentage.text = "$p%"
            } else {
                binding.percentage.text = ""
            }

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

        fun renderProgressFromState(ui: ProgressUi?) {
            val downloading = ui?.isDownloading == true && !ui.isDownloaded

            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.download.visibility =
                if (downloading || ui?.isDownloaded == true) View.GONE else View.VISIBLE

            if (downloading) {
                val p = (ui.progress ?: 0).coerceIn(0, 100)
                binding.percentage.text = "$p%"
            }
        }
    }

    override fun onBindViewHolder(h: VH, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val state = payloads.firstOrNull() as? ProgressUi
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

    fun updateProgress(templateId: Int, state: ProgressUi) {
        val idx = currentList.indexOfFirst { it.id == templateId }
        if (idx != -1) notifyItemChanged(idx, state)
    }

    fun updateItem(updated: TemplateEntity) {
        val newList = currentList.toMutableList()
        val idx = newList.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            newList[idx] = updated
            submitList(newList)
        }
    }

    class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
