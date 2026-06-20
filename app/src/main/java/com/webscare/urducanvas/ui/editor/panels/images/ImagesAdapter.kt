package com.webscare.urducanvas.ui.editor.panels.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.LayoutImagesItemBinding
import com.webscare.urducanvas.databinding.LayoutImagesItemExpandedBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Top-level URL resolver ────────────────────────────────────────────────────
// Pexels images store full https:// URLs directly in file_url.
// Your own images store relative paths that need BASE_URL_GLIDE prepended.
// id >= PEXELS_ID_OFFSET (10_000_000) means it's a Pexels image.
fun resolveUrl(image: ImageEntity): String =
    if (image.id >= Constants.PEXELS_ID_OFFSET) image.file_url
    else Constants.BASE_URL_GLIDE + image.file_url

class ImagesAdapter(
    private val context: Context,
    private val onImageSelected: (Bitmap?, PictureDrawable?, svgXml: String?, ImageEntity) -> Unit,
    private val onLongPress: (ImageEntity) -> Unit = {}
) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED    = 0
        const val TYPE_EXPANDED     = 1
        const val PAYLOAD_SELECTION = "selection_changed"
        private const val PRELOAD_WINDOW = 15   // first visible screen only
    }

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var isInMultiSelectMode: Boolean = false

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

    /**
     * Cancel in-flight preload work. Call from the host fragment's onDestroyView
     * so a tab scrolled away from stops doing background rasterization/decoding.
     * (The adapter instance itself may be reused on reattach; adapterScope stays
     * alive, only the current preloadJob is cancelled.)
     */
    fun cancelPreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /**
     * Append-only update — adds [newItems] to the END of the current list.
     * Does NOT go through DiffUtil at all. No move animations, no jumping.
     * Only notifies the adapter about the newly inserted range.
     * Use this for pagination — existing items stay exactly where they are.
     */
    fun appendItems(newItems: List<ImageEntity>) {
        if (newItems.isEmpty()) return
        val existingIds = differ.currentList.map { it.id }.toHashSet()
        val toAdd = newItems.filter { it.id !in existingIds }
        if (toAdd.isEmpty()) return
        val insertStart = differ.currentList.size
        // submitList with full combined list — but DiffUtil is smart enough
        // to see only additions at the end when items are stable
        differ.submitList(differ.currentList + toAdd)
        // Extra safety: scroll position is preserved because inserted items
        // are at positions >= insertStart, so existing items don't move
    }

    override fun getItemViewType(position: Int): Int =
        if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder =
        if (viewType == TYPE_EXPANDED) {
            ImageViewHolder.Expanded(
                LayoutImagesItemExpandedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                adapter         = this,
                onImageSelected = onImageSelected,
                onLongPress     = onLongPress
            )
        } else {
            ImageViewHolder.Collapsed(
                LayoutImagesItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                adapter         = this,
                onImageSelected = onImageSelected,
                onLongPress     = {}
            )
        }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, isItemSelected(item.id), isInMultiSelectMode)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelectionOnly(isItemSelected(items[position].id), isInMultiSelectMode)
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    override fun getItemCount() = items.size

    // Only the first on-screen window needs warming. RecyclerView lazy-binds the
    // rest as the user scrolls. Eager full-list preload on every submitList (i.e.
    // every tab switch) was allocating the entire category at once and stacking
    // across tabs -> GC thrash. Bound it hard, and cancel the previous tab's
    // preload before starting a new one.
    private var preloadJob: Job? = null

    private fun preloadAll(list: List<ImageEntity>) {
        // Cancel any preload still running from the previously-shown list/tab.
        preloadJob?.cancel()
        if (list.isEmpty()) return

        // First visible screen only: 3 columns x ~4 rows = 12, plus a little
        // lookahead = 15. Tiny, bounded, same on every device.
        val window = list.take(PRELOAD_WINDOW)

        preloadJob = adapterScope.launch {
            // SVG thumbnails (own images only; Pexels images are never SVG).
            val svgUrls = window
                .filter { it.file_name.endsWith(".svg", ignoreCase = true) }
                .map { Constants.BASE_URL_GLIDE + it.file_url }
            if (svgUrls.isNotEmpty()) SvgLoader.preload(svgUrls, adapterScope)

            // Non-SVG: warm Glide's cache for just the visible window.
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
        private val onLongPress: (ImageEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        protected abstract val imageView: android.widget.ImageView
        protected abstract val shimmer: com.facebook.shimmer.ShimmerFrameLayout
        protected abstract val premiumBadge: android.widget.ImageView
        protected abstract val loadingAnim: com.airbnb.lottie.LottieAnimationView
        protected abstract val cardRoot: com.google.android.material.card.MaterialCardView
        protected open val selectionIcon: android.widget.ImageView? get() = null

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

        fun bind(image: ImageEntity, isSelected: Boolean, inMultiSelectMode: Boolean) {
            boundImage = image
            premiumBadge.isVisible = image.is_premium && !image.is_subscribed
            cardRoot.setCardBackgroundColor(Color.WHITE)
            updateSelectionOnly(isSelected, inMultiSelectMode)
            loadForDisplay(image)
            wireClicks(image)
        }

        fun updateSelectionOnly(isSelected: Boolean, inMultiSelectMode: Boolean) {
            selectionIcon?.apply {
                visibility = if (inMultiSelectMode) android.view.View.VISIBLE
                else android.view.View.GONE
                setImageResource(
                    if (isSelected) R.drawable.ic_selected_radio
                    else R.drawable.ic_unselected_radio
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
                        val url   = resolveUrl(currentImage)
                        val isSvg = currentImage.file_name.endsWith(".svg", ignoreCase = true)
                        tapJob?.cancel()
                        tapJob = scope.launch { handleTap(currentImage, url, isSvg) }
                    }
                }, {
                    val currentImage = boundImage ?: image
                    onLongPress(currentImage)
                }
            )
        }

        private fun loadForDisplay(image: ImageEntity) {
            val displayUrl = resolveUrl(image)  // file_url — medium for Pexels, own URL for yours
            val isSvg = image.file_name.endsWith(".svg", ignoreCase = true)
            displayJob?.cancel()
            if (isSvg) {
                // INSTANT PATH: if the thumbnail is already rasterized, set it
                // synchronously during bind — no coroutine hop, no shimmer restart.
                // This removes the per-bind hitch when scrolling back over cells
                // that were already seen.
                val cached = SvgLoader.peekThumbnail(displayUrl)
                if (cached != null) {
                    shimmer.stopShimmer(); shimmer.setShimmer(null)
                    imageView.setImageBitmap(cached)
                } else {
                    shimmer.startShimmer()
                    displayJob = SvgLoader.load(displayUrl, imageView, scope, image.bitmapData) { _, _ ->
                        shimmer.stopShimmer(); shimmer.setShimmer(null)
                    }
                    displayJob?.invokeOnCompletion {
                        itemView.post {
                            if (shimmer.isShimmerStarted) { shimmer.stopShimmer(); shimmer.setShimmer(null) }
                        }
                    }
                }
            } else {
                shimmer.startShimmer()
                Glide.with(itemView.context)
                    .load(displayUrl)   // always use file_url for display — medium for Pexels
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .thumbnail(0.1f)    // show 10% quality placeholder instantly while full loads
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                            shimmer.stopShimmer(); shimmer.setShimmer(null); return false
                        }
                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            shimmer.stopShimmer(); shimmer.setShimmer(null); return false
                        }
                    }).into(imageView)
            }
        }

        private suspend fun handleTap(image: ImageEntity, url: String, isSvg: Boolean) {
            if (isSvg) {
                loadingAnim.isVisible = true
                val result = withContext(Dispatchers.IO) { SvgLoader.resolve(url, image.bitmapData) }
                loadingAnim.isVisible = false
                result?.let { (d, xml) -> onImageSelected(null, d, xml, image) }
            } else {
                // For Pexels: bitmapData holds the large URL → use for full quality on canvas
                // For own images: bitmapData is SVG XML or null → use file_url
                val tapUrl = if (image.id >= Constants.PEXELS_ID_OFFSET && image.bitmapData != null) {
                    image.bitmapData!!   // large Pexels URL
                } else {
                    url                  // own image URL
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
            onLongPress: (ImageEntity) -> Unit
        ) : ImageViewHolder(binding.root, adapter, onImageSelected, onLongPress) {
            override val imageView    get() = binding.image
            override val shimmer      get() = binding.shimmerLayout
            override val premiumBadge get() = binding.isPremium
            override val loadingAnim  get() = binding.loading
            override val cardRoot     get() = binding.root
        }

        class Expanded(
            private val binding: LayoutImagesItemExpandedBinding,
            adapter: ImagesAdapter,
            onImageSelected: (Bitmap?, PictureDrawable?, String?, ImageEntity) -> Unit,
            onLongPress: (ImageEntity) -> Unit
        ) : ImageViewHolder(binding.root, adapter, onImageSelected, onLongPress) {
            override val imageView     get() = binding.image
            override val shimmer       get() = binding.shimmerLayout
            override val premiumBadge  get() = binding.isPremium
            override val loadingAnim   get() = binding.loading
            override val cardRoot      get() = binding.root
            override val selectionIcon get() = binding.checkIcon
        }
    }
}