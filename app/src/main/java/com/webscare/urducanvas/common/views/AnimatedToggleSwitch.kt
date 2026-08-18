package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import com.webscare.urducanvas.databinding.LayoutAnimatedToggleSwitchBinding

class AnimatedToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = LayoutAnimatedToggleSwitchBinding.inflate(LayoutInflater.from(context), this, true)

    private var isCheckedState = false
    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private val trackBg = GradientDrawable().apply {
        cornerRadius = 100f
    }
    private val thumbBg = GradientDrawable().apply {
        cornerRadius = 100f
    }

    init {
        binding.track.background = trackBg
        binding.thumb.background = thumbBg
        updateState(isCheckedState, animate = false)

        addPressEffect {
            vibrateSoft()
            setChecked(!isCheckedState, animate = true)
        }
    }

    fun isChecked(): Boolean = isCheckedState

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (isCheckedState != checked) {
            isCheckedState = checked
            updateState(checked, animate)
            onCheckedChangeListener?.invoke(checked)
        }
    }

    fun setCheckedQuietly(checked: Boolean) {
        if (isCheckedState != checked) {
            isCheckedState = checked
            updateState(checked, animate = false)
        }
    }

    private fun updateState(checked: Boolean, animate: Boolean) {
        val density = resources.displayMetrics.density
        val travelDist = 20f * density // 48dp track - 22dp thumb - 6dp margins = 20dp travel

        val offTrackColor = Color.parseColor("#DCDCDC")
        val onTrackColor  = Color.parseColor("#005D28")

        val offThumbColor = Color.WHITE
        val onThumbColor  = Color.WHITE

        val targetTrackColor = if (checked) onTrackColor else offTrackColor
        val targetThumbColor = if (checked) onThumbColor else offThumbColor
        val targetTranslationX = if (checked) travelDist else 0f

        if (animate) {
            val startTransX = binding.thumb.translationX
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 240
                interpolator = OvershootInterpolator(1.15f)
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    binding.thumb.translationX = startTransX + (targetTranslationX - startTransX) * fraction

                    val curTrackColor = blendColors(if (checked) offTrackColor else onTrackColor, targetTrackColor, fraction)
                    val curThumbColor = blendColors(if (checked) offThumbColor else onThumbColor, targetThumbColor, fraction)

                    trackBg.setColor(curTrackColor)
                    thumbBg.setColor(curThumbColor)
                }
            }
            anim.start()
        } else {
            binding.thumb.translationX = targetTranslationX
            trackBg.setColor(targetTrackColor)
            thumbBg.setColor(targetThumbColor)
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio
        val g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio
        val b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }
}
