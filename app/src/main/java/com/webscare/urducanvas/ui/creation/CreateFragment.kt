package com.webscare.urducanvas.ui.creation

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.UnitType
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.utils.Converter
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentCreateBinding
import com.webscare.urducanvas.databinding.PopupUnitSelectorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private val unitList = listOf("Pixels", "Inches", "Centimeters")

    private val sizeList = listOf(
        CanvasSize(
            "Instagram Story", 1080f, 1920f
        ), CanvasSize(
            "Instagram Post", 1080f, 1080f
        ), CanvasSize(
            "YouTube Thumbnail", 1280f, 720f
        ), CanvasSize(
            "Facebook Cover", 820f, 312f
        ), CanvasSize(
            "YouTube Channel Art", 2560f, 1440f
        ), CanvasSize(
            "A4", 2480f, 3508f
        ),               // 210mm × 297mm
        CanvasSize(
            "Letter", 2550f, 3300f
        ),          // 8.5in × 11in
        CanvasSize(
            "Poster", 3600f, 5400f
        ),          // 12in × 18in
        CanvasSize(
            "Business Card", 1050f, 600f
        ), // 3.5in × 2in
        CanvasSize(
            "Billboard", 1920f, 1080f
        ), CanvasSize(
            "Vertical Banner", 1080f, 1920f
        ), CanvasSize(
            "Horizontal Banner", 1920f, 600f
        ), CanvasSize(
            "Flyer (US Letter)", 2550f, 3300f
        ), CanvasSize(
            "Resume", 2480f, 3508f
        ), CanvasSize(
            "Invitation", 1500f, 2100f
        ), CanvasSize(
            "Logo", 800f, 800f
        )
    )

    private var currentUnit = UnitType.PIXELS
    private var isLinked = false
    private var aspectRatio: Float? = null
    private val viewModel: CanvasViewModel by activityViewModels()

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

    @SuppressLint("DefaultLocale")
    private fun setEvents() {
        binding.apply {

            // 🔹 Unit selection (same as before)
            unitBox.addPressEffect {
                showUnitPopup(unitBox)
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
                val wInput = clampCanvasSize(getSafeIntValue(width), currentUnit)
                val hInput = clampCanvasSize(getSafeIntValue(height), currentUnit)

                // ✅ Always convert to pixels before storing in ViewModel
                val wPx = when (currentUnit) {
                    UnitType.PIXELS -> wInput
                    UnitType.INCHES -> Converter.inchesToPx(wInput).toFloat()
                    UnitType.CENTIMETERS -> Converter.cmToPx(wInput).toFloat()
                }
                val hPx = when (currentUnit) {
                    UnitType.PIXELS -> hInput
                    UnitType.INCHES -> Converter.inchesToPx(hInput).toFloat()
                    UnitType.CENTIMETERS -> Converter.cmToPx(hInput).toFloat()
                }

                val canvasSize = CanvasSize("Custom", wPx, hPx)
                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                view?.post { findNavController().navigate(R.id.editorFragment, null) }
                dismiss()
            }

            back.addPressEffect { dismiss() }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun showUnitPopup(anchorView: View) {
        val popupBinding = PopupUnitSelectorBinding.inflate(LayoutInflater.from(requireContext()))

        val popupWindow = PopupWindow(
            popupBinding.root,
            (140 * requireContext().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            isOutsideTouchable = true
        }

        binding.spinner.rotation = 180f
        popupWindow.setOnDismissListener { binding.spinner.rotation = 0f }

        fun onUnitSelected(selectedUnitStr: String) {
            binding.unit.text = selectedUnitStr

            // Convert current displayed values → px first
            val oldWidthPx = when (currentUnit) {
                UnitType.PIXELS -> getSafeIntValue(binding.width)
                UnitType.INCHES -> Converter.inchesToPx(getSafeIntValue(binding.width))
                UnitType.CENTIMETERS -> Converter.cmToPx(getSafeIntValue(binding.width))
            }
            val oldHeightPx = when (currentUnit) {
                UnitType.PIXELS -> getSafeIntValue(binding.height)
                UnitType.INCHES -> Converter.inchesToPx(getSafeIntValue(binding.height))
                UnitType.CENTIMETERS -> Converter.cmToPx(getSafeIntValue(binding.height))
            }

            currentUnit = when (selectedUnitStr) {
                "Pixels" -> UnitType.PIXELS
                "Inches" -> UnitType.INCHES
                "Centimeters" -> UnitType.CENTIMETERS
                else -> UnitType.PIXELS
            }

            // Convert px → new unit for display
            when (currentUnit) {
                UnitType.PIXELS -> {
                    binding.width.setText(oldWidthPx.toFloat().toString())
                    binding.height.setText(oldHeightPx.toFloat().toString())
                }
                UnitType.INCHES -> {
                    binding.width.setText(String.format("%.1f", Converter.pxToInches(oldWidthPx.toFloat())))
                    binding.height.setText(String.format("%.1f", Converter.pxToInches(oldHeightPx.toFloat())))
                }
                UnitType.CENTIMETERS -> {
                    binding.width.setText(String.format("%.1f", Converter.pxToCm(oldWidthPx.toFloat())))
                    binding.height.setText(String.format("%.1f", Converter.pxToCm(oldHeightPx.toFloat())))
                }
            }

            updateListForUnit(currentUnit)
            popupWindow.dismiss()
        }

        popupBinding.unitPixels.addPressEffect { onUnitSelected("Pixels") }
        popupBinding.unitInches.addPressEffect { onUnitSelected("Inches") }
        popupBinding.unitCentimeters.addPressEffect { onUnitSelected("Centimeters") }

        // Smart positioning — same pattern as showItemPopupMenu
        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            val spaceBelow = screenHeight - anchorBottom

            if (spaceBelow >= popupHeight) {
                popupWindow.showAsDropDown(anchorView)
            } else if (anchorTop >= popupHeight) {
                popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY,
                    location[0],
                    anchorTop - popupHeight
                )
            } else {
                popupWindow.showAsDropDown(anchorView)
            }
        }
    }

    private fun clampCanvasSize(value: Float, unit: UnitType): Float {
        val (min, max, unitLabel) = when (unit) {
            UnitType.PIXELS -> Triple(256f, 8000f, "px")
            UnitType.INCHES -> Triple(1f, 80f, "inches")
            UnitType.CENTIMETERS -> Triple(2.5f, 200f, "cm")
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

    @SuppressLint("DefaultLocale")
    private fun updateListForUnit(unitType: UnitType) {
        val convertedList = sizeList.map { size ->
            when (unitType) {
                UnitType.PIXELS -> size.copy()
                UnitType.INCHES -> size.copy(
                    width = String.format(
                        "%.1f", Converter.pxToInches(
                            size.width
                        )
                    ).toFloat(), height = String.format(
                        "%.1f", Converter.pxToInches(
                            size.height
                        )
                    ).toFloat()
                )

                UnitType.CENTIMETERS -> size.copy(
                    width = String.format(
                        "%.1f", Converter.pxToCm(
                            size.width
                        )
                    ).toFloat(), height = String.format(
                        "%.1f", Converter.pxToCm(
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
        forceImmersiveMode()

        binding.unit.text = when (currentUnit) {
            UnitType.INCHES -> "Inches"
            UnitType.CENTIMETERS -> "Centimeters"
            UnitType.PIXELS -> "Pixels"
        }
        binding.link.setImageResource(
            if (isLinked) R.drawable.ic_linked else R.drawable.ic_unlinked
        )
    }

    private fun forceImmersiveMode() {
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    hide(
                        WindowInsets.Type.navigationBars()
                    )
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.45f)
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }

            decorView.setOnSystemUiVisibilityChangeListener {
                forceImmersiveMode()
            }
        }

        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.background = ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_bg)
        bottomSheet.setBackgroundResource(android.R.color.transparent)

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
            WindowInsetsCompat.CONSUMED
        }

        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.apply {
            isFitToContents = false
            expandedOffset = 0
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            halfExpandedRatio = 0.75f
            skipCollapsed = true
        }

        forceImmersiveMode()
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}