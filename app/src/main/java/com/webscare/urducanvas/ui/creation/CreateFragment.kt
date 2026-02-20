package com.webscare.urducanvas.ui.creation

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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.utils.Converter.cmToPx
import com.example.urduphotodesigner.common.utils.Converter.inchesToPx
import com.example.urduphotodesigner.common.utils.Converter.pxToCm
import com.example.urduphotodesigner.common.utils.Converter.pxToInches
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentCreateBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFragment : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {
    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private val unitList = listOf("Pixels", "Inches", "Centimeters")

    private val sizeList = listOf(
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Instagram Story",
            1080f,
            1920f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Instagram Post",
            1080f,
            1080f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "YouTube Thumbnail",
            1280f,
            720f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Facebook Cover",
            820f,
            312f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "YouTube Channel Art",
            2560f,
            1440f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "A4",
            2480f,
            3508f
        ),               // 210mm × 297mm
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Letter",
            2550f,
            3300f
        ),          // 8.5in × 11in
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Poster",
            3600f,
            5400f
        ),          // 12in × 18in
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Business Card",
            1050f,
            600f
        ), // 3.5in × 2in
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Billboard",
            1920f,
            1080f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Vertical Banner",
            1080f,
            1920f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Horizontal Banner",
            1920f,
            600f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Flyer (US Letter)",
            2550f,
            3300f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Resume",
            2480f,
            3508f
        ),
        _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
            "Invitation",
            1500f,
            2100f
        )   // 5in × 7in
    )

    private var currentUnit = _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS
    private var isLinked = false
    private var aspectRatio: Float? = null
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    private lateinit var adapter: CanvasSizeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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

            // 🔹 Unit selection (same as before)
            unitBox.addPressEffect {
                spinner.rotation = 180f
                val popup = PopupMenu(requireContext(), unit)
                unitList.forEachIndexed { index, unit ->
                    popup.menu.add(Menu.NONE, index, index, unit)
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    val selectedUnitStr = unitList[menuItem.itemId]
                    unit.text = selectedUnitStr

                    // Convert old width/height to px first
                    val oldWidthPx = when (currentUnit) {
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> getSafeIntValue(width)
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.inchesToPx(
                            getSafeIntValue(width)
                        )
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.cmToPx(
                            getSafeIntValue(width)
                        )
                    }
                    val oldHeightPx = when (currentUnit) {
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> getSafeIntValue(height)
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.inchesToPx(
                            getSafeIntValue(height)
                        )
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.cmToPx(
                            getSafeIntValue(height)
                        )
                    }

                    currentUnit = when (selectedUnitStr) {
                        "Pixels" -> _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS
                        "Inches" -> _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES
                        "Centimeters" -> _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS
                        else -> _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS
                    }

                    // Convert back for display
                    when (currentUnit) {
                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> {
                            width.setText(oldWidthPx.toFloat().toString())
                            height.setText(oldHeightPx.toFloat().toString())
                        }

                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> {
                            width.setText(String.format("%.1f",
                                _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToInches(
                                    oldWidthPx.toFloat()
                                )
                            ))
                            height.setText(String.format("%.1f",
                                _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToInches(
                                    oldHeightPx.toFloat()
                                )
                            ))
                        }

                        _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> {
                            width.setText(String.format("%.1f",
                                _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToCm(
                                    oldWidthPx.toFloat()
                                )
                            ))
                            height.setText(String.format("%.1f",
                                _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToCm(
                                    oldHeightPx.toFloat()
                                )
                            ))
                        }
                    }

                    updateListForUnit(currentUnit)
                    true
                }
                popup.setOnDismissListener { spinner.rotation = 0f }
                popup.show()
            }

            // 🔹 Adapter setup
            adapter = CanvasSizeAdapter(sizeList, onClick = { selected ->
                viewModel.clearCanvas()
                viewModel.setCanvasSize(selected)
                view?.post { findNavController().navigate(R.id.editorFragment, null) }
                dismiss()
            }, true)
            sizesRV.adapter = adapter

            // ------------------------------
            // 🔹 Aspect Ratio Sync Logic
            // ------------------------------

            fun updateAspectRatio() {
                val w = getSafeIntValue(width)
                val h = getSafeIntValue(height)
                aspectRatio = if (h > 0f) w / h else null
            }

            // 🔹 Link toggle
            link.addPressEffect {
                isLinked = !isLinked
                link.setImageResource(if (isLinked) R.drawable.ic_linked else R.drawable.ic_unlinked)
                if (isLinked) updateAspectRatio()
            }

            // 🔹 Text watchers for live proportional resizing
            width.addTextChangedListener {
                if (isLinked && aspectRatio != null && width.hasFocus()) {
                    val w = getSafeIntValue(width)
                    val h = (w / aspectRatio!!).coerceAtLeast(1f)
                    height.setText(String.format("%.1f", h))
                }
            }

            height.addTextChangedListener {
                if (isLinked && aspectRatio != null && height.hasFocus()) {
                    val h = getSafeIntValue(height)
                    val w = (h * aspectRatio!!).coerceAtLeast(1f)
                    width.setText(String.format("%.1f", w))
                }
            }

            // ------------------------------
            // 🔹 Increment / Decrement buttons
            // ------------------------------

            incWidth.addPressEffect {
                val newWidth = getSafeIntValue(width) + 1
                width.setText(String.format("%.1f", newWidth))
                if (isLinked && aspectRatio != null) {
                    val newHeight = (newWidth / aspectRatio!!).coerceAtLeast(1f)
                    height.setText(String.format("%.1f", newHeight))
                }
            }

            decWidth.addPressEffect {
                val current = getSafeIntValue(width)
                if (current > 1) {
                    val newWidth = current - 1
                    width.setText(String.format("%.1f", newWidth))
                    if (isLinked && aspectRatio != null) {
                        val newHeight = (newWidth / aspectRatio!!).coerceAtLeast(1f)
                        height.setText(String.format("%.1f", newHeight))
                    }
                }
            }

            incHeight.addPressEffect {
                val newHeight = getSafeIntValue(height) + 1
                height.setText(String.format("%.1f", newHeight))
                if (isLinked && aspectRatio != null) {
                    val newWidth = (newHeight * aspectRatio!!).coerceAtLeast(1f)
                    width.setText(String.format("%.1f", newWidth))
                }
            }

            decHeight.addPressEffect {
                val current = getSafeIntValue(height)
                if (current > 1) {
                    val newHeight = current - 1
                    height.setText(String.format("%.1f", newHeight))
                    if (isLinked && aspectRatio != null) {
                        val newWidth = (newHeight * aspectRatio!!).coerceAtLeast(1f)
                        width.setText(String.format("%.1f", newWidth))
                    }
                }
            }

            // ------------------------------
            // 🔹 Create Button
            // ------------------------------

            create.addPressEffect {
                val wVal = clampCanvasSize(getSafeIntValue(width), currentUnit)
                val hVal = clampCanvasSize(getSafeIntValue(height), currentUnit)
                val canvasSize =
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
                        "Custom",
                        wVal,
                        hVal
                    )
                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                view?.post { findNavController().navigate(R.id.editorFragment, null) }
                dismiss()
            }

            back.addPressEffect { dismiss() }
        }
    }

    private fun clampCanvasSize(value: Float, unit: com.webscare.urducanvas.common.canvas.enums.UnitType): Float {
        val (min, max, unitLabel) = when (unit) {
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> Triple(256f, 8000f, "px")
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> Triple(1f, 80f, "inches")
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> Triple(2.5f, 200f, "cm")
        }

        val clamped = value.coerceIn(min, max)

        if (clamped != value && _binding != null) {
            val msg = if (value > max) {
                "Maximum allowed is $max $unitLabel"
            } else {
                "Minimum allowed is $min $unitLabel"
            }
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT
            ).setAnchorView(binding.create).show()
        }

        return clamped
    }

    private fun updateListForUnit(unitType: com.webscare.urducanvas.common.canvas.enums.UnitType) {
        val convertedList = sizeList.map { size ->
            when (unitType) {
                _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> size.copy()
                _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> size.copy(
                    width = String.format("%.1f",
                        _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToInches(
                            size.width
                        )
                    ).toFloat(),
                    height = String.format("%.1f",
                        _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToInches(
                            size.height
                        )
                    ).toFloat()
                )

                _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> size.copy(
                    width = String.format("%.1f",
                        _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToCm(
                            size.width
                        )
                    ).toFloat(),
                    height = String.format("%.1f",
                        _root_ide_package_.com.webscare.urducanvas.common.utils.Converter.pxToCm(
                            size.height
                        )
                    ).toFloat()
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
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.INCHES -> "Inches"
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.CENTIMETERS -> "Centimeters"
            _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.UnitType.PIXELS -> "Pixels"
        }
        binding.link.setImageResource(
            if (isLinked) R.drawable.ic_linked else R.drawable.ic_unlinked
        )
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val shapeAppearanceModel =
            ShapeAppearanceModel.builder().setTopLeftCorner(CornerFamily.ROUNDED, 32f)
                .setTopRightCorner(CornerFamily.ROUNDED, 32f).build()

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
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}