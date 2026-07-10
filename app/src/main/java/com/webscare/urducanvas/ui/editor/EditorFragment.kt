package com.webscare.urducanvas.ui.editor

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build.MANUFACTURER
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
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
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.webscare.urducanvas.BuildConfig
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.MultiAlignMode
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.enums.UnitType
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

fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density + 0.5f).toInt()

@AndroidEntryPoint
class EditorFragment : Fragment() {
    internal var _binding: FragmentEditorBinding? = null
    internal val binding get() = _binding!!
    internal lateinit var canvasManager: CanvasManager
    internal var _navController: NavController? = null
    internal val navController get() = _navController!!
    internal var panelsLocked = false
    internal lateinit var canvasSize: CanvasSize
    internal var currentUnit = UnitType.PIXELS
    internal val viewModel: CanvasViewModel by activityViewModels()
    internal var lastSelection: List<CanvasElement> = emptyList()
    internal var activePanel: View? = null
    internal val mainViewModel: MainViewModel by activityViewModels()
    internal var currentPanelItemId: Int? = null
    internal lateinit var sizedCanvasView: CanvasView
    internal var currentMode: MultiAlignMode = MultiAlignMode.CANVAS
    internal var exportModel: ExportResult? = null
    internal var jsonPath: String = "project_${System.currentTimeMillis()}.json"
    internal var imagePath: String = "project_img_${System.currentTimeMillis()}.png"
    internal var exportDialog: Dialog? = null
    internal var exportDialogBinding: DialogAutoSavingLayoutBinding? = null
    internal var rotationAnimator: ObjectAnimator? = null
    internal var isSaving = false
    internal var shapeJustAdded = false
    internal var saveJsonJob: Job? = null
    internal var savePending = false
    internal var lastJsonSaveTime = 0L
    internal val saveDebounce = 500L
    internal var selectionFromUserInteraction = false
    internal var isFabMenuOpen = false
    internal var fabInitialX = 0f
    internal var fabInitialY = 0f
    internal var fabInitialTouchX = 0f
    internal var fabInitialTouchY = 0f
    internal var fabMargin = 0

    internal var panelSheet: PanelSheetBehavior? = null
    internal var uiFullyInitialized = false

    // Fragments that open on element selection are NOT expandable — the sheet
    // must be locked collapsed while any of these destinations is active.
    internal val nonExpandableDestinations = setOf(
        R.id.adjustmentsParentFragment,
        R.id.shapeFragment,
        R.id.textAdjustmentsFragment,
    )
    internal var isPanelExpandable = true
    internal var currentDragHandle: View? = null // stored so we can block/restore touch
    internal val registeredDragHandles = mutableListOf<View>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEditorBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowAndInsets()
        setupPanelNavContainer()
        setupNavigation()

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

    private fun setupWindowAndInsets() {
        if (!BuildConfig.DEBUG) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            if (MANUFACTURER.equals("realme", ignoreCase = true)) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
            }
            insets
        }
    }

    private fun setupPanelNavContainer() {
        binding.panelNavContainer.dragListener = object : com.webscare.urducanvas.common.views.GestureFrameLayout.DragListener {
            override fun onDragBegin(downRawY: Float, currentRawY: Float) {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false) {
                    panelSheet?.externalDragBegin(downRawY, currentRawY)
                }
            }

            override fun onDragBy(currentRawY: Float) {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false) {
                    panelSheet?.externalDragBy(currentRawY)
                }
            }

            override fun onDragEnd() {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false) {
                    panelSheet?.externalDragEnd()
                }
            }
        }
        binding.panelNavContainer.isSwipeUpEnabled = {
            isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false
        }

        binding.panelNavHost.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val radius = (30 * view.resources.displayMetrics.density + 0.5f).toInt()
                outline.setRoundRect(0, 0, view.width, view.height + radius, radius.toFloat())
            }
        }
        binding.panelNavHost.clipToOutline = true
    }

    private fun setupNavigation() {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.panelNavHost) as NavHostFragment
        _navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        _navController?.addOnDestinationChangedListener { _, destination, _ ->
            onDestinationChanged(destination.id)
        }
    }

    private fun onDestinationChanged(destinationId: Int) {
        // Hide bottom nav for adjustments
        binding.bottomNavigation.isVisible =
            destinationId != R.id.adjustmentsParentFragment &&
            destinationId != R.id.shapeFragment &&
            destinationId != R.id.textAdjustmentsFragment

        // Lock the panel sheet collapsed for adjustment panels (non-expandable).
        // When the user navigates back to an expandable panel, attachDragHandle()
        // is called by that panel which resets the sheet and re-enables expanding.
        val isNonExpandable = destinationId in nonExpandableDestinations
        if (isPanelExpandable != !isNonExpandable) {
            isPanelExpandable = !isNonExpandable
            panelSheet?.isSwipeEnabled = isPanelExpandable
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

        updateBottomNavigationSelection(destinationId)
    }

    private fun updateBottomNavigationSelection(destinationId: Int) {
        when (destinationId) {
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
                    rawBitmap,
                    com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX,
                    com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX,
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
    internal fun initFab() {
        initFabClickListeners()
        initFabTouchListener()
    }

    private fun initFabClickListeners() {
        binding.fabAdd.setOnClickListener {
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
            shapeJustAdded = false // ← was true, change to false so observer allows navigation
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFabTouchListener() {
        binding.fabAdd.setOnTouchListener { v, event ->
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
                    handleFabActionMove(v, event, parentWidth, parentHeight)
                }

                MotionEvent.ACTION_UP -> {
                    handleFabActionUp(v, event)
                }
            }
            true
        }
    }

    private fun handleFabActionMove(v: View, event: MotionEvent, parentWidth: Int, parentHeight: Int) {
        val dx = event.rawX - fabInitialTouchX
        val dy = event.rawY - fabInitialTouchY

        var newX = fabInitialX + dx
        var newY = fabInitialY + dy

        newX = newX.coerceIn(
            fabMargin.toFloat(),
            (parentWidth - v.width - fabMargin).toFloat(),
        )
        newY = newY.coerceIn(
            fabMargin.toFloat(),
            (parentHeight - v.height - fabMargin).toFloat(),
        )

        v.x = newX
        v.y = newY

        if (isFabMenuOpen) {
            updateFabMenuPosition(binding.fabAdd, binding.fabMenu)
        }
    }

    private fun handleFabActionUp(v: View, event: MotionEvent) {
        v.translationZ = 10f

        if (isFabMenuOpen) {
            updateFabMenuPosition(binding.fabAdd, binding.fabMenu)
        }

        val deltaX = event.rawX - fabInitialTouchX
        val deltaY = event.rawY - fabInitialTouchY
        val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance < 10) {
            v.performClick()
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
                    ObjectAnimator.ofFloat(fabMenu, View.SCALE_Y, 0.5f, 1f),
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

    internal fun showTextEditDialog(element: CanvasElement) {
        EditorDialogManager.showTextEditDialog(requireContext(), element, viewModel)
    }

    internal fun scheduleJsonSave() {
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
        canvasSize: CanvasSize,
    ) = withContext(Dispatchers.IO) {
        try {
            if (exportImage) {
                saveImageFile(exportBitmap)
            }

            copyJsonFileAtomically(exportJsonFile)
            Log.d(TAG, "saveOnExitSafe: wrote $jsonPath")
            withContext(Dispatchers.Main) { updateExportDialog(97, "JSON saved") }

            val imageSizeBytes = if (exportImage) File(imagePath).length() else 0L
            val jsonSizeBytes = File(jsonPath).length()
            val fileSizeMB = (imageSizeBytes + jsonSizeBytes) / (1024.0 * 1024.0)

            val exportDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = viewModel.buildProjectFileName()

            createOrUpdateExportModel(fileSizeMB, options, canvasSize, exportDate, fileName)

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

    private suspend fun saveImageFile(exportBitmap: Bitmap) {
        File(imagePath).outputStream().use { out ->
            exportBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        withContext(Dispatchers.Main) { updateExportDialog(96, "Image saved") }
    }

    private fun copyJsonFileAtomically(exportJsonFile: File) {
        val tmpDest = File("$jsonPath.tmp")
        try {
            if (!exportJsonFile.exists()) {
                Log.e(TAG, "saveOnExitSafe: temp JSON missing: ${exportJsonFile.path}")
                return
            }
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
        } finally {
            exportJsonFile.delete() // always delete the source temp file
        }
    }

    private fun createOrUpdateExportModel(
        fileSizeMB: Double,
        options: ExportOptions,
        canvasSize: CanvasSize,
        exportDate: String,
        fileName: String
    ) {
        val current = exportModel
        if (current == null) {
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
            if (current.imagePath.startsWith("/storage")) {
                current.imagePath = imagePath
            }
            current.canvasSize = canvasSize
            current.fileSizeMB = fileSizeMB
            current.updatedDate = exportDate
        }
    }

    private fun observeViewModel() {
        EditorObserverBinder.observeViewModel(
            fragment = this,
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            onCanvasReady = { observeAfterCanvasReady() }
        )
    }

    internal fun observeAfterCanvasReady() {
        EditorObserverBinder.observeAfterCanvasReady(this, binding, viewModel, mainViewModel)
    }


    // 👇 new helper
    internal fun resetPanelsOnSelectionChange() {
        binding.seekBarOpacity.isVisible = false
        binding.opacityValue.isVisible = false
        binding.opacityIcon.isVisible = true
        binding.seekBarFontSize.isVisible = false
        binding.blendSpinner.isVisible = false
    }

    internal fun updateToolbarVisibility(selected: List<CanvasElement>) {
        EditorToolbarHandler.updateToolbarVisibility(this, binding, selected)
    }

    internal fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2,
    ) {
        EditorToolbarHandler.updateIconVisibility(binding, view, shouldBeVisible, animShow, animHide)
    }

    // ── Stable callback holder ────────────────────────────────────────────────
    // CanvasView lives in the ViewModel across fragment destruction. Callbacks
    // that reference binding / navController / lifecycleScope / viewLifecycleOwner
    // become stale when the fragment is recreated. We route those through these
    // fragment-level vars and update them in rewireCanvasCallbacks() every
    // onViewCreated. Callbacks that only touch viewModel (activityViewModels —
    // survives recreation) are passed as plain lambdas directly to CanvasView
    // and never need updating.
    private var cbOnEditTextRequested: (CanvasElement) -> Unit = {}
    private var cbOnElementSelected: (List<CanvasElement>) -> Unit = {}
    private var cbOnRequestOpenLayers: () -> Unit = {}
    private var cbOnCanvasLongPressed: (Float, Float) -> Unit = { _, _ -> }

    /** Call on every onViewCreated to point all fragment-state callbacks at the live instance. */
    private fun rewireCanvasCallbacks() {
        cbOnEditTextRequested = { element -> handleEditTextRequested(element) }
        cbOnElementSelected = { elements ->
            selectionFromUserInteraction = true
            viewModel.onCanvasSelectionChanged(elements)
        }
        cbOnRequestOpenLayers = { handleRequestOpenLayers() }
        cbOnCanvasLongPressed = { sx, sy -> showCanvasPopupMenu(sx, sy) }
    }

    /** Attach/restore CanvasView inside container */
    internal fun initCanvas(widthPx: Int, heightPx: Int) {
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
        } else {
            sizedCanvasView = createNewCanvasView(widthPx, heightPx)
        }

        canvasManager = CanvasManager(sizedCanvasView)
    }

    private fun createNewCanvasView(widthPx: Int, heightPx: Int): CanvasView {
        val newView = CanvasView(
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
            onCanvasLongPressed = { sx, sy -> cbOnCanvasLongPressed(sx, sy) },
        ).apply {
            binding.canvasContainer.addView(this)
        }
        viewModel.setCanvasView(newView)
        return newView
    }

    /** Handles double-tap / edit requests from the canvas. */
    private fun handleEditTextRequested(element: CanvasElement) {
        if (!isAdded ||
            view == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            return
        }
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
                Log.e("EditorFragment", "Navigation failed", e)
            }
        }
    }

    /** Opens the layers panel — called from the canvas long-press callback. */
    private fun handleRequestOpenLayers() {
        // Guard BEFORE requireActivity() — the long-press fires from GestureDetector on the
        // main thread and can arrive after the fragment has been detached, at which point
        // requireActivity() throws IllegalStateException.
        if (!isAdded ||
            view == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            return
        }
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
    internal fun initBottomNavigation() {
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
    internal fun initUIControls() {
        EditorToolbarHandler.initUIControls(this, binding, viewModel)
    }

    internal fun colorOf(@ColorRes colorRes: Int): Int = ContextCompat.getColor(requireActivity(), colorRes)

    internal fun resetOpacityState() {
        binding.opacityValue.setTextColor(ColorStateList.valueOf(colorOf(R.color.black)))
        binding.opacityValue.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
    }

    internal fun resetFontSizeState() {
        binding.fontSize.setTextColor(ColorStateList.valueOf(colorOf(R.color.black)))
        binding.fontSize.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
    }

    internal fun resetBlendState() {
        binding.blendIcon.imageTintList = ColorStateList.valueOf(colorOf(R.color.black))
        binding.blendIcon.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.white))
    }

    internal fun showItemPopupMenu(anchorView: View) {
        EditorDialogManager.showItemPopupMenu(this, anchorView, viewModel)
    }

    /** Setup back button behavior */
    internal fun initBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    autoSave()
                }
            },
        )

        binding.back.addPressEffect { autoSave() }
    }

    internal fun toggleBlendPanel() {
        EditorToolbarHandler.toggleBlendPanel(this, binding)
    }

    internal fun togglePanel(showOpacityPanel: Boolean) {
        EditorToolbarHandler.togglePanel(this, binding, showOpacityPanel)
    }

    internal fun updateModeDrawables() {
        EditorToolbarHandler.updateModeDrawables(this, binding)
    }

    internal fun showExportProgressDialog() {
        EditorDialogManager.showExportProgressDialog(this)
    }

    internal fun updateExportDialog(percent: Int, stage: String) {
        EditorDialogManager.updateExportDialog(this, percent, stage)
    }

    internal fun dismissExportDialog() {
        EditorDialogManager.dismissExportDialog(this)
    }

    internal fun autoSaveSilent() {
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

    internal fun showZoomPopup(anchorView: View) {
        EditorDialogManager.showZoomPopup(this, anchorView, viewModel)
    }

    internal fun updateToggleButton(view: ImageView, isActive: Boolean) {
        EditorToolbarHandler.updateToggleButton(this, view, isActive)
    }

    internal fun initPanelSheet() {
        val root = _binding?.root as? ConstraintLayout ?: return
        val guideline = root.findViewById<Guideline>(R.id.centerExpandableGuide) ?: return

        root.doOnLayout {
            val rootHeight = root.height
            if (rootHeight == 0) return@doOnLayout
            val b = _binding ?: return@doOnLayout

            val collapsedPx = (rootHeight * 0.65f).toInt() // resting position

            val expandedPx = 0

            panelSheet = PanelSheetBehavior(
                root = root,
                guideline = guideline,
                dragHandleView = b.panelNavHost, // placeholder; panels override via attachDragHandle()
                collapsedPx = collapsedPx,
                expandedPx = expandedPx,
                onSlide = { offset ->
                    mainViewModel.setPanelSlideOffset(offset)
                    val bb = _binding ?: return@PanelSheetBehavior
                    bb.fabContainer.alpha = 1f - offset
                    bb.fabContainer.visibility = if (offset >= 1f) View.GONE else View.VISIBLE
                },
                onStateSettled = { expanded ->
                    // Sync ViewModel so panels react
                    if (!expanded && mainViewModel.expandedPanel.value != null) {
                        mainViewModel.collapsePanel()
                    }
                    // Expansion is set by attachDragHandle's onStateSettled which knows panel type.
                    // If no panel has registered yet, collapse is the only action needed here.
                },
                dimView = b.dimOverlay,
            )
            // Don't call attach() here — the real handle comes via attachDragHandle()
        }
    }

    fun registerAdditionalDragHandle(view: View) {
        val b = _binding ?: return
        if (!registeredDragHandles.contains(view)) {
            registeredDragHandles.add(view)
            b.panelNavContainer.dragHandles = ArrayList(registeredDragHandles)
        }
    }

    /** Called by child panels to hand their drag handle to the sheet behavior. */
    fun attachDragHandle(handleView: View) {
        // Called only by expandable panel fragments — restore expandable state.
        isPanelExpandable = true
        currentDragHandle = handleView
        handleView.setOnTouchListener(null) // ensure any block is cleared
        // Sheet may not exist yet if layout hasn't run — post it
        val rootView = _binding?.root ?: return
        rootView.post {
            setupPanelSheetAndDrag(handleView)
        }
    }

    private fun setupPanelSheetAndDrag(handleView: View) {
        val b = _binding ?: return
        val root = b.root as? ConstraintLayout ?: return
        val guideline = root.findViewById<Guideline>(R.id.centerExpandableGuide) ?: return
        val rootHeight = root.height.takeIf { it > 0 } ?: return

        val collapsedPx = (rootHeight * 0.65f).toInt()
        val expandedPx = 0

        // Reset drag handles tracker
        registeredDragHandles.clear()
        registeredDragHandles.add(handleView)
        b.panelNavContainer.dragHandles = ArrayList(registeredDragHandles)

        // Cancel any in-flight spring from the previous instance before replacing it.
        // Also hard-reset the dimOverlay so a stale half-expanded state from the old
        // PanelSheetBehavior instance never keeps blocking canvas touches.
        panelSheet?.snapTo(expanded = false, immediate = true)
        b.dimOverlay.visibility = View.INVISIBLE
        b.dimOverlay.isClickable = false

        val dest = _navController?.currentDestination?.id
        val panelType = getPanelTypeFromDestination(dest)

        panelSheet = createPanelSheetBehavior(root, guideline, handleView, collapsedPx, expandedPx, panelType)
        panelSheet!!.attach()
    }

    private fun getPanelTypeFromDestination(destId: Int?): PanelType? {
        return when (destId) {
            R.id.imagesFragment -> PanelType.IMAGES
            R.id.objectsFragment -> PanelType.OBJECTS
            R.id.shapesParentFragment -> PanelType.SHAPES
            R.id.textFragment -> PanelType.FONTS
            R.id.drawFragment -> PanelType.DRAW
            R.id.layersFragment -> PanelType.LAYERS
            else -> null
        }
    }

    private fun createPanelSheetBehavior(
        root: ConstraintLayout,
        guideline: Guideline,
        handleView: View,
        collapsedPx: Int,
        expandedPx: Int,
        panelType: PanelType?
    ): PanelSheetBehavior {
        return PanelSheetBehavior(
            root = root,
            guideline = guideline,
            dragHandleView = handleView,
            collapsedPx = collapsedPx,
            expandedPx = expandedPx,
            onSlide = { offset ->
                mainViewModel.setPanelSlideOffset(offset)
                val bb = _binding ?: return@PanelSheetBehavior
                bb.fabContainer.alpha = 1f - offset
                bb.fabContainer.visibility = if (offset >= 1f) View.GONE else View.VISIBLE
            },
            onStateSettled = { expanded ->
                if (expanded) {
                    panelType?.let { mainViewModel.setPanelExpandedType(it) }
                } else {
                    mainViewModel.collapsePanel()
                }
            },
            dimView = binding.dimOverlay,
        ).apply {
            onAdditionalHandleAttached = { view ->
                registerAdditionalDragHandle(view)
            }
        }
    }

    internal fun expandPanel(expanded: Boolean) {
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
    internal fun setPanelTouchBlocked(blocked: Boolean) {
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
            b.dimOverlay.visibility = View.INVISIBLE
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
                    (2 * resources.displayMetrics.density).toInt(),
                    ContextCompat.getColor(requireContext(), R.color.appColor),
                )
            }
            // Fill underneath (inset), ring on top.
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(fill, ring))
            val inset = (4 * resources.displayMetrics.density).toInt()
            layerDrawable.setLayerInset(0, inset, inset, inset, inset)
            layerDrawable
        } else {
            // Not selected — just the fill, no ring.
            fill
        }
    }

    private fun showCanvasPopupMenu(touchRawX: Float, touchRawY: Float) {
        if (!isAdded) return
        EditorDialogManager.showCanvasPopupMenu(this, touchRawX, touchRawY, viewModel)
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
        cbOnElementSelected = {}
        cbOnRequestOpenLayers = {}
        cbOnCanvasLongPressed = { _, _ -> }
        if (!BuildConfig.DEBUG) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
