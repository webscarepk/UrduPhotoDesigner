package com.example.urduphotodesigner.ui.navigation.templates

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
import com.example.urduphotodesigner.databinding.LayoutTemplateCategoryBinding

class TemplatesMiniAdapter(
    private val onClick: (TemplateEntity, Boolean) -> Unit,
    private val progressProvider: (Int) -> ProgressUi?
) : ListAdapter<TemplateEntity, TemplatesMiniAdapter.VH>(Diff()) {

    inner class VH(val binding: LayoutTemplateCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TemplateEntity) {
            binding.shimmerLayout.startShimmer()

            binding.root.strokeColor =
                ContextCompat.getColor(binding.root.context, R.color.appColor)

            renderProgressFor(item.id)

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

        fun renderProgressFor(templateId: Int, fallback: TemplateEntity? = null) {
            val st = progressProvider(templateId)

            val isDownloaded = st?.isDownloaded == true ||
                    fallback?.is_downloaded == true ||
                    currentList.find { it.id == templateId }?.is_downloaded == true

            val downloading = when {
                st != null -> st.isDownloading && !isDownloaded
                fallback != null -> fallback.is_downloading && !isDownloaded
                else -> false
            }

            binding.download.visibility    = if (downloading || isDownloaded) View.GONE else View.VISIBLE
            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.percentage.visibility  = if (downloading) View.VISIBLE else View.GONE

            if (downloading) {
                val p = (st?.progress ?: fallback?.download_progress ?: 0).coerceIn(0, 100)
                binding.progressBar.progress = p
                binding.percentage.text = "$p%"
            } else {
                binding.progressBar.progress = 0
                binding.percentage.text = ""
            }
        }

    }

    override fun onBindViewHolder(h: VH, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            h.renderProgressFor(getItem(pos).id)
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

    class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
