package com.webscare.urducanvas.ui.navigation.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
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
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.startShimmerSoft
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.LayoutTemplatePopularBinding

class PopularTemplatesAdapter(private val onClick: (TemplateEntity, Boolean) -> Unit) : ListAdapter<TemplateEntity, PopularTemplatesAdapter.VH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    fun updateProgress(templateId: Int, state: ProgressUi) {
        val idx = currentList.indexOfFirst { it.id == templateId }
        if (idx != -1) notifyItemChanged(idx, state)
    }

    inner class VH(val binding: LayoutTemplatePopularBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TemplateEntity) {
            val isDark = binding.root.context.isDarkModeEnabled()
            binding.shimmerLayout.startShimmerSoft(isDark)

            binding.isPremium.isVisible = item.is_premium && !item.is_subscribed
            binding.root.strokeColor =
                ContextCompat.getColor(binding.root.context, R.color.appColor)

            val downloading = item.is_downloading && !item.is_downloaded

//            binding.progressBox.isVisible = downloading
            binding.loading.isVisible = downloading

            binding.download.isVisible = !(downloading || item.is_downloaded)

//            if (downloading) {
//                val p = item.download_progress.coerceIn(0, 100)
//                binding.percentage.text = "$p%"
//            } else {
//                binding.percentage.text = ""
//            }

            binding.root.addPressEffect {
                if (!item.is_downloading) {
                    onClick(item, item.is_downloaded)
                }
            }

            // Thumbnail
            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url

            Glide.with(binding.root.context).load(url).diskCacheStrategy(DiskCacheStrategy.ALL)
                .thumbnail(0.1f).listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        binding.shimmerLayout.hideShimmer()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        binding.shimmerLayout.hideShimmer()
                        return false
                    }
                }).into(binding.image)
        }

        fun applyProgress(state: ProgressUi) {
            binding.apply {
                val downloading = state.isDownloading && !state.isDownloaded

//                progressBox.isVisible = downloading
                loading.isVisible = downloading

                download.isVisible = !(downloading || state.isDownloaded)

//                if (downloading) {
//                    val p = state.progress.coerceIn(0, 100)
//                    percentage.text = "$p%"
//                } else {
//                    percentage.text = ""
//                }
            }
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
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

    override fun getItemId(position: Int) = getItem(position).id.toLong()

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(LayoutTemplatePopularBinding.inflate(LayoutInflater.from(p.context), p, false))

    class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
