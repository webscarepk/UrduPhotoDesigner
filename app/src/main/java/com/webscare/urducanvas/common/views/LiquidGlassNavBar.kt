package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

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
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Glass stroke — static, no animation, no app colour
    // Two layers: outer soft glow + inner bright top-heavy gradient
    private val borderGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 3.5f
        color       = Color.argb(55, 255, 255, 255)  // soft uniform white glow
    }
    private val borderShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 2.5f
        // shader set in onSizeChanged
    }

    // Indicator circle deeper tint — 35% white over heavier blur
    private val indicatorTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 255)
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
    private var  indPillH = 0f       // half-height of indicator
    private var  indPillW = 0f       // half-width of indicator
    private val  indicatorRect = RectF()

    // ── Scale-burst animation fields ─────────────────────────────────────
    // journeyProgress tracks 0→1 as indicator travels from source to target.
    // Scale follows a sine curve: 1.0 at start, peak at midpoint, 1.0 at end.
    // MAX_SCALE > 1 means the indicator pops OUTSIDE the bar clip — drawn after restore().
    private var journeyStart   = 0f
    private var journeyTarget  = 0f
    private var journeyProgress = 1f   // 1 = at rest (full size, no animation)
    private var indicatorScale  = 1f   // current scale applied to indPillH/W

    companion object {
        // How large the indicator grows at the midpoint of its journey.
        // 1.9 means it scales to 190% — large enough to pop outside the bar.
        private const val MAX_INDICATOR_SCALE = 1.35f
    }

    // ── Spring ───────────────────────────────────────────────────────

    private var indicatorX      = 0f
    private val indicatorHolder = FloatValueHolder(0f)

    private val springAnim: SpringAnimation by lazy {
        SpringAnimation(indicatorHolder).apply {
            spring = SpringForce().apply {
                stiffness    = 160f    // tuned to finish ~600ms — matches scaleAnim duration
                dampingRatio = 0.78f   // gentle overshoot, settles cleanly
            }
            addUpdateListener { _, value, _ ->
                indicatorX = value
                invalidate()
            }
        }
    }

    private var scaleAnim: ValueAnimator? = null

    private val clipPath     = Path()
    private val indClipPath  = Path()

    // ── Public API ───────────────────────────────────────────────────

    fun setItems(navItems: List<NavItem>) {
        items.clear()
        // mutate() gives each drawable its own independent state so tint
        // applied to one item never bleeds onto another item's drawable
        navItems.forEach { item ->
            items.add(item.copy(iconDrawable = item.iconDrawable?.mutate()))
        }
        requestLayout(); invalidate()
    }

    fun setOnItemSelectedListener(l: (Int) -> Unit) { onItemSelected = l }

    fun selectItem(index: Int, animate: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (items.getOrNull(index)?.isCta == true) return
        selectedIndex = index
        val target = slotCentreX(index)
        if (animate && width > 0) {
            journeyStart  = indicatorX
            journeyTarget = target

            // Spring drives position
            indicatorHolder.value = indicatorX
            springAnim.cancel()
            springAnim.animateToFinalPosition(target)

            // ValueAnimator drives scale: 0→1 over ~440ms
            // scale = 1 + (MAX-1)*sin(π*t)  →  peaks at t=0.5, returns to 1 at t=1
            scaleAnim?.cancel()
            scaleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    // sine curve: 0 at start, peak at 0.5, 0 at end
                    val sine = Math.sin(Math.PI * t.toDouble()).toFloat()
                    indicatorScale = 1f + (MAX_INDICATOR_SCALE - 1f) * sine
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        indicatorScale = 1f
                        journeyProgress = 1f
                        invalidate()
                        onComplete?.invoke()   // navigate AFTER animation completes
                    }
                })
                start()
            }
        } else {
            // Cancel all animations immediately — no scale, no spring
            springAnim.cancel()
            scaleAnim?.cancel()
            scaleAnim = null
            indicatorX     = target
            indicatorScale = 1f
            indicatorHolder.value = target
            invalidate()
            onComplete?.invoke()
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

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnim.cancel()
    }

    // ── Touch ────────────────────────────────────────────────────────

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val slot = ((event.x - hPad) / slotWidth).toInt().coerceIn(0, items.size - 1)
            val item = items.getOrNull(slot) ?: return true
            if (item.isCta) {
                onItemSelected?.invoke(slot)
            } else {
                // Pass navigation as callback — fires after animation completes
                selectItem(slot, animate = true) { onItemSelected?.invoke(slot) }
            }
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
        // Pill indicator with generous equal margin from all 4 sides
        val indicatorMargin = barRadius * 0.32f   // 32% of barRadius — generous equal margin
        indPillH = barRadius - indicatorMargin
        indPillW = indPillH * 1.35f   // slightly narrower pill

        indicatorX = slotCentreX(selectedIndex)
        indicatorHolder.value = indicatorX

        // Glass stroke: bright at top, fades at bottom — light catching rim
        borderShinePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.argb(220, 255, 255, 255),   // top  — bright white
                Color.argb(140, 255, 255, 255),   // upper
                Color.argb(40,  255, 255, 255),   // lower
                Color.argb(8,   255, 255, 255)    // bottom — near invisible
            ),
            floatArrayOf(0f, 0.25f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )

        // indicatorBorderPaint shader rebuilt per-frame in onDraw
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

        canvas.restore()  // end pill clip

        // ── Layer 3: Indicator pill — drawn OUTSIDE pill clip ────
        // Must be outside clip so it can scale beyond bar bounds.
        val selItem = items.getOrNull(selectedIndex)
        if (selItem != null && !selItem.isCta) {
            val cx = indicatorX
            // Apply scale around the indicator centre
            val scaledH = indPillH * indicatorScale
            val scaledW = indPillW * indicatorScale
            val pillRadius = scaledH  // corner radius = half-height = pill ends
            indicatorRect.set(cx - scaledW, cy - scaledH, cx + scaledW, cy + scaledH)

            // 3a. Shadow
            canvas.drawRoundRect(indicatorRect, pillRadius, pillRadius, indicatorShadowPaint)

            // 3b. Deep blur clipped to scaled pill
            indClipPath.reset()
            indClipPath.addRoundRect(indicatorRect, pillRadius, pillRadius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(indClipPath)

            val deepBmp = deepBlurBitmap
            if (deepBmp != null && !deepBmp.isRecycled) {
                canvas.drawBitmap(deepBmp,
                    Rect(0, 0, deepBmp.width, deepBmp.height),
                    RectF(0f, 0f, w, h), bitmapPaint)
            } else {
                canvas.drawRoundRect(indicatorRect, pillRadius, pillRadius,
                    Paint().apply { color = Color.argb(210, 245, 245, 248) })
            }

            // 3c. Tint
            canvas.drawRoundRect(indicatorRect, pillRadius, pillRadius, indicatorTintPaint)
            canvas.restore()

            // 3d. Border
            indicatorBorderPaint.shader = LinearGradient(
                cx, cy - scaledH, cx, cy + scaledH,
                intArrayOf(
                    Color.argb(200, 255, 255, 255),
                    Color.argb(80,  255, 255, 255),
                    Color.argb(15,  200, 200, 200)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(indicatorRect, pillRadius, pillRadius, indicatorBorderPaint)
        }

        // ── Layer 4: Static glass stroke — no animation, no app colour ──
        // Outer soft glow lifts bar from background, inner gradient catches light at top
        canvas.drawRoundRect(barRect, barRadius, barRadius, borderGlowPaint)
        canvas.drawRoundRect(barRect, barRadius, barRadius, borderShinePaint)

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

                // Each drawable is mutated in setItems — independent tint state per item.
                // Selected/CTA: apply accent tint. Unselected: reset to drawable's own colours.
                if (item.isCta || isSelected) {
                    d.setTintMode(android.graphics.PorterDuff.Mode.SRC_IN)
                    d.setTint("#2E7D32".toColorInt())
                } else {
                    d.setTintList(null)    // clear tint — drawable uses its own selector colours
                    d.colorFilter = null   // belt-and-suspenders: clear any ColorFilter directly
                }
                d.alpha = 255
                d.draw(canvas)
            }
        }
    }

    private fun slotCentreX(index: Int) = hPad + slotWidth * index + slotWidth / 2f
}