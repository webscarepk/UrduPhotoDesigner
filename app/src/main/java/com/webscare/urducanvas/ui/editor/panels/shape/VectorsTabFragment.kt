package com.webscare.urducanvas.ui.editor.panels.shape

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@AndroidEntryPoint
class VectorsTabFragment : Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var shapesAdapter: ShapeAdapter? = null
    private var isPanelExpanded = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = requireContext().contentResolver
                        .openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    bitmap?.let {
                        withContext(Dispatchers.Main) { viewModel.addImageInsideShape(it, requireActivity()) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VectorsTabFragment", "Image pick failed", e)
                }
            }
        }

    fun pickImage() { pickImageLauncher.launch("image/*") }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh()
        setupExpandGesture()

        if (shapesAdapter == null) {
            shapesAdapter = ShapeAdapter(requireContext(), ShapeType.entries) { shape ->
                handleShapeTap(shape)
            }
        }

        binding.objects.apply {
            setHasFixedSize(true)
            itemAnimator = null          // suppress flicker on dataset changes
            layoutManager = buildLayoutManager(isPanelExpanded)
            adapter = shapesAdapter
        }
        binding.noEmojis.visibility = View.GONE
    }

    // ── Shape tap logic ───────────────────────────────────────────────────────
    //
    // Behaviour:
    // A) Masking mode + image selected → merge image into shape (existing behaviour)
    //
    // B) Shape already selected on canvas → UPDATE that shape's type.
    //    Show a one-time hint: "Tap to change shape. Deselect to add a new one."
    //    Do NOT collapse — user is likely browsing shapes to find the right one.
    //
    // C) No shape selected → ADD a new shape element, then collapse the panel
    //    so user can see what was added and interact with it immediately.

    private fun handleShapeTap(shape: ShapeType) {
        val elements      = viewModel.canvasElements.value
        val isMask        = viewModel.isMaskingMode.value
        val isShapeSelected = elements?.any { it.isSelected && it.type == ElementType.SHAPE } == true
        val isImageSelected = elements?.any { it.isSelected && it.type == ElementType.IMAGE } == true

        when {
            // A: Mask mode — merge image into this shape
            isMask == true && isImageSelected -> {
                elements?.find { it.isSelected && it.type == ElementType.IMAGE }
                    ?.let { viewModel.mergeImageToShape(it, shape, requireActivity()) }
                collapsePanel()
            }

            // B: Shape already selected — update its type then collapse so user sees the result
            isShapeSelected -> {
                viewModel.updateShapeType(shape)
                collapsePanel()
            }

            // C: No shape selected — add new shape and collapse so user sees it
            else -> {
                viewModel.updateShapeType(shape)
                viewModel.addShapeElement()
                collapsePanel()
            }
        }
    }

    private fun collapsePanel() {
        if (mainViewModel.isPanelExpanded(PanelType.SHAPES)) {
            mainViewModel.togglePanel(PanelType.SHAPES)
        }
    }

    // ── SwipeRefreshLayout ────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.apply {
            isEnabled = false
            setColorSchemeResources(R.color.appColor)
            setOnRefreshListener {
                shapesAdapter?.updateShapes(ShapeType.entries.shuffled())
                isRefreshing = false
            }
        }
    }

    // ── Swipe-up to expand ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExpandGesture() {
        val thresholdPx = 40 * resources.displayMetrics.density
        var startY = 0f; var startX = 0f

        binding.objects.setOnTouchListener { _, event ->
            if (isPanelExpanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; startX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY
                    val dx = abs(startX - event.rawX)
                    if (dy > thresholdPx && dy > dx * 1.5f) {
                        mainViewModel.togglePanel(PanelType.SHAPES); true
                    } else false
                }
                else -> false
            }
        }
    }

    // ── Panel expand/collapse ─────────────────────────────────────────────────

    fun onPanelExpandedSmooth(effectiveExpanded: Boolean) {
        if (_binding == null) return
        if (isPanelExpanded == effectiveExpanded) return   // already in right state
        isPanelExpanded = effectiveExpanded

        binding.objects.recycledViewPool.clear()
        binding.objects.layoutManager = buildLayoutManager(effectiveExpanded)
        shapesAdapter?.isExpanded = effectiveExpanded

        val bottomPadding = if (effectiveExpanded) (64 * resources.displayMetrics.density).toInt() else 0
        binding.objects.setPadding(0, 0, 0, bottomPadding)
    }

    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        binding.swipeRefresh?.isEnabled = expanded
        binding.objects.layoutManager = buildLayoutManager(expanded)
        shapesAdapter?.isExpanded = expanded
        if (expanded) binding.objects.scrollToPosition(0)
    }

    private fun buildLayoutManager(expanded: Boolean): GridLayoutManager =
        GridLayoutManager(
            requireContext(), 3,
            if (expanded) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
            false
        )

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}