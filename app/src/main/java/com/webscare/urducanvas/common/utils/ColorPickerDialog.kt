package com.webscare.urducanvas.common.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.graphics.toColorInt
import androidx.core.widget.addTextChangedListener
import com.webscare.urducanvas.databinding.DialogColorPickerBinding

class ColorPickerDialog(context: Context, private val onColorSelected: (Int) -> Unit) : Dialog(context) {

    private lateinit var binding: DialogColorPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogColorPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.colorCode.isSingleLine = true

        binding.colorCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryApplyColor()
                true
            } else {
                false
            }
        }

        binding.colorCode.addTextChangedListener(afterTextChanged = { editable ->
            val color = parseColor(editable.toString())
            if (color != null) binding.colorPreview.setCardBackgroundColor(color)
        })

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }

        binding.colorCode.requestFocus()
    }

    private fun tryApplyColor() {
        val color = parseColor(binding.colorCode.text.toString())
        if (color != null) {
            onColorSelected(color)
            dismiss()
        } else {
            binding.colorCode.error = "Invalid color code"
        }
    }

    private fun parseColor(input: String): Int? {
        var trimmed = input.trim()

        if (!trimmed.startsWith("#") &&
            !trimmed.startsWith("rgb") &&
            trimmed.matches(Regex("[0-9a-fA-F]{3,8}"))
        ) {
            trimmed = "#$trimmed"
        }

        return try {
            when {
                trimmed.startsWith("#") -> Color.parseColor(trimmed)

                trimmed.startsWith("rgb(") -> {
                    val v = trimmed.removePrefix("rgb(").removeSuffix(")")
                        .split(",").map { it.trim().toInt() }
                    Color.rgb(v[0], v[1], v[2])
                }

                trimmed.startsWith("rgba(") -> {
                    val v = trimmed.removePrefix("rgba(").removeSuffix(")")
                        .split(",").map { it.trim() }
                    Color.argb(
                        (v[3].toFloat() * 255).toInt(),
                        v[0].toInt(),
                        v[1].toInt(),
                        v[2].toInt(),
                    )
                }

                else -> trimmed.toColorInt()
            }
        } catch (e: Exception) {
            android.util.Log.e("ColorPickerDialog", "Failed to parse color: $input", e)
            null
        }
    }
}
