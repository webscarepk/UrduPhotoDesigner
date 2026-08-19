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
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
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

        if (shapesAdapter == null) {
            shapesAdapter = ShapeAdapter(requireContext(), ShapeType.entries) { shape ->
                handleShapeTap(shape)
            }
        }

        binding.objects.apply {
            setHasFixedSize(true)
            itemAnimator = null          // suppress flicker on dataset changes
            layoutManager = MorphGridLayoutManager(
                context = requireContext(),
                collapsedSpan = 3,
                expandedSpan = 3
            ).apply {
                applyFraction(binding.objects, if (mainViewModel.isPanelExpanded(PanelType.SHAPES)) 1f else 0f)
            }
            adapter = shapesAdapter
        }
        binding.noEmojis.visibility = View.GONE

        val isExpandedNow = mainViewModel.isPanelExpanded(PanelType.SHAPES)
        isPanelExpanded = !isExpandedNow
        onPanelExpanded(isExpandedNow)
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



    // ── Panel expand/collapse ─────────────────────────────────────────────────

    fun onPanelSlide(offset: Float) {
        if (_binding == null) return
        val effectiveExpanded = offset >= MorphGridLayoutManager.DEFAULT_FLIP_THRESHOLD
        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, offset)
            if (shapesAdapter?.isExpanded != effectiveExpanded) {
                binding.objects.recycledViewPool.clear()
                shapesAdapter?.isExpanded = effectiveExpanded
            }
        }

        binding.objects.alpha = MorphGridLayoutManager.computeMorphAlpha(offset)

        // Smoothly animate bottom padding during slide to avoid jerking
        val maxPadding = (64 * resources.displayMetrics.density).toInt()
        val currentPadding = (offset * maxPadding).toInt()
        binding.objects.setPadding(
            binding.objects.paddingLeft,
            binding.objects.paddingTop,
            binding.objects.paddingRight,
            currentPadding
        )

        // Smoothly update size of all visible items in 60fps!
        val adapter = shapesAdapter ?: return
        val rvWidth = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.objects.childCount) {
            val child = binding.objects.getChildAt(i)
            val holder = binding.objects.getChildViewHolder(child) as? ShapeAdapter.ShapeViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }
    }

    fun onPanelExpanded(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        if (_binding == null) return

        val rv = binding.objects
        if (rv.width == 0) {
            rv.post {
                if (_binding != null) {
                    // force logic run by clearing flag
                    isPanelExpanded = !expanded
                    onPanelExpanded(expanded)
                }
            }
            return
        }

        binding.swipeRefresh?.isEnabled = expanded
        val lm = binding.objects.layoutManager as? MorphGridLayoutManager
        if (lm != null) {
            lm.applyFraction(binding.objects, if (expanded) 1f else 0f)
            if (shapesAdapter?.isExpanded != expanded) {
                binding.objects.recycledViewPool.clear()
                shapesAdapter?.isExpanded = expanded
            }
        }
        binding.objects.alpha = 1f
        val bottomPadding = if (expanded) (64 * resources.displayMetrics.density).toInt() else 0
        binding.objects.setPadding(
            binding.objects.paddingLeft,
            binding.objects.paddingTop,
            binding.objects.paddingRight,
            bottomPadding
        )

        // Sync item size on final settle state
        val adapter = shapesAdapter ?: return
        val rvWidth = binding.objects.width
        val rvPadding = binding.objects.paddingLeft + binding.objects.paddingRight
        val offset = if (expanded) 1f else 0f
        adapter.slideOffset = offset
        adapter.recyclerViewWidth = rvWidth
        adapter.recyclerViewPadding = rvPadding

        for (i in 0 until binding.objects.childCount) {
            val child = binding.objects.getChildAt(i)
            val holder = binding.objects.getChildViewHolder(child) as? ShapeAdapter.ShapeViewHolder
            holder?.updateSize(offset, rvWidth, rvPadding)
        }

        if (expanded) binding.objects.scrollToPosition(0)
    }

    override fun onDestroyView() {
        _binding?.objects?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        // Fragment is genuinely going away — safe to cancel in-flight rendering.
        shapesAdapter?.cancelRender()
        shapesAdapter = null
        super.onDestroy()
    }
}