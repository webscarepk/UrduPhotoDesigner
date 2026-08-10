package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.webscare.urducanvas.databinding.LayoutAnimatedThemeSwitchBinding

class AnimatedThemeSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = LayoutAnimatedThemeSwitchBinding.inflate(LayoutInflater.from(context), this, true)

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

        setOnClickListener {
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
        val travelDist = 28f * density // 64dp track - 30dp thumb - 6dp margins = 28dp travel

        val offTrackColor = Color.parseColor("#DCDCDC")
        val onTrackColor  = Color.parseColor("#276738")

        val offThumbColor = Color.WHITE
        val onThumbColor  = Color.parseColor("#52B788")

        val targetTrackColor = if (checked) onTrackColor else offTrackColor
        val targetThumbColor = if (checked) onThumbColor else offThumbColor
        val targetTranslationX = if (checked) travelDist else 0f

        if (animate) {
            val startTransX = binding.thumb.translationX
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260
                interpolator = OvershootInterpolator(1.15f)
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    binding.thumb.translationX = startTransX + (targetTranslationX - startTransX) * fraction

                    val curTrackColor = blendColors(if (checked) offTrackColor else onTrackColor, targetTrackColor, fraction)
                    val curThumbColor = blendColors(if (checked) offThumbColor else onThumbColor, targetThumbColor, fraction)

                    trackBg.setColor(curTrackColor)
                    thumbBg.setColor(curThumbColor)

                    binding.iconSun.alpha = if (checked) (1f - fraction) else fraction
                    binding.iconMoon.alpha = if (checked) fraction else (1f - fraction)
                }
            }
            anim.start()
        } else {
            binding.thumb.translationX = targetTranslationX
            trackBg.setColor(targetTrackColor)
            thumbBg.setColor(targetThumbColor)
            binding.iconSun.alpha  = if (checked) 0f else 1f
            binding.iconMoon.alpha = if (checked) 1f else 0f
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
