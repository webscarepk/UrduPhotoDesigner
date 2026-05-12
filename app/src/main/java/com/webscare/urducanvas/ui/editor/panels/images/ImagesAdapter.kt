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

class ImagesAdapter(
    private val context: Context,
    private val onImageSelected: (Bitmap?, PictureDrawable?, svgXml: String?, ImageEntity) -> Unit,
    private val onLongPress: (ImageEntity) -> Unit = {}
) : RecyclerView.Adapter<ImagesAdapter.ImageViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED    = 0
        const val TYPE_EXPANDED     = 1
        const val PAYLOAD_SELECTION = "selection_changed"
    }

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    // ── CORE FIX ──────────────────────────────────────────────────────────────
    //
    // isInMultiSelectMode is a plain field on the ADAPTER (not passed into
    // ViewHolder). ViewHolders read it via the adapter reference at CLICK TIME,
    // not at bind time. This means the click handler is never stale —
    // no matter when you tap, it reads the current live value.
    //
    // Previously wireClicks captured `inMultiSelectMode: Boolean` as a lambda
    // parameter at bind time. Once bound, that lambda was frozen. Even after
    // applyModeToAll() or updateSelectionOnly() ran, the click lambda on
    // un-notified ViewHolders still had the old value captured.

    var isInMultiSelectMode: Boolean = false

    /**
     * Notifies all items with PAYLOAD_SELECTION so every visible ViewHolder
     * refreshes its radio icon visibility. Called when mode flips.
     * No Glide reload — payload path only touches radio + stroke.
     */
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
                onLongPress     = {}   // no multi-select in collapsed
            )
        }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, isItemSelected(item.id), isInMultiSelectMode)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
        if (payloads.contains(PAYLOAD_SELECTION)) {
            // Lightweight: radio icon + stroke only, reads live adapter state
            holder.updateSelectionOnly(isItemSelected(items[position].id), isInMultiSelectMode)
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    override fun getItemCount() = items.size

    private fun preloadAll(list: List<ImageEntity>) {
        val svgUrls = list.take(40)
            .filter { it.file_name.endsWith(".svg", ignoreCase = true) }
            .map { Constants.BASE_URL_GLIDE + it.file_url }
        SvgLoader.preload(svgUrls, adapterScope)
        list.take(40)
            .filterNot { it.file_name.endsWith(".svg", ignoreCase = true) }
            .forEach { image ->
                Glide.with(context)
                    .load(Constants.BASE_URL_GLIDE + image.file_url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload()
            }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    sealed class ImageViewHolder(
        itemView: android.view.View,
        // Adapter reference — read isInMultiSelectMode at click time, not bind time
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

        // The image this holder is currently bound to
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
                cardRoot.strokeColor =
                    ContextCompat.getColor(itemView.context, R.color.appColor)
            }
        }

        // ── KEY FIX ───────────────────────────────────────────────────────────
        //
        // wireClicks is called ONCE at bind time and never again.
        // The click listener reads adapter.isInMultiSelectMode at the moment
        // of the tap — not when the listener was registered. This means:
        //   - No stale captures
        //   - No need to re-wire on every selection update
        //   - Works correctly even if mode changes after bind without a rebind

        private fun wireClicks(image: ImageEntity) {
            itemView.addPressEffectWithLongClick(
                {
                    val currentImage = boundImage ?: image
                    if (adapter.isInMultiSelectMode) {
                        // In selection mode — single tap toggles this item
                        onLongPress(currentImage)
                    } else {
                        // Normal mode — tap adds to canvas
                        val url   = Constants.BASE_URL_GLIDE + currentImage.file_url
                        val isSvg = currentImage.file_name.endsWith(".svg", ignoreCase = true)
                        tapJob?.cancel()
                        tapJob = scope.launch { handleTap(currentImage, url, isSvg) }
                    }
                },{
                    val currentImage = boundImage ?: image
                    onLongPress(currentImage)
                }
            )
        }

        private fun loadForDisplay(image: ImageEntity) {
            val url   = Constants.BASE_URL_GLIDE + image.file_url
            val isSvg = image.file_name.endsWith(".svg", ignoreCase = true)
            displayJob?.cancel()
            if (isSvg) {
                shimmer.startShimmer()
                displayJob = SvgLoader.load(url, imageView, scope, image.bitmapData) { _, _ ->
                    shimmer.stopShimmer(); shimmer.setShimmer(null)
                }
                displayJob?.invokeOnCompletion {
                    itemView.post {
                        if (shimmer.isShimmerStarted) { shimmer.stopShimmer(); shimmer.setShimmer(null) }
                    }
                }
            } else {
                shimmer.startShimmer()
                Glide.with(itemView.context)
                    .load(image.bitmapData ?: url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
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
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        Glide.with(itemView.context).asBitmap()
                            .load(image.bitmapData ?: url)
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