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
import com.google.android.material.snackbar.Snackbar
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
import com.webscare.urducanvas.common.views.CalloutArrowDirection
import com.webscare.urducanvas.common.views.CalloutBubbleDrawable
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
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
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
import kotlinx.coroutines.isActive
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
    @javax.inject.Inject
    lateinit var dataStore: com.webscare.urducanvas.common.datastore.PreferencesDataStoreHelper
    private var lastSelection: List<CanvasElement>? = null
    private val pendingHideRunnables = HashMap<View, Runnable>()
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
        R.id.textAdjustmentsFragment,
        R.id.tableAdjustmentsFragment,
        R.id.universalEraserFragment,
        R.id.drawFragment
    )
    private var isPanelExpandable = true
    private var currentDragHandle: View? = null  // stored so we can block/restore touch
    private val registeredDragHandles = mutableListOf<View>()

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

        if (BuildConfig.IS_PROD_LOGIC) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        binding.editorRoot.clipChildren = false
        binding.editorRoot.clipToPadding = false
        binding.header.clipChildren = false
        binding.header.clipToPadding = false
        binding.topToolbarRow.clipChildren = false
        binding.topToolbarRow.clipToPadding = false
        binding.normalTools.clipChildren = false
        binding.normalTools.clipToPadding = false
        binding.tableTools.clipChildren = false
        binding.tableTools.clipToPadding = false
        binding.drawTools.clipChildren = false
        binding.drawTools.clipToPadding = false
        binding.redoUndo.clipChildren = false
        binding.redoUndo.clipToPadding = false
        (binding.root.parent as? ViewGroup)?.clipChildren = false
        (binding.root.parent as? ViewGroup)?.clipToPadding = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavContainer) { view, insets ->
            if (MANUFACTURER.equals("realme", ignoreCase = true)) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
            }
            insets
        }

        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.panelNavHost) as NavHostFragment
        _navController = navHostFragment.navController

        binding.panelNavContainer.dragListener = object : com.webscare.urducanvas.common.views.GestureFrameLayout.DragListener {
            override fun onDragBegin(downRawY: Float, currentRawY: Float) {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false && _navController?.currentDestination?.id != R.id.layersFragment) {
                    panelSheet?.externalDragBegin(downRawY, currentRawY)
                }
            }

            override fun onDragBy(currentRawY: Float) {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false && _navController?.currentDestination?.id != R.id.layersFragment) {
                    panelSheet?.externalDragBy(currentRawY)
                }
            }

            override fun onDragEnd() {
                if (isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false && _navController?.currentDestination?.id != R.id.layersFragment) {
                    panelSheet?.externalDragEnd()
                }
            }
        }
        binding.panelNavContainer.isSwipeUpEnabled = {
            isPanelExpandable && panelSheet?.isCurrentlyExpanded() == false && _navController?.currentDestination?.id != R.id.layersFragment
        }

        binding.panelNavHost.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val radius = (30 * view.resources.displayMetrics.density + 0.5f).toInt()
                outline.setRoundRect(0, 0, view.width, view.height + radius, radius.toFloat())
            }
        }
        binding.panelNavHost.clipToOutline = true

        _navController?.addOnDestinationChangedListener { _, destination, _ ->

            val isAdjustment = destination.id in nonExpandableDestinations
            animateBottomNav(show = !isAdjustment)

            updateToolbarMode(animate = true)
            updateToolbarVisibility(viewModel.selectedElements.value.orEmpty(), animate = false)

            // Lock the panel sheet collapsed for adjustment panels (non-expandable).
            // When the user navigates back to an expandable panel, attachDragHandle()
            // is called by that panel which resets the sheet and re-enables expanding.
            val isNonExpandable = destination.id in nonExpandableDestinations
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

            when (destination.id) {
                R.id.textFragment -> {
                    updateBottomNavSelection(R.id.nav_text)
                    currentPanelItemId = R.id.nav_text
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.imagesFragment -> {
                    updateBottomNavSelection(R.id.nav_images)
                    currentPanelItemId = R.id.nav_images
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.shapesParentFragment -> {
                    updateBottomNavSelection(R.id.nav_shapes)
                    currentPanelItemId = R.id.nav_shapes
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.objectsFragment -> {
                    updateBottomNavSelection(R.id.nav_stickers)
                    currentPanelItemId = R.id.nav_stickers
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.tablesParentFragment -> {
                    updateBottomNavSelection(R.id.nav_tables)
                    currentPanelItemId = R.id.nav_tables
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.drawFragment -> {
                    updateBottomNavSelection(R.id.nav_draw)
                    currentPanelItemId = R.id.nav_draw
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                R.id.layersFragment -> {
                    updateBottomNavSelection(R.id.nav_layers)
                    currentPanelItemId = R.id.nav_layers
                    binding.panelNavHost.visibility = View.VISIBLE
                }

                else -> {
                    updateBottomNavSelection(null)
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

        // ── Update all callback references to point at this (fresh) fragment instance.
        // Must happen before observeViewModel() so that any LiveData re-delivery
        // that fires synchronously already sees the live lambdas.
        rewireCanvasCallbacks()

        // ── Restore state on process death / view recreation ──
        if (savedInstanceState != null) {
            val restoredModel = savedInstanceState.getSerializable("key_export_model") as? ExportResult
            val restoredJsonPath = savedInstanceState.getString("key_json_path")
            val restoredImagePath = savedInstanceState.getString("key_image_path")
            val restoredCanvasSize = savedInstanceState.getSerializable("key_canvas_size") as? CanvasSize

            if (restoredModel != null) {
                exportModel = restoredModel
                jsonPath = restoredModel.jsonPath
                imagePath = restoredModel.imagePath
                if (viewModel.canvasSize.value == null) {
                    viewModel.loadTemplateFromJsonFile(restoredModel, requireContext())
                }
            } else if (restoredJsonPath != null && restoredImagePath != null && restoredCanvasSize != null) {
                jsonPath = restoredJsonPath
                imagePath = restoredImagePath
                canvasSize = restoredCanvasSize
                if (viewModel.canvasSize.value == null && File(restoredJsonPath).exists()) {
                    val tempModel = ExportResult(
                        imagePath = restoredImagePath,
                        jsonPath = restoredJsonPath,
                        fileName = "Project",
                        fileSizeMB = 0.0,
                        resolution = "Medium",
                        format = "PNG",
                        quality = "Normal",
                        canvasSize = restoredCanvasSize,
                        exportDate = "",
                        updatedDate = ""
                    )
                    viewModel.loadTemplateFromJsonFile(tempModel, requireContext())
                }
            }
        }

        // ── Re-attach CanvasView only if state matches active project size ──
        if (viewModel.canvasSize.value != null) {
            viewModel.getCanvasView()?.let { existing ->
                if (existing.parent !== binding.canvasContainer) {
                    (existing.parent as? ViewGroup)?.removeView(existing)
                    binding.canvasContainer.addView(existing)
                }
            }
        }

        observeViewModel()
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = com.webscare.urducanvas.common.utils.ImageUtils.getFileNameFromUri(requireActivity(), uri)
                val filePath =
                    ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        ?: return@launch

                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return@launch

                // Consistent with every other image entry point in the app:
                // User explicitly picked this image as a canvas element — preserve full
                // quality up to the GPU hard limit (24 MP / 4899 px per side).
                // CanvasView's display-proxy system handles render performance transparently.
                val bitmap = ImageProcessor.downsampleIfNeeded(
                    rawBitmap, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX
                )

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE, customName = fileName)
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

        binding.addTable.addPressEffect {
            val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
            navController.navigate(R.id.tablesParentFragment, null, navOptions)
            toggleFabMenu(false)
        }

        binding.addDraw.addPressEffect {
            val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
            navController.navigate(R.id.drawFragment, null, navOptions)
            toggleFabMenu(false)
        }

        val highFabZ = 50f * resources.displayMetrics.density
        binding.fabContainer.translationZ = highFabZ
        binding.fabContainer.bringToFront()
        fabAdd.translationZ = 0f
        fabMenu.translationZ = 0f

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
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - fabInitialTouchX
                    val dy = event.rawY - fabInitialTouchY
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance > 10) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }

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
                    v.translationZ = highFabZ
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()

                    if (isFabMenuOpen) {
                        updateFabMenuPosition(fabAdd, fabMenu)
                    }

                    val deltaX = event.rawX - fabInitialTouchX
                    val deltaY = event.rawY - fabInitialTouchY
                    val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

                    if (distance < 10) {
                        toggleFabMenu(!isFabMenuOpen)
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.translationZ = highFabZ
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }
            }
            true
        }
    }

    private var bottomNavAnimator: android.animation.ValueAnimator? = null
    private var bottomNavExpandedHeight: Int = 0
    private var bottomNavShown: Boolean? = null

    /**
     * Slides the bottom nav out of the way for adjustment panels.
     *
     * The height is animated rather than the view being translated and then flipped to GONE:
     * the panel container is constrained to this view's top, so a GONE flip collapsed 64dp of
     * layout in a single frame and the whole panel snapped upward at the end of the slide.
     * Driving the height keeps that growth continuous, so nothing jumps.
     */
    private fun animateBottomNav(show: Boolean) {
        val b = _binding ?: return
        val nav = b.bottomNavContainer

        if (bottomNavExpandedHeight <= 0) {
            bottomNavExpandedHeight = nav.height.takeIf { it > 0 }
                ?: (64f * resources.displayMetrics.density).toInt()
        }
        if (bottomNavShown == show) return
        bottomNavShown = show

        bottomNavAnimator?.cancel()

        val from = nav.layoutParams.height.takeIf { it >= 0 } ?: bottomNavExpandedHeight
        val to = if (show) bottomNavExpandedHeight else 0
        if (show) nav.visibility = View.VISIBLE

        bottomNavAnimator = android.animation.ValueAnimator.ofInt(from, to).apply {
            duration = 260
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { anim ->
                val binding = _binding ?: return@addUpdateListener
                val h = anim.animatedValue as Int
                binding.bottomNavContainer.layoutParams =
                    binding.bottomNavContainer.layoutParams.also { it.height = h }
                binding.bottomNavContainer.alpha =
                    (h.toFloat() / bottomNavExpandedHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val binding = _binding ?: return
                    if (!show) binding.bottomNavContainer.visibility = View.INVISIBLE
                }
            })
            start()
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
        dialog.setContentView(R.layout.dialog_canvas_text_edit)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        editText.setText(element.text)
        editText.setSelection(editText.text.length)
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
                launch {
                    mainViewModel.expandedPanel.collect { panel ->
                        val expanded = panel != null
                        expandPanel(expanded)
                        // When any panel is expanded full-screen, block touches from
                        // reaching the canvas behind it. Handled here once for every
                        // panel type rather than in each panel fragment.
                        setPanelTouchBlocked(expanded)
                    }
                }
                launch {
                    mainViewModel.panelSlideOffset.collect { offset ->
                        val b = _binding ?: return@collect
                        val dimAlpha = (offset * PanelSheetBehavior.MAX_DIM_ALPHA).coerceIn(0f, PanelSheetBehavior.MAX_DIM_ALPHA)
                        b.dimOverlay.alpha = dimAlpha
                        if (dimAlpha > 0.01f) {
                            b.dimOverlay.visibility = View.VISIBLE
                            b.dimOverlay.isClickable = true
                        } else {
                            b.dimOverlay.visibility = View.INVISIBLE
                            b.dimOverlay.isClickable = false
                        }
                    }
                }
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showExportProgressDialog()
                val initialStage = viewModel.loadingStage.value
                val title = initialStage?.first ?: "Loading..."
                val pct = initialStage?.second ?: 0
                updateExportDialog(pct, title)
            } else if (isLoading == false) {
                dismissExportDialog()
                if (isAdded && isResumed && viewModel.canvasSize.value == null) {
                    Snackbar.make(binding.root, "Failed to restore project", Snackbar.LENGTH_LONG).show()
                    try {
                        findNavController().navigateUp()
                    } catch (e: Exception) {
                        Log.e(TAG, "Navigation failed: ${e.message}")
                    }
                }
                viewModel.clearLoading()
            }
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            updateExportDialog(percent, "$message...")
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

        viewModel.exportResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                exportModel = result
                jsonPath = result.jsonPath
                imagePath = result.imagePath
            }
        }
    }

    private fun observeAfterCanvasReady() {
        if (::sizedCanvasView.isInitialized) {
            sizedCanvasView.isSmartSnappingEnabled = viewModel.isSmartSnappingEnabled
        }
        startPeriodicAutoSave()

        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (::sizedCanvasView.isInitialized) sizedCanvasView.setSelectionMode(enabled)
        }

        // No theme-derived default here any more: the artboard starts white from
        // CanvasViewModel.ensureBackgroundElement, and the chrome keeps its own theme colour
        // so the artboard colour is the only thing that reaches the exported image.
        val chromeColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        binding.editorRoot.setBackgroundColor(chromeColor)
        binding.canvasContainer.setBackgroundColor(chromeColor)

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            if (isAdded) {
                color?.let {
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
            if (isAdded && elements != null) {
                if (::canvasManager.isInitialized) {
                    canvasManager.syncElements(elements)
                    binding.canvasContainer.invalidate()
                    if (elements.isNotEmpty()) {
                        scheduleJsonSave()
                    }
                }
                val panelDestinations = listOf(
                    R.id.adjustmentsParentFragment,
                    R.id.shapeFragment,
                    R.id.textAdjustmentsFragment,
                    R.id.tableAdjustmentsFragment
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
            // Filter changes are handled via canvasElements observer syncing elements with filterIntensity preserved.
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
                sizedCanvasView.setDrawingMode(isDrawing, viewModel.getActiveDrawSession())
            }
            updateToolbarMode(animate = true)
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

        viewModel.brushOpacity.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(opacity = it)
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(style = it)
        }

        viewModel.brushGradient.observe(viewLifecycleOwner) {
            sizedCanvasView.updateBrushSettings(gradient = it)
        }

        viewModel.isBrushSmoothingEnabled.observe(viewLifecycleOwner) { enabled ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.isBrushSmoothingEnabled = (enabled == true)
            }
        }

        viewModel.isEraserActive.observe(viewLifecycleOwner) { isEraser ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.isEraserActive = (isEraser == true)
            }
            updateToolbarVisibility(viewModel.selectedElements.value.orEmpty(), animate = false)
        }

        viewModel.isDrawingMode.observe(viewLifecycleOwner) {
            updateToolbarVisibility(viewModel.selectedElements.value.orEmpty(), animate = false)
        }

        viewModel.eraserThickness.observe(viewLifecycleOwner) { thickness ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.currentEraserThickness = thickness ?: 50f
            }
        }

        viewModel.eraserHardness.observe(viewLifecycleOwner) { hardness ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.currentEraserHardness = hardness ?: 1f
            }
        }

        viewModel.eraserOpacity.observe(viewLifecycleOwner) { opacity ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.currentEraserOpacity = opacity ?: 1f
            }
        }

        viewModel.isEraserSmoothingEnabled.observe(viewLifecycleOwner) { enabled ->
            if (::sizedCanvasView.isInitialized) {
                sizedCanvasView.isEraserSmoothingEnabled = (enabled == true)
            }
        }

        viewModel.sizePreviewState.observe(viewLifecycleOwner) { state ->
            if (::sizedCanvasView.isInitialized) {
                if (state != null && state.isVisible) {
                    sizedCanvasView.showSizePreview(
                        size = state.size,
                        hardness = state.hardness,
                        opacity = state.opacity,
                        color = state.color,
                        isEraser = state.isEraser
                    )
                } else {
                    sizedCanvasView.hideSizePreview()
                }
            }
        }

        viewModel.activePicker.observe(viewLifecycleOwner) { slot ->
            if (::sizedCanvasView.isInitialized) {
                when (slot) {
                    PickerTarget.EYE_DROPPER_LABEL, PickerTarget.EYE_DROPPER_OVERLAY, PickerTarget.EYE_DROPPER_SHADOW, PickerTarget.EYE_DROPPER_GLOW, PickerTarget.EYE_DROPPER_BACKGROUND, PickerTarget.EYE_DROPPER_TEXT_FILL, PickerTarget.EYE_DROPPER_TEXT_STROKE, PickerTarget.EYE_DROPPER_GRADIENT, PickerTarget.EYE_DROPPER_DRAW_STROKE, PickerTarget.EYE_DROPPER_DRAW_FILL, PickerTarget.EYE_DROPPER_IMAGE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_FILL, PickerTarget.EYE_DROPPER_TABLE_FILL, PickerTarget.EYE_DROPPER_TABLE_STROKE -> {
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

        viewModel.rulerState.observe(viewLifecycleOwner) { rulerState ->
            sizedCanvasView.setRulerState(rulerState)
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
        }

        viewModel.zoomLevel.observe(viewLifecycleOwner) { zoom ->
            if (sizedCanvasView.getCurrentZoom() != zoom) {
                sizedCanvasView.setZoomLevel(zoom)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.localFonts.collect { fonts ->
                    if (::sizedCanvasView.isInitialized) {
                        sizedCanvasView.localFonts = fonts
                    }
                }
            }
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { newSelection ->

            if (!isAdded) return@observe

            val selectionChanged = !newSelection.sameSelectionAs(lastSelection)
            if (!selectionChanged) return@observe

            val wasAnySelected = !lastSelection.isNullOrEmpty()
            lastSelection = newSelection.toList()
            val anySelected = newSelection.isNotEmpty()
            val isSelectionSwitch = wasAnySelected && anySelected

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
            val effectiveSelection = if (selectionFromUserInteraction) newSelection else emptyList()
            updateToolbarVisibility(effectiveSelection, animate = !isSelectionSwitch && selectionFromUserInteraction)

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

            var isMixedGroupSelection = false
            var targetGroupId: String? = null

            val targetDestination = when {
                newSelection.size == 1 && first != null -> {
                    when (first.type) {

                        ElementType.TEXT -> R.id.textAdjustmentsFragment

                        ElementType.TABLE -> R.id.tableAdjustmentsFragment

                        ElementType.IMAGE, ElementType.STICKER, ElementType.BACKGROUND, ElementType.DRAW -> R.id.adjustmentsParentFragment

                        ElementType.SHAPE -> if (shapeJustAdded) {
                            shapeJustAdded = false
                            null
                        } else {
                            R.id.shapeFragment
                        }

                        ElementType.GROUP -> {
                            targetGroupId = first.id
                            val children = viewModel.canvasElements.value?.filter { it.groupId == first.id } ?: emptyList()
                            when {
                                children.isNotEmpty() && children.all { it.type == ElementType.TEXT } -> {
                                    R.id.textAdjustmentsFragment
                                }
                                children.isNotEmpty() && children.all { it.type == ElementType.TABLE } -> {
                                    R.id.tableAdjustmentsFragment
                                }
                                children.isNotEmpty() && children.all { it.type == ElementType.SHAPE } -> {
                                    R.id.shapeFragment
                                }
                                children.isNotEmpty() && children.all { it.type in listOf(ElementType.IMAGE, ElementType.STICKER, ElementType.BACKGROUND, ElementType.DRAW) } -> {
                                    R.id.adjustmentsParentFragment
                                }
                                else -> {
                                    isMixedGroupSelection = true
                                    null
                                }
                            }
                        }

                        else -> null
                    }
                }

                else -> null
            }

            val panelDestinations = listOf(
                R.id.adjustmentsParentFragment,
                R.id.shapeFragment,
                R.id.textAdjustmentsFragment,
                R.id.tableAdjustmentsFragment
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
                val bundle = Bundle().apply {
                    putString("elementId", element.id)
                    if (isMixedGroupSelection) putBoolean("isMixedGroup", true)
                    if (targetGroupId != null) putString("groupId", targetGroupId)
                }

                if (targetDestination == R.id.adjustmentsParentFragment) {
                    if (element.bitmap != null) {
                        BitmapCache.put(element.id, element.bitmap!!)
                    } else if (!element.bitmapData.isNullOrBlank()) {
                        val decoded = com.webscare.urducanvas.common.utils.ImageProcessor.base64ToBitmap(element.bitmapData!!)
                        if (decoded != null) {
                            element.bitmap = decoded
                            BitmapCache.put(element.id, decoded)
                        } else {
                            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                            BitmapCache.put(element.id, bmp)
                        }
                    } else if (element.svgDrawable != null) {
                        val svg = element.svgDrawable!!
                        val w = svg.intrinsicWidth.takeIf { it > 0 } ?: 512
                        val h = svg.intrinsicHeight.takeIf { it > 0 } ?: 512
                        svg.setBounds(0, 0, w, h)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).also { svg.draw(it) }
                        BitmapCache.put(element.id, bmp)
                    } else if (element.type == ElementType.DRAW && !element.drawStrokes.isNullOrEmpty()) {
                        val w = (element.logicalContentWidth.takeIf { it > 0 } ?: 512f).toInt()
                        val h = (element.logicalContentHeight.takeIf { it > 0 } ?: 512f).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bmp)
                        element.drawStrokes?.forEach { s ->
                            com.webscare.urducanvas.common.utils.BrushRenderUtils.drawSingleStroke(c, s, 255)
                        }
                        element.bitmap = bmp
                        BitmapCache.put(element.id, bmp)
                    } else if (element.type == ElementType.GROUP || element.type == ElementType.DRAW) {
                        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                        BitmapCache.put(element.id, bmp)
                    }
                } else if (targetDestination == R.id.shapesParentFragment) {
                    if (!shapeJustAdded) {
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
                    .setEnterAnim(R.anim.slide_in_up)
                    .setExitAnim(R.anim.slide_out_down)
                    .setPopEnterAnim(R.anim.slide_in_up)
                    .setPopExitAnim(R.anim.slide_out_down)
                    .build()

                if (targetDestination == R.id.shapesParentFragment) {
                    shapeJustAdded = false
                }

                navController.navigate(targetDestination, bundle, navOptions)
            }
        }

    }

    private fun List<CanvasElement>.sameSelectionAs(other: List<CanvasElement>?): Boolean {
        if (other == null) return false
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

    private fun updateToolbarVisibility(selected: List<CanvasElement>, animate: Boolean = true) {
        val isEraserOrDrawingMode = viewModel.isEraserActive.value == true ||
                viewModel.isDrawingMode.value == true ||
                _navController?.currentDestination?.id == R.id.universalEraserFragment ||
                _navController?.currentDestination?.id == R.id.drawFragment

        if (isEraserOrDrawingMode) {
            // Hide "more options" toggle button, side tools, alignment kit, and FAB in eraser/drawing mode
            binding.showHideContainer.visibility = View.GONE
            binding.fabContainer.visibility = View.GONE
            resetPanelsOnSelectionChange()
            updateIconVisibility(binding.opacityPane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.blendPane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.fontSizePane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.copyIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.cutOutIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.eraserIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.alignmentKit, false, animate = animate, animHide = R.anim.slide_out)
            updateIconVisibility(binding.selection, false, animate = animate)
            return
        }

        val isTableEdit = viewModel.isTableEditMode.value == true
        if (isTableEdit) {
            // Hide "more options" toggle button, side tools, and alignment kit in table edit mode
            binding.showHideContainer.visibility = View.GONE
            resetPanelsOnSelectionChange()
            updateIconVisibility(binding.opacityPane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.blendPane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.fontSizePane, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.copyIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.cutOutIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.eraserIcon, false, animate = animate, animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.alignmentKit, false, animate = animate, animHide = R.anim.slide_out)
            updateIconVisibility(binding.selection, false, animate = animate)
            return
        }

        binding.fabContainer.visibility = View.VISIBLE

        // Toggle button stays on screen instantly — no slide animation
        binding.showHideContainer.visibility =
            if (selected.isNotEmpty()) View.VISIBLE else View.GONE
        if (panelsLocked) {
            // 🔒 force hide everything
            resetPanelsOnSelectionChange()
            updateIconVisibility(binding.opacityPane, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.blendPane, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.fontSizePane, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.copyIcon, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.cutOutIcon, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.eraserIcon, false, animate = animate,
                animHide = R.anim.slide_out_left)
            updateIconVisibility(binding.alignmentKit, false, animate = animate,
                animHide = R.anim.slide_out)
            updateIconVisibility(binding.selection, false, animate = animate)
            return
        }

        val hasText = selected.any { it.type == ElementType.TEXT || it.type == ElementType.TABLE }
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
        val firstEl = selected.firstOrNull()
        val isGroupSel = firstEl?.type == ElementType.GROUP || (selected.size > 1 && selected.mapNotNull { it.groupId }.distinct().size == 1)
        val showRemoveBg = (hasImage || hasBackground || hasShapeMask) && !isMulti && !isGroupSel && !isSvg

        updateIconVisibility(
            binding.opacityPane, anySelected, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.blendPane, anySelected, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.fontSizePane, showFont, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.copyIcon, showCopy, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.cutOutIcon, showRemoveBg, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.eraserIcon, showRemoveBg, animate = animate,
            animShow = R.anim.slide_in_left, animHide = R.anim.slide_out_left
        )
        updateIconVisibility(
            binding.alignmentKit,
            anySelected, animate = animate,
            animShow = R.anim.slide_in,
            animHide = R.anim.slide_out
        )
        updateIconVisibility(binding.selection, showAlignWithSelection, animate = animate)
    }

    private fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        animate: Boolean = true,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2
    ) {
        val pendingHide = pendingHideRunnables[view]

        if (pendingHide != null) {
            view.removeCallbacks(pendingHide)
            pendingHideRunnables.remove(view)
            view.clearAnimation()
        }

        if (shouldBeVisible) {
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                if (animate) {
                    view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
                }
            }
        } else {
            if (view == binding.fontSizePane) {
                binding.seekBarFontSize.isVisible = false
            }

            if (!animate) {
                view.clearAnimation()
                view.visibility = View.GONE
            } else if (view.visibility == View.VISIBLE) {
                val anim = AnimationUtils.loadAnimation(view.context, animHide)
                view.startAnimation(anim)
                val duration = anim.duration

                val hideRunnable = Runnable {
                    view.visibility = View.GONE
                    pendingHideRunnables.remove(view)
                }
                pendingHideRunnables[view] = hideRunnable
                view.postDelayed(hideRunnable, duration)
            } else {
                view.visibility = View.GONE
            }
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
        if (::sizedCanvasView.isInitialized) {
            sizedCanvasView.onExitTableEditMode = {
                viewModel.exitTableEditMode()
                updateTableSelectionBar()
            }
            sizedCanvasView.onTableCellSelected = { r, c ->
                if (r >= 0 && c >= 0) {
                    viewModel.setTableScope(com.webscare.urducanvas.common.canvas.enums.TableScope.CELL, r, c)
                } else {
                    viewModel.clearTableCellSelection()
                }
                updateTableSelectionBar()
            }
            sizedCanvasView.onTableCellToggleSelected = { r, c ->
                val tableData = viewModel.getSelectedTableData()
                if (tableData != null && tableData.selectedCells.isNotEmpty()) {
                    viewModel.setSelectedTableCells(tableData.selectedCells)
                } else {
                    viewModel.clearTableCellSelection()
                    viewModel.toggleTableMultiSelect(false)
                }
                updateTableSelectionBar()
            }
            sizedCanvasView.onTableMultiSelectChanged = { isMulti ->
                viewModel.toggleTableMultiSelect(isMulti)
                if (!isMulti) {
                    val tableData = viewModel.getSelectedTableData()
                    if (tableData?.selectedCells?.isEmpty() == true) {
                        viewModel.clearTableCellSelection()
                    }
                }
                updateTableSelectionBar()
                updateToolbarVisibility(viewModel.selectedElements.value ?: emptyList(), animate = false)
            }
            sizedCanvasView.onProcessingStateChanged = { isProcessing -> viewModel.setProcessingAdjustments(isProcessing) }
        }
        viewModel.isProcessingAdjustments.observe(viewLifecycleOwner) { isProcessing ->
            val canvasView = if (::sizedCanvasView.isInitialized) sizedCanvasView else viewModel.getCanvasView()
            canvasView?.isProcessingAdjustments = (isProcessing == true)
        }
        viewModel.isTableEditMode.observe(viewLifecycleOwner) { isTableEdit ->
            val canvasView = if (::sizedCanvasView.isInitialized) sizedCanvasView else viewModel.getCanvasView()
            canvasView?.isTableEditMode = (isTableEdit == true)
            updateToolbarMode(animate = true)
            updateTableSelectionBar()
            updateToolbarVisibility(viewModel.selectedElements.value ?: emptyList(), animate = false)
        }
        viewModel.isTableMultiSelectMode.observe(viewLifecycleOwner) { isMulti ->
            val canvasView = if (::sizedCanvasView.isInitialized) sizedCanvasView else viewModel.getCanvasView()
            canvasView?.isTableMultiSelectMode = (isMulti == true)
            // Selection mode swaps the back arrow for a cross, so the toolbar has to rerun.
            updateToolbarMode(animate = true)
            updateTableSelectionBar()
            updateToolbarVisibility(viewModel.selectedElements.value ?: emptyList(), animate = false)
        }
        viewModel.selectedTableCellCount.observe(viewLifecycleOwner) {
            updateTableSelectionBar()
        }
        setupTableEditToolbar()
    }

    private fun setupTableEditToolbar() {
        binding.btnTableCellText.addPressEffect {
            showCellEditDialog()
        }
        binding.btnTableZoom.addPressEffect {
            showZoomPopup(binding.btnTableZoom)
        }
        binding.btnDismissTableSelection.addPressEffect {
            viewModel.clearTableCellSelection()
            viewModel.toggleTableMultiSelect(false)
            val canvasView = if (::sizedCanvasView.isInitialized) sizedCanvasView else viewModel.getCanvasView()
            canvasView?.let { cv ->
                cv.clearTableCellSelection()
                cv.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            updateTableSelectionBar()
        }
    }

    private fun updateTableSelectionBar() {
        val b = _binding ?: return
        val isTableEdit = viewModel.isTableEditMode.value == true
        val isMulti = viewModel.isTableMultiSelectMode.value == true
        val count = viewModel.selectedTableCellCount.value ?: 0
        val shouldShow = isTableEdit && isMulti && count > 0

        val isCurrentlyVisible = (b.tableSelectionBar.visibility == View.VISIBLE)

        if (shouldShow) {
            b.tvTableSelectionCount.text = when {
                count == 1 -> "1 cell selected"
                count > 1 -> "$count cells selected"
                else -> "Select cells"
            }
            if (!isCurrentlyVisible) {
                b.tableSelectionBar.animate().cancel()
                androidx.transition.TransitionManager.beginDelayedTransition(
                    b.header,
                    androidx.transition.AutoTransition().apply {
                        duration = 220L
                        interpolator = android.view.animation.DecelerateInterpolator()
                    }
                )
                b.tableSelectionBar.visibility = View.VISIBLE
                b.tableSelectionBar.alpha = 0f
                b.tableSelectionBar.translationY = -12f * resources.displayMetrics.density
                b.tableSelectionBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        } else {
            if (isCurrentlyVisible) {
                b.tableSelectionBar.animate().cancel()
                b.tableSelectionBar.animate()
                    .alpha(0f)
                    .translationY(-12f * resources.displayMetrics.density)
                    .setDuration(180L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        val endB = _binding ?: return@withEndAction
                        androidx.transition.TransitionManager.beginDelayedTransition(
                            endB.header,
                            androidx.transition.AutoTransition().apply {
                                duration = 180L
                                interpolator = android.view.animation.DecelerateInterpolator()
                            }
                        )
                        endB.tableSelectionBar.visibility = View.GONE
                        endB.tableSelectionBar.translationY = 0f
                    }
                    .start()
            } else {
                b.tableSelectionBar.visibility = View.GONE
            }
        }
    }

    private fun showCellEditDialog() {
        val tableData = viewModel.getSelectedTableData() ?: return
        if (tableData.selectedCells.isEmpty()) {
            val r = (viewModel.selectedTableRow.value ?: 0).coerceIn(0, (tableData.rows - 1).coerceAtLeast(0))
            val c = (viewModel.selectedTableCol.value ?: 0).coerceIn(0, (tableData.cols - 1).coerceAtLeast(0))
            viewModel.setTableScope(com.webscare.urducanvas.common.canvas.enums.TableScope.CELL, r, c)
        }
        val dialog = com.webscare.urducanvas.ui.editor.panels.table.CellTextEditDialog.newInstance()
        dialog.show(childFragmentManager, "CellTextEditDialog")
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
                    try {
                        val colorHex = String.format("#%06X", 0xFFFFFF and opaque)
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Color Code", colorHex)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    viewModel.finishPicking(opaque)
                    viewModel.stopPicking()
                    viewModel.markChanged()
                },
                onRequestOpenLayers = { cbOnRequestOpenLayers() },
                onExitSelectionMode = { viewModel.exitSelectionMode() },
                onStrokeCompleted = { stroke -> viewModel.notifyDrawStrokeAdded(stroke) },
                onZoomChanged = { zoom -> viewModel.setZoomLevel(zoom) },
                onCanvasLongPressed = { sx, sy -> cbOnCanvasLongPressed(sx, sy) },
                onExitTableEditMode = { viewModel.exitTableEditMode() },
                onTableCellSelected = { r, c ->
                    viewModel.setTableScope(com.webscare.urducanvas.common.canvas.enums.TableScope.CELL, r, c)
                },
                onTableCellToggleSelected = { r, c ->
                    viewModel.setTableScope(com.webscare.urducanvas.common.canvas.enums.TableScope.CELL, r, c)
                },
                onTableMultiSelectChanged = { isMulti ->
                    viewModel.toggleTableMultiSelect(isMulti)
                },
                onProcessingStateChanged = { isProcessing -> viewModel.setProcessingAdjustments(isProcessing) }
            ).apply {
                binding.canvasContainer.addView(this)
            }
            sizedCanvasView.onTransformChanged = { oldZoom, oldPanX, oldPanY, newZoom, newPanX, newPanY ->
                viewModel.recordCanvasTransform(oldZoom, oldPanX, oldPanY, newZoom, newPanX, newPanY)
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
                                .setEnterAnim(R.anim.slide_in_up)
                                .setExitAnim(R.anim.slide_out_down)
                                .setPopEnterAnim(R.anim.slide_in_up)
                                .setPopExitAnim(R.anim.slide_out_down)
                                .build()
                            navController.navigate(R.id.adjustmentsParentFragment, bundle, navOptions)
                        }
                    }
                    ElementType.DRAW, ElementType.SHAPE -> {
                        if (element.type == ElementType.SHAPE) {
                            val navOptions = NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setEnterAnim(R.anim.slide_in_up)
                                .setExitAnim(R.anim.slide_out_down)
                                .setPopEnterAnim(R.anim.slide_in_up)
                                .setPopExitAnim(R.anim.slide_out_down)
                                .build()
                            navController.navigate(R.id.shapeFragment, null, navOptions)
                        } else {
                            val bundle = Bundle().apply { putInt("startPage", 0) }
                            val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
                            navController.navigate(R.id.drawFragment, bundle, navOptions)
                        }
                    }
                    ElementType.TABLE -> {
                        if (viewModel.isTableEditMode.value == true) {
                            showCellEditDialog()
                        } else {
                            viewModel.enterTableEditMode()
                            try {
                                val bundle = Bundle().apply { putInt("startPage", 0) }
                                val navOptions = NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setEnterAnim(R.anim.slide_in_up)
                                    .setExitAnim(R.anim.slide_out_down)
                                    .setPopEnterAnim(R.anim.slide_in_up)
                                    .setPopExitAnim(R.anim.slide_out_down)
                                    .build()
                                navController.navigate(R.id.tableAdjustmentsFragment, bundle, navOptions)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
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
            updateBottomNavSelection(R.id.nav_layers)
            navController.navigate(R.id.layersFragment)
            currentPanelItemId = R.id.nav_layers
            b.panelNavHost.visibility = View.VISIBLE
        }
    }

    /** Setup bottom navigation with navHost */
    private fun initBottomNavigation() {
        val toolItems = listOf(
            binding.navText to R.id.nav_text,
            binding.navImages to R.id.nav_images,
            binding.navShapes to R.id.nav_shapes,
            binding.navStickers to R.id.nav_stickers,
            binding.navTables to R.id.nav_tables,
            binding.navDraw to R.id.nav_draw,
            binding.navLayers to R.id.nav_layers
        )

        for ((view, id) in toolItems) {
            view.addPressEffect {
                onBottomToolSelected(id)
            }
        }

        binding.bottomNavScrollView.setOnScrollChangeListener { v, scrollX, _, _, _ ->
            val maxScroll = binding.scrollableToolsContainer.width - v.width
            val canScrollRight = scrollX < (maxScroll - 4)
            binding.bottomNavFadeRight.isVisible = canScrollRight
        }

        binding.bottomNavScrollView.post {
            val maxScroll = binding.scrollableToolsContainer.width - binding.bottomNavScrollView.width
            binding.bottomNavFadeRight.isVisible = maxScroll > 4
        }
    }

    private fun onBottomToolSelected(itemId: Int) {
        if (currentPanelItemId != itemId) {
            val isCurrentlyExpanded = panelSheet?.isCurrentlyExpanded() == true || mainViewModel.expandedPanel.value != null
            val targetPanel = when (itemId) {
                R.id.nav_text -> PanelType.FONTS
                R.id.nav_images -> PanelType.IMAGES
                R.id.nav_shapes -> PanelType.SHAPES
                R.id.nav_stickers -> PanelType.OBJECTS
                R.id.nav_tables -> PanelType.TABLES
                R.id.nav_layers -> PanelType.LAYERS
                else -> null
            }

            if (isCurrentlyExpanded && targetPanel != null) {
                mainViewModel.setPanelExpandedType(targetPanel)
                mainViewModel.setPanelSlideOffset(1f)
            } else {
                mainViewModel.collapsePanel()
                mainViewModel.setPanelSlideOffset(0f)
            }

            binding.panelNavHost.visibility = View.VISIBLE
            currentPanelItemId = itemId
            updateBottomNavSelection(itemId)

            if (itemId != R.id.nav_layers) {
                panelSheet?.touchDragZoneEnabled = true
            }

            when (itemId) {
                R.id.nav_text -> navController.navigate(R.id.textFragment)
                R.id.nav_images -> navController.navigate(R.id.imagesFragment)
                R.id.nav_shapes -> navController.navigate(R.id.shapesParentFragment)
                R.id.nav_stickers -> navController.navigate(R.id.objectsFragment)
                R.id.nav_tables -> navController.navigate(R.id.tablesParentFragment)
                R.id.nav_draw -> navController.navigate(R.id.drawFragment)
                R.id.nav_layers -> navController.navigate(R.id.layersFragment)
            }
        }
    }

    private fun updateBottomNavSelection(selectedItemId: Int?) {
        val context = context ?: return
        val appColor = ContextCompat.getColor(context, R.color.appColor)
        val grayColor = ContextCompat.getColor(context, R.color.gray)
        val boldFont = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.bold)
        val regularFont = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.regular)

        val items = listOf(
            Triple(R.id.nav_text, Pair(binding.navTextIcon, binding.navTextLabel), Pair(R.drawable.ic_text, R.drawable.ic_text_filled)),
            Triple(R.id.nav_images, Pair(binding.navImagesIcon, binding.navImagesLabel), Pair(R.drawable.ic_images, R.drawable.ic_images_filled)),
            Triple(R.id.nav_shapes, Pair(binding.navShapesIcon, binding.navShapesLabel), Pair(R.drawable.ic_shapes, R.drawable.ic_shapes_filled)),
            Triple(R.id.nav_stickers, Pair(binding.navStickersIcon, binding.navStickersLabel), Pair(R.drawable.ic_stickers, R.drawable.ic_stickers_filled)),
            Triple(R.id.nav_tables, Pair(binding.navTablesIcon, binding.navTablesLabel), Pair(R.drawable.ic_grid, R.drawable.ic_grid_filled)),
            Triple(R.id.nav_draw, Pair(binding.navDrawIcon, binding.navDrawLabel), Pair(R.drawable.ic_draw, R.drawable.ic_draw_filled)),
            Triple(R.id.nav_layers, Pair(binding.navLayersIcon, binding.navLayersLabel), Pair(R.drawable.ic_layer, R.drawable.ic_layer_filled))
        )

        for ((id, views, drawables) in items) {
            val isSelected = (id == selectedItemId)
            val (icon, label) = views
            val (outlineRes, filledRes) = drawables
            if (isSelected) {
                icon.setImageResource(filledRes)
                icon.setColorFilter(appColor)
                label.setTextColor(appColor)
                label.typeface = boldFont
            } else {
                icon.setImageResource(outlineRes)
                icon.setColorFilter(grayColor)
                label.setTextColor(grayColor)
                label.typeface = regularFont
            }
        }

        if (selectedItemId != null && selectedItemId != R.id.nav_layers) {
            val selectedView = when (selectedItemId) {
                R.id.nav_text -> binding.navText
                R.id.nav_images -> binding.navImages
                R.id.nav_shapes -> binding.navShapes
                R.id.nav_stickers -> binding.navStickers
                R.id.nav_tables -> binding.navTables
                R.id.nav_draw -> binding.navDraw
                else -> null
            }
            selectedView?.post {
                val scrollBounds = android.graphics.Rect()
                binding.bottomNavScrollView.getDrawingRect(scrollBounds)
                if (selectedView.left < scrollBounds.left) {
                    binding.bottomNavScrollView.smoothScrollTo(selectedView.left, 0)
                } else if (selectedView.right > scrollBounds.right) {
                    binding.bottomNavScrollView.smoothScrollTo(selectedView.right - binding.bottomNavScrollView.width, 0)
                }
            }
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

        var lastUndoClickTime = 0L
        binding.undo.addPressEffect {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUndoClickTime > 100L) {
                lastUndoClickTime = currentTime
                viewModel.undo()
            }
        }
        var lastRedoClickTime = 0L
        binding.redo.addPressEffect {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRedoClickTime > 100L) {
                lastRedoClickTime = currentTime
                viewModel.redo()
            }
        }
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

        binding.back.addPressEffect {
            val destId = _navController?.currentDestination?.id
            val isAdjustment = destId == R.id.adjustmentsParentFragment ||
                    destId == R.id.shapeFragment ||
                    destId == R.id.textAdjustmentsFragment ||
                    destId == R.id.tableAdjustmentsFragment
            if (destId == R.id.universalEraserFragment || currentToolbarMode == EditorToolbarMode.ERASER) {
                viewModel.exitDrawingMode(commit = false)
                _navController?.popBackStack()
            } else if (viewModel.isTableMultiSelectMode.value == true) {
                // The cross in table selection mode drops the selection, not the screen.
                viewModel.toggleTableMultiSelect(false)
            } else if (viewModel.isTableEditMode.value == true) {
                viewModel.exitTableEditMode()
            } else if (isAdjustment || currentToolbarMode == EditorToolbarMode.ADJUSTMENT) {
                _navController?.popBackStack()
            } else {
                findNavController().popBackStack()
            }
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

        binding.eraserIcon.addPressEffect {
            view?.post {
                val selected = viewModel.selectedElements.value?.firstOrNull()
                if (selected != null) {
                    viewModel.setEraserActive(true)
                    viewModel.enterDrawingMode(requireActivity(), selected)
                    val navOptions = NavOptions.Builder().setPopUpTo(R.id.editorFragment, false).build()
                    _navController?.navigate(R.id.universalEraserFragment, null, navOptions)
                }
            }
        }

        binding.zoom.addPressEffect {
            showZoomPopup(binding.zoom)
        }

        binding.grid.addPressEffect {
            binding.grid.vibrateSoft()
            viewModel.toggleGrid()
        }


        binding.pan.addPressEffect {
            viewModel.togglePanMode()
        }

        binding.done.addPressEffect {
            binding.done.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            binding.done.vibrateSoft()
            val destId = _navController?.currentDestination?.id
            val isAdjustment = destId == R.id.adjustmentsParentFragment ||
                    destId == R.id.shapeFragment ||
                    destId == R.id.textAdjustmentsFragment ||
                    destId == R.id.tableAdjustmentsFragment
            if (destId == R.id.universalEraserFragment || currentToolbarMode == EditorToolbarMode.ERASER) {
                viewModel.exitDrawingMode(commit = true)
                _navController?.popBackStack()
            } else if (viewModel.isDrawingMode.value == true) {
                viewModel.exitDrawingMode(commit = true)
            } else if (viewModel.isTableEditMode.value == true) {
                viewModel.exitTableEditMode()
            } else if (isAdjustment || currentToolbarMode == EditorToolbarMode.ADJUSTMENT) {
                _navController?.popBackStack()
            } else {
                viewModel.setCanvasView(sizedCanvasView)
                sizedCanvasView.clearSelection()
                view?.post {
                    findNavController().navigate(R.id.exportFragment)
                }
            }
        }

        binding.btnClearDrawing.addPressEffect {
            viewModel.clearDrawingSession()
        }

        binding.btnResetBrushDefaults.addPressEffect {
            viewModel.resetBrushSettings()
        }

        initPanelSheet()
    }

    private enum class EditorToolbarMode {
        NORMAL,
        DRAW,
        TABLE_EDIT,
        ERASER,
        ADJUSTMENT
    }

    private var currentToolbarMode: EditorToolbarMode? = null
    private var currentBackIsCross: Boolean? = null

    private fun updateToolbarMode(animate: Boolean = true) {
        val b = _binding ?: return
        val ctx = context ?: return
        val destId = _navController?.currentDestination?.id
        val isEraser = destId == R.id.universalEraserFragment
        val isAdjustment = destId == R.id.adjustmentsParentFragment ||
                destId == R.id.shapeFragment ||
                destId == R.id.textAdjustmentsFragment ||
                destId == R.id.tableAdjustmentsFragment
        val isDrawing = viewModel.isDrawingMode.value == true && !isEraser
        val isTableEdit = viewModel.isTableEditMode.value == true

        val targetMode = when {
            isEraser -> EditorToolbarMode.ERASER
            isDrawing -> EditorToolbarMode.DRAW
            isTableEdit -> EditorToolbarMode.TABLE_EDIT
            isAdjustment -> EditorToolbarMode.ADJUSTMENT
            else -> EditorToolbarMode.NORMAL
        }

        // Table multi-select flips the back glyph without changing the toolbar mode, so it
        // has to be part of the "nothing to do" test or the cross never appears.
        val wantsCross = isTableEdit || viewModel.isTableMultiSelectMode.value == true
        if (currentToolbarMode == targetMode && currentBackIsCross == wantsCross) return
        val previousMode = currentToolbarMode
        currentToolbarMode = targetMode
        currentBackIsCross = wantsCross

        val slideOffset = 70f * resources.displayMetrics.density
        val animDuration = 320L
        val overshootInterpolator = android.view.animation.OvershootInterpolator(2.2f)
        val accelerateInterpolator = android.view.animation.AccelerateInterpolator()

        val blackColor = ContextCompat.getColor(ctx, R.color.black)

        fun applyDoneButtonState(isDoneCheckmark: Boolean) {
            if (isDoneCheckmark) {
                b.done.setImageResource(R.drawable.ic_done)
                b.done.setBackgroundResource(R.drawable.button_bg_round)
                b.done.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.contrast))
                b.done.imageTintList = ColorStateList.valueOf(blackColor)
            } else {
                b.done.setImageResource(R.drawable.ic_export_canvas)
                b.done.setBackgroundResource(R.drawable.ic_button_gradient_wrap)
                b.done.backgroundTintList = ColorStateList.valueOf(colorOf(R.color.selection))
                b.done.imageTintList = ColorStateList.valueOf(blackColor)
            }
        }

        // The cross belongs to the table modes only. Opening an adjustment panel keeps the
        // plain back arrow — the slide animation already signals the transition.
        val useCrossIcon = wantsCross
        val targetBackIcon = if (useCrossIcon) R.drawable.ic_close else R.drawable.ic_back
        // The cross glyph reads heavier than the arrow at the same box size, so pad it in.
        val backPadding = (if (useCrossIcon) 10f else 8f) * resources.displayMetrics.density
        b.back.setPadding(
            backPadding.toInt(), backPadding.toInt(), backPadding.toInt(), backPadding.toInt()
        )

        val targetLeftTools = when (targetMode) {
            EditorToolbarMode.DRAW -> b.drawTools
            EditorToolbarMode.TABLE_EDIT -> b.tableTools
            else -> b.normalTools
        }

        val showBack = targetMode == EditorToolbarMode.NORMAL ||
                targetMode == EditorToolbarMode.ERASER ||
                targetMode == EditorToolbarMode.ADJUSTMENT ||
                targetMode == EditorToolbarMode.TABLE_EDIT

        val showGrid = targetMode == EditorToolbarMode.NORMAL

        if (!animate || previousMode == null) {
            b.back.setImageResource(targetBackIcon)
            b.back.visibility = if (showBack) View.VISIBLE else View.GONE
            b.back.translationX = 0f
            b.back.scaleX = 1f
            b.back.scaleY = 1f
            b.back.rotation = 0f
            b.back.alpha = 1f

            b.normalTools.visibility = if (targetLeftTools === b.normalTools) View.VISIBLE else View.GONE
            b.drawTools.visibility = if (targetLeftTools === b.drawTools) View.VISIBLE else View.GONE
            b.tableTools.visibility = if (targetLeftTools === b.tableTools) View.VISIBLE else View.GONE
            b.grid.visibility = if (showGrid) View.VISIBLE else View.GONE

            b.normalTools.translationX = 0f
            b.normalTools.scaleX = 1f
            b.normalTools.scaleY = 1f
            b.normalTools.alpha = 1f

            b.drawTools.translationX = 0f
            b.drawTools.scaleX = 1f
            b.drawTools.scaleY = 1f
            b.drawTools.alpha = 1f

            b.tableTools.translationX = 0f
            b.tableTools.scaleX = 1f
            b.tableTools.scaleY = 1f
            b.tableTools.alpha = 1f

            applyDoneButtonState(targetMode != EditorToolbarMode.NORMAL)
            return
        }

        // 1. Prominent jump-settle on the entire top toolbar row
        b.topToolbarRow.animate().cancel()
        b.topToolbarRow.translationY = -6f * resources.displayMetrics.density
        b.topToolbarRow.animate()
            .translationY(0f)
            .setDuration(animDuration)
            .setInterpolator(overshootInterpolator)
            .start()

        // 2. Pulse Undo/Redo container
        b.redoUndo.animate().cancel()
        b.redoUndo.animate()
            .scaleX(1.16f)
            .scaleY(1.16f)
            .setDuration(120L)
            .withEndAction {
                val endB = _binding ?: return@withEndAction
                endB.redoUndo.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(overshootInterpolator)
                    .start()
            }
            .start()

        // 3. Back button icon flip with 3D rotation and bounce
        val isBackVisibleBefore = (previousMode == EditorToolbarMode.NORMAL || previousMode == EditorToolbarMode.ERASER || previousMode == EditorToolbarMode.ADJUSTMENT)
        if (showBack && isBackVisibleBefore) {
            // Back stays visible, animate icon change if different
            val prevBackIcon = if (previousMode == EditorToolbarMode.ADJUSTMENT || previousMode == EditorToolbarMode.ERASER) R.drawable.ic_close else R.drawable.ic_back
            if (prevBackIcon != targetBackIcon) {
                b.back.animate().cancel()
                b.back.animate()
                    .rotation(if (targetBackIcon == R.drawable.ic_close) 90f else -90f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(130L)
                    .setInterpolator(accelerateInterpolator)
                    .withEndAction {
                        val endB = _binding ?: return@withEndAction
                        endB.back.setImageResource(targetBackIcon)
                        endB.back.rotation = if (targetBackIcon == R.drawable.ic_close) -90f else 90f
                        endB.back.animate()
                            .rotation(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(220L)
                            .setInterpolator(overshootInterpolator)
                            .start()
                    }
                    .start()
            }
        } else if (showBack && !isBackVisibleBefore) {
            // Slide + jump in back button
            b.back.setImageResource(targetBackIcon)
            b.back.animate().cancel()
            b.back.visibility = View.VISIBLE
            b.back.translationX = -slideOffset
            b.back.scaleX = 0.5f
            b.back.scaleY = 0.5f
            b.back.alpha = 0f
            b.back.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(animDuration)
                .setInterpolator(overshootInterpolator)
                .start()
        } else if (!showBack && isBackVisibleBefore) {
            // Slide out back button
            b.back.animate().cancel()
            b.back.animate()
                .translationX(-slideOffset)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .alpha(0f)
                .setDuration(160L)
                .setInterpolator(accelerateInterpolator)
                .withEndAction {
                    val endB = _binding ?: return@withEndAction
                    endB.back.visibility = View.GONE
                }
                .start()
        }

        // 4. Grid visibility transition
        if (targetLeftTools === b.normalTools) {
            b.grid.visibility = if (showGrid) View.VISIBLE else View.GONE
        }

        // 5. Left tool groups sliding / jump-settle transition
        val previousLeftTools = when (previousMode) {
            EditorToolbarMode.DRAW -> b.drawTools
            EditorToolbarMode.TABLE_EDIT -> b.tableTools
            else -> b.normalTools
        }

        if (previousLeftTools !== targetLeftTools) {
            // Slide out previous tool group
            previousLeftTools.animate().cancel()
            previousLeftTools.animate()
                .translationX(-slideOffset)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0f)
                .setDuration(160L)
                .setInterpolator(accelerateInterpolator)
                .withEndAction {
                    previousLeftTools.visibility = View.GONE
                }
                .start()

            // Slide in new tool group with jump-settle overshoot
            targetLeftTools.animate().cancel()
            targetLeftTools.visibility = View.VISIBLE
            targetLeftTools.translationX = -slideOffset * 1.2f
            targetLeftTools.scaleX = 0.65f
            targetLeftTools.scaleY = 0.65f
            targetLeftTools.alpha = 0f
            targetLeftTools.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(animDuration)
                .setInterpolator(overshootInterpolator)
                .start()
        }

        // 6. Right Done / Export button flip transition
        val wasCheckmark = (previousMode != EditorToolbarMode.NORMAL)
        val isNowCheckmark = (targetMode != EditorToolbarMode.NORMAL)

        if (wasCheckmark != isNowCheckmark) {
            b.done.animate().cancel()
            b.done.animate()
                .translationX(slideOffset)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .alpha(0f)
                .setDuration(140L)
                .setInterpolator(accelerateInterpolator)
                .withEndAction {
                    val endB = _binding ?: return@withEndAction
                    applyDoneButtonState(isNowCheckmark)
                    endB.done.translationX = slideOffset
                    endB.done.animate()
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(animDuration)
                        .setInterpolator(overshootInterpolator)
                        .start()
                }
                .start()
        }
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
                    if (viewModel.isTableEditMode.value == true) {
                        viewModel.exitTableEditMode()
                    } else {
                        autoSave()
                    }
                }
            })

        binding.back.addPressEffect {
            if (viewModel.isTableEditMode.value == true) {
                viewModel.exitTableEditMode()
            } else {
                autoSave()
            }
        }
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
        if (!isAdded || activity?.isFinishing == true) return
        if (exportDialog?.isShowing == true) return

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show export dialog: ${e.message}")
        }
    }

    private fun updateExportDialog(percent: Int, stage: String) {
        if (!isAdded) return
        exportDialogBinding?.apply {
            progressBar.progress = percent
            tvProgressPercent.text = getString(R.string.complete, percent)
            exportValue.text = stage
        }
    }

    private fun dismissExportDialog() {
        stopIconRotation()
        try {
            exportDialog?.dismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss export dialog: ${e.message}")
        } finally {
            exportDialog = null
            exportDialogBinding = null
        }
    }

    private fun startPeriodicAutoSave() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val intervalStr = dataStore.getFirstPreference(
                    com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.KEY_AUTO_SAVE_INTERVAL,
                    "3"
                )
                if (intervalStr != "off") {
                    val minutes = intervalStr.toLongOrNull() ?: 3L
                    val intervalMs = minutes * 60 * 1000L
                    while (isActive) {
                        delay(intervalMs)
                        if (viewModel.hasChanges.value == true && !isSaving) {
                            autoSaveSilent()
                        }
                    }
                }
            }
        }
    }

    private fun autoSaveSilent() {
        if (!isAdded || !::sizedCanvasView.isInitialized) {
            return
        }
        val options = viewModel.exportOptions.value ?: return
        val canvasSize = viewModel.canvasSize.value ?: return

        lifecycleScope.launch {
            try {
                val (thumbnailBitmap, jsonFile) = withContext(Dispatchers.Default) {
                    sizedCanvasView.exportCanvasThumbnailBitmap { _, _ -> }
                }
                withContext(Dispatchers.IO) {
                    saveOnExitSafe(options, thumbnailBitmap, jsonFile, false, canvasSize)
                }
            } catch (e: Exception) {
                Log.e(TAG, "autoSaveSilent failed: ${e.message}")
            }
        }
    }

    private fun autoSave() {
        if (!isAdded) return
        if (!::sizedCanvasView.isInitialized || viewModel.hasChanges.value != true) {
            if (isAdded && isResumed) {
                try {
                    findNavController().navigateUp()
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation failed: ${e.message}")
                }
            }
            return
        }
        if (isSaving) return
        isSaving = true
        val options = viewModel.exportOptions.value ?: return
        val canvasSize = viewModel.canvasSize.value ?: return

        showExportProgressDialog()

        lifecycleScope.launch {
            try {
                val (thumbnailBitmap, jsonFile) = withContext(Dispatchers.Default) {
                    sizedCanvasView.exportCanvasThumbnailBitmap { percent, stage ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (isAdded) updateExportDialog(percent, stage)
                        }
                    }
                }
                if (!isAdded) return@launch

                withContext(Dispatchers.Main) {
                    if (isAdded) updateExportDialog(97, "Saving files...")
                }
                withContext(Dispatchers.IO) {
                    saveOnExitSafe(options, thumbnailBitmap, jsonFile, true, canvasSize)
                }
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        updateExportDialog(100, "Saved successfully")
                        delay(500)
                        dismissExportDialog()
                        if (isAdded && isResumed) {
                            try {
                                findNavController().navigateUp()
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation up failed: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "autoSave failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    dismissExportDialog()
                }
            } finally {
                isSaving = false
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

        var seekbarStartZoom = sizedCanvasView.getCurrentZoom()
        var seekbarStartPanX = sizedCanvasView.getCurrentPanX()
        var seekbarStartPanY = sizedCanvasView.getCurrentPanY()

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

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seekbarStartZoom = sizedCanvasView.getCurrentZoom()
                seekbarStartPanX = sizedCanvasView.getCurrentPanX()
                seekbarStartPanY = sizedCanvasView.getCurrentPanY()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val endZoom = sizedCanvasView.getCurrentZoom()
                val endPanX = sizedCanvasView.getCurrentPanX()
                val endPanY = sizedCanvasView.getCurrentPanY()
                if (kotlin.math.abs(endZoom - seekbarStartZoom) > 0.01f || kotlin.math.abs(endPanX - seekbarStartPanX) > 2f || kotlin.math.abs(endPanY - seekbarStartPanY) > 2f) {
                    viewModel.recordCanvasTransform(seekbarStartZoom, seekbarStartPanX, seekbarStartPanY, endZoom, endPanX, endPanY)
                }
            }
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

    fun registerAdditionalDragHandle(view: View) {
        val b = _binding ?: return
        if (!registeredDragHandles.contains(view)) {
            registeredDragHandles.add(view)
            b.panelNavContainer.dragHandles = ArrayList(registeredDragHandles)
        }
    }

    /** Called by child panels to hand their drag handle to the sheet behavior. */
    fun attachDragHandle(handleView: View) {
        if (currentDragHandle != null && currentDragHandle != handleView) {
            currentDragHandle?.setOnTouchListener(null)
        }
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

            // Reset drag handles tracker
            registeredDragHandles.clear()
            registeredDragHandles.add(handleView)
            b.panelNavContainer.dragHandles = ArrayList(registeredDragHandles)

            val shouldBeExpanded = mainViewModel.expandedPanel.value != null || panelSheet?.isCurrentlyExpanded() == true
            val dest = _navController?.currentDestination?.id
            val panelType = when (dest) {
                R.id.imagesFragment       -> PanelType.IMAGES
                R.id.objectsFragment      -> PanelType.OBJECTS
                R.id.shapesParentFragment -> PanelType.SHAPES
                R.id.tablesParentFragment -> PanelType.TABLES
                R.id.textFragment         -> PanelType.FONTS
                R.id.layersFragment       -> PanelType.LAYERS
                else                      -> null
            }

            if (shouldBeExpanded && panelType != null) {
                panelSheet?.snapTo(expanded = true, immediate = true)
                mainViewModel.setPanelExpandedType(panelType)
                mainViewModel.setPanelSlideOffset(1f)
                b.dimOverlay.visibility  = View.VISIBLE
                b.dimOverlay.isClickable = true
                b.dimOverlay.alpha       = PanelSheetBehavior.MAX_DIM_ALPHA
            } else {
                panelSheet?.snapTo(expanded = false, immediate = true)
                mainViewModel.setPanelSlideOffset(0f)
                mainViewModel.collapsePanel()
                b.dimOverlay.visibility  = View.INVISIBLE
                b.dimOverlay.isClickable = false
            }

            panelSheet = PanelSheetBehavior(
                root            = root,
                guideline       = guideline,
                dragHandleView  = handleView,
                collapsedPx     = collapsedPx,
                expandedPx      = expandedPx,
                onSlide         = { offset ->
                    mainViewModel.setPanelSlideOffset(offset)
                    val bb = _binding ?: return@PanelSheetBehavior
                    bb.fabContainer.alpha = 1f - offset
                    bb.fabContainer.visibility = if (offset >= 1f) View.GONE else View.VISIBLE
                },
                onStateSettled  = { expanded ->
                    if (expanded) {
                        panelType?.let { mainViewModel.setPanelExpandedType(it) }
                    } else {
                        mainViewModel.collapsePanel()
                    }
                },
                dimView = b.dimOverlay
            ).apply {
                onAdditionalHandleAttached = { view ->
                    registerAdditionalDragHandle(view)
                }
            }
            panelSheet!!.attach()
            if (shouldBeExpanded && panelType != null) {
                panelSheet?.snapTo(expanded = true, immediate = true)
            }
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
        }
    }

    /**
     * Shown when the user long-presses inside the CanvasView but away from any
     * art-board element. Anchored at the raw touch coordinates.
     */

    @SuppressLint("UseKtx")
    private fun showCanvasPopupMenu(touchRawX: Float, touchRawY: Float) {
        if (!isAdded) return

        val density = resources.displayMetrics.density
        val popupWidthPx = (168 * density).toInt()

        val popupBinding = LayoutCanvasPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            popupWidthPx,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 4f
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

        // ── Background color: contrast / black swatches ──
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        val blackColor    = ContextCompat.getColor(requireContext(), R.color.black)

        val currentBgColor = viewModel.backgroundColor.value ?: contrastColor

        val isContrastSelected = currentBgColor == contrastColor
        val isDarkSelected = currentBgColor == blackColor || currentBgColor == ContextCompat.getColor(requireContext(), R.color.black)

        fun bindColorSwatch(
            outer: com.google.android.material.card.MaterialCardView,
            inner: com.google.android.material.card.MaterialCardView,
            colorView: View,
            color: Int,
            isSelected: Boolean
        ) {
            colorView.setBackgroundColor(color)

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255

            val strokePx = (1.5f * density + 0.5f).toInt()
            val marginPx = (2f * density + 0.5f).toInt()

            val lp = inner.layoutParams as ViewGroup.MarginLayoutParams

            if (isSelected) {
                outer.strokeWidth = strokePx
                outer.strokeColor = ContextCompat.getColor(requireContext(), R.color.appColor)
                outer.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                lp.setMargins(marginPx, marginPx, marginPx, marginPx)
            } else {
                lp.setMargins(0, 0, 0, 0)
                if (luminance > 0.85) {
                    outer.strokeWidth = (1f * density + 0.5f).toInt()
                    outer.strokeColor = ContextCompat.getColor(requireContext(), R.color.light_gray)
                } else {
                    outer.strokeWidth = 0
                    outer.strokeColor = Color.TRANSPARENT
                }
            }
            inner.layoutParams = lp
        }

        bindColorSwatch(
            popupBinding.bgLightOuter,
            popupBinding.bgLightInner,
            popupBinding.bgLightColor,
            contrastColor,
            isContrastSelected
        )
        bindColorSwatch(
            popupBinding.bgDarkOuter,
            popupBinding.bgDarkInner,
            popupBinding.bgDarkColor,
            blackColor,
            isDarkSelected
        )

        popupBinding.bgLightOuter.addPressEffect {
            viewModel.setCanvasBackgroundColor(contrastColor)
            popupWindow.dismiss()
        }
        popupBinding.bgDarkOuter.addPressEffect {
            viewModel.setCanvasBackgroundColor(blackColor)
            popupWindow.dismiss()
        }

        // ── Ruler ─────────────────────────────────────────────────────────
        val currentRulerState = viewModel.rulerState.value ?: com.webscare.urducanvas.common.canvas.enums.RulerState.OFF
        popupBinding.rulerTitle.text = when (currentRulerState) {
            com.webscare.urducanvas.common.canvas.enums.RulerState.OFF -> getString(R.string.ruler)
            com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES -> getString(R.string.ruler_two_sides)
            com.webscare.urducanvas.common.canvas.enums.RulerState.FOUR_SIDES -> getString(R.string.ruler_four_sides)
            else -> getString(R.string.ruler)
        }
        val isRulerActive = currentRulerState != com.webscare.urducanvas.common.canvas.enums.RulerState.OFF
        val rulerIconTint = if (isRulerActive) {
            ContextCompat.getColor(requireContext(), R.color.appColor)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }
        popupBinding.rulerIcon.imageTintList = ColorStateList.valueOf(rulerIconTint)
        popupBinding.actionRuler.addPressEffect {
            viewModel.toggleRuler()
            popupWindow.dismiss()
        }

        // ── Lock / Unlock ─────────────────────────────────────────────────
        val locked = viewModel.isCanvasPanLocked.value ?: false
        popupBinding.lockTitle.text =
            getString(if (locked) R.string.unlock_canvas else R.string.lock_canvas)
        popupBinding.lockIcon.setImageResource(
            if (locked) R.drawable.ic_unlock else R.drawable.ic_lock
        )
        popupBinding.actionLock.addPressEffect {
            viewModel.toggleCanvasPanLock()
            popupWindow.dismiss()
        }

        // ── Anchor directly at touch point with seamless continuous callout bubble ──
        binding.canvasContainer.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val arrowWidth = 12f * density
            val arrowHeight = 6f * density
            val cornerRadius = 10f * density
            val strokeWidth = 1f * density
            val screenMargin = (8 * density).toInt()

            val fillColor = ContextCompat.getColor(requireContext(), R.color.white)
            val strokeColor = ContextCompat.getColor(requireContext(), R.color.light_gray)

            popupBinding.root.setPadding(0, 0, 0, 0)
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val cardHeight = popupBinding.root.measuredHeight
            val totalHeight = (cardHeight + arrowHeight).toInt()

            // Horizontally center card around touchRawX, clamped within screen margins
            var popupX = (touchRawX - popupWidthPx / 2f).toInt()
            popupX = popupX.coerceIn(screenMargin, maxOf(screenMargin, screenWidth - popupWidthPx - screenMargin))

            // Horizontally align arrow with touchRawX (relative to popup left)
            val targetArrowX = touchRawX - popupX
            val minArrowX = cornerRadius + arrowWidth / 2f
            val maxArrowX = popupWidthPx - cornerRadius - arrowWidth / 2f
            val clampedArrowX = targetArrowX.coerceIn(minArrowX, maxArrowX)

            // Check if there is enough space below touch point
            val spaceBelow = screenHeight - touchRawY
            val showBelow = spaceBelow >= totalHeight + screenMargin + (20 * density)

            val arrowDirection = if (showBelow) {
                CalloutArrowDirection.TOP
            } else {
                CalloutArrowDirection.BOTTOM
            }

            if (showBelow) {
                popupBinding.root.setPadding(0, arrowHeight.toInt(), 0, 0)
            } else {
                popupBinding.root.setPadding(0, 0, 0, arrowHeight.toInt())
            }

            val bubbleDrawable = CalloutBubbleDrawable(
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius,
                arrowWidth = arrowWidth,
                arrowHeight = arrowHeight,
                arrowDirection = arrowDirection,
                arrowX = clampedArrowX
            )
            popupBinding.root.background = bubbleDrawable

            val popupY = if (showBelow) {
                touchRawY.toInt()
            } else {
                (touchRawY - totalHeight).toInt()
            }

            popupWindow.showAtLocation(binding.root, Gravity.NO_GRAVITY, popupX, popupY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentDragHandle?.setOnTouchListener(null)
        currentDragHandle = null
        registeredDragHandles.clear()
        saveJsonJob?.cancel()
        viewModel.clearLoading()
        _binding?.let { b ->
            b.back.animate().cancel()
            b.redoUndo.animate().cancel()
            b.normalTools.animate().cancel()
            b.tableTools.animate().cancel()
            b.drawTools.animate().cancel()
            b.done.animate().cancel()
            b.tableSelectionBar.animate().cancel()
        }
        currentToolbarMode = null
        panelSheet?.snapTo(expanded = false, immediate = true)
        panelSheet = null
        _binding?.canvasContainer?.removeAllViews()
        _navController = null
        cbOnEditTextRequested = {}
        cbOnElementSelected   = {}
        cbOnRequestOpenLayers = {}
        cbOnCanvasLongPressed = { _, _ -> }
        if (BuildConfig.IS_PROD_LOGIC) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("key_export_model", exportModel)
        outState.putString("key_json_path", jsonPath)
        outState.putString("key_image_path", imagePath)
        if (::canvasSize.isInitialized) {
            outState.putSerializable("key_canvas_size", canvasSize)
        }
    }

    companion object {
    }
}