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

    class VH(
        private val binding: LayoutTemplateItemBinding,
        private val onClick: (TemplateEntity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateEntity) {

            binding.download.addPressEffect { onClick(item) }
            binding.fontCard.addPressEffect { onClick(item) }

            // 🔥 PROGRESS FROM ENTITY (truth)
            val downloading = item.is_downloading && !item.is_downloaded

            binding.progressBox.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
            binding.percentage.visibility = if (downloading) View.VISIBLE else View.GONE

            binding.download.visibility =
                if (downloading || item.is_downloaded) View.GONE else View.VISIBLE

            if (downloading) {
                val pct = item.download_progress.coerceIn(0, 100)
                binding.progressBar.progress = pct
                binding.percentage.text = "$pct%"
            } else {
                binding.progressBar.progress = 0
                binding.percentage.text = ""
            }

            // IMAGE
            val url = Constants.BASE_URL_GLIDE + item.thumbnail_url

            if (url.isNotEmpty()) {
                Glide.with(binding.root.context).asBitmap().load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL).thumbnail(0.1f)
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
                    }).into(binding.template)

                binding.shimmerLayout.startShimmer()
            }
        }
    }

    private class Diff : DiffUtil.ItemCallback<TemplateEntity>() {
        override fun areItemsTheSame(o: TemplateEntity, n: TemplateEntity) = o.id == n.id
        override fun areContentsTheSame(o: TemplateEntity, n: TemplateEntity) = o == n
    }
}
