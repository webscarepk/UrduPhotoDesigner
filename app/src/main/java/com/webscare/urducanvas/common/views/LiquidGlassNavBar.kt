package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.toColorInt
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.*

class LiquidGlassNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class NavItem(
        val id: Int,
        val iconDrawable: Drawable?,
        val isCta: Boolean = false
    )

    private val items = mutableListOf<NavItem>()
    private var selectedIndex = 0
    private var onItemSelected: ((Int) -> Unit)? = null

    private var barBlurBitmap:  Bitmap? = null
    private var deepBlurBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Offscreen: nav bar with ALL icons filled. Pill clips into this as lens.
    private var filledNavBitmap: Bitmap? = null
    private var filledNavCanvas: Canvas? = null

    // ── Paints ────────────────────────────────────────────────────────────────
    private val barTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255); style = Paint.Style.FILL
    }
    // Bar border — completely removed, no stroke against background
    private val borderGlowPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 0 }
    private val borderShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 0 }
    private val indicatorTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 255); style = Paint.Style.FILL
    }
    private val indicatorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 0, 0, 0); style = Paint.Style.FILL
    }
    // Pill border — NO solid outline stroke. Only a very faint inner glow
    // that blends with content and disappears on white backgrounds.
    // We achieve this by drawing it INSIDE the clip with low alpha.
    private val pillInnerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        // Drawn inside clip so it composites with the pill content, not against bg
    }
    private val lensHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // Inner-edge distortion — simulates thick curved glass refracting light
    // at the inner surface. Two overlapping sweep-gradient strokes.
    private val distortTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val distortBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val distortRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f
        color = Color.argb(18, 0, 0, 0)  // subtle inner shadow depth
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    private val barRect   = RectF()
    private var barRadius = 0f
    private var slotWidth = 0f
    private var iconSize  = 0; private var iconSizeCta = 0
    private var hPad      = 0f
    private var indPillH  = 0f
    private var indPillW  = 0f
    private val indicatorRect = RectF()

    companion object {
        private const val MAX_INDICATOR_SCALE = 1.18f
        private const val MAX_ELONGATION      = 1.85f
        private const val MAX_V_SQUISH        = 0.80f
        private const val DRAG_MAGNIFY_MAX    = 1.32f
        private const val VEL_NORM            = 26f
        private const val TAPER_STRENGTH      = 0.55f
        private const val LENS_ZOOM           = 1.45f  // magnification for filled icon reveal
    }

    // ── Spring / morph state ──────────────────────────────────────────────────
    private var indicatorX = 0f
    private val indicatorHolder = FloatValueHolder(0f)
    private val springAnim: SpringAnimation by lazy {
        SpringAnimation(indicatorHolder).apply {
            spring = SpringForce().apply { stiffness = 180f; dampingRatio = 0.72f }
            addUpdateListener { _, value, _ ->
                indicatorX = value; updateFluidMorph(); invalidate()
            }
            addEndListener { _, _, _, _ ->
                stretchX = 1f; squishY = 1f; taper = 0f
                indicatorGlobalScale = 1f; invalidate()
            }
        }
    }

    private var journeyFrom = 0f; private var journeyTo = 0f
    private var indicatorGlobalScale = 1f
    private var stretchX = 1f; private var squishY = 1f; private var taper = 0f
    private var lastIndicatorX = 0f

    private var lensActive       = false
    private var dragMagnifyScale = 1f
    private var isDragging       = false
    private var dragHoverSlot    = -1

    private val iconScales        = mutableListOf<Float>()
    private val iconSpringHolders = mutableListOf<FloatValueHolder>()
    private val iconSpringAnims   = mutableListOf<SpringAnimation>()
    private val iconAlphas        = mutableListOf<Float>()

    private var dragStartX     = 0f
    private var dragSlotOnDown = -1
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private val clipPath      = Path()
    private val indClipPath   = Path()
    private val pillPath      = Path()
    private val pillInsetPath = Path()

    // ── Fluid morph ───────────────────────────────────────────────────────────
    private fun updateFluidMorph() {
        val vel    = indicatorX - lastIndicatorX
        lastIndicatorX = indicatorX
        val signed = (vel / VEL_NORM).coerceIn(-1f, 1f)
        val t      = abs(signed)
        val targetStretch = 1f + (MAX_ELONGATION - 1f) * t
        val targetSquish  = (1f / sqrt(targetStretch)).coerceAtLeast(MAX_V_SQUISH)
        val targetTaper   = -signed * TAPER_STRENGTH
        val rising = t > (abs(stretchX - 1f) / (MAX_ELONGATION - 1f))
        val ease   = if (rising) 0.40f else 0.16f
        stretchX = lerp(stretchX, targetStretch, ease)
        squishY  = lerp(squishY,  targetSquish,  ease)
        taper    = lerp(taper,    targetTaper,   ease)
        val dist = abs(journeyTo - journeyFrom)
        if (dist > 1f) {
            val progress = (1f - abs(journeyTo - indicatorX) / dist).coerceIn(0f, 1f)
            val sine = sin(Math.PI * progress.toDouble()).toFloat()
            indicatorGlobalScale = 1f + (MAX_INDICATOR_SCALE - 1f) * sine
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun setItems(navItems: List<NavItem>) {
        items.clear(); iconScales.clear(); iconAlphas.clear()
        iconSpringHolders.clear(); iconSpringAnims.clear()
        navItems.forEach { item ->
            items.add(item.copy(iconDrawable = item.iconDrawable?.mutate()))
            iconScales.add(1f)
            iconAlphas.add(if (item.isCta) 1f else 0.42f)
            val holder = FloatValueHolder(1f)
            iconSpringHolders.add(holder)
            val idx = items.size - 1
            val anim = SpringAnimation(holder).apply {
                spring = SpringForce(1f).apply { stiffness = 500f; dampingRatio = 0.55f }
                addUpdateListener { _, v, _ ->
                    if (idx < iconScales.size) { iconScales[idx] = v; invalidate() }
                }
            }
            iconSpringAnims.add(anim)
        }
        if (items.isNotEmpty()) iconAlphas[selectedIndex] = 1f
        requestLayout(); invalidate()
    }

    fun setOnItemSelectedListener(l: (Int) -> Unit) { onItemSelected = l }

    fun selectItem(index: Int, animate: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (items.getOrNull(index)?.isCta == true) return
        if (index == selectedIndex && animate) return
        val prev = selectedIndex; selectedIndex = index
        updateIconAlphas(prev, index)
        val target = slotCentreX(index)
        if (animate && width > 0) {
            journeyFrom = indicatorX; journeyTo = target
            indicatorHolder.value = indicatorX
            springAnim.cancel(); springAnim.animateToFinalPosition(target)
            postNavigation(onComplete)
        } else {
            springAnim.cancel()
            indicatorX = target; indicatorGlobalScale = 1f
            stretchX = 1f; squishY = 1f; taper = 0f
            indicatorHolder.value = target; lastIndicatorX = target
            invalidate(); onComplete?.invoke()
        }
    }

    private var pendingNav: Runnable? = null
    private fun postNavigation(onComplete: (() -> Unit)?) {
        pendingNav?.let { removeCallbacks(it) }
        if (onComplete == null) return
        val r = Runnable { onComplete.invoke() }; pendingNav = r
        postDelayed(r, 130L)
    }

    fun updateBlur(bitmap: Bitmap) {
        barBlurBitmap?.recycle(); barBlurBitmap = bitmap; invalidate()
    }
    fun updateIndicatorBlur(bitmap: Bitmap) {
        deepBlurBitmap?.recycle(); deepBlurBitmap = bitmap; invalidate()
    }

    // ── Icon helpers ──────────────────────────────────────────────────────────
    private fun updateIconAlphas(deselected: Int, selected: Int) {
        if (deselected in iconAlphas.indices && !items[deselected].isCta) animateIconAlpha(deselected, 0.42f)
        if (selected   in iconAlphas.indices && !items[selected].isCta)   animateIconAlpha(selected, 1f)
    }
    private fun animateIconAlpha(index: Int, target: Float) {
        val start = iconAlphas[index]
        ValueAnimator.ofFloat(start, target).apply {
            duration = 220L; interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { iconAlphas[index] = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    private fun pressIcon(index: Int) {
        if (index !in iconSpringAnims.indices) return
        iconSpringAnims[index].cancel()
        iconSpringHolders[index].value = 0.82f; iconScales[index] = 0.82f
        iconSpringAnims[index].animateToFinalPosition(1f)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnim.cancel(); pendingNav?.let { removeCallbacks(it) }
        velocityTracker?.recycle(); velocityTracker = null
        iconSpringAnims.forEach { it.cancel() }
        filledNavBitmap?.recycle(); filledNavBitmap = null
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker = velocityTracker ?: VelocityTracker.obtain()
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false; dragStartX = event.x
                dragSlotOnDown = slotIndexAt(event.x); dragHoverSlot = dragSlotOnDown
                items.getOrNull(dragSlotOnDown)?.let { pressIcon(dragSlotOnDown) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && abs(event.x - dragStartX) > touchSlop) {
                    isDragging = true; lensActive = true
                    springAnim.cancel(); journeyFrom = indicatorX; journeyTo = indicatorX
                }
                if (isDragging) {
                    dragMagnifyScale = lerp(dragMagnifyScale, DRAG_MAGNIFY_MAX, 0.10f)
                    val firstCX  = slotCentreX(0); val lastCX = slotCentreX(items.size - 1)
                    val clampedX = event.x.coerceIn(firstCX, lastCX)
                    val nearestCX = slotCentreX(nearestNonCtaSlot(clampedX))
                    val halfSlot  = slotWidth / 2f
                    val pull = (1f - (abs(clampedX - nearestCX) / halfSlot).coerceIn(0f, 1f)) * 0.35f
                    val magnetX = clampedX - (clampedX - nearestCX) * pull
                    val prev = indicatorX
                    indicatorX = lerp(indicatorX, magnetX, 0.55f)
                    lastIndicatorX = prev
                    // Track nearest slot — includes CTA so CTA also gets reveal
                    dragHoverSlot = nearestSlotIncludingCta(indicatorX)
                    updateFluidMorph(); invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val fling = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null
                lensActive = false; dragHoverSlot = -1
                if (isDragging) {
                    isDragging = false
                    val snapSlot = nearestNonCtaSlot(indicatorX + fling * 0.04f)
                    journeyFrom = indicatorX; journeyTo = slotCentreX(snapSlot)
                    indicatorHolder.value = indicatorX
                    springAnim.cancel(); springAnim.setStartVelocity(fling)
                    if (snapSlot != selectedIndex) {
                        selectItem(snapSlot, animate = true) { onItemSelected?.invoke(snapSlot) }
                    } else {
                        springAnim.animateToFinalPosition(slotCentreX(snapSlot))
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val slot = slotIndexAt(event.x)
                    val item = items.getOrNull(slot) ?: return true
                    if (item.isCta) onItemSelected?.invoke(slot)
                    else selectItem(slot, animate = true) { onItemSelected?.invoke(slot) }
                    performClick()
                }
            }
        }
        return true
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val dp = resources.displayMetrics.density
        barRect.set(0f, 0f, w.toFloat(), h.toFloat()); barRadius = h / 2f
        hPad = 14f * dp
        if (items.isNotEmpty()) slotWidth = (w - hPad * 2f) / items.size.toFloat()
        iconSize = (22 * dp).toInt(); iconSizeCta = (26 * dp).toInt()
        val indicatorMargin = barRadius * 0.32f
        indPillH = barRadius - indicatorMargin; indPillW = indPillH * 1.35f
        indicatorX = slotCentreX(selectedIndex)
        lastIndicatorX = indicatorX; indicatorHolder.value = indicatorX

        filledNavBitmap?.recycle()
        filledNavBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        filledNavCanvas = Canvas(filledNavBitmap!!)
    }

    // ── Offscreen filled-nav (lens content) ───────────────────────────────────
    /**
     * Renders bar background + ALL icons (including CTA) in filled/solid state
     * at their REAL fixed slot positions. The pill clips into this — pure reveal
     * lens. Nothing translates with the pill.
     *
     * The filled icon is drawn at LENS_ZOOM scale anchored on its slot centre
     * directly into the offscreen, so when the pill samples it at 1:1 the icon
     * appears magnified and on top of everything — solid, full opacity.
     */
    private fun renderFilledNav(hoverSlot: Int) {
        val bmp = filledNavBitmap ?: return
        val c   = filledNavCanvas ?: return
        val w   = bmp.width.toFloat(); val h = bmp.height.toFloat(); val cy = h / 2f

        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Bar background
        val clipP = Path().apply {
            addRoundRect(RectF(0f, 0f, w, h), barRadius, barRadius, Path.Direction.CW)
        }
        c.save(); c.clipPath(clipP)
        val barBmp = barBlurBitmap
        if (barBmp != null && !barBmp.isRecycled)
            c.drawBitmap(barBmp, Rect(0, 0, barBmp.width, barBmp.height),
                RectF(0f, 0f, w, h), bitmapPaint)
        else
            c.drawColor(Color.argb(160, 240, 240, 242))
        c.drawRoundRect(RectF(0f, 0f, w, h), barRadius, barRadius, barTintPaint)
        c.restore()

        // Draw all icons in filled state.
        // Hovered slot is drawn ONCE at magnified size (replaces the normal draw).
        // All other slots drawn at normal size.
        // Result: exactly one icon per slot, hovered one just bigger — no duplicates.
        items.forEachIndexed { index, item ->
            val iconCX = slotCentreX(index)   // always fixed
            val baseSize = if (item.isCta) iconSizeCta else iconSize
            val size = if (index == hoverSlot) (baseSize * LENS_ZOOM).toInt() else baseSize
            item.iconDrawable?.let { d ->
                d.state = if (item.isCta) intArrayOf() else intArrayOf(android.R.attr.state_checked)
                d.setBounds(
                    (iconCX - size / 2f).toInt(), (cy - size / 2f).toInt(),
                    (iconCX + size / 2f).toInt(), (cy + size / 2f).toInt()
                )
                d.setTintMode(PorterDuff.Mode.SRC_IN)
                d.setTint("#2E7D32".toColorInt())
                d.alpha = 255
                d.draw(c)
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        if (!isDragging) dragMagnifyScale = lerp(dragMagnifyScale, 1f, 0.16f)

        val cy = height / 2f; val w = width.toFloat(); val h = height.toFloat()

        // Layer 1: Bar background clipped to rounded rect
        clipPath.reset()
        clipPath.addRoundRect(barRect, barRadius, barRadius, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clipPath)
        val barBmp = barBlurBitmap
        if (barBmp != null && !barBmp.isRecycled)
            canvas.drawBitmap(barBmp, Rect(0, 0, barBmp.width, barBmp.height),
                RectF(0f, 0f, w, h), bitmapPaint)
        else
            canvas.drawColor(Color.argb(180, 30, 30, 32))
        canvas.drawRoundRect(barRect, barRadius, barRadius, barTintPaint)
        canvas.restore()

        // Layers 2–5: Indicator pill (outside bar clip — can overshoot edges)
        val selItem = items.getOrNull(selectedIndex)
        if (selItem != null && !selItem.isCta) drawIndicatorPill(canvas, cy, w, h)

        // Layer 6: Bar border — very faint, almost invisible on white
        canvas.drawRoundRect(barRect, barRadius, barRadius, borderGlowPaint)
        canvas.drawRoundRect(barRect, barRadius, barRadius, borderShinePaint)

        // Layer 7: Icons
        drawIcons(canvas, cy)

        if (isDragging || abs(dragMagnifyScale - 1f) > 0.001f) invalidate()
    }

    private fun drawIndicatorPill(canvas: Canvas, cy: Float, w: Float, h: Float) {
        val cx = indicatorX
        val fScaleX = indicatorGlobalScale * stretchX * dragMagnifyScale
        val fScaleY = indicatorGlobalScale * squishY  * dragMagnifyScale
        val scaledH = indPillH * fScaleY
        val scaledW = indPillW * fScaleX

        buildTeardropPath(pillPath, cx, cy, scaledW, scaledH, taper)
        indicatorRect.set(cx - scaledW, cy - scaledH, cx + scaledW, cy + scaledH)

        // Shadow
        canvas.drawPath(pillPath, indicatorShadowPaint)

        // ── Interior clip ─────────────────────────────────────────────────────
        indClipPath.set(pillPath)
        canvas.save(); canvas.clipPath(indClipPath)

        if (lensActive) {
            // LENS MODE: draw filled-nav bitmap at 1:1 inside the pill.
            // The hovered icon is already pre-magnified in the offscreen, so
            // the pill simply reveals it — no zoom transform here, no sliding.
            renderFilledNav(dragHoverSlot)
            filledNavBitmap?.let { offBmp ->
                if (!offBmp.isRecycled)
                    canvas.drawBitmap(offBmp, 0f, 0f, bitmapPaint)
            }
        } else {
            // REST / SPRING: plain blur
            val deepBmp = deepBlurBitmap
            when {
                deepBmp != null && !deepBmp.isRecycled ->
                    canvas.drawBitmap(deepBmp, Rect(0, 0, deepBmp.width, deepBmp.height),
                        RectF(0f, 0f, w, h), bitmapPaint)
                barBmp != null && !barBmp!!.isRecycled ->
                    canvas.drawBitmap(barBmp!!, Rect(0, 0, barBmp!!.width, barBmp!!.height),
                        RectF(0f, 0f, w, h), bitmapPaint)
                else ->
                    canvas.drawPath(pillPath, Paint().apply { color = Color.argb(200, 50, 50, 55) })
            }
        }

        // Frost tint
        canvas.drawPath(pillPath, indicatorTintPaint)

        // ── Inner-edge distortion ─────────────────────────────────────────────
        // Drawn INSIDE the pill clip. Two sweep-gradient arcs simulate the way
        // thick curved glass bends and disperses light at its inner surface —
        // bright warm arc on top (light entering), cool arc on bottom (exiting).
        // A dark rim stroke adds depth / "thickness" of the glass.
        val inset = (scaledH * 0.18f).coerceIn(3f, 7f)
        buildTeardropPath(pillInsetPath, cx, cy, scaledW - inset, scaledH - inset, taper)

        // Top arc: warm white → cyan → transparent sweep
        distortTopPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(0,   255, 255, 255),
                Color.argb(110, 255, 255, 240),
                Color.argb(80,  180, 235, 255),
                Color.argb(55,  100, 180, 255),
                Color.argb(20,  80,  140, 220),
                Color.argb(0,   255, 255, 255)
            ),
            floatArrayOf(0f, 0.10f, 0.28f, 0.45f, 0.60f, 1f)
        )
        canvas.drawPath(pillInsetPath, distortTopPaint)

        // Bottom arc: blue-purple dispersion sweep
        distortBottomPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(0,   80,  100, 200),
                Color.argb(45,  100, 140, 255),
                Color.argb(35,  160,  90, 220),
                Color.argb(20,  200, 120, 180),
                Color.argb(0,   80,  100, 200)
            ),
            floatArrayOf(0.42f, 0.55f, 0.70f, 0.85f, 1f)
        )
        canvas.drawPath(pillInsetPath, distortBottomPaint)

        // Dark inner rim — sells the glass thickness / depth
        canvas.drawPath(pillInsetPath, distortRimPaint)

        // Inner glow on the pill border itself (composited inside, not against bg)
        pillInnerGlowPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(80,  255, 255, 255),
                Color.argb(40,  255, 255, 255),
                Color.argb(10,  200, 200, 200),
                Color.argb(80,  255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f)
        )
        // Draw just inside the pill edge so it composites with content not bg
        buildTeardropPath(pillInsetPath, cx, cy, scaledW - 1f, scaledH - 1f, taper)
        canvas.drawPath(pillInsetPath, pillInnerGlowPaint)

        canvas.restore()
        // ─────────────────────────────────────────────────────────────────────

        // Top specular highlight — outside clip, on top of everything
        val hiH   = scaledH * 0.36f; val hiW = scaledW * 0.58f
        val hiTop = cy - scaledH + scaledH * 0.10f
        lensHighlightPaint.shader = LinearGradient(
            cx, hiTop, cx, hiTop + hiH,
            intArrayOf(Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(cx - hiW, hiTop, cx + hiW, hiTop + hiH), lensHighlightPaint)

        // NO external border stroke — nothing drawn outside the pill shape
        // so there is no visible outline against white backgrounds
    }

    private fun buildTeardropPath(
        out: Path, cx: Float, cy: Float, halfW: Float, halfH: Float, taper: Float
    ) {
        out.reset()
        val tp  = taper.coerceIn(-1f, 1f); val mag = abs(tp)
        val fullR = halfH; val thinR = halfH * (1f - 0.6f * mag)
        val leftR  = if (tp < 0f) thinR else fullR
        val rightR = if (tp > 0f) thinR else fullR
        val leftHalfH  = halfH * (if (tp < 0f) (1f - 0.18f * mag) else 1f)
        val rightHalfH = halfH * (if (tp > 0f) (1f - 0.18f * mag) else 1f)
        val l = cx - halfW; val r = cx + halfW
        out.moveTo(l + leftR, cy - leftHalfH)
        out.lineTo(r - rightR, cy - rightHalfH)
        out.cubicTo(r - rightR * 0.45f, cy - rightHalfH, r, cy - rightHalfH * 0.45f, r, cy)
        out.cubicTo(r, cy + rightHalfH * 0.45f, r - rightR * 0.45f, cy + rightHalfH, r - rightR, cy + rightHalfH)
        out.lineTo(l + leftR, cy + leftHalfH)
        out.cubicTo(l + leftR * 0.45f, cy + leftHalfH, l, cy + leftHalfH * 0.45f, l, cy)
        out.cubicTo(l, cy - leftHalfH * 0.45f, l + leftR * 0.45f, cy - leftHalfH, l + leftR, cy - leftHalfH)
        out.close()
    }

    private fun drawIcons(canvas: Canvas, cy: Float) {
        items.forEachIndexed { index, item ->
            val iconCX     = slotCentreX(index)
            val isSelected = (index == selectedIndex) && !item.isCta
            val size       = if (item.isCta) iconSizeCta else iconSize
            val iconScale  = iconScales.getOrElse(index) { 1f }
            val iconAlpha  = iconAlphas.getOrElse(index) { 1f }
            item.iconDrawable?.let { d ->
                d.state = if (isSelected) intArrayOf(android.R.attr.state_checked)
                else            intArrayOf(-android.R.attr.state_checked)
                val s = (size * iconScale).toInt()
                d.setBounds((iconCX - s/2f).toInt(), (cy - s/2f).toInt(),
                    (iconCX + s/2f).toInt(), (cy + s/2f).toInt())
                if (item.isCta || isSelected) {
                    d.setTintMode(PorterDuff.Mode.SRC_IN); d.setTint("#2E7D32".toColorInt())
                } else { d.setTintList(null); d.colorFilter = null }
                d.alpha = (iconAlpha * 255).toInt().coerceIn(0, 255)
                d.draw(canvas)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun slotCentreX(index: Int) = hPad + slotWidth * index + slotWidth / 2f
    private fun slotIndexAt(x: Float) = ((x - hPad) / slotWidth).toInt().coerceIn(0, items.size - 1)

    /** For drag snap — skips CTA (pill can't land on CTA) */
    private fun nearestNonCtaSlot(x: Float): Int {
        var best = selectedIndex; var bestDist = Float.MAX_VALUE
        items.forEachIndexed { i, item ->
            if (!item.isCta) { val d = abs(slotCentreX(i) - x); if (d < bestDist) { bestDist = d; best = i } }
        }
        return best
    }

    /** For hover tracking — includes CTA so it also gets the filled reveal */
    private fun nearestSlotIncludingCta(x: Float): Int {
        var best = 0; var bestDist = Float.MAX_VALUE
        items.forEachIndexed { i, _ ->
            val d = abs(slotCentreX(i) - x); if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private val barBmp get() = barBlurBitmap
}