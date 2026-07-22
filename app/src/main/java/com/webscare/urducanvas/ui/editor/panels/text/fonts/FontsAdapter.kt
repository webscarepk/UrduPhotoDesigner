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
import com.webscare.urducanvas.common.utils.startShimmerSoft
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
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

    private val downloadingIds = mutableSetOf<Int>()

    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

    fun addDownloadingId(id: Int) {
        if (downloadingIds.add(id)) {
            val pos = currentList.indexOfFirst { it.id == id }
            if (pos != -1) notifyItemChanged(pos)
        }
    }

    fun clearDownloadingId(id: Int) {
        if (downloadingIds.remove(id)) {
            val pos = currentList.indexOfFirst { it.id == id }
            if (pos != -1) notifyItemChanged(pos)
        }
    }

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
            Expanded(
                LayoutFontItemExpandedBinding.inflate(inflater, parent, false),
                onFontSelected
            )
        } else {
            Collapsed(
                LayoutFontItemBinding.inflate(inflater, parent, false),
                onFontSelected
            )
        }
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val font = getItem(position)
        val isDownloading = downloadingIds.contains(font.id)
        holder.bind(font, selectedFontId, isDownloading, slideOffset, recyclerViewWidth, recyclerViewPadding)
    }

    // ── ViewHolder ──────────────────────────────────────────────────────────

    sealed class FontViewHolder(
        itemView: View,
        private val onFontSelected: (FontEntity, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        abstract val cardRoot: com.google.android.material.card.MaterialCardView
        abstract val fontImage: android.widget.ImageView
        abstract val shimmer: com.facebook.shimmer.ShimmerFrameLayout
        abstract val loadingAnim: com.airbnb.lottie.LottieAnimationView
        abstract val premiumBadge: android.widget.ImageView
        abstract val downloadIcon: android.widget.ImageView
        open val fontNameTextView: android.widget.TextView? get() = null

        fun bind(
            font: FontEntity,
            selectedFontId: String?,
            isDownloading: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            val isSelected = font.id.toString() == selectedFontId

            cardRoot.strokeWidth = if (isSelected) 2 else 0
            cardRoot.strokeColor = ContextCompat.getColor(
                cardRoot.context, R.color.appColor
            )

            fontNameTextView?.text = com.webscare.urducanvas.common.utils.Utils.cleanFontName(font.font_name)

            premiumBadge.isVisible = font.is_premium && !font.is_subscribed
            downloadIcon.visibility =
                if (font.is_downloaded || isDownloading) View.GONE else View.VISIBLE
            loadingAnim.visibility =
                if (isDownloading) View.VISIBLE else View.GONE

            itemView.addPressEffect {
                if (font.is_downloaded) {
                    onFontSelected(font, true)
                } else {
                    onFontSelected(font, false)
                }
            }

            updateSize(slideOffset, rvWidth, rvPadding)
            loadImage(font, fontImage, shimmer)
        }

        fun updateSize(slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            if (rvWidth <= 0) return
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val collapsedSize = (44 * density).toInt()

            val marginPx = 18 * density // spacing space (3 columns * 2 sides * 3dp = 18dp)
            val columnWidth = ((rvWidth - rvPadding - marginPx) / 3).toInt()

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()

            val lp = cardRoot.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (lp != null) {
                val marginEndPx = (6 * density).toInt()
                val marginBottomPx = (6 * density).toInt()

                // Calculate vertical clamping when horizontal orientation to prevent overlapping rows
                val recyclerView = itemView.parent as? androidx.recyclerview.widget.RecyclerView
                val lm = recyclerView?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                val finalSize = if (lm != null && lm.orientation == androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL) {
                    val rvHeight = recyclerView.height
                    val rvPaddingY = recyclerView.paddingTop + recyclerView.paddingBottom
                    val availHeight = rvHeight - rvPaddingY
                    val spanCount = lm.spanCount.coerceAtLeast(1)
                    val rowHeight = availHeight / spanCount
                    val maxAllowedHeight = rowHeight - marginBottomPx
                    minOf(currentSize, maxAllowedHeight).coerceAtLeast((28 * density).toInt())
                } else {
                    currentSize
                }

                if (lp.width != finalSize || lp.height != finalSize || lp.rightMargin != marginEndPx || lp.bottomMargin != marginBottomPx) {
                    lp.width = finalSize
                    lp.height = finalSize
                    lp.rightMargin = marginEndPx
                    lp.bottomMargin = marginBottomPx
                    cardRoot.layoutParams = lp
                }
            }
        }

        private fun loadImage(
            font: FontEntity,
            imageView: android.widget.ImageView,
            shimmer: com.facebook.shimmer.ShimmerFrameLayout
        ) {
            val isDarkMode = imageView.context.isDarkModeEnabled()
            shimmer.startShimmerSoft(isDarkMode)

            if (isDarkMode) {
                imageView.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                imageView.clearColorFilter()
            }

            if (font.image_url.isEmpty()) {
                if (font.font_image?.isNotEmpty() == true) {
                    Glide.with(imageView.context)
                        .load(font.font_image)
                        .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean = false
                            override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                if (imageView.context.isDarkModeEnabled()) {
                                    imageView.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                                }
                                return false
                            }
                        })
                        .into(imageView)
                    shimmer.hideShimmer()
                } else {
                    imageView.setImageResource(R.drawable.ic_font_thumbnail)
                    shimmer.hideShimmer()
                }
                return
            }

            val imgUrl = Constants.BASE_URL_GLIDE + font.image_url
            val isSvg = font.image_url.endsWith(".svg", ignoreCase = true)
            if (isSvg) {
                com.webscare.urducanvas.common.utils.SvgLoader.load(
                    url = imgUrl,
                    imageView = imageView,
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                    cachedXml = null,
                    maxPx = 1024,
                    applyWhiteTint = isDarkMode
                ) { _, _ ->
                    shimmer.stopShimmer()
                    shimmer.setShimmer(null)
                    if (imageView.context.isDarkModeEnabled()) {
                        imageView.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                }
            } else {
                Glide.with(imageView.context)
                    .load(imgUrl)
                    .centerInside()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            shimmer.stopShimmer(); shimmer.setShimmer(null); return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            if (imageView.context.isDarkModeEnabled()) {
                                imageView.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                            }
                            shimmer.stopShimmer(); shimmer.setShimmer(null); return false
                        }
                    })
                    .into(imageView)
            }
        }
    }

    class Collapsed(
        private val binding: LayoutFontItemBinding,
        onFontSelected: (FontEntity, Boolean) -> Unit
    ) : FontViewHolder(binding.root, onFontSelected) {
        override val cardRoot     get() = binding.root
        override val fontImage    get() = binding.font
        override val shimmer      get() = binding.shimmerLayout
        override val loadingAnim  get() = binding.loading
        override val premiumBadge get() = binding.isPremium
        override val downloadIcon get() = binding.download
    }

    class Expanded(
        private val binding: LayoutFontItemExpandedBinding,
        onFontSelected: (FontEntity, Boolean) -> Unit
    ) : FontViewHolder(binding.root, onFontSelected) {
        override val cardRoot     get() = binding.root
        override val fontImage    get() = binding.font
        override val shimmer      get() = binding.shimmerLayout
        override val loadingAnim  get() = binding.loading
        override val premiumBadge get() = binding.isPremium
        override val downloadIcon get() = binding.download
        override val fontNameTextView get() = binding.fontName
    }

    class DiffCallback : DiffUtil.ItemCallback<FontEntity>() {
        override fun areItemsTheSame(oldItem: FontEntity, newItem: FontEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FontEntity, newItem: FontEntity) =
            oldItem == newItem
    }
}