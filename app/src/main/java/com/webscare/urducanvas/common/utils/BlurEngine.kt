package com.webscare.urducanvas.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import java.lang.ref.WeakReference

/**
 * BlurEngine — captures the exact pixels that sit behind the nav bar.
 *
 * APPROACH:
 *   The source view is the root ConstraintLayout (the PARENT of both the
 *   fragment host AND the nav bar). Drawing the root excludes the nav bar
 *   itself only if we hide it before drawing and restore after — but that
 *   causes flicker. Instead we draw the root (which includes everything),
 *   then in the crop step we get the exact region behind the bar.
 *
 *   To avoid the bar appearing in its own blur we draw the root view but
 *   TRANSLATE the canvas so the nav bar's own pixels land outside the
 *   capture area. Since we only crop to the bar's rect, and the bar is
 *   at the bottom, we just need to ensure source.draw() renders content
 *   behind it — which it does because the fragment host is drawn BEFORE
 *   the nav bar in z-order (nav bar has higher translationZ).
 *
 *   In practice: source = root ConstraintLayout. Root draws:
 *     1. FragmentContainerView (content)   ← this is what we want
 *     2. LiquidGlassNavBar                 ← also drawn, ON TOP
 *   We crop the region corresponding to the bar's position from the
 *   snapshot. At that position the fragment content pixels are under the
 *   bar pixels. But since we draw into a fresh bitmap and the bar draws
 *   on top, the crop will contain bar pixels, not content pixels.
 *
 *   REAL FIX: pass the FragmentContainerView as sourceView. It is a
 *   sibling of the nav bar, spans the FULL screen (including behind the
 *   bar), and draw() on it NEVER includes the nav bar. The fragment's
 *   RecyclerView content extends behind the bar because the layout sets
 *   nav_host to fill parent fully. So the crop from the fragment host
 *   at the bar's position = exactly the content pixels behind the bar.
 *
 * Thread model:
 *   Phase 1  MAIN THREAD  source.draw() → snapshot Bitmap
 *   Phase 2  BLUR THREAD  crop → blur (bar) + blur (indicator) → post back
 *
 * No View is ever touched from the blur thread.
 */
class BlurEngine(
    sourceView: View,   // FragmentContainerView — full-screen, sibling of nav bar
    navBar: com.webscare.urducanvas.common.views.LiquidGlassNavBar
) {
    private val sourceRef = WeakReference(sourceView)
    private val navBarRef = WeakReference(navBar)

    private val blurThread  = HandlerThread("BlurEngine").also { it.start() }
    private val blurHandler = Handler(blurThread.looper)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    @Volatile private var pending = false

    // Reusable capture bitmap — scaled-down copy of sourceView
    // Recreated only when dimensions change
    private var captureBmp:    Bitmap? = null
    private var captureCanvas: Canvas? = null
    private var lastCW = 0
    private var lastCH = 0

    // Bar position in source-view local coordinates (refreshed every frame on main thread)
    // These are plain Ints — safe to read from blur thread after being written on main thread
    @Volatile private var barRelLeft = 0
    @Volatile private var barRelTop  = 0
    @Volatile private var barW       = 0
    @Volatile private var barH       = 0

    private val srcPos = IntArray(2)
    private val barPos = IntArray(2)

    companion object {
        // Draw sourceView at this fraction before blurring.
        // 0.35 = enough resolution for a smooth blur, fast enough for 30 fps.
        private const val CAPTURE_SCALE = 0.35f
    }

    // ── Public API ───────────────────────────────────────────────────

    fun updatePositions() {
        val src = sourceRef.get() ?: return
        val bar = navBarRef.get() ?: return
        src.getLocationOnScreen(srcPos)
        bar.getLocationOnScreen(barPos)
        barRelLeft = barPos[0] - srcPos[0]
        barRelTop  = barPos[1] - srcPos[1]
        barW       = bar.width
        barH       = bar.height
    }

    /** Must be called on the MAIN THREAD. Throttled — skipped if already in flight. */
    fun scheduleCapture() {
        if (pending) return
        pending = true
        doMainThreadCapture()
    }

    /**
     * Force an immediate capture regardless of pending state.
     * Call this when RecyclerView scroll reaches IDLE — ensures the blur
     * updates even when content behind is plain white (end of list).
     */
    fun forceCapture() {
        pending = false   // reset so next scheduleCapture goes through immediately
        scheduleCapture()
    }

    fun startContinuous() { mainHandler.post(loopRunnable) }
    fun stopContinuous()  { mainHandler.removeCallbacks(loopRunnable) }

    private val loopRunnable = object : Runnable {
        override fun run() {
            scheduleCapture()
            mainHandler.postDelayed(this, 16L)  // 60fps — keeps up with any scroll speed
        }
    }

    /**
     * Drop-in RecyclerView.OnScrollListener implementation.
     * Attach to every RecyclerView that sits behind the nav bar:
     *
     *   recyclerView.addOnScrollListener(blurEngine.scrollListener)
     *
     * Handles both vertical and horizontal scroll, plus snap-to-idle force.
     */
    val scrollListener = object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
        override fun onScrolled(
            recyclerView: androidx.recyclerview.widget.RecyclerView,
            dx: Int, dy: Int
        ) {
            // Called on every scroll pixel — scheduleCapture is throttled internally
            scheduleCapture()
        }

        override fun onScrollStateChanged(
            recyclerView: androidx.recyclerview.widget.RecyclerView,
            newState: Int
        ) {
            // Force a fresh capture when scroll fully stops — catches the
            // "scrolled to white bottom" and "images just loaded" cases
            if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                forceCapture()
            }
        }
    }

    // ── Phase 1 — main thread ────────────────────────────────────────

    private fun doMainThreadCapture() {
        val source = sourceRef.get() ?: run { pending = false; return }

        val vw = source.width
        val vh = source.height
        if (vw <= 0 || vh <= 0) { pending = false; return }

        // Refresh bar position every frame
        updatePositions()
        val bw = barW; val bh = barH
        if (bw <= 0 || bh <= 0) { pending = false; return }

        val cw = (vw * CAPTURE_SCALE).toInt().coerceAtLeast(1)
        val ch = (vh * CAPTURE_SCALE).toInt().coerceAtLeast(1)

        if (cw != lastCW || ch != lastCH || captureBmp == null) {
            captureBmp?.recycle()
            captureBmp    = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            captureCanvas = Canvas(captureBmp!!)
            lastCW = cw; lastCH = ch
        }

        val canvas = captureCanvas!!
        canvas.drawColor(0xFFFFFFFF.toInt())      // white base — reflects reality when content is empty/white
        canvas.save()
        canvas.scale(CAPTURE_SCALE, CAPTURE_SCALE)
        // sourceView = FragmentContainerView. It is full-screen (fills parent in XML).
        // Drawing it here renders exactly the content behind the nav bar.
        // Nav bar is a SIBLING — it will never appear here.
        source.draw(canvas)
        canvas.restore()

        // Snapshot: mutable copy owned exclusively by blur thread
        val snapshot = captureBmp!!.copy(Bitmap.Config.ARGB_8888, true)

        // Capture the bar geometry we already refreshed above
        val snapRelL = barRelLeft
        val snapRelT = barRelTop

        blurHandler.post {
            doBlur(snapshot, vw, vh, bw, bh, snapRelL, snapRelT)
        }
    }

    // ── Phase 2 — blur thread (no View access whatsoever) ────────────

    private fun doBlur(
        snapshot: Bitmap,
        vw: Int, vh: Int,
        bw: Int, bh: Int,
        relLeft: Int, relTop: Int
    ) {
        try {
            val sw = snapshot.width.toFloat()
            val sh = snapshot.height.toFloat()

            // Map bar rect from source-view pixels → snapshot pixels
            val sx = sw / vw
            val sy = sh / vh

            val cropL = (relLeft * sx).toInt().coerceIn(0, snapshot.width  - 1)
            val cropT = (relTop  * sy).toInt().coerceIn(0, snapshot.height - 1)
            val cropW = (bw      * sx).toInt().coerceIn(1, snapshot.width  - cropL)
            val cropH = (bh      * sy).toInt().coerceIn(1, snapshot.height - cropT)

            // Exact crop = pixels that sit behind the bar
            val region = Bitmap.createBitmap(snapshot, cropL, cropT, cropW, cropH)
            snapshot.recycle()

            // ── Bar blur — 3 passes radius 4 — visible frosted glass ─
            val barWork = region.copy(Bitmap.Config.ARGB_8888, true)
            repeat(3) { boxBlur(barWork, 4) }   // same as indicator — frosted glass effect
            val barOut = Bitmap.createScaledBitmap(barWork, bw, bh, true)
            barWork.recycle()

            // ── Indicator blur — same region, 5 passes radius 5 ──────
            // Same source region, more passes → same hue but denser blur
            // = looks like a thicker slab of the same glass
            val indWork = region.copy(Bitmap.Config.ARGB_8888, true)
            repeat(3) { boxBlur(indWork, 4) }   // denser than bar but not excessive
            val indOut = Bitmap.createScaledBitmap(indWork, bw, bh, true)
            indWork.recycle()
            region.recycle()

            mainHandler.post {
                pending = false
                navBarRef.get()?.apply {
                    updateBlur(barOut)
                    updateIndicatorBlur(indOut)
                }
            }
        } catch (e: Exception) {
            pending = false
            e.printStackTrace()
            try { snapshot.recycle() } catch (_: Exception) {}
        }
    }

    // ── Box blur ─────────────────────────────────────────────────────

    private fun boxBlur(bmp: Bitmap, r: Int) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        blurH(px, w, h, r)
        blurV(px, w, h, r)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun blurH(px: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1; val out = IntArray(w * h)
        for (y in 0 until h) {
            var rs = 0; var gs = 0; var bs = 0
            for (k in -r..r) { val c = px[y*w + k.coerceIn(0,w-1)]; rs += (c shr 16)and 0xFF; gs += (c shr 8)and 0xFF; bs += c and 0xFF }
            for (x in 0 until w) {
                out[y*w+x] = (0xFF shl 24) or ((rs/div) shl 16) or ((gs/div) shl 8) or (bs/div)
                val a = px[y*w+(x+r+1).coerceIn(0,w-1)]; val s = px[y*w+(x-r).coerceIn(0,w-1)]
                rs += ((a shr 16)and 0xFF)-((s shr 16)and 0xFF); gs += ((a shr 8)and 0xFF)-((s shr 8)and 0xFF); bs += (a and 0xFF)-(s and 0xFF)
            }
        }
        out.copyInto(px)
    }

    private fun blurV(px: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1; val out = IntArray(w * h)
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (k in -r..r) { val c = px[k.coerceIn(0,h-1)*w+x]; rs += (c shr 16)and 0xFF; gs += (c shr 8)and 0xFF; bs += c and 0xFF }
            for (y in 0 until h) {
                out[y*w+x] = (0xFF shl 24) or ((rs/div) shl 16) or ((gs/div) shl 8) or (bs/div)
                val a = px[(y+r+1).coerceIn(0,h-1)*w+x]; val s = px[(y-r).coerceIn(0,h-1)*w+x]
                rs += ((a shr 16)and 0xFF)-((s shr 16)and 0xFF); gs += ((a shr 8)and 0xFF)-((s shr 8)and 0xFF); bs += (a and 0xFF)-(s and 0xFF)
            }
        }
        out.copyInto(px)
    }

    fun destroy() {
        stopContinuous()
        blurHandler.removeCallbacksAndMessages(null)
        blurThread.quitSafely()
        captureBmp?.recycle(); captureBmp = null
    }
}