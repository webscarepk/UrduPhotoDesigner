package com.example.urduphotodesigner.ui.creation

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.Converter.cmToPx
import com.example.urduphotodesigner.common.utils.Converter.inchesToPx
import com.example.urduphotodesigner.common.utils.Converter.pxToCm
import com.example.urduphotodesigner.common.utils.Converter.pxToInches
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentCreateBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private val unitList = listOf("Pixels", "Inches", "Centimeters")

    private val sizeList = listOf(
        CanvasSize("Instagram Story", 1080f, 1920f),
        CanvasSize("Instagram Post", 1080f, 1080f),
        CanvasSize("YouTube Thumbnail", 1280f, 720f),
        CanvasSize("Facebook Cover", 820f, 312f),
        CanvasSize("YouTube Channel Art", 2560f, 1440f),
        CanvasSize("A4", 2480f, 3508f),               // 210mm × 297mm
        CanvasSize("Letter", 2550f, 3300f),          // 8.5in × 11in
        CanvasSize("Poster",3600f, 5400f),          // 12in × 18in
        CanvasSize("Business Card", 1050f, 600f), // 3.5in × 2in
        CanvasSize("Billboard", 1920f, 1080f),
        CanvasSize("Vertical Banner", 1080f, 1920f),
        CanvasSize("Horizontal Banner", 1920f, 600f),
        CanvasSize("Flyer (US Letter)", 2550f, 3300f),
        CanvasSize("Resume", 2480f, 3508f),
        CanvasSize("Invitation", 1500f, 2100f)   // 5in × 7in
    )

    private var currentUnit = UnitType.PIXELS
    private var isLinked = false
    private var aspectRatio: Float? = null
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var adapter: CanvasSizeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    private fun setEvents() {
        binding.apply {
            unitBox.addPressEffect {
                spinner.rotation = 180f
                val popup = PopupMenu(requireContext(), unit)
                unitList.forEachIndexed { index, unit ->
                    popup.menu.add(Menu.NONE, index, index, unit)
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    val selectedUnitStr = unitList[menuItem.itemId]
                    unit.text = selectedUnitStr

                    // Convert old width/height to pixels first (if not PIXELS)
                    val oldWidthPx = when(currentUnit) {
                        UnitType.PIXELS -> getSafeIntValue(width)
                        UnitType.INCHES -> inchesToPx(width.text.toString().toFloatOrNull() ?: 1f)
                        UnitType.CENTIMETERS -> cmToPx(width.text.toString().toFloatOrNull() ?: 1f)
                    }
                    val oldHeightPx = when(currentUnit) {
                        UnitType.PIXELS -> getSafeIntValue(height)
                        UnitType.INCHES -> inchesToPx(height.text.toString().toFloatOrNull() ?: 1f)
                        UnitType.CENTIMETERS -> cmToPx(height.text.toString().toFloatOrNull() ?: 1f)
                    }

                    // Update current unit
                    currentUnit = when(selectedUnitStr) {
                        "Pixels" -> UnitType.PIXELS
                        "Inches" -> UnitType.INCHES
                        "Centimeters" -> UnitType.CENTIMETERS
                        else -> UnitType.PIXELS
                    }

                    // Convert pixel values to selected unit for UI display
                    when(currentUnit) {
                        UnitType.PIXELS -> {
                            width.setText(oldWidthPx.toString())
                            height.setText(oldHeightPx.toString())
                        }
                        UnitType.INCHES -> {
                            width.setText(String.format("%.1f", pxToInches(oldWidthPx.toFloat())))
                            height.setText(String.format("%.1f", pxToInches(oldHeightPx.toFloat())))
                        }
                        UnitType.CENTIMETERS -> {
                            width.setText(String.format("%.1f", pxToCm(oldWidthPx.toFloat())))
                            height.setText(String.format("%.1f", pxToCm(oldHeightPx.toFloat())))
                        }
                    }

                    // Update the RecyclerView list with converted sizes
                    updateListForUnit(currentUnit)

                    true
                }
                popup.setOnDismissListener {
                    spinner.rotation = 0f
                }
                popup.show()
            }

            adapter = CanvasSizeAdapter(sizeList, onClick =  { selected ->
                viewModel.clearCanvas()
                viewModel.setCanvasSize(selected)
                view?.post {
                    findNavController().navigate(R.id.editorFragment, null)
                }
                dismiss()
            }, true)
            sizesRV.adapter = adapter

            incWidth.addPressEffect {
                val newWidth = getSafeIntValue(width) + 1
                width.setText(newWidth.toString())

                if (isLinked && aspectRatio != null) {
                    val newHeight = (newWidth / aspectRatio!!).toInt()
                    height.setText(newHeight.toString())
                }
            }

            decWidth.addPressEffect {
                val current = getSafeIntValue(width)
                if (current > 1) {
                    val newWidth = current - 1
                    width.setText(newWidth.toString())

                    if (isLinked && aspectRatio != null) {
                        val newHeight = (newWidth / aspectRatio!!).toInt()
                        height.setText(newHeight.toString())
                    }
                }
            }

            incHeight.addPressEffect {
                val newHeight = getSafeIntValue(height) + 1
                height.setText(newHeight.toString())

                if (isLinked && aspectRatio != null) {
                    val newWidth = (newHeight * aspectRatio!!).toInt()
                    width.setText(newWidth.toString())
                }
            }

            decHeight.addPressEffect {
                val current = getSafeIntValue(height)
                if (current > 1) {
                    val newHeight = current - 1
                    height.setText(newHeight.toString())

                    if (isLinked && aspectRatio != null) {
                        val newWidth = (newHeight * aspectRatio!!).toInt()
                        width.setText(newWidth.toString())
                    }
                }
            }

            link.addPressEffect {
                isLinked = !isLinked
                link.setImageResource(
                    if (isLinked) R.drawable.ic_link else R.drawable.ic_unlink
                )

                if (isLinked) {
                    val widthVal = getSafeIntValue(width)
                    val heightVal = getSafeIntValue(height)
                    if (heightVal != 0.toFloat()) {
                        aspectRatio = widthVal / heightVal
                    }
                } else {
                    aspectRatio = null
                }
            }

            // Create button click
            binding.create.addPressEffect {
                val widthText = width.text.toString().trim()
                val heightText = height.text.toString().trim()

                if (widthText.isEmpty() || widthText.toFloatOrNull() == 0f ) {
                    Snackbar.make(binding.root, "Width cannot be 0", Snackbar.LENGTH_SHORT).show()
                    return@addPressEffect
                }
                if (heightText.isEmpty() || heightText.toFloatOrNull() == 0f){
                    Snackbar.make(binding.root, "Height cannot be 0", Snackbar.LENGTH_SHORT).show()
                    return@addPressEffect
                }

                val widthVal = getSafeIntValue(width)
                val heightVal = getSafeIntValue(height)

                val canvasSize = CanvasSize("Custom", widthVal, heightVal)

                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                view?.post {  }
                dismiss()
            }

            back.addPressEffect { dismiss() }
        }
    }

    private fun updateListForUnit(unitType: UnitType) {
        val convertedList = sizeList.map { size ->
            when(unitType) {
                UnitType.PIXELS -> size.copy()
                UnitType.INCHES -> size.copy(
                    width = String.format("%.1f", pxToInches(size.width)).toFloat(),
                    height = String.format("%.1f", pxToInches(size.height)).toFloat()
                )
                UnitType.CENTIMETERS -> size.copy(
                    width = String.format("%.1f", pxToCm(size.width)).toFloat(),
                    height = String.format("%.1f", pxToCm(size.height)).toFloat()
                )
            }
        }
        adapter.submitList(convertedList)
    }

    private fun getSafeIntValue(editText: EditText): Float {
        return editText.text.toString().toFloatOrNull()?.coerceAtLeast(1f) ?: 1f
    }

    override fun onResume() {
        super.onResume()
        // Assuming you have a TextView in your layout called unitTextView
        binding.unit.text = when (currentUnit) {
            UnitType.INCHES -> "Inches"
            UnitType.CENTIMETERS -> "Centimeters"
            UnitType.PIXELS -> "Pixels"
        }
        binding.link.setImageResource(
            if (isLinked) R.drawable.ic_link else R.drawable.ic_unlink
        )
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, 32f)
            .setTopRightCorner(CornerFamily.ROUNDED, 32f)
            .build()

        val materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel).apply {
            fillColor = ColorStateList.valueOf(Color.WHITE)
        }

        ViewCompat.setBackground(bottomSheet, materialShapeDrawable)
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.85f
        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.hide(WindowInsets.Type.navigationBars())
            } else {
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}