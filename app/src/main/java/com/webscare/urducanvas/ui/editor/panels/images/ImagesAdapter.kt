package com.webscare.urducanvas.ui.editor.panels.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.SvgLoader
import com.webscare.urducanvas.common.utils.Utils.addPressEffectWithLongClick
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.startShimmerSoft
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.LayoutImagesItemBinding
import com.webscare.urducanvas.databinding.LayoutImagesItemExpandedBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Top-level URL resolver ────────────────────────────────────────────────────
// Pexels images store full https:// URLs directly in file_url.
// Your own images store relative paths that need BASE_URL_GLIDE prepended.
// id >= PEXELS_ID_OFFSET (10_000_000) means it's a Pexels image.
fun resolveUrl(image: ImageEntity): String = if (image.id >= Constants.PEXELS_ID_OFFSET) {
    image.file_url
} else {
    Constants.BASE_URL_GLIDE + image.file_url
}

class ImagesAdapter(
    private val context: Context,
    private val onImageSelected: (Bitmap?, PictureDrawable?, svgXml: String?, ImageEntity) -> Unit,
    private val onLongPress: (ImageEntity) -> Unit = {},
) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED = 0
        const val TYPE_EXPANDED = 1
        const val PAYLOAD_SELECTION = "selection_changed"
        private const val PRELOAD_WINDOW = 15
    }

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var isInMultiSelectMode: Boolean = false
    var applyWhiteTint: Boolean = false

    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

    fun applyModeToAll() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    // ── Selection shadow ──────────────────────────────────────────────────────

    private val selectionShadow = mutableMapOf<Int, Boolean>()
    fun isItemSelected(id: Int): Boolean = selectionShadow[id] == true

    fun updateSelectionForId(id: Int, isSelected: Boolean) {
        selectionShadow[id] = isSelected
        val pos = items.indexOfFirst { it.id == id }
        if (pos >= 0) notifyItemChanged(pos, PAYLOAD_SELECTION)
    }

    fun clearSelectionShadow() {
        if (selectionShadow.isEmpty()) return
        selectionShadow.clear()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    // ── Differ ────────────────────────────────────────────────────────────────

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val diffCallback = object : DiffUtil.ItemCallback<ImageEntity>() {
        override fun areItemsTheSame(old: ImageEntity, new: ImageEntity) = old.id == new.id
        override fun areContentsTheSame(old: ImageEntity, new: ImageEntity) = old == new
        override fun getChangePayload(oldItem: ImageEntity, newItem: ImageEntity): Any? = null
    }

    private val differ = AsyncListDiffer(this, diffCallback).apply {
        addListListener { _, currentList -> preloadAll(currentList) }
    }

    val items: List<ImageEntity> get() = differ.currentList

    fun submitList(newList: List<ImageEntity>) {
        differ.submitList(newList.toList())
    }

    fun cancelPreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    fun appendItems(newItems: List<ImageEntity>) {
        if (newItems.isEmpty()) return
        val existingIds = differ.currentList.map { it.id }.toHashSet()
        val toAdd = newItems.filter { it.id !in existingIds }
        if (toAdd.isEmpty()) return
        differ.submitList(differ.currentList + toAdd)
    }

    override fun getItemViewType(position: Int): Int = if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EXPANDED) {
            ImageViewHolder.Expanded(
                LayoutImagesItemExpandedBinding.inflate(inflater, parent, false),
                this,
                onImageSelected,
                onLongPress,
            )
        } else {
            ImageViewHolder.Collapsed(
                LayoutImagesItemBinding.inflate(inflater, parent, false),
                this,
                onImageSelected,
                onLongPress,
            )
        }
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(
            item,
            isItemSelected(item.id),
            isInMultiSelectMode,
            slideOffset,
            recyclerViewWidth,
            recyclerViewPadding,
        )
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelectionOnly(isItemSelected(items[position].id), isInMultiSelectMode)
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    override fun getItemCount() = items.size

    private var preloadJob: Job? = null

    private fun preloadAll(list: List<ImageEntity>) {
        preloadJob?.cancel()
        if (list.isEmpty()) return

        val window = list.take(PRELOAD_WINDOW)

        preloadJob = adapterScope.launch {
            delay(250) // Debounce preloading to prevent resource starvation during tab switches
            val svgUrls = window
                .filter { it.file_name.endsWith(".svg", ignoreCase = true) }
                .map { Constants.BASE_URL_GLIDE + it.file_url }
            if (svgUrls.isNotEmpty()) SvgLoader.preload(svgUrls, this)

            window
                .filterNot { it.file_name.endsWith(".svg", ignoreCase = true) }
                .forEach { image ->
                    Glide.with(context)
                        .load(resolveUrl(image))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .preload()
                }
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    sealed class ImageViewHolder(
        itemView: android.view.View,
        private val adapter: ImagesAdapter,
        private val onImageSelected: (Bitmap?, PictureDrawable?, String?, ImageEntity) -> Unit,
        private val onLongPress: (ImageEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        abstract val imageView: android.widget.ImageView
        abstract val shimmer: com.facebook.shimmer.ShimmerFrameLayout
        abstract val premiumBadge: android.widget.ImageView
        abstract val loadingAnim: com.airbnb.lottie.LottieAnimationView
        abstract val cardRoot: com.google.android.material.card.MaterialCardView
        open val selectionIcon: android.widget.ImageView? get() = null

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var displayJob: Job? = null
        private var tapJob: Job? = null
        private var boundImage: ImageEntity? = null

        fun onRecycled() {
            displayJob?.cancel()
            tapJob?.cancel()
            Glide.with(itemView.context).clear(imageView)
            shimmer.stopShimmer()
            shimmer.setShimmer(null)
            loadingAnim.isVisible = false
            boundImage = null
        }

        fun bind(
            image: ImageEntity,
            isSelected: Boolean,
            inMultiSelectMode: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int,
        ) {
            boundImage = image
            premiumBadge.isVisible = image.is_premium && !image.is_subscribed
            cardRoot.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.white))
            updateSelectionOnly(isSelected, inMultiSelectMode)
            updateSize(slideOffset, rvWidth, rvPadding)
            loadForDisplay(image)
            wireClicks(image)
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
                val finalSize = if (lm != null &&
                    lm.orientation == androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL
                ) {
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

                if (lp.width != finalSize ||
                    lp.height != finalSize ||
                    lp.rightMargin != marginEndPx ||
                    lp.bottomMargin != marginBottomPx
                ) {
                    lp.width = finalSize
                    lp.height = finalSize
                    lp.rightMargin = marginEndPx
                    lp.bottomMargin = marginBottomPx
                    cardRoot.layoutParams = lp
                }
            }
        }

        fun updateSelectionOnly(isSelected: Boolean, inMultiSelectMode: Boolean) {
            selectionIcon?.apply {
                visibility = if (inMultiSelectMode) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                setImageResource(
                    if (isSelected) {
                        R.drawable.ic_selected_radio
                    } else {
                        R.drawable.ic_unselected_radio
                    },
                )
            }
            cardRoot.strokeWidth = if (isSelected) 2 else 0
            if (isSelected) {
                cardRoot.strokeColor = ContextCompat.getColor(itemView.context, R.color.appColor)
            }
        }

        private fun wireClicks(image: ImageEntity) {
            itemView.addPressEffectWithLongClick(
                {
                    val currentImage = boundImage ?: image
                    if (adapter.isInMultiSelectMode) {
                        onLongPress(currentImage)
                    } else {
                        val url = resolveUrl(currentImage)
                        val isSvg = currentImage.file_name.endsWith(".svg", ignoreCase = true)
                        tapJob?.cancel()
                        tapJob = scope.launch { handleTap(currentImage, url, isSvg) }
                    }
                },
                {
                    val currentImage = boundImage ?: image
                    onLongPress(currentImage)
                },
            )
        }

        private fun loadForDisplay(image: ImageEntity) {
            val displayUrl = resolveUrl(image)
            val isSvg = image.file_name.endsWith(".svg", ignoreCase = true)
            displayJob?.cancel()
            val isDark = itemView.context.isDarkModeEnabled()
            if (isSvg) {
                val cached = SvgLoader.peekThumbnail(displayUrl, adapter.applyWhiteTint)
                if (cached != null) {
                    shimmer.stopShimmer()
                    shimmer.setShimmer(null)
                    imageView.setImageBitmap(cached)
                } else {
                    shimmer.startShimmerSoft(isDark)
                    displayJob =
                        SvgLoader.load(
                            displayUrl,
                            imageView,
                            scope,
                            image.bitmapData,
                            applyWhiteTint = adapter.applyWhiteTint,
                        ) {
                                _,
                                _,
                            ->
                            shimmer.stopShimmer()
                            shimmer.setShimmer(null)
                        }
                    displayJob?.invokeOnCompletion {
                        itemView.post {
                            if (shimmer.isShimmerStarted) {
                                shimmer.stopShimmer()
                                shimmer.setShimmer(null)
                            }
                        }
                    }
                }
            } else {
                shimmer.startShimmerSoft(isDark)
                Glide.with(itemView.context)
                    .load(displayUrl)
                    .centerInside()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            shimmer.stopShimmer()
                            shimmer.setShimmer(null)
                            return false
                        }
                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            shimmer.stopShimmer()
                            shimmer.setShimmer(null)
                            return false
                        }
                    }).into(imageView)
            }
        }

        private suspend fun handleTap(image: ImageEntity, url: String, isSvg: Boolean) {
            if (isSvg) {
                loadingAnim.isVisible = true
                val result =
                    withContext(Dispatchers.IO) { SvgLoader.resolve(url, image.bitmapData, adapter.applyWhiteTint) }
                loadingAnim.isVisible = false
                result?.let { (d, xml) -> onImageSelected(null, d, xml, image) }
            } else {
                val tapUrl = if (image.id >= Constants.PEXELS_ID_OFFSET && image.bitmapData != null) {
                    image.bitmapData!!
                } else {
                    url
                }
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        Glide.with(itemView.context).asBitmap()
                            .load(tapUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL).submit().get()
                    }.getOrNull()
                }
                bitmap?.let { onImageSelected(it, null, null, image) }
            }
        }

        class Collapsed(
            private val binding: LayoutImagesItemBinding,
            adapter: ImagesAdapter,
            onImageSelected: (Bitmap?, PictureDrawable?, String?, ImageEntity) -> Unit,
            onLongPress: (ImageEntity) -> Unit,
        ) : ImageViewHolder(binding.root, adapter, onImageSelected, onLongPress) {
            override val imageView get() = binding.image
            override val shimmer get() = binding.shimmerLayout
            override val premiumBadge get() = binding.isPremium
            override val loadingAnim get() = binding.loading
            override val cardRoot get() = binding.root
        }

        class Expanded(
            private val binding: LayoutImagesItemExpandedBinding,
            adapter: ImagesAdapter,
            onImageSelected: (Bitmap?, PictureDrawable?, String?, ImageEntity) -> Unit,
            onLongPress: (ImageEntity) -> Unit,
        ) : ImageViewHolder(binding.root, adapter, onImageSelected, onLongPress) {
            override val imageView get() = binding.image
            override val shimmer get() = binding.shimmerLayout
            override val premiumBadge get() = binding.isPremium
            override val loadingAnim get() = binding.loading
            override val cardRoot get() = binding.root
            override val selectionIcon get() = binding.checkIcon
        }
    }
}
