package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.graphics.drawable.PictureDrawable
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
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.databinding.LayoutFontItemBinding
import com.webscare.urducanvas.databinding.LayoutFontItemExpandedBinding

class FontsAdapter(
    private val onFontSelected: (FontEntity, Boolean) -> Unit
) : ListAdapter<FontEntity, FontsAdapter.FontViewHolder>(DiffCallback()) {

    companion object {
        const val TYPE_COLLAPSED = 0
        const val TYPE_EXPANDED  = 1
    }

    // FIX 2: toggle between collapsed (horizontal grid 2 rows) and expanded (vertical grid 3 cols)
    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var selectedFontId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                old?.let { oldId ->
                    val oldPos = currentList.indexOfFirst { it.id.toString() == oldId }
                    if (oldPos != -1) notifyItemChanged(oldPos)
                }
                value?.let { newId ->
                    val newPos = currentList.indexOfFirst { it.id.toString() == newId }
                    if (newPos != -1) notifyItemChanged(newPos)
                }
            }
        }

    override fun getItemViewType(position: Int): Int =
        if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EXPANDED) {
            FontViewHolder.Expanded(
                LayoutFontItemExpandedBinding.inflate(inflater, parent, false),
                onFontSelected
            )
        } else {
            FontViewHolder.Collapsed(
                LayoutFontItemBinding.inflate(inflater, parent, false),
                onFontSelected
            )
        }
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(getItem(position), selectedFontId)
    }

    // ── ViewHolder hierarchy ──────────────────────────────────────────────────

    sealed class FontViewHolder(
        itemView: View,
        private val onFontSelected: (FontEntity, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        abstract fun bind(font: FontEntity, selectedFontId: String?)

        protected fun loadImage(
            font: FontEntity,
            imageView: android.widget.ImageView,
            shimmer: com.facebook.shimmer.ShimmerFrameLayout
        ) {
            shimmer.startShimmer()

            if (font.image_url.isEmpty()) {
                if (font.font_image?.isNotEmpty() == true) {
                    Glide.with(imageView.context)
                        .load(font.font_image)
                        .into(imageView)
                    shimmer.hideShimmer()
                } else {
                    imageView.setImageResource(R.drawable.ic_font_thumbnail)
                    shimmer.hideShimmer()
                }
            } else {
                val url = Constants.BASE_URL_GLIDE + font.image_url
                if (isSvgUrl(url)) {
                    Glide.with(imageView.context)
                        .`as`(PictureDrawable::class.java)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .listener(object : RequestListener<PictureDrawable> {
                            override fun onLoadFailed(
                                e: GlideException?, model: Any?,
                                target: Target<PictureDrawable>, isFirstResource: Boolean
                            ) = false.also { shimmer.hideShimmer() }

                            override fun onResourceReady(
                                resource: PictureDrawable, model: Any,
                                target: Target<PictureDrawable>?,
                                dataSource: DataSource, isFirstResource: Boolean
                            ) = false.also { shimmer.hideShimmer() }
                        })
                        .into(imageView)
                } else {
                    Glide.with(imageView.context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .thumbnail(0.1f)
                        .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?, model: Any?,
                                target: Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                shimmer.hideShimmer(); return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable, model: Any,
                                target: Target<android.graphics.drawable.Drawable>?,
                                dataSource: DataSource, isFirstResource: Boolean
                            ): Boolean {
                                shimmer.hideShimmer(); return false
                            }
                        })
                        .into(imageView)
                }
            }
        }

        private fun isSvgUrl(raw: String): Boolean {
            val q = raw.substringBefore('#').substringBefore('?').lowercase()
            return q.endsWith(".svg")
        }

        // ── Collapsed item (58dp × 58dp, used in horizontal GridLayoutManager spanCount=2) ──

        class Collapsed(
            private val binding: LayoutFontItemBinding,
            onFontSelected: (FontEntity, Boolean) -> Unit
        ) : FontViewHolder(binding.root, onFontSelected) {

            private val onFontSelectedRef = onFontSelected

            override fun bind(font: FontEntity, selectedFontId: String?) {
                val isSelected = font.id.toString() == selectedFontId

                binding.root.strokeWidth = if (isSelected) 2 else 0
                binding.root.strokeColor = ContextCompat.getColor(
                    binding.root.context, R.color.appColor
                )

                binding.isPremium.isVisible = font.is_premium && !font.is_subscribed
                binding.download.visibility =
                    if (font.is_downloaded || font.is_downloading) View.GONE else View.VISIBLE
                binding.loading.visibility =
                    if (font.is_downloading) View.VISIBLE else View.GONE

                binding.root.addPressEffect {
                    if (font.is_downloaded) {
                        onFontSelectedRef(font, true)
                    } else {
                        onFontSelectedRef(font, false)
                    }
                }

                loadImage(font, binding.font, binding.shimmerLayout)
            }
        }

        // ── Expanded item (match_parent width in vertical GridLayoutManager spanCount=3) ──

        class Expanded(
            private val binding: LayoutFontItemExpandedBinding,
            onFontSelected: (FontEntity, Boolean) -> Unit
        ) : FontViewHolder(binding.root, onFontSelected) {

            private val onFontSelectedRef = onFontSelected

            override fun bind(font: FontEntity, selectedFontId: String?) {
                val isSelected = font.id.toString() == selectedFontId

                binding.root.strokeWidth = if (isSelected) 2 else 0
                binding.root.strokeColor = ContextCompat.getColor(
                    binding.root.context, R.color.appColor
                )

                binding.isPremium.isVisible = font.is_premium && !font.is_subscribed
                binding.download.visibility =
                    if (font.is_downloaded || font.is_downloading) View.GONE else View.VISIBLE
                binding.loading.visibility =
                    if (font.is_downloading) View.VISIBLE else View.GONE

                binding.root.addPressEffect {
                    if (font.is_downloaded) {
                        onFontSelectedRef(font, true)
                    } else {
                        onFontSelectedRef(font, false)
                    }
                }

                loadImage(font, binding.font, binding.shimmerLayout)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FontEntity>() {
        override fun areItemsTheSame(oldItem: FontEntity, newItem: FontEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FontEntity, newItem: FontEntity) =
            oldItem == newItem
    }
}