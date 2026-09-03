package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.SeekBar
import com.webscare.urducanvas.databinding.ViewCustom3dSliderBinding

class Custom3DSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewCustom3dSliderBinding.inflate(LayoutInflater.from(context), this, true)

    var label: String = ""
        set(v) {
            field = v
            binding.tvSliderLabel.text = v
        }

    var unit: String = ""
        set(v) {
            field = v
            updateValueDisplay()
        }

    var minValue: Int = 0
        set(v) {
            field = v
            updateProgressBounds()
        }

    var maxValue: Int = 100
        set(v) {
            field = v
            updateProgressBounds()
        }

    private var _value: Int = 0

    var value: Int
        get() = _value
        set(v) {
            val clamped = v.coerceIn(minValue, maxValue)
            if (_value != clamped) {
                _value = clamped
                updateValueDisplay()
                val progress = clamped - minValue
                if (binding.seekBar.progress != progress) {
                    binding.seekBar.progress = progress
                }
            }
        }

    var onValueChanged: ((Int) -> Unit)? = null
    var onDragStateChanged: ((Boolean) -> Unit)? = null

    init {
        updateProgressBounds()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val realVal = (minValue + progress).coerceIn(minValue, maxValue)
                _value = realVal
                updateValueDisplay()
                if (fromUser) {
                    onValueChanged?.invoke(realVal)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                onDragStateChanged?.invoke(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onDragStateChanged?.invoke(false)
            }
        })
    }

    private fun updateProgressBounds() {
        val range = (maxValue - minValue).coerceAtLeast(1)
        binding.seekBar.max = range
        binding.seekBar.progress = (_value - minValue).coerceIn(0, range)
        updateValueDisplay()
    }

    private fun updateValueDisplay() {
        binding.tvSliderValue.text = "$_value$unit"
    }
}
