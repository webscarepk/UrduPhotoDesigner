package com.webscare.urducanvas.ui.editor

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build.MANUFACTURER
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import com.webscare.urducanvas.databinding.LayoutCanvasPopupBinding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnimRes
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.webscare.urducanvas.BuildConfig
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.HAlign
import com.webscare.urducanvas.common.canvas.enums.MultiAlignMode
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.enums.UnitType
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.CanvasView
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.databinding.DialogAutoSavingLayoutBinding
import com.webscare.urducanvas.databinding.FragmentEditorBinding
import com.webscare.urducanvas.databinding.LayoutBlendPopupBinding
import com.webscare.urducanvas.databinding.LayoutZoomPopupBinding
import com.webscare.urducanvas.ui.creation.CreateFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
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
import kotlin.math.roundToInt

fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density + 0.5f).toInt()
}

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
    private var activePanel: View? = null
    private val mainViewModel: MainViewModel by activityViewModels()
    private var currentPanelItemId: Int? = null
    private lateinit var sizedCanvasView: CanvasView
    private var currentMode: MultiAlignMode = MultiAlignMode.CANVAS
    private var exportModel: ExportResult? = null
    private var jsonPath: String = "project_${System.currentTimeMillis()}.json"
    private var imagePath: String = "project_img_${System.currentTimeMillis()}.png"
    private var exportDialog: Dialog? = null
    private var exportDialogBinding: DialogAutoSavingLayoutBinding? = null
    private var rotationAnimator: ObjectAnimator? = null
    private var isSaving = false
    private var shapeJustAdded = false
    private var saveJsonJob: Job? = null
    private var savePending = false
    private var lastJsonSaveTime = 0L
    private val saveDebounce = 500L
    private var selectionFromUserInteraction = false
    private var isFabMenuOpen = false
    private var fabInitialX = 0f
    private var fabInitialY = 0f
    private var fabInitialTouchX = 0f
    private var fabInitialTouchY = 0f
    private var fabMargin = 0

    private var panelSheet: PanelSheetBehavior? = null
    private var uiFullyInitialized = false

    // Fragments that open on element selection are NOT expandable — the sheet
    // must be locked collapsed while any of these destinations is active.
    private val nonExpandableDestinations = setOf(
        R.id.adjustmentsParentFragment,
        R.id.shapeFragment,
        R.id.textAdjustmentsFragment
    )
    private var isPanelExpandable = true
    private var currentDragHandle: View? = null  // stored so we can block/restore touch

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (BuildConfig.DEBUG) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            if (MANUFACTURER.equals("realme", ignoreCase = true)) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
            }
            insets
        }

        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.panelNavHost) as NavHostFragment
        _navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        _navController?.addOnDestinationChangedListener { _, destination, _ ->

            // Hide bottom nav for adjustments
            binding.bottomNavigation.isVisible =
                destination.id != R.id.adjustmentsParentFragment &&
                        destination.id != R.id.shapeFragment &&
                        destination.id != R.id.textAdjustmentsFragment

            // Lock the panel sheet collapsed for adjustment panels (non-expandable).
            // When the user navigates back to an expandable panel, attachDragHandle()
            // is called by that panel which resets the sheet and re-enables expanding.
            val isNonExpandable = destination.id in nonExpandableDestinations
            if (isPanelExpandable != !isNonExpandable) {
                isPanelExpandable = !isNonExpandable
                if (!isPanelExpandable) {
                    // Snap to collapsed immediately — no spring animation, no user drag
                    panelSheet?.snapTo(expanded = false, immediate = true)
                    // Block touch on the drag handle so user can't swipe up manually
                    currentDragHandle?.setOnTouchListener { _, _ -> true }
                } else {
                    // Restore touch on the drag handle
                    currentDragHandle?.setOnTouchListener(null)
                }
            }

            when (destination.id) {

                R.id.textFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_text
                    currentPanelItemId = R.id.nav_text
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.objectsFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_stickers
                    currentPanelItemId = R.id.nav_stickers
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.drawFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_draw
                    currentPanelItemId = R.id.nav_draw
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.imagesFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_images
                    currentPanelItemId = R.id.nav_images
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.layersFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_layers
                    currentPanelItemId = R.id.nav_layers
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.shapesParentFragment -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_shapes
                    currentPanelItemId = R.id.nav_shapes
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                else -> {
                    currentPanelItemId = null
                }
            }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        val jsonFileName = "project_$timestamp.json"
        val imageFileName = "project_img_$timestamp.png"

        jsonPath = File(requireContext().filesDir, jsonFileName).absolutePath
        imagePath = File(requireContext().filesDir, imageFileName).absolutePath
        fabMargin = 8.dpToPx(requireContext())
        // FIX: Reset on every onViewCreated — view is being recreated so UI needs full re-init.
        uiFullyInitialized = false
        viewModel.clearLoading()

        // ── Update all callback references to point at this (fresh) fragment instance.
        // Must happen before observeViewModel() so that any LiveData re-delivery
        // that fires synchronously already sees the live lambdas.
        rewireCanvasCallbacks()

        // ── Eagerly re-attach the CanvasView so the container is never blank between
        // onViewCreated and the canvasSize observer firing.
        viewModel.getCanvasView()?.let { existing ->
            if (existing.parent !== binding.canvasContainer) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                binding.canvasContainer.addView(existing)
            }
        }

        observeViewModel()
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath =
                    ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        ?: return@launch

                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return@launch

                // Consistent with every other image entry point in the app:
                // User explicitly picked this image as a canvas element — preserve full
                // quality up to the GPU hard limit (24 MP / 4899 px per side).
                // CanvasView's display-proxy system handles render performance transparently.
                val bitmap = ImageProcessor.downsampleIfNeeded(
                    rawBitmap, GPU_SAFE_MAX_PX, GPU_SAFE_MAX_PX
                )

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE)
                }
            } catch (e: Exception) {
                Log.e("EditorFragment", "Failed to import image", e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFab() {
        val fabMenu = binding.fabMenu
        val fabAdd = binding.fabAdd

        fabAdd.setOnClickListener {
            toggleFabMenu(!isFabMenuOpen)
        }

        binding.addText.addPressEffect {
            viewModel.addText(requireActivity().getString(R.string.dummyText), requireActivity())
            navController.navigate(R.id.textFragment)
            toggleFabMenu(false)
        }

        binding.addObject.addPressEffect {
            val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
            val bundle = Bundle().apply { putInt("startPage", 1) } // Assuming page 1 is index 0
            navController.navigate(R.id.objectsFragment, bundle, navOptions)
            toggleFabMenu(false)
        }

        binding.addShapes.addPressEffect {
            shapeJustAdded = false   // ← was true, change to false so observer allows navigation
            viewModel.addShapeElement()
            val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
            navController.navigate(R.id.shapesParentFragment, null, navOptions)
            toggleFabMenu(false)
        }

        binding.addImage.addPressEffect {
            pickImage.launch("image/*")
            toggleFabMenu(false)
        }

        binding.addDraw.addPressEffect {
            viewModel.enterDrawingMode(requireActivity())
            val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
            navController.navigate(R.id.drawFragment, null, navOptions)
            toggleFabMenu(false)
        }

        fabAdd.setOnTouchListener { v, event ->
            val parent = binding.fabContainer
            val parentWidth = parent.width
            val parentHeight = parent.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    fabInitialX = v.x
                    fabInitialY = v.y
                    fabInitialTouchX = event.rawX
                    fabInitialTouchY = event.rawY
                    v.translationZ = 11f
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - fabInitialTouchX
                    val dy = event.rawY - fabInitialTouchY

                    var newX = fabInitialX + dx
                    var newY = fabInitialY + dy

                    newX = newX.coerceIn(
                        fabMargin.toFloat(), (parentWidth - v.width - fabMargin).toFloat()
                    )
                    newY = newY.coerceIn(
                        fabMargin.toFloat(), (parentHeight - v.height - fabMargin).toFloat()
                    )

                    v.x = newX
                    v.y = newY

                    if (isFabMenuOpen) {
                        updateFabMenuPosition(fabAdd, fabMenu)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    v.translationZ = 10f

                    if (isFabMenuOpen) {
                        updateFabMenuPosition(fabAdd, fabMenu)
                    }

                    val deltaX = event.rawX - fabInitialTouchX
                    val deltaY = event.rawY - fabInitialTouchY
                    val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

                    if (distance < 10) {
                        v.performClick()
                    }
                }
            }
            true
        }
    }

    private fun updateFabMenuPosition(fab: ImageView, menu: ConstraintLayout) {
        val menuHeight = menu.height

        val spaceAboveFab = fab.y
        val isExpandDown = spaceAboveFab < menuHeight + fabMargin

        if (isExpandDown) {
            menu.x = fab.x + fab.width / 2 - menu.width / 2
            menu.y = fab.y + fab.height + fabMargin
        } else {
            menu.x = fab.x + fab.width / 2 - menu.width / 2
            menu.y = fab.y - menu.height - fabMargin
        }
    }

    private fun toggleFabMenu(show: Boolean) {
        isFabMenuOpen = show
        val fabMenu = binding.fabMenu
        val fabAdd = binding.fabAdd

        if (show) {
            fabMenu.visibility = View.VISIBLE

            fabMenu.post {
                updateFabMenuPosition(fabAdd, fabMenu)

                val pivotX = fabAdd.x + fabAdd.width / 2 - fabMenu.x
                val pivotY = fabAdd.y + fabAdd.height / 2 - fabMenu.y

                fabMenu.pivotX = pivotX
                fabMenu.pivotY = pivotY

                fabMenu.alpha = 0f
                fabMenu.scaleX = 0.5f
                fabMenu.scaleY = 0.5f

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(fabMenu, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(fabMenu, View.SCALE_X, 0.5f, 1f),
                    ObjectAnimator.ofFloat(fabMenu, View.SCALE_Y, 0.5f, 1f)
                )
                animatorSet.duration = 200
                animatorSet.start()
            }

            // Change FAB icon to 'X' (or rotate the '+')
            fabAdd.animate().rotation(45f).setDuration(200).start()

        } else {
            // Collapse/Hide Menu
            fabAdd.animate().rotation(0f).setDuration(200).start()

            // Recalculate pivots for a smooth collapse animation back to the FAB center
            val pivotX = fabAdd.x + fabAdd.width / 2 - fabMenu.x
            val pivotY = fabAdd.y + fabAdd.height / 2 - fabMenu.y

            fabMenu.pivotX = pivotX
            fabMenu.pivotY = pivotY

            fabMenu.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(200).withEndAction {
                fabMenu.visibility = View.GONE
            }.start()
        }
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

                    // ✅ Check on live list BEFORE serializing — free, no RAM cost
                    val hasRealElements =
                        viewModel.canvasElements.value?.any { it.type?.name != "Background" }

                    val isEmpty = viewModel.canvasElements.value?.isEmpty()

                    if (isEmpty == true) {
                        Log.w("saveJson", "Skipped saving empty JSON")
                        return@launch
                    }

                    if (!hasRealElements!! && viewModel.isLoadingTemplate.value == true) {
                        Log.w("saveJson", "Skipped saving background-only JSON during load")
                        return@launch
                    }

                    // ✅ Stream directly to file — no String in RAM at all
                    withContext(Dispatchers.IO) {
                        sizedCanvasView.exportCanvasJson(jsonPath)
                    }

                    Log.d("saveJson", "Saved JSON at $jsonPath")
                    lastJsonSaveTime = now
                }
            }
        }
    }

    private suspend fun saveOnExitSafe(
        options: ExportOptions,
        exportBitmap: Bitmap,
        exportJsonFile: File,
        exportImage: Boolean,
        canvasSize: CanvasSize
    ) = withContext(Dispatchers.IO) {
        try {
            // ---- Save thumbnail image ------------------------------------------------
            if (exportImage) {
                File(imagePath).outputStream().use { out ->
                    exportBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                withContext(Dispatchers.Main) { updateExportDialog(96, "Image saved") }
            }

            // ---- Atomic JSON copy: temp file → .tmp → rename to final path ----------
            // Stream-copy the temp file to a sibling .tmp, then rename atomically.
            // Using try/finally guarantees the temp file is always cleaned up, even if
            // the copy fails. The rename is atomic on Android (same FS partition) so the
            // reader never sees a partial write.
            val tmpDest = File("$jsonPath.tmp")
            try {
                if (!exportJsonFile.exists()) {
                    Log.e(TAG, "saveOnExitSafe: temp JSON missing: ${exportJsonFile.path}")
                } else {
                    exportJsonFile.inputStream().use { src ->
                        tmpDest.outputStream().use { dst ->
                            src.copyTo(dst, bufferSize = 8 * 1024)
                        }
                    }
                    if (tmpDest.length() >= 4L) {
                        tmpDest.renameTo(File(jsonPath))
                    } else {
                        Log.e(TAG, "saveOnExitSafe: tmp too small, keeping old JSON")
                        tmpDest.delete()
                    }
                }
            } finally {
                exportJsonFile.delete()   // always delete the source temp file
            }
            Log.d(TAG, "saveOnExitSafe: wrote $jsonPath")
            withContext(Dispatchers.Main) { updateExportDialog(97, "JSON saved") }

            // ---- File size ----------------------------------------------------------
            val imageSizeBytes = if (exportImage) File(imagePath).length() else 0L
            val jsonSizeBytes = File(jsonPath).length()   // read from final destination
            val fileSizeMB = (imageSizeBytes + jsonSizeBytes) / (1024.0 * 1024.0)

            val exportDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = viewModel.buildProjectFileName()

            // ---- Prepare model ------------------------------------------------------
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

            // ---- Save to DB ---------------------------------------------------------
            val id = mainViewModel.insertExportResult(exportModel!!)
            exportModel!!.id = id

            withContext(Dispatchers.Main) {
                viewModel.setExportResult(exportModel!!)
                updateExportDialog(99, "Database updated")
                updateExportDialog(100, "Saved successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "saveOnExitSafe failed: ${e.message}", e)
        }
    }

    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.collect { panel ->
                    val expanded = panel != null
                    expandPanel(expanded)
                    // When any panel is expanded full-screen, block touches from
                    // reaching the canvas behind it. Handled here once for every
                    // panel type rather than in each panel fragment.
                    setPanelTouchBlocked(expanded)
                }
            }
        }

        viewModel.canvasSize.observe(viewLifecycleOwner) { size ->
            if (size != null) {
                canvasSize = size

                if (!uiFullyInitialized) {
                    // FIX: Use uiFullyInitialized instead of ::sizedCanvasView.isInitialized.
                    // sizedCanvasView survives view destruction (it lives on the fragment
                    // instance, not the view). On return from BgRemovalFragment the view is
                    // recreated but isInitialized is still true, so all setup was skipped —
                    // bottom nav dead, no click listeners, no observers. Now we always run full
                    // setup on every new view creation, regardless of prior sizedCanvasView state.
                    uiFullyInitialized = true
                    initBottomNavigation()
                    initCanvas(size.width.toInt(), size.height.toInt())
                    initUIControls()
                    initBackHandling()
                    observeAfterCanvasReady()
                    if (exportModel == null) autoSaveSilent()
                } else {
                    // Canvas size changed (e.g. user resized canvas) — update dimensions only.
                    sizedCanvasView.resizeCanvas(size.width.toInt(), size.height.toInt())
                    autoSaveSilent()
                }
            }
        }

    }

    private fun observeAfterCanvasReady() {
        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (::sizedCanvasView.isInitialized) sizedCanvasView.setSelectionMode(enabled)
        }

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            if (isAdded) {
                color?.let {
                    binding.editorRoot.setBackgroundColor(it)
                    scheduleJsonSave()
                }
            }
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { exportResult ->
            if (exportResult == null) {
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
                val panelDestinations = listOf(
                    R.id.adjustmentsParentFragment,
                    R.id.shapeFragment,
                    R.id.textAdjustmentsFragment   // ← ADD THIS
                )
                val currentDest = navController.currentDestination?.id
                if (currentDest != null && currentDest in panelDestinations) {
                    val hasSelection = elements?.any { it.isSelected } == true
                    if (!hasSelection) navController.popBackStack(currentDest, true)
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

        viewModel.isDrawingMode.observe(viewLifecycleOwner) { isDrawing ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.setDrawingMode(isDrawing)
            }
        }

        viewModel.brushColor.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(color = it)
        }

        viewModel.brushThickness.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(thickness = it)
        }

        viewModel.brushHardness.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(hardness = it)
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(style = it)
        }

        viewModel.brushGradient.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(gradient = it)
        }

        viewModel.activePicker.observe(viewLifecycleOwner) { slot ->
            if (::sizedCanvasView.isInitialized) {
                when (slot) {
                    PickerTarget.EYE_DROPPER_LABEL, PickerTarget.EYE_DROPPER_OVERLAY, PickerTarget.EYE_DROPPER_SHADOW, PickerTarget.EYE_DROPPER_BACKGROUND, PickerTarget.EYE_DROPPER_TEXT_FILL, PickerTarget.EYE_DROPPER_TEXT_STROKE, PickerTarget.EYE_DROPPER_GRADIENT, PickerTarget.EYE_DROPPER_DRAW_STROKE, PickerTarget.EYE_DROPPER_DRAW_FILL, PickerTarget.EYE_DROPPER_IMAGE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_FILL -> {
                        sizedCanvasView.enableColorPicker()
                    }

                    else -> {
                        sizedCanvasView.disableColorPicker()
                    }
                }
            }
        }

        viewModel.isGridEnabled.observe(viewLifecycleOwner) { enabled ->
            updateToggleButton(binding.grid, enabled)
            sizedCanvasView.setGridEnabled(enabled)
        }

        viewModel.isRulerEnabled.observe(viewLifecycleOwner) { enabled ->
            updateToggleButton(binding.ruler, enabled)
            sizedCanvasView.setRulerEnabled(enabled)
        }

        viewModel.isPanMode.observe(viewLifecycleOwner) { enabled ->
            updateToggleButton(binding.pan, enabled)
            sizedCanvasView.setPanMode(enabled)
        }

        // ── Canvas pan lock — top-bar button + CanvasView ────────────────────────
        viewModel.isCanvasPanLocked.observe(viewLifecycleOwner) { locked ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.setCanvasPanLocked(locked)
            }
            updateToggleButton(binding.canvasPanLock, locked)
            binding.canvasPanLock.setImageResource(
                if (locked) R.drawable.ic_lock else R.drawable.ic_unlock
            )
        }

        viewModel.zoomLevel.observe(viewLifecycleOwner) { zoom ->
            sizedCanvasView.setZoomLevel(zoom)
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { newSelection ->

            if (!isAdded) return@observe

            val selectionChanged = !newSelection.sameSelectionAs(lastSelection)
            if (!selectionChanged) return@observe

            lastSelection = newSelection.toList()

            // ── Toolbar visibility ────────────────────────────────────────────────
            // When panel is expanded (full-screen sticker browser), suppress ALL
            // context tools — alignment kit, opacity, blend etc. The user is in
            // browse mode, not edit mode.
            if (mainViewModel.expandedPanel.value != null) {
                // Just update internal state, show nothing
                resetPanelsOnSelectionChange()
                selectionFromUserInteraction = false
                return@observe
            }

            resetPanelsOnSelectionChange()
            updateToolbarVisibility(newSelection)

            if (viewModel.inSelectionMode.value == true) {
                selectionFromUserInteraction = false
                return@observe
            }

            val first = newSelection.firstOrNull()
            val currentDest = navController.currentDestination?.id

            if (currentDest == R.id.layersFragment) {
                selectionFromUserInteraction = false
                return@observe
            }

            // ── KEY FIX: Only navigate to adjustment/shape panels when the
            //    selection came from a real user interaction (tap on canvas,
            //    double-tap, edit icon tap). NOT when an element was just added
            //    programmatically via addSticker/addSvgSticker. ──────────────────
            if (!selectionFromUserInteraction) return@observe
            selectionFromUserInteraction = false   // consume — one-shot flag

            val targetDestination = when {
                newSelection.size == 1 && first != null -> {
                    when (first.type) {

                        ElementType.TEXT -> R.id.textAdjustmentsFragment

                        ElementType.IMAGE, ElementType.STICKER, ElementType.BACKGROUND -> R.id.adjustmentsParentFragment

                        ElementType.SHAPE -> if (shapeJustAdded) {
                            shapeJustAdded = false
                            null
                        } else {
                            R.id.shapeFragment
                        }

                        else -> null
                    }
                }

                else -> null
            }

            val panelDestinations = listOf(
                R.id.adjustmentsParentFragment,
                R.id.shapeFragment,
                R.id.textAdjustmentsFragment
            )

            if (targetDestination == null) {
                viewModel.closeAppearanceTab()
                val dest = navController.currentDestination?.id
                if (dest != null && dest in panelDestinations) {
                    navController.popBackStack(dest, true)
                }
                return@observe
            }

            if (currentDest == targetDestination) return@observe

            first?.let { element ->
                val bundle = Bundle().apply { putString("elementId", element.id) }

                if (targetDestination == R.id.adjustmentsParentFragment) {
                    if (element.bitmap != null) {
                        BitmapCache.put(element.id, element.bitmap!!)
                    } else if (element.svgDrawable != null) {
                        val svg = element.svgDrawable!!
                        val w = svg.intrinsicWidth.takeIf { it > 0 } ?: 512
                        val h = svg.intrinsicHeight.takeIf { it > 0 } ?: 512
                        svg.setBounds(0, 0, w, h)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).also { svg.draw(it) }
                        BitmapCache.put(element.id, bmp)
                    }
                } else if (targetDestination == R.id.textAdjustmentsFragment || targetDestination == R.id.shapesParentFragment) {
                    if (!(targetDestination == R.id.shapesParentFragment && shapeJustAdded)) {
                        viewModel.openAppearanceTab()
                    }
                }

                if (currentDest != null
                    && currentDest in panelDestinations
                    && currentDest != targetDestination
                ) {
                    navController.popBackStack(currentDest, true)
                }

                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()

                if (targetDestination == R.id.shapesParentFragment) {
                    shapeJustAdded = false
                }

                navController.navigate(targetDestination, bundle, navOptions)
            }
        }

    }

    private fun List<CanvasElement>.sameSelectionAs(other: List<CanvasElement>): Boolean {
        if (size != other.size) return false
        // FIX: Comparing only IDs was not enough. After applyMaskToSelected the element
        // ID stays the same but the bitmap changes. The observer would early-return thinking
        // nothing changed, so canvasManager.syncElements never ran and the old image stayed
        // on screen. Now also compare bitmapData so a mask change triggers a proper re-sync.
        return this.zip(other).all { (a, b) ->
            a.id == b.id && a.bitmapData == b.bitmapData
        }
    }

    // 👇 new helper
    private fun resetPanelsOnSelectionChange() {
        binding.seekBarOpacity.isVisible = false
        binding.opacityValue.isVisible = false
        binding.opacityIcon.isVisible = true
        binding.seekBarFontSize.isVisible = false
        binding.blendSpinner.isVisible = false
    }

    private fun updateToolbarVisibility(selected: List<CanvasElement>) {
        updateIconVisibility(binding.showHideContainer, selected.isNotEmpty())
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
        val hasImage =
            selected.any { it.type == ElementType.IMAGE || it.type == ElementType.STICKER }
        val hasBackground = selected.any { it.type == ElementType.BACKGROUND }
        val hasShapeMask = selected.any { it.type == ElementType.SHAPE && it.bitmap != null }
        val isMulti = selected.size > 1
        val isSvg = selected.any { it.svgData != null }
        val anySelected = selected.isNotEmpty()

        val showFont = anySelected && hasText && !isMulti && !hasImage && !hasBackground
        val showCopy = anySelected && !hasBackground && !isMulti
        val showAlignWithSelection = isMulti
        val showRemoveBg = (hasImage || hasBackground || hasShapeMask) && !isMulti && !isSvg

        updateIconVisibility(binding.opacityPane, anySelected)
        updateIconVisibility(binding.blendPane, anySelected)
        updateIconVisibility(binding.fontSizePane, showFont)
        updateIconVisibility(binding.copyIcon, showCopy)
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

    // ── Stable callback holder ────────────────────────────────────────────────
    // CanvasView lives in the ViewModel across fragment destruction. Callbacks
    // that reference binding / navController / lifecycleScope / viewLifecycleOwner
    // become stale when the fragment is recreated. We route those through these
    // fragment-level vars and update them in rewireCanvasCallbacks() every
    // onViewCreated. Callbacks that only touch viewModel (activityViewModels —
    // survives recreation) are passed as plain lambdas directly to CanvasView
    // and never need updating.
    private var cbOnEditTextRequested : (CanvasElement) -> Unit = {}
    private var cbOnElementSelected   : (List<CanvasElement>) -> Unit = {}
    private var cbOnRequestOpenLayers : () -> Unit = {}
    private var cbOnCanvasLongPressed : (Float, Float) -> Unit = { _, _ -> }

    /** Call on every onViewCreated to point all fragment-state callbacks at the live instance. */
    private fun rewireCanvasCallbacks() {
        cbOnEditTextRequested = { element -> handleEditTextRequested(element) }
        cbOnElementSelected   = { elements ->
            selectionFromUserInteraction = true
            viewModel.onCanvasSelectionChanged(elements)
        }
        cbOnRequestOpenLayers = { handleRequestOpenLayers() }
        cbOnCanvasLongPressed = { sx, sy -> showCanvasPopupMenu(sx, sy) }
    }

    /** Attach/restore CanvasView inside container */
    private fun initCanvas(widthPx: Int, heightPx: Int) {
        val existing = viewModel.getCanvasView()
        if (existing != null) {
            sizedCanvasView = existing
            sizedCanvasView.resizeCanvas(widthPx, heightPx)
            sizedCanvasView.resetZoomAndPan()
            // Re-parent only if needed — eager re-attach in onViewCreated may
            // have already done this; guard against double-add crash.
            if (sizedCanvasView.parent !== binding.canvasContainer) {
                (sizedCanvasView.parent as? ViewGroup)?.removeView(sizedCanvasView)
                binding.canvasContainer.addView(sizedCanvasView)
            }
            // canvasCallbacks was already updated by rewireCanvasCallbacks() in
            // onViewCreated — nothing more to do here for the existing view.
        } else {
            // First creation: callbacks that only use viewModel (activityViewModels, survives
            // recreation) are passed inline. The three that touch binding / navController /
            // lifecycleScope are forwarded through cb* vars updated every onViewCreated.
            sizedCanvasView = CanvasView(
                requireContext(),
                canvasWidth = widthPx,
                canvasHeight = heightPx,
                onEditTextRequested = { element -> cbOnEditTextRequested(element) },
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
                onElementSelected = { elements -> cbOnElementSelected(elements) },
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
                onRequestOpenLayers = { cbOnRequestOpenLayers() },
                onExitSelectionMode = { viewModel.exitSelectionMode() },
                onStrokeCompleted = { stroke -> viewModel.notifyDrawStrokeAdded(stroke) },
                onZoomChanged = { zoom -> viewModel.setZoomLevel(zoom) },
                onCanvasLongPressed = { sx, sy -> cbOnCanvasLongPressed(sx, sy) }
            ).apply {
                binding.canvasContainer.addView(this)
            }
            viewModel.setCanvasView(sizedCanvasView)
        }

        canvasManager = CanvasManager(sizedCanvasView)
    }

    /** Handles double-tap / edit requests from the canvas. */
    private fun handleEditTextRequested(element: CanvasElement) {
        if (!isAdded || view == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (element.type) {
                    ElementType.IMAGE, ElementType.BACKGROUND, ElementType.STICKER -> {
                        val selected = viewModel.canvasElements.value?.find { it.id == element.id }
                        selected?.let {
                            val key = it.id
                            BitmapCache.put(key, it.bitmap!!)
                            val bundle = Bundle().apply { putString("elementId", key) }
                            val navOptions = NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setPopUpTo(R.id.adjustmentsParentFragment, inclusive = true)
                                .build()
                            navController.navigate(R.id.adjustmentsParentFragment, bundle, navOptions)
                        }
                    }
                    ElementType.DRAW, ElementType.SHAPE -> {
                        if (element.type == ElementType.SHAPE) {
                            val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
                            navController.navigate(R.id.shapeFragment, null, navOptions)
                        } else {
                            val bundle = Bundle().apply { putInt("startPage", 0) }
                            val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
                            navController.navigate(R.id.drawFragment, bundle, navOptions)
                        }
                    }
                    else -> showTextEditDialog(element)
                }
            } catch (e: Exception) {
                Log.e("EditorFragment", "Navigation failed: ${e.message}")
            }
        }
    }

    /** Opens the layers panel — called from the canvas long-press callback. */
    private fun handleRequestOpenLayers() {
        // Guard BEFORE requireActivity() — the long-press fires from GestureDetector on the
        // main thread and can arrive after the fragment has been detached, at which point
        // requireActivity() throws IllegalStateException.
        if (!isAdded || view == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) return
        requireActivity().runOnUiThread {
            // Re-check inside the post in case state changed between the outer guard and now.
            if (!isAdded || view == null) return@runOnUiThread
            val b = _binding ?: return@runOnUiThread
            viewModel.enterSelectionMode()
            b.bottomNavigation.selectedItemId = R.id.nav_layers
            navController.navigate(R.id.layersFragment)
            currentPanelItemId = R.id.nav_layers
            b.panelNavHost.visibility = View.VISIBLE
        }
    }

    /** Setup bottom navigation with navHost */
    private fun initBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (currentPanelItemId != menuItem.itemId) {
                mainViewModel.collapsePanel()
                binding.panelNavHost.visibility = View.VISIBLE
                currentPanelItemId = menuItem.itemId
                when (menuItem.itemId) {
                    R.id.nav_shapes -> navController.navigate(R.id.shapesParentFragment)
                    R.id.nav_stickers -> navController.navigate(R.id.objectsFragment)
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
        binding.fabContainer.post {
            // Set initial position of the FAB to the bottom right of the container
            val fab = binding.fabAdd
            val container = binding.fabContainer

            // Ensure width/height are measured (should be by 'post')
            if (container.width > 0 && container.height > 0) {
                fab.x = container.width - fab.width - fabMargin.toFloat()
                fab.y = container.height - fab.height - fabMargin.toFloat()
            }

            initFab()
        }

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

        binding.opacityIcon.addPressEffect {
            togglePanel(showOpacityPanel = true)
            binding.opacityValue.setTextColor(ColorStateList.valueOf(colorOf(R.color.white)))
            binding.opacityValue.backgroundTintList =
                ColorStateList.valueOf(colorOf(R.color.appColor))
            resetFontSizeState()
            resetBlendState()
            activePanel = binding.opacityValue
        }

        binding.opacityValue.addPressEffect {
            togglePanel(showOpacityPanel = true)
            binding.opacityValue.setTextColor(ColorStateList.valueOf(colorOf(R.color.black)))
            binding.opacityValue.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
            resetFontSizeState()
            resetBlendState()
            activePanel = null
        }

        binding.fontSize.addPressEffect {
            if (activePanel == binding.fontSize) {
                resetFontSizeState()
                resetOpacityState()
                resetBlendState()
                activePanel = null
            } else {
                binding.fontSize.setTextColor(ColorStateList.valueOf(colorOf(R.color.white)))
                binding.fontSize.backgroundTintList =
                    ColorStateList.valueOf(colorOf(R.color.appColor))
                resetOpacityState()
                resetBlendState()
                activePanel = binding.fontSize
            }
            togglePanel(showOpacityPanel = false)
        }

        binding.blendIcon.addPressEffect {
            if (activePanel == binding.blendIcon) {
                resetBlendState()
                resetOpacityState()
                resetFontSizeState()
                activePanel = null
            } else {
                binding.blendIcon.imageTintList = ColorStateList.valueOf(colorOf(R.color.white))
                binding.blendIcon.backgroundTintList =
                    ColorStateList.valueOf(colorOf(R.color.appColor))
                resetOpacityState()
                resetFontSizeState()
                activePanel = binding.blendIcon
            }
            toggleBlendPanel()
        }

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
                val selected = viewModel.selectedElements.value?.firstOrNull()
                if (selected?.bitmap != null && selected.bitmapData != null) {
                    findNavController().navigate(R.id.bgRemovalFragment)
                }
            }
        }

        binding.zoom.addPressEffect {
            showZoomPopup(binding.zoom)
        }

        binding.grid.addPressEffect {
            viewModel.toggleGrid()
        }

        binding.ruler.addPressEffect {
            viewModel.toggleRuler()
        }

        binding.pan.addPressEffect {
            viewModel.togglePanMode()
        }

        binding.done.addPressEffect {
            viewModel.setCanvasView(sizedCanvasView)
            sizedCanvasView.clearSelection()
            view?.post {
                findNavController().navigate(R.id.exportFragment)
            }
        }

        initPanelSheet()
    }

    private fun colorOf(@ColorRes colorRes: Int): Int {
        return ContextCompat.getColor(requireActivity(), colorRes)
    }

    private fun resetOpacityState() {
        binding.opacityValue.setTextColor(ColorStateList.valueOf(colorOf(R.color.black)))
        binding.opacityValue.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
    }

    private fun resetFontSizeState() {
        binding.fontSize.setTextColor(ColorStateList.valueOf(colorOf(R.color.black)))
        binding.fontSize.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
    }

    private fun resetBlendState() {
        binding.blendIcon.imageTintList = ColorStateList.valueOf(colorOf(R.color.black))
        binding.blendIcon.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
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
            activePanel = binding.blendIcon
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
                activePanel = binding.opacityValue
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
                activePanel = binding.fontSize
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
            val (thumbnailBitmap, jsonFile) = withContext(Dispatchers.Default) {
                sizedCanvasView.exportCanvasThumbnailBitmap { _, _ -> }
            }
            withContext(Dispatchers.IO) {
                saveOnExitSafe(options, thumbnailBitmap, jsonFile, false, canvasSize)
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
            // exportCanvasThumbnailBitmap returns Pair<Bitmap, File>.
            // The File is a temp JSON file written via bufferedWriter — the JSON is never
            // held as a String in RAM. saveOnExitSafe stream-copies it to the final path.
            val (thumbnailBitmap, jsonFile) = withContext(Dispatchers.Default) {
                sizedCanvasView.exportCanvasThumbnailBitmap { percent, stage ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateExportDialog(percent, stage)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                updateExportDialog(97, "Saving files...")
            }
            withContext(Dispatchers.IO) {
                saveOnExitSafe(options, thumbnailBitmap, jsonFile, true, canvasSize)
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

    private fun showZoomPopup(anchorView: View) {
        val popupBinding = LayoutZoomPopupBinding.inflate(LayoutInflater.from(requireActivity()))

        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        popupWindow.isOutsideTouchable = true

        fun zoomPercentFromProgress(progress: Int): Int = 50 + progress

        fun progressFromZoomLevel(zoomLevel: Float): Int =
            ((zoomLevel * 100f).roundToInt() - 50).coerceIn(0, 250)

        fun refreshLabel(progress: Int) {
            popupBinding.zoomValue.text = "${zoomPercentFromProgress(progress)}%"
        }

        // Sync seekbar to current zoom level
        val initialProgress = progressFromZoomLevel(viewModel.zoomLevel.value ?: 1f)
        popupBinding.zoomSeekbar.progress = initialProgress
        refreshLabel(initialProgress)

        popupBinding.zoomSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomFraction = zoomPercentFromProgress(progress) / 100f
                    viewModel.setZoomLevel(zoomFraction)
                    // Read back actual snapped value directly from canvasView, not ViewModel
                    val actualZoom = sizedCanvasView.getCurrentZoom()
                    val actualProgress = progressFromZoomLevel(actualZoom)
                    seekBar.progress = actualProgress
                    refreshLabel(actualProgress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        popupBinding.reset.addPressEffect {
            viewModel.resetZoom()
            val resetProgress = progressFromZoomLevel(viewModel.zoomLevel.value ?: 1f)
            popupBinding.zoomSeekbar.progress = resetProgress
            refreshLabel(resetProgress)
            popupWindow.dismiss()
        }

        // ── Smart positioning ──
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
            val spaceAbove = anchorTop

            when {
                spaceBelow >= popupHeight -> popupWindow.showAsDropDown(anchorView)
                spaceAbove >= popupHeight -> popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY, location[0], anchorTop - popupHeight
                )

                else -> popupWindow.showAsDropDown(anchorView)
            }
        }
    }

    private fun updateToggleButton(view: ImageView, isActive: Boolean) {
        if (isActive) {
            view.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.appColor))
            view.imageTintList = ColorStateList.valueOf(colorOf(R.color.white))
        } else {
            view.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.contrast))
            view.imageTintList = ColorStateList.valueOf(colorOf(R.color.gray))
        }
    }

    private fun initPanelSheet() {
        val root = _binding?.root as? ConstraintLayout ?: return
        val guideline = root.findViewById<Guideline>(R.id.centerExpandableGuide) ?: return

        root.doOnLayout {
            val rootHeight   = root.height
            if (rootHeight == 0) return@doOnLayout
            val b = _binding ?: return@doOnLayout

            val collapsedPx = (rootHeight * 0.65f).toInt()   // resting position

            val expandedPx = 0

            panelSheet = PanelSheetBehavior(
                root            = root,
                guideline       = guideline,
                dragHandleView  = b.panelNavHost,   // placeholder; panels override via attachDragHandle()
                collapsedPx     = collapsedPx,
                expandedPx      = expandedPx,
                onSlide         = { offset ->
                    mainViewModel.setPanelSlideOffset(offset)
                    val bb = _binding ?: return@PanelSheetBehavior
                    bb.fabContainer.alpha = 1f - offset
                    bb.fabContainer.visibility = if (offset >= 1f) View.GONE else View.VISIBLE
                },
                onStateSettled  = { expanded ->
                    // Sync ViewModel so panels react
                    if (!expanded && mainViewModel.expandedPanel.value != null) {
                        mainViewModel.collapsePanel()
                    }
                    // Expansion is set by attachDragHandle's onStateSettled which knows panel type.
                    // If no panel has registered yet, collapse is the only action needed here.
                },
                dimView = b.dimOverlay
            )
            // Don't call attach() here — the real handle comes via attachDragHandle()
        }
    }

    /** Called by child panels to hand their drag handle to the sheet behavior. */
    fun attachDragHandle(handleView: View) {
        // Called only by expandable panel fragments — restore expandable state.
        isPanelExpandable = true
        currentDragHandle = handleView
        handleView.setOnTouchListener(null)  // ensure any block is cleared
        // Sheet may not exist yet if layout hasn't run — post it
        val rootView = _binding?.root ?: return
        rootView.post {
            val b = _binding ?: return@post
            val root = b.root as? ConstraintLayout ?: return@post
            val guideline = root.findViewById<Guideline>(R.id.centerExpandableGuide) ?: return@post
            val rootHeight = root.height.takeIf { it > 0 } ?: return@post

            val collapsedPx = (rootHeight * 0.65f).toInt()
            val expandedPx = 0

            // Cancel any in-flight spring from the previous instance before replacing it.
            // Also hard-reset the dimOverlay so a stale half-expanded state from the old
            // PanelSheetBehavior instance never keeps blocking canvas touches.
            panelSheet?.snapTo(expanded = false, immediate = true)
            b.dimOverlay.visibility  = View.INVISIBLE
            b.dimOverlay.isClickable = false

            val dest = _navController?.currentDestination?.id
            val panelType = when (dest) {
                R.id.imagesFragment       -> PanelType.IMAGES
                R.id.objectsFragment      -> PanelType.OBJECTS
                R.id.shapesParentFragment -> PanelType.SHAPES
                R.id.textFragment         -> PanelType.FONTS
                R.id.drawFragment         -> PanelType.DRAW
                R.id.layersFragment       -> PanelType.LAYERS
                else                      -> null
            }

            panelSheet = PanelSheetBehavior(
                root            = root,
                guideline       = guideline,
                dragHandleView  = handleView,
                collapsedPx     = collapsedPx,
                expandedPx      = expandedPx,
                onSlide         = { offset ->
                    mainViewModel.setPanelSlideOffset(offset)
                    val b = _binding ?: return@PanelSheetBehavior
                    b.fabContainer.alpha = 1f - offset
                    b.fabContainer.visibility = if (offset >= 1f) View.GONE else View.VISIBLE
                },
                onStateSettled  = { expanded ->
                    if (expanded) {
                        panelType?.let { mainViewModel.setPanelExpandedType(it) }
                    } else {
                        mainViewModel.collapsePanel()
                    }
                },
                dimView = b.dimOverlay
            )
            panelSheet!!.attach()
        }
    }

    private fun expandPanel(expanded: Boolean) {
        // Never expand when the current destination doesn't support it.
        if (expanded && !isPanelExpandable) return
        val sheet = panelSheet
        if (sheet == null) {
            // Sheet not ready — re-try after layout
            _binding?.root?.post { expandPanel(expanded) }
            return
        }
        if (sheet.isCurrentlyExpanded() == expanded) return
        sheet.snapTo(expanded)
    }

    /** For panels that want to forward gestures (e.g. swipe-up on RV at top). */
    fun panelSheetBehavior(): PanelSheetBehavior? = panelSheet

    /**
     * Block all touches from reaching the canvas while a panel is expanded
     * full-screen. Centralized here so every panel fragment doesn't have to
     * reimplement it.
     *
     * `panelNavHost` is the container that holds every panel (fonts, images,
     * shapes, etc.). When expanded, we set a no-op touch listener on it so any
     * touch that bubbles up unhandled — taps on gaps, drags on empty regions —
     * is swallowed by the panel itself and never reaches the canvas behind.
     * Children of the panel (RV scroll, button taps, search input, drag handle)
     * keep working because they consume their own touches before the bubble.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setPanelTouchBlocked(blocked: Boolean) {
        val b = _binding ?: return
        if (blocked) {
            b.panelNavHost.setOnTouchListener { _, _ -> true }
        } else {
            b.panelNavHost.setOnTouchListener(null)
            // Safety net: guarantee the dimOverlay is invisible and non-intercepting
            // whenever the panel collapses, regardless of whether PanelSheetBehavior's
            // spring endListener already reset it. Rapid expand→collapse gestures can
            // leave the overlay visible/clickable if the spring settles before the
            // expandedPanel Flow emits the null (collapsed) state.
            b.dimOverlay.visibility  = View.INVISIBLE
            b.dimOverlay.isClickable = false
        }
    }

    /**
     * Shown when the user long-presses inside the CanvasView but away from any
     * art-board element. Anchored at the raw touch coordinates.
     */

    fun View.applySelectionRing(isSelected: Boolean, fillColor: Int) {
        // The swatch's own color fill — always present.
        val fill = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(fillColor)
        }

        background = if (isSelected) {
            // Ring with a transparent center so the fill shows through it.
            val ring = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(
                    (3 * resources.displayMetrics.density).toInt(),
                    ContextCompat.getColor(requireContext(), R.color.appColor)
                )
            }
            // Fill underneath, ring on top.
            android.graphics.drawable.LayerDrawable(arrayOf(fill, ring))
        } else {
            // Not selected — just the fill, no ring.
            fill
        }
    }

    private fun showCanvasPopupMenu(touchRawX: Float, touchRawY: Float) {
        if (!isAdded) return

        val popupBinding = LayoutCanvasPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (210 * resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 2f
            isOutsideTouchable = true
        }

        // ── Current canvas size ───────────────────────────────────────────
        val size = viewModel.canvasSize.value
        popupBinding.canvasSizeValue.text = if (size != null) {
            getString(R.string.canvas_size_value, size.width.toInt(), size.height.toInt())
        } else ""

        popupBinding.actionCanvasSize.addPressEffect {
            popupWindow.dismiss()
            CreateFragment.newResizeInstance().show(parentFragmentManager, "resize_canvas")
        }

        // ── Background color: light / dark ────────────────────────────────
        val lightColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        val darkColor   = ContextCompat.getColor(requireContext(), R.color.black)

        val currentBgColor = viewModel.backgroundColor.value
            ?: requireActivity().getColor(R.color.contrast)

        popupBinding.bgLight.applySelectionRing(currentBgColor == lightColor, lightColor)
        popupBinding.bgDark.applySelectionRing(currentBgColor == darkColor, darkColor)

        popupBinding.bgLight.addPressEffect {
            viewModel.setCanvasBackgroundColor(lightColor)
            popupWindow.dismiss()
        }
        popupBinding.bgDark.addPressEffect {
            viewModel.setCanvasBackgroundColor(darkColor)
            popupWindow.dismiss()
        }

        // ── Lock / Unlock ─────────────────────────────────────────────────
        val locked = viewModel.isCanvasPanLocked.value ?: false
        popupBinding.actionLock.text =
            getString(if (locked) R.string.unlock_canvas else R.string.lock_canvas)
        popupBinding.actionLock.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, if (locked) R.drawable.ic_unlock else R.drawable.ic_lock, 0
        )
        popupBinding.actionLock.addPressEffect {
            viewModel.toggleCanvasPanLock()
            popupWindow.dismiss()
        }

        // ── Anchor at touch point, flip up if not enough room below ────────
        binding.canvasContainer.post {
            val screenHeight = resources.displayMetrics.heightPixels
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            val x = touchRawX.toInt()
            val y = if (screenHeight - touchRawY >= popupHeight) {
                touchRawY.toInt()
            } else {
                (touchRawY - popupHeight).toInt()
            }
            popupWindow.showAtLocation(binding.root, Gravity.NO_GRAVITY, x, y)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveJsonJob?.cancel()
        // Cancel the spring animation before nulling binding — its onSlide/onStateSettled
        // lambdas capture binding, and Choreographer can deliver one more frame after
        // onDestroyView, causing NPE on _binding!!.
        panelSheet?.snapTo(expanded = false, immediate = true)
        panelSheet = null
        _binding = null
        _navController = null
        // Null out CanvasView callbacks that capture fragment state. CanvasView lives in the
        // ViewModel across fragment recreation; without this, a GestureDetector long-press
        // arriving after onDestroyView throws IllegalStateException on requireActivity().
        cbOnEditTextRequested = {}
        cbOnElementSelected   = {}
        cbOnRequestOpenLayers = {}
        cbOnCanvasLongPressed = { _, _ -> }
        if (!BuildConfig.DEBUG) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        // GPU hard limit: 24 MP (ARGB_8888 @ 4 bytes/px = 96 MB).
        // Applied to every image entering the canvas — CanvasView's display-proxy
        // handles render performance, this just prevents hard OOM crashes.
        private const val GPU_SAFE_MAX_PX = 4899
    }
}