package com.webscare.urducanvas.ui.editor.panels.table

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.views.ColorPickerBar

class TableAppearanceOptionsFragment : Fragment() {

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val fillLabel = TextView(context).apply {
            text = "Background Color"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        root.addView(fillLabel)

        val colorPicker = ColorPickerBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * density).toInt()
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, (12 * density).toInt())
            }
            onColorPicked = { hexColor ->
                try {
                    val colorInt = Color.parseColor(hexColor)
                    viewModel.updateSelectedTableData { data ->
                        data.base.bgColor = colorInt
                    }
                } catch (e: Exception) { }
            }
        }
        root.addView(colorPicker)

        val borderFillLabel = TextView(context).apply {
            text = "Border Color"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        root.addView(borderFillLabel)

        val borderColorPicker = ColorPickerBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * density).toInt()
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, (12 * density).toInt())
            }
            onColorPicked = { hexColor ->
                try {
                    val colorInt = Color.parseColor(hexColor)
                    viewModel.updateSelectedTableData { data ->
                        data.borderColor = colorInt
                    }
                } catch (e: Exception) { }
            }
        }
        root.addView(borderColorPicker)

        return root
    }
}
