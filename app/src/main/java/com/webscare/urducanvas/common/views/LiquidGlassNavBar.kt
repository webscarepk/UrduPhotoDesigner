package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.graphics.toColorInt

/**
 * LiquidGlassNavBar — iOS 26 liquid glass style.
 *
 * KEY FIX: BlurEngine now captures ONLY the region behind the nav bar
 * (not the full screen), so the bitmap maps 1:1 to the bar rect.
 * No more stretching/color smearing.
 *
 * Visual layers (bottom → top):
 *   1. Blurred crop of content behind bar  (real glass substrate)
 *   2. Very light white tint overlay       (15–20% — just enough frost)
 *   3. Gradient border stroke              (bright top arc → invisible bottom)
 *   4. Circle indicator                    (same blur cropped to circle + deeper tint)
 *   5. Icons                               (dark grey / accent, drawn on top)
 */
class LiquidGlassNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ── Model ────────────────────────────────────────────────────────

    data class NavItem(
        val id: Int,
        val iconDrawable: Drawable?,
        val isCta: Boolean = false
    )

    private val items = mutableListOf<NavItem>()
    private var selectedIndex = 0
    private var onItemSelected: ((Int) -> Unit)? = null

    // ── Blur bitmaps ─────────────────────────────────────────────────
    // Both bitmaps are already cropped to exactly the bar's region.
    // barBlur  = light blur (bar background)
    // deepBlur = heavy blur (indicator circle)

    private var barBlurBitmap:  Bitmap? = null
    private var deepBlurBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // ── Paints ───────────────────────────────────────────────────────

    // LIGHT tint — 18% white. Just enough to make it "frosted", not opaque.
    private val barTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Gradient border — bright at top, fades to nothing at bottom
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    // Indicator circle deeper tint — 35% white over heavier blur
    private val indicatorTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Indicator border gradient
    private val indicatorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }

    // Indicator shadow (hardware accel required — default on all modern Android)
    private val indicatorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(1, 0, 0, 0) // nearly transparent fill, shadow is the point
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 3f, Color.argb(55, 0, 0, 0))
    }

    // ── Geometry ─────────────────────────────────────────────────────

    private val  barRect    = RectF()
    private var  barRadius  = 0f
    private var  slotWidth  = 0f
    private var  iconSize   = 0
    private var  iconSizeCta = 0
    private var  hPad       = 0f
    private var  indicatorRx = 0f   // horizontal radius
    private var  indicatorRy = 0f   // vertical radius
    private val  indicatorOval = RectF()

    // ── Spring ───────────────────────────────────────────────────────

    private var indicatorX      = 0f
    private val indicatorHolder = FloatValueHolder(0f)

    private val springAnim: SpringAnimation by lazy {
        SpringAnimation(indicatorHolder).apply {
            spring = SpringForce().apply {
                stiffness    = SpringForce.STIFFNESS_LOW
                dampingRatio = 0.72f
            }
            addUpdateListener { _, value, _ ->
                indicatorX = value
                invalidate()
            }
        }
    }

    private val clipPath = Path()
    private val indClip  = Path()

    // ── Public API ───────────────────────────────────────────────────

    fun setItems(navItems: List<NavItem>) {
        items.clear(); items.addAll(navItems)
        requestLayout(); invalidate()
    }

    fun setOnItemSelectedListener(l: (Int) -> Unit) { onItemSelected = l }

    fun selectItem(index: Int, animate: Boolean = true) {
        if (items.getOrNull(index)?.isCta == true) return
        selectedIndex = index
        val target = slotCentreX(index)
        if (animate && width > 0) {
            indicatorHolder.value = indicatorX
            springAnim.cancel()
            springAnim.animateToFinalPosition(target)
        } else {
            springAnim.cancel()
            indicatorX = target
            indicatorHolder.value = target
            invalidate()
        }
    }

    /**
     * Bar background blur — CROPPED to bar region, light blur.
     * BlurEngine should call this with a bitmap whose pixel dimensions
     * match the bar's width×height ratio exactly.
     */
    fun updateBlur(bitmap: Bitmap) {
        barBlurBitmap?.recycle()
        barBlurBitmap = bitmap
        invalidate()
    }

    /**
     * Indicator deep blur — ALSO cropped to bar region, heavier blur.
     * We re-crop to the circle region in onDraw.
     */
    fun updateIndicatorBlur(bitmap: Bitmap) {
        deepBlurBitmap?.recycle()
        deepBlurBitmap = bitmap
        invalidate()
    }

    // ── Touch ────────────────────────────────────────────────────────

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val slot = ((event.x - hPad) / slotWidth).toInt().coerceIn(0, items.size - 1)
            val item = items.getOrNull(slot) ?: return true
            if (item.isCta) onItemSelected?.invoke(slot)
            else { selectItem(slot); onItemSelected?.invoke(slot) }
            performClick()
        }
        return true
    }

    // ── Layout ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val dp = resources.displayMetrics.density

        barRect.set(0f, 0f, w.toFloat(), h.toFloat())
        barRadius = h / 2f

        hPad = 14f * dp
        val usableW = w - hPad * 2f
        if (items.isNotEmpty()) slotWidth = usableW / items.size.toFloat()

        iconSize    = (22 * dp).toInt()
        iconSizeCta = (26 * dp).toInt()
        indicatorRy = (h / 2f) * 0.78f
        indicatorRx = indicatorRy * 1.25f   // 2:2.5 ratio

        indicatorX = slotCentreX(selectedIndex)
        indicatorHolder.value = indicatorX

        // Gradient border: bright white top → transparent bottom
        borderPaint.shader = LinearGradient(
            w / 2f, 0f, w / 2f, h.toFloat(),
            intArrayOf(
                Color.argb(200, 255, 255, 255),
                Color.argb(70,  255, 255, 255),
                Color.argb(15,  255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        indicatorBorderPaint.shader = LinearGradient(
            indicatorX, height / 2f - indicatorRy,
            indicatorX, height / 2f + indicatorRy,
            intArrayOf(
                Color.argb(180, 255, 255, 255),
                Color.argb(40,  255, 255, 255),
                Color.argb(10,  200, 200, 200)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ── Draw ─────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val cy = height / 2f
        val w  = width.toFloat()
        val h  = height.toFloat()

        // ── Clip to pill ──────────────────────────────────────────
        clipPath.reset()
        clipPath.addRoundRect(barRect, barRadius, barRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // ── Layer 1: Bar blur ─────────────────────────────────────
        // Bitmap is already cropped to bar region → draw it at 1:1
        val barBmp = barBlurBitmap
        if (barBmp != null && !barBmp.isRecycled) {
            // Stretch to fill bar exactly — bitmap was captured at bar's region
            val src = Rect(0, 0, barBmp.width, barBmp.height)
            val dst = RectF(0f, 0f, w, h)
            canvas.drawBitmap(barBmp, src, dst, bitmapPaint)
        } else {
            // Fallback until first blur frame: soft translucent white
            canvas.drawColor(Color.argb(160, 240, 240, 242))
        }

        // ── Layer 2: Light frost tint ─────────────────────────────
        canvas.drawRoundRect(barRect, barRadius, barRadius, barTintPaint)

        // ── Layer 3: Indicator circle ─────────────────────────────
        val selItem = items.getOrNull(selectedIndex)
        if (selItem != null && !selItem.isCta) {
            val cx = indicatorX

            // 3a. Shadow (drawn as nearly-transparent fill, shadow is the visual)
            indicatorOval.set(cx - indicatorRx, cy - indicatorRy, cx + indicatorRx, cy + indicatorRy)
            canvas.drawOval(indicatorOval, indicatorShadowPaint)

            // 3b. Deep blur cropped to circle
            indClip.reset()
            indClip.addOval(indicatorOval, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(indClip)

            val deepBmp = deepBlurBitmap
            if (deepBmp != null && !deepBmp.isRecycled) {
                // deepBmp is also a bar-region crop — map it into circle bounds
                val src = Rect(0, 0, deepBmp.width, deepBmp.height)
                val dst = RectF(0f, 0f, w, h)
                canvas.drawBitmap(deepBmp, src, dst, bitmapPaint)
            } else {
                canvas.drawOval(indicatorOval,
                    Paint().apply { color = Color.argb(210, 245, 245, 248) })
            }

            // 3c. Deeper tint over the heavier blur
            canvas.drawOval(indicatorOval, indicatorTintPaint)
            canvas.restore()  // end circle clip

            // 3d. Gradient border ring on indicator
            // Rebuild indicator border shader centred on current cx
            indicatorBorderPaint.shader = LinearGradient(
                cx, cy - indicatorRy, cx, cy + indicatorRy,
                intArrayOf(
                    Color.argb(200, 255, 255, 255),
                    Color.argb(50,  255, 255, 255),
                    Color.argb(10,  200, 200, 200)
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(indicatorOval, indicatorBorderPaint)
        }

        canvas.restore()  // end pill clip

        // ── Layer 4: Gradient border stroke ──────────────────────
        // Drawn outside clip so it sits exactly on the pill edge
        canvas.drawRoundRect(barRect, barRadius, barRadius, borderPaint)

        // ── Layer 5: Icons ────────────────────────────────────────
        items.forEachIndexed { index, item ->
            val cx         = slotCentreX(index)
            val isSelected = (index == selectedIndex) && !item.isCta
            val size       = if (item.isCta) iconSizeCta else iconSize

            item.iconDrawable?.let { d ->
                d.state = if (isSelected) intArrayOf(android.R.attr.state_checked)
                else            intArrayOf(-android.R.attr.state_checked)

                val left = (cx - size / 2f).toInt()
                val top  = (cy - size / 2f).toInt()
                d.setBounds(left, top, left + size, top + size)

                // ↓ Replace "#2E7D32" with your app accent colour ↓
                val tint = when {
                    isSelected  -> "#2E7D32".toColorInt()
                    item.isCta  -> Color.argb(180, 30, 30, 30)
                    else        -> Color.argb(100, 30, 30, 30)
                }
                d.setTint(tint)
                d.draw(canvas)
            }
        }
    }

    private fun slotCentreX(index: Int) = hPad + slotWidth * index + slotWidth / 2f
}