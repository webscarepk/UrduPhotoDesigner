package com.example.urduphotodesigner.ui.editor

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.os.Build
import android.os.Build.MANUFACTURER
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AnimRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasManager
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.HAlign
import com.example.urduphotodesigner.common.canvas.enums.MultiAlignMode
import com.example.urduphotodesigner.common.canvas.enums.PickerTarget
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.enums.VAlign
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.utils.BitmapCache
import com.example.urduphotodesigner.common.utils.Converter.cmToPx
import com.example.urduphotodesigner.common.utils.Converter.inchesToPx
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.databinding.DialogAutoSavingLayoutBinding
import com.example.urduphotodesigner.databinding.FragmentEditorBinding
import com.example.urduphotodesigner.databinding.LayoutBlendPopupBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditorFragment : Fragment() {
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var canvasManager: CanvasManager
    private var _navController: NavController? = null
    private val navController get() = _navController!!
    private var panelsLocked = false
    private lateinit var canvasSize: CanvasSize
    private var currentUnit = UnitType.PIXELS
    private val viewModel: CanvasViewModel by activityViewModels()
    private var lastSelection: List<CanvasElement> = emptyList()

    private val mainViewModel: MainViewModel by activityViewModels()
    private var currentPanelItemId: Int? = null
    private lateinit var sizedCanvasView: CanvasView
    private var currentMode: MultiAlignMode = MultiAlignMode.CANVAS
    private var exportModel: ExportResult? = null
    private var jsonPath: String = "canvas_data_${System.currentTimeMillis()}.json"
    private var imagePath: String = "design_data_${System.currentTimeMillis()}.png"
    private var exportDialog: Dialog? = null
    private var exportDialogBinding: DialogAutoSavingLayoutBinding? = null
    private var rotationAnimator: ObjectAnimator? = null
    private var isSaving = false

    private var saveJsonJob: Job? = null
    private var savePending = false
    private var lastJsonSaveTime = 0L
    private val saveDebounce = 500L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.show(
                WindowInsets.Type.statusBars()
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            if (MANUFACTURER.equals("realme", ignoreCase = true)) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
            }
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val navHostFragment =
                childFragmentManager.findFragmentById(R.id.panelNavHost) as NavHostFragment
            _navController = navHostFragment.navController
            _navController?.addOnDestinationChangedListener { _, destination, _ ->
                // Check the destination
                binding.bottomNavigation.isVisible = destination.id != R.id.adjustmentsParentFragment
            }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        val jsonFileName = "canvas_$timestamp.json"
        val imageFileName = "design_$timestamp.png"

        jsonPath = File(requireContext().filesDir, jsonFileName).absolutePath
        imagePath = File(requireContext().filesDir, imageFileName).absolutePath

        viewModel.clearLoading()
        observeViewModel()
    }

    private fun showTextEditDialog(element: CanvasElement) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_text)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        editText.setText(element.text)
        editText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString() ?: ""
                element.text = newText
                viewModel.updateText(element)
                viewModel.markChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        // Set dialog window attributes for no dim background
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent) // Make background transparent
            setDimAmount(0f) // No dim
            setGravity(Gravity.BOTTOM)
            // You might want to adjust width/height if the layout doesn't fill as expected
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        // Show the dialog
        dialog.show()
    }

    private fun scheduleJsonSave() {
        savePending = true

        if (saveJsonJob?.isActive != true) {
            saveJsonJob = lifecycleScope.launch(Dispatchers.Default) {
                while (savePending) {
                    delay(saveDebounce)
                    savePending = false

                    val now = System.currentTimeMillis()
                    if (now - lastJsonSaveTime < saveDebounce) return@launch

                    val json = sizedCanvasView.exportCanvasJson()
                    Log.d("saveJson", "Saved JSON as $json")

                    var hasRealElements = false
                    try {
                        val arr = org.json.JSONArray(json)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val type = obj.optString("type")
                            if (type != "Background") {
                                hasRealElements = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("saveJson", "JSON parse failed: ${e.message}")
                    }

                    if (json.isNotBlank() && json != "[]" && json != "{}") {
                        if (hasRealElements || viewModel.isLoadingTemplate.value == false) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                File(jsonPath).writeText(json)
                                Log.d("saveJson", "Saved JSON at $jsonPath")
                                lastJsonSaveTime = now
                            }
                        } else {
                            Log.w("saveJson", "Skipped saving background-only JSON during load")
                        }
                    } else {
                        Log.w("saveJson", "Skipped saving empty JSON")
                    }
                }
            }
        }
    }

    private fun saveOnExitSafe(
        options: ExportOptions,
        exportBitmap: Bitmap,
        exportJson: String,
        exportImage: Boolean,
        canvasSize: CanvasSize
    ) {
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                // ---- Save Image ----
                if (exportImage) {
                    ImageProcessor.saveBitmapToFile(exportBitmap, options, imagePath)
                    withContext(Dispatchers.Main) {
                        updateExportDialog(96, "Image saved")
                    }
                }

                // ---- Save JSON ----
                File(jsonPath).writeText(exportJson)
                Log.d(TAG, "saveOnExitSafe: $jsonPath")
                Log.d("ImagePath", "bind: $imagePath")
                withContext(Dispatchers.Main) {
                    updateExportDialog(97, "JSON saved")
                }
                val jsonSizeBytes = exportJson.toByteArray(Charsets.UTF_8).size
                // ---- Calculate file size ----
                val fileSizeMB = (estimateBitmapSize(
                    exportBitmap, options.format.format!!, options.quality.quality
                ) + jsonSizeBytes) / (1024.0 * 1024.0)

                val exportDate =
                    SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileBaseName = "project_${System.currentTimeMillis()}"
                val fileName = "$fileBaseName.proj"
                // ---- Prepare model ----
                if (exportModel == null) {
                    exportModel = ExportResult(
                        imagePath = imagePath,
                        jsonPath = jsonPath,
                        fileName = fileName,
                        fileSizeMB = fileSizeMB,
                        resolution = options.resolution.label,
                        format = options.format.name,
                        quality = options.quality.label,
                        canvasSize = canvasSize,
                        exportDate = exportDate,
                        updatedDate = exportDate,
                    )
                } else {
                    if (exportModel!!.imagePath.startsWith("/storage")) {
                        exportModel!!.imagePath = imagePath
                    }

                    exportModel!!.canvasSize = canvasSize
                    exportModel!!.fileSizeMB = fileSizeMB
                    exportModel!!.updatedDate = exportDate
                }

                // ---- Save to DB ----
                val id = mainViewModel.insertExportResult(exportModel!!)
                exportModel!!.id = id

                withContext(Dispatchers.Main) {
                    viewModel.setExportResult(exportModel!!)
                    updateExportDialog(99, "Database updated")
                    updateExportDialog(100, "Saved successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background save failed: ${e.message}")
        }
    }

    private fun estimateBitmapSize(
        bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int
    ): Long {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.size().toLong()
    }

    private fun observeViewModel() {

        viewModel.canvasSize.observe(viewLifecycleOwner) { size ->
            if (size != null) {
                canvasSize = size

                val widthPx = when (currentUnit) {
                    UnitType.INCHES -> inchesToPx(size.width)
                    UnitType.CENTIMETERS -> cmToPx(size.width)
                    UnitType.PIXELS -> size.width.toInt()
                }
                val heightPx = when (currentUnit) {
                    UnitType.INCHES -> inchesToPx(size.height)
                    UnitType.CENTIMETERS -> cmToPx(size.height)
                    UnitType.PIXELS -> size.height.toInt()
                }

                initBottomNavigation()
                initCanvas(widthPx, heightPx)

                initUIControls()
                initBackHandling()
                observeAfterCanvasReady()

                if (exportModel == null) {
                    autoSaveSilent()
                }
            }
        }

    }

    private fun observeAfterCanvasReady() {
        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (::sizedCanvasView.isInitialized) sizedCanvasView.setSelectionMode(enabled)
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { exportResult ->
            if (exportResult == null) {
                // brand new canvas → trigger first silent save
                Log.d("EditorFragment", "Blank canvas detected → running autoSaveSilent()")
                viewModel.ensureBackgroundElement(requireActivity())
                autoSaveSilent()
            } else {
                // existing project → use its paths
                exportModel = exportResult
                jsonPath = exportResult.jsonPath
                if (exportResult.imagePath.startsWith("/storage")) {
                    exportModel!!.imagePath = imagePath
                } else {
                    imagePath = exportResult.imagePath
                }
            }
        }

        viewModel.canvasUnit.observe(viewLifecycleOwner) { unit ->
            if (unit != null) {
                currentUnit = unit
                binding.canvasContainer.invalidate()
            }
        }

        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            if (isAdded) {
                if (!elements.isNullOrEmpty()) {
                    canvasManager.syncElements(elements)
                    binding.canvasContainer.invalidate()
                    scheduleJsonSave()
                }
            }
        }

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            if (isAdded) {
                color?.let {
                    canvasManager.setCanvasBackgroundColor(it)
                    scheduleJsonSave()
                }
            }
        }

        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            binding.undo.isEnabled = canUndo
        }

        viewModel.canRedo.observe(viewLifecycleOwner) { canRedo ->
            binding.redo.isEnabled = canRedo
        }

        viewModel.backgroundImage.observe(viewLifecycleOwner) { bitmap ->
            if (isAdded) {
                bitmap?.let {
                    canvasManager.setCanvasBackgroundImage(it)
                    scheduleJsonSave()
                }
            }
        }

        viewModel.backgroundGradient.observe(viewLifecycleOwner) { gradient ->
            if (isAdded) {
                gradient?.let {
                    canvasManager.setCanvasBackgroundGradient(it)
                    scheduleJsonSave()
                }
            }
        }

        viewModel.currentFont.observe(viewLifecycleOwner) { font ->
            if (font != null && viewModel.isExplicitChange()) {
                font.let { canvasManager.setFont(it) }
            }
        }

        viewModel.currentImageFilter.observe(viewLifecycleOwner) { filter ->
            if (filter != null && viewModel.isExplicitChange()) {
                canvasManager.applyImageFilter(filter)
            }
        }

        viewModel.opacity.observe(viewLifecycleOwner) { opacity ->
            binding.seekBarOpacity.progress = opacity
            binding.opacityValue.text = "${opacity ?: 255}"
        }

        viewModel.currentTextSize.observe(viewLifecycleOwner) { size ->
            binding.fontSize.text = "${size?.toInt() ?: 40}"
            binding.seekBarFontSize.progress = size?.toInt() ?: 40
        }

        viewModel.blendingType.observe(viewLifecycleOwner) { type ->
            binding.blendSpinner.text = type.name
        }

        viewModel.activePicker.observe(viewLifecycleOwner) { slot ->
            if (::sizedCanvasView.isInitialized) {
                when (slot) {
                    PickerTarget.EYE_DROPPER_LABEL, PickerTarget.EYE_DROPPER_SHADOW, PickerTarget.EYE_DROPPER_BACKGROUND, PickerTarget.EYE_DROPPER_TEXT_FILL, PickerTarget.EYE_DROPPER_TEXT_STROKE, PickerTarget.EYE_DROPPER_GRADIENT -> {
                        sizedCanvasView.enableColorPicker()
                    }

                    else -> {
                        sizedCanvasView.disableColorPicker()
                    }
                }
            }
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { newSelection ->
            if (!newSelection.sameSelectionAs(lastSelection)) {
                resetPanelsOnSelectionChange()
                updateToolbarVisibility(newSelection)
                lastSelection = newSelection.toList()
            }
        }
    }

    private fun List<CanvasElement>.sameSelectionAs(other: List<CanvasElement>): Boolean {
        if (size != other.size) return false
        return this.map { it.id } == other.map { it.id }
    }

    // 👇 new helper
    private fun resetPanelsOnSelectionChange() {
        binding.seekBarOpacity.isVisible = false
        binding.opacityValue.isVisible = false
        binding.seekBarFontSize.isVisible = false
        binding.blendSpinner.isVisible = false
    }

    private fun updateToolbarVisibility(selected: List<CanvasElement>) {
        if (panelsLocked) {
            // 🔒 force hide everything
            resetPanelsOnSelectionChange()
            updateIconVisibility(binding.opacityPane, false)
            updateIconVisibility(binding.blendPane, false)
            updateIconVisibility(binding.fontSizePane, false)
            updateIconVisibility(binding.copyIcon, false)
            updateIconVisibility(binding.cutOutIcon, false)
            updateIconVisibility(binding.alignmentKit, false)
            updateIconVisibility(binding.selection, false)
            return
        }

        val hasText = selected.any { it.type == ElementType.TEXT }
        val hasImage = selected.any { it.type == ElementType.IMAGE }
        val hasBackground = selected.any { it.type == ElementType.BACKGROUND }
        val isMulti = selected.size > 1
        val anySelected = selected.isNotEmpty()

        val showFont = anySelected && hasText && !isMulti && !hasImage && !hasBackground
        val showCopy = anySelected && !hasBackground && !isMulti
        val showAlignWithSelection = isMulti
        val showRemoveBg = hasImage || hasBackground && !isMulti

        updateIconVisibility(binding.opacityPane, anySelected)
        updateIconVisibility(binding.blendPane, anySelected)
        updateIconVisibility(binding.fontSizePane, showFont)
        updateIconVisibility(binding.copyIcon, showCopy)
        updateIconVisibility(binding.cutOutIcon, showRemoveBg)
        updateIconVisibility(
            binding.alignmentKit,
            anySelected,
            animShow = R.anim.slide_in,
            animHide = R.anim.slide_out
        )
        updateIconVisibility(binding.selection, showAlignWithSelection)
    }

    private fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2
    ) {
        val isVisible = view.isVisible

        if (shouldBeVisible && !isVisible) {
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
        } else if (!shouldBeVisible && isVisible) {
            if (view == binding.fontSizePane) {
                binding.seekBarFontSize.isVisible = false
            }
            val anim = AnimationUtils.loadAnimation(view.context, animHide)
            view.startAnimation(anim)
            val duration = anim.duration
            view.postDelayed({ view.visibility = View.GONE }, duration)
        }
    }

    /** Attach/restore CanvasView inside container */
    private fun initCanvas(widthPx: Int, heightPx: Int) {
        val existing = viewModel.getCanvasView()
        if (existing != null) {
            sizedCanvasView = existing
            sizedCanvasView.resizeCanvas(widthPx, heightPx)
            (sizedCanvasView.parent as? ViewGroup)?.removeView(sizedCanvasView)
            binding.canvasContainer.addView(sizedCanvasView)
        } else {
            sizedCanvasView = CanvasView(
                requireContext(),
                canvasWidth = widthPx,
                canvasHeight = heightPx,
                onEditTextRequested = { element ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            if (element.type == ElementType.IMAGE || element.type == ElementType.BACKGROUND) {
                                val selected =
                                    viewModel.canvasElements.value?.find { it.id == element.id }
                                selected?.let {
                                    val key = it.id
                                    BitmapCache.put(key, it.bitmap!!)
                                    val bundle = Bundle().apply { putString("elementId", key) }
                                    val navOptions = NavOptions.Builder().setLaunchSingleTop(true)
                                        .setPopUpTo(R.id.adjustmentsParentFragment, inclusive = true).build()

                                    navController.navigate(R.id.adjustmentsParentFragment, bundle, navOptions)
                                }
                            } else {
                                showTextEditDialog(element)
                            }
                        } catch (e: Exception) {
                            Log.e("EditorFragment", "Navigation failed: ${e.message}")
                        }
                    }
                },
                onElementChanged = { canvasElement ->
                    viewModel.canvasElements.value?.find { it.id == canvasElement.id }?.let {
                        viewModel.updateElement(canvasElement)
                        viewModel.markChanged()
                    }
                },
                onElementRemoved = { canvasElement ->
                    viewModel.canvasElements.value?.find { it.id == canvasElement.id }?.let {
                        viewModel.removeElement(it)
                        viewModel.markChanged()
                    }
                },
                onElementSelected = { elements ->
                    viewModel.onCanvasSelectionChanged(elements)
                },
                onEndBatchUpdate = { elementId ->
                    viewModel.endBatchUpdate(elementId)
                    viewModel.markChanged()
                },
                onStartBatchUpdate = { elementId, actionType ->
                    viewModel.startBatchUpdate(elementId, actionType)
                    viewModel.markChanged()
                },
                onColorPicked = { colorInt ->
                    val opaque = (colorInt and 0x00FFFFFF) or (0xFF shl 24)
                    viewModel.finishPicking(opaque)
                    viewModel.stopPicking()
                    viewModel.markChanged()
                },
                onRequestOpenLayers = {
                    requireActivity().runOnUiThread {
                        if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                                Lifecycle.State.CREATED
                            )
                        ) {
                            viewModel.enterSelectionMode()
                            binding.bottomNavigation.selectedItemId = R.id.nav_layers
                            navController.navigate(R.id.layersFragment)
                            currentPanelItemId = R.id.nav_layers
                            binding.panelNavHost.visibility = View.VISIBLE
                        }
                    }
                },
                onExitSelectionMode = {
                    viewModel.exitSelectionMode()
                }).apply {
                binding.canvasContainer.addView(this)
            }
            viewModel.setCanvasView(sizedCanvasView)
        }

        canvasManager = CanvasManager(sizedCanvasView)
    }

    /** Setup bottom navigation with navHost */
    private fun initBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (currentPanelItemId == menuItem.itemId) {
                binding.panelNavHost.visibility = View.GONE
                currentPanelItemId = null
            } else {
                binding.panelNavHost.visibility = View.VISIBLE
                currentPanelItemId = menuItem.itemId
                when (menuItem.itemId) {
                    R.id.nav_background -> navController.navigate(R.id.backgroundsFragment)
                    R.id.nav_objects -> navController.navigate(R.id.objectsFragment)
                    R.id.nav_text -> navController.navigate(R.id.textFragment)
                    R.id.nav_draw -> navController.navigate(R.id.drawFragment)
                    R.id.nav_images -> navController.navigate(R.id.imagesFragment)
                    R.id.nav_layers -> navController.navigate(R.id.layersFragment)
                }
            }
            true
        }
    }

    /** Setup UI controls (undo, redo, align, opacity, etc.) */
    private fun initUIControls() {
        binding.undo.addPressEffect { viewModel.undo() }
        binding.redo.addPressEffect { viewModel.redo() }
        binding.showHide.addPressEffect {
            panelsLocked = !panelsLocked
            if (panelsLocked) {
                resetPanelsOnSelectionChange()
                binding.showHide.animate().rotation(180f).setDuration(300).start()
            } else {
                binding.showHide.animate().rotation(0f).setDuration(300).start()
            }
            updateToolbarVisibility(viewModel.selectedElements.value ?: emptyList())
        }

        binding.opacityIcon.addPressEffect { togglePanel(showOpacityPanel = true) }
        binding.opacityValue.addPressEffect { togglePanel(showOpacityPanel = true) }
        binding.fontSize.addPressEffect { togglePanel(showOpacityPanel = false) }
        binding.blendIcon.addPressEffect { toggleBlendPanel() }

        binding.artBoard.addPressEffect {
            if (currentMode != MultiAlignMode.CANVAS) {
                currentMode = MultiAlignMode.CANVAS
                updateModeDrawables()
            }
        }
        binding.selection.addPressEffect {
            if (currentMode != MultiAlignMode.SELECTION) {
                currentMode = MultiAlignMode.SELECTION
                updateModeDrawables()
            }
        }

        binding.blendSpinner.addPressEffect {
            showItemPopupMenu(binding.blendSpinner)
        }

        binding.leftAlign.addPressEffect {
            sizedCanvasView.alignHorizontal(
                HAlign.LEFT, currentMode
            )
        }
        binding.centerHorizontal.addPressEffect {
            sizedCanvasView.alignHorizontal(
                HAlign.CENTER, currentMode
            )
        }
        binding.rightAlign.addPressEffect {
            sizedCanvasView.alignHorizontal(
                HAlign.RIGHT, currentMode
            )
        }

        binding.topAlign.addPressEffect { sizedCanvasView.alignVertical(VAlign.TOP, currentMode) }
        binding.centerVertical.addPressEffect {
            sizedCanvasView.alignVertical(
                VAlign.MIDDLE, currentMode
            )
        }
        binding.bottomAlign.addPressEffect {
            sizedCanvasView.alignVertical(
                VAlign.BOTTOM, currentMode
            )
        }

        binding.seekBarOpacity.apply {
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) viewModel.setOpacity(progress)
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.seekBarFontSize.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.fontSize.text = "$progress"
                        viewModel.setTextSizeForAllSelected(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.copyIcon.addPressEffect { viewModel.copySelectedElementsGroup() }

        binding.cutOutIcon.addPressEffect {
            view?.post {
                findNavController().navigate(R.id.bgRemovalFragment)
            }
        }

        binding.done.addPressEffect {
            viewModel.setCanvasView(sizedCanvasView)
            sizedCanvasView.clearSelection()
            view?.post {
                findNavController().navigate(R.id.exportFragment)
            }
        }
    }

    private fun showItemPopupMenu(anchorView: View) {
        val popupBinding = LayoutBlendPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (150 * requireActivity().resources.displayMetrics.density).toInt(), // ~200dp width
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 2f
        popupWindow.isOutsideTouchable = true


        // ---- item logic ----

        popupBinding.source.addPressEffect {
            viewModel.setBlendingType(BlendType.SRC)
            popupWindow.dismiss()
        }

        popupBinding.normal.addPressEffect {
            viewModel.setBlendingType(BlendType.NORMAL)
            popupWindow.dismiss()
        }

        popupBinding.darken.addPressEffect {
            viewModel.setBlendingType(BlendType.DARKEN)
            popupWindow.dismiss()
        }

        popupBinding.lighten.addPressEffect {
            viewModel.setBlendingType(BlendType.LIGHTEN)
            popupWindow.dismiss()
        }

        popupBinding.multiply.addPressEffect {
            viewModel.setBlendingType(BlendType.MULTIPLY)
            popupWindow.dismiss()
        }

        popupBinding.screen.addPressEffect {
            viewModel.setBlendingType(BlendType.SCREEN)
            popupWindow.dismiss()
        }

        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            // Measure popup height
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight

            val spaceBelow = screenHeight - anchorBottom
            val spaceAbove = anchorTop

            if (spaceBelow >= popupHeight) {
                // Enough space below → dropdown
                popupWindow.showAsDropDown(anchorView)
            } else if (spaceAbove >= popupHeight) {
                // Enough space above → show on top
                popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY, location[0], // x
                    anchorTop - popupHeight // y (above anchor)
                )
            } else {
                // Default fallback → force dropdown
                popupWindow.showAsDropDown(anchorView)
            }
        }
    }

    /** Setup back button behavior */
    private fun initBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    autoSave()
                }
            })

        binding.back.addPressEffect { autoSave() }
    }

    private fun toggleBlendPanel() {
        val isCurrentlyVisible = binding.blendSpinner.isVisible
        if (isCurrentlyVisible) {
            // hide blend panel
            binding.blendSpinner.isVisible = false
        } else {
            // show blendSpinner, hide other panels
            binding.blendSpinner.isVisible = true
            binding.seekBarOpacity.isVisible = false
            binding.opacityValue.isVisible = false
            binding.opacityIcon.isVisible = true
            binding.seekBarFontSize.isVisible = false
        }
    }

    private fun togglePanel(showOpacityPanel: Boolean) {
        if (showOpacityPanel) {
            val isCurrentlyVisible = binding.seekBarOpacity.isVisible
            if (isCurrentlyVisible) {
                binding.seekBarOpacity.isVisible = false
                binding.opacityValue.isVisible = false
                binding.opacityIcon.isVisible = true
            } else {
                binding.seekBarOpacity.isVisible = true
                binding.opacityIcon.isVisible = false
                binding.opacityValue.isVisible = true
                // hide other panels
                binding.seekBarFontSize.isVisible = false
                binding.blendSpinner.isVisible = false
            }
        } else {
            val isCurrentlyVisible = binding.seekBarFontSize.isVisible
            if (isCurrentlyVisible) {
                binding.seekBarFontSize.isVisible = false
            } else {
                binding.seekBarFontSize.isVisible = true
                binding.seekBarOpacity.isVisible = false
                binding.opacityValue.isVisible = false
                binding.opacityIcon.isVisible = true
                binding.blendSpinner.isVisible = false
            }
        }
    }

    private fun updateModeDrawables() {
        when (currentMode) {
            MultiAlignMode.CANVAS -> {
                binding.artBoard.setImageResource(R.drawable.ic_align_art_board_filled)
                binding.selection.setImageResource(R.drawable.ic_align_selection_stroke)
            }

            MultiAlignMode.SELECTION -> {
                binding.artBoard.setImageResource(R.drawable.ic_align_art_board_stroke)
                binding.selection.setImageResource(R.drawable.ic_align_selection_filled)
            }
        }
    }

    private fun showExportProgressDialog() {
        if (exportDialog?.isShowing == true) return

        exportDialogBinding = DialogAutoSavingLayoutBinding.inflate(layoutInflater)

        exportDialog = Dialog(requireContext()).apply {
            setContentView(exportDialogBinding!!.root)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 80% width
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params

            window?.setGravity(Gravity.CENTER)
            show()
        }
        startIconRotation()
    }

    private fun updateExportDialog(percent: Int, stage: String) {
        exportDialogBinding?.apply {
            progressBar.progress = percent
            tvProgressPercent.text = getString(R.string.complete, percent)
            exportValue.text = stage
        }
    }

    private fun dismissExportDialog() {
        stopIconRotation()
        exportDialog?.dismiss()
        exportDialog = null
        exportDialogBinding = null
    }

    private fun autoSaveSilent() {
        if (!::sizedCanvasView.isInitialized) {
            return
        }
        val options = viewModel.exportOptions.value ?: return
        val canvasSize = viewModel.canvasSize.value ?: return

        lifecycleScope.launch {
            val (bitmap, json) = withContext(Dispatchers.Default) {
                sizedCanvasView.exportCanvasThumbnail { _, _ -> }
            }
            withContext(Dispatchers.IO) {
                saveOnExitSafe(options, bitmap, json, false, canvasSize)
            }
        }
    }

    private fun autoSave() {
        if (!viewModel.hasChanges.value!!) {
            findNavController().navigateUp()
            return
        }
        if (isSaving) return
        isSaving = true
        val options = viewModel.exportOptions.value ?: return
        val canvasSize = viewModel.canvasSize.value ?: return

        showExportProgressDialog()

        lifecycleScope.launch {
            val (bitmap, json) = withContext(Dispatchers.Default) {
                sizedCanvasView.exportCanvasThumbnail { percent, stage ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateExportDialog(percent, stage)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                updateExportDialog(97, "Saving files...")
            }
            withContext(Dispatchers.IO) {
                saveOnExitSafe(options, bitmap, json, true, canvasSize)
            }
            withContext(Dispatchers.Main) {
                updateExportDialog(100, "Saved successfully")
                delay(1000)
                dismissExportDialog()
                findNavController().navigateUp()
            }
        }
    }

    private fun startIconRotation() {
        exportDialogBinding?.view4?.let { icon ->
            rotationAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopIconRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        saveJsonJob?.cancel()
        _binding = null
        _navController = null
    }
}