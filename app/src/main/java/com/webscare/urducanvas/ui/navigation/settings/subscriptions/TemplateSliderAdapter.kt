package com.webscare.urducanvas.ui.navigation.settings.subscriptions

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.isDarkModeEnabled
import com.webscare.urducanvas.common.utils.startShimmerSoft
import com.webscare.urducanvas.databinding.LayoutSubscriptionsTemplateSlideBinding

/** One slide of the popular-templates slideshow. */
data class TemplateSlide(val imageUrl: String, val title: String)

/**
 * Feeds popular/premium templates into the ViewPager2 slideshow behind the
 * banner on the subscriptions screen. Each slide shows a shimmer placeholder
 * until its thumbnail finishes loading.
 *
 * REQUIRES: slideShimmer and slideImage must be SIBLINGS in
 * layout_subscriptions_template_slide.xml, not nested. See the comment in
 * that file for why — nesting breaks both the shimmer animation and the
 * final image reveal.
 *
 * IMPORTANT #1: call submitSlides() to update data — do NOT recreate this
 * adapter on every ViewModel emission (see comment on submitSlides()).
 *
 * IMPORTANT #2: slideImage is toggled with plain View.VISIBLE/INVISIBLE
 * below, not the isVisible KTX extension. `isVisible = false` maps to
 * View.GONE, and a GONE view never gets measured, so Glide's target never
 * resolves a size and the load hangs with no callback at all. INVISIBLE
 * still gets measured/laid out normally, it just isn't drawn.
 *
 * If a slide's image genuinely still fails to load, filter Logcat for tag
 * "TemplateSlider" — onLoadFailed logs the exact URL and the Glide
 * exception (404, redirect not followed, auth header needed, etc.).
 *
 * NOTE: swap the Glide call below for Coil/your image loader if the project
 * doesn't use Glide.
 */
class TemplateSliderAdapter : RecyclerView.Adapter<TemplateSliderAdapter.VH>() {

    private val slides = mutableListOf<TemplateSlide>()

    /**
     * Updates the slideshow's data. No-ops (no rebind, no reload) if
     * unchanged. Do NOT recreate this adapter on every ViewModel emission —
     * localTemplates is backed by a Room Flow that re-emits on every DB
     * write while templates sync in the background; swapping the adapter
     * each time re-binds the visible page, resetting the shimmer and
     * cancelling the in-flight Glide request before it can finish.
     */
    fun submitSlides(newSlides: List<TemplateSlide>) {
        if (slides == newSlides) return
        slides.clear()
        slides.addAll(newSlides)
        notifyDataSetChanged()
    }

    inner class VH(val binding: LayoutSubscriptionsTemplateSlideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutSubscriptionsTemplateSlideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun getItemCount() = slides.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val slide = slides[position]
        val url = buildThumbnailUrl(slide.imageUrl)

        with(holder.binding) {
            val isDark = root.context.isDarkModeEnabled()
            slideShimmer.isVisible = true
            slideShimmer.startShimmerSoft(isDark)
            // INVISIBLE, not GONE — see class doc.
            slideImage.visibility = View.INVISIBLE
        }

        Log.d("TemplateSlider", "onBindViewHolder: $url")

        Glide.with(holder.binding.slideImage)
            .load(url)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    Log.e("TemplateSlider", "Failed to load thumbnail: $url", e)
                    stopShimmer(holder)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    stopShimmer(holder)
                    return false
                }
            })
            .into(holder.binding.slideImage)
    }

    /**
     * Joins the base URL + relative thumbnail path safely regardless of
     * trailing/leading slashes.
     */
    private fun buildThumbnailUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = Constants.BASE_URL_GLIDE.trimEnd('/')
        val rel = path.trimStart('/')
        return "$base/$rel"
    }

    private fun stopShimmer(holder: VH) = with(holder.binding) {
        slideShimmer.stopShimmer()
        slideShimmer.isVisible = false
        slideImage.visibility = View.VISIBLE
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.binding.slideShimmer.stopShimmer()
    }
}
