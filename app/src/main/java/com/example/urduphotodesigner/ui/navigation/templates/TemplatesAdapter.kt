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
import com.bumptech.glide.signature.ObjectKey
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutTemplateItemBinding

class TemplatesAdapter(
    private val onTemplateSelected: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<TemplateEntity, TemplatesAdapter.TemplateVH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateVH {
        val binding = LayoutTemplateItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TemplateVH(binding)
    }

    override fun onBindViewHolder(holder: TemplateVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TemplateVH(private val binding: LayoutTemplateItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateEntity) {

            binding.shimmerLayout.startShimmer()

            binding.root.strokeColor =
                ContextCompat.getColor(binding.root.context, R.color.appColor)

            // Download/progress UI
            binding.download.visibility =
                if (item.is_downloaded || item.is_downloading) View.GONE else View.VISIBLE
            binding.progressBar.visibility = if (item.is_downloading) View.VISIBLE else View.GONE

            // Clicks: open if downloaded, otherwise trigger download
            binding.root.addPressEffect {
                if (item.is_downloaded) {
                    onTemplateSelected(item, true)
                } else {
                    onTemplateSelected(item, false)
                }
            }

            // Thumbnail
            val url = item.thumbnail_url

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
                .into(binding.template)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(oldItem: TemplateEntity, newItem: TemplateEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TemplateEntity, newItem: TemplateEntity) =
            oldItem == newItem
    }
}