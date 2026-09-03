package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.model.Text3DData
import com.webscare.urducanvas.common.canvas.model.Text3DSurface
import com.webscare.urducanvas.common.canvas.model.Text3DSurfaceShading
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.Fragment3dMaterialBinding
import com.webscare.urducanvas.databinding.ItemMaterialSurfaceBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Three screens, one fragment:
 *   1. Surfaces      - the swatch row. Tapping the selected swatch opens screen 2.
 *   2. Adjustments   - the standard solid/gradient colour panel, with a tick to come back
 *                      and a settings icon that opens screen 3.
 *   3. Properties    - roughness / metallic / specular / reflection.
 *
 * Screens 2 and 3 lock the parent pager so a vertical drag edits the panel rather than
 * flicking to the next 3D section.
 */
@AndroidEntryPoint
class Material3DFragment : Fragment() {

    private var _binding: Fragment3dMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var surfaceAdapter: SurfaceAdapter
    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter

    /** false = editing the front face, true = editing the extrusion side. */
    private var editingSide: Boolean = false

    /** Mirrors [com.webscare.urducanvas.common.canvas.model.Text3DMaterial.sameAsFront];
     *  the round tick has no checked state of its own to read back. */
    private var sameAsFront: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dMaterialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSurfaceList()
        setupColorAndGradientLists()
        setupFaceTargeting()
        initSliders()
        initHeaderButtons()
        initObservers()
    }

    // ── Screen switching ──────────────────────────────────────────────────────

    private enum class Screen { SURFACES, ADJUSTMENTS, PROPERTIES }

    private fun showScreen(screen: Screen) {
        val b = _binding ?: return
        b.rvSurfaces.visibility = if (screen == Screen.SURFACES) View.VISIBLE else View.GONE
        b.layoutAdjustments.visibility = if (screen == Screen.ADJUSTMENTS) View.VISIBLE else View.GONE
        b.layoutProperties.visibility = if (screen == Screen.PROPERTIES) View.VISIBLE else View.GONE
        viewModel.setPagingLocked(screen != Screen.SURFACES)
    }

    private fun initHeaderButtons() {
        binding.btnDone.addPressEffect { showScreen(Screen.SURFACES) }
        binding.btnSettings.addPressEffect { showScreen(Screen.PROPERTIES) }
        binding.btnPropertiesDone.addPressEffect { showScreen(Screen.ADJUSTMENTS) }
    }

    // ── Surfaces ──────────────────────────────────────────────────────────────

    private fun setupSurfaceList() {
        surfaceAdapter = SurfaceAdapter(
            list = Text3DData.SURFACES,
            onSelect = { surfaceId ->
                val current = viewModel.text3dData.value?.material?.surface ?: "plain"
                if (current == surfaceId) {
                    showScreen(Screen.ADJUSTMENTS)
                } else {
                    viewModel.updateText3D(pushToUndo = true) { it.material.surface = surfaceId }
                    surfaceAdapter.setSelectedId(surfaceId)
                }
            },
            onEditClick = { showScreen(Screen.ADJUSTMENTS) }
        )
        // Three rows deep: the surface list is long enough now that one row would be an
        // endless flick, and a grid shows a whole family at a glance.
        binding.rvSurfaces.layoutManager =
            GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
        binding.rvSurfaces.setHasFixedSize(true)
        binding.rvSurfaces.adapter = surfaceAdapter
    }

    // ── Front / side targeting ────────────────────────────────────────────────

    private fun setupFaceTargeting() {
        binding.tabFront.addPressEffect { setEditingSide(false) }
        binding.tabSide.addPressEffect { setEditingSide(true) }

        binding.cbSameAsFront.addPressEffect { setSameAsFront(!sameAsFront) }

        renderSameAsFrontTick()
        setEditingSide(false)
    }

    /** Writes the sync flag through to the model and repaints the little round tick. */
    private fun setSameAsFront(checked: Boolean) {
        sameAsFront = checked
        renderSameAsFrontTick()
        viewModel.updateText3D(pushToUndo = true) { data ->
            data.material.sameAsFront = checked
            if (checked) data.material.extrusionColor = data.material.frontColor
        }
        if (checked) setEditingSide(false) else setEditingSide(editingSide)
    }

    private fun renderSameAsFrontTick() {
        val b = _binding ?: return
        val ctx = context ?: return
        b.ivSameAsFrontTick.setBackgroundResource(
            if (sameAsFront) R.drawable.bg_round_check_on else R.drawable.bg_round_check_off
        )
        b.ivSameAsFrontTick.setImageResource(if (sameAsFront) R.drawable.ic_done else 0)
        b.ivSameAsFrontTick.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.white))
    }

    private fun setEditingSide(side: Boolean) {
        editingSide = side
        val b = _binding ?: return
        val ctx = context ?: return
        val active = ContextCompat.getColor(ctx, R.color.white)
        val inactive = ContextCompat.getColor(ctx, R.color.contrast)
        b.tabFront.backgroundTintList = ColorStateList.valueOf(if (side) inactive else active)
        b.tabSide.backgroundTintList = ColorStateList.valueOf(if (side) active else inactive)
        // With the sides synced there is nothing separate to edit, so keep Side inert.
        val sideEditable = !sameAsFront
        b.tabSide.isEnabled = sideEditable
        b.tabSide.alpha = if (sideEditable) 1f else 0.45f
        syncSelectedSwatch()
    }

    private fun syncSelectedSwatch() {
        val mat = viewModel.text3dData.value?.material ?: return
        val hex = if (editingSide) mat.extrusionColor else mat.frontColor
        colorsAdapter.selectedColor = runCatching { hex.toColorInt() }.getOrDefault(Color.BLACK)
    }

    /** Writes a picked colour into whichever face is being edited, honouring the sync flag. */
    private fun applyPickedColor(color: Int) {
        val hex = String.format("#%06X", (0xFFFFFF and color))
        viewModel.updateText3D(pushToUndo = true) { data ->
            if (editingSide) {
                data.material.extrusionColor = hex
            } else {
                data.material.frontColor = hex
                if (data.material.sameAsFront) data.material.extrusionColor = hex
            }
        }
    }

    // ── Colour + gradient lists (same adapters as FillStrokeFragment) ─────────

    private fun setupColorAndGradientLists() {
        colorsAdapter = ColorsAdapter(
            Constants.colorList,
            onColorSelected = { color ->
                val selectedColor = color.colorCode.toColorInt()
                colorsAdapter.selectedColor = selectedColor
                applyPickedColor(selectedColor)
            },
            onNoneSelected = {
                applyPickedColor(Color.TRANSPARENT)
            },
            onColorPickerClicked = {
                viewModel.startPicking(PickerTarget.COLOR_PICKER_TEXT_FILL)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.adjustmentsParentFragment, ColorPickerFragment())
                    .addToBackStack(null)
                    .commit()
            },
            onEyeDropperClicked = {
                viewModel.startPicking(PickerTarget.EYE_DROPPER_TEXT_FILL)
            }
        )

        gradientsAdapter = GradientsAdapter(
            gradientList = emptyList(),
            onGradientSelected = { _, item ->
                viewModel.setTextFillGradient(item)
                if (item.colors.isNotEmpty()) {
                    applyPickedColor(item.colors.first())
                }
            },
            onGradientEditSelected = { _, item ->
                viewModel.startPickingGradient(GradientPickerTarget.TEXT_FILL)
                viewModel.setGradient(item)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.adjustmentsParentFragment, GradientEditorFragment().apply {
                        arguments = Bundle().apply { putBoolean("IS_EDIT", true) }
                    })
                    .addToBackStack(null)
                    .commit()
            },
            onNoneSelected = {
                viewModel.clearFillGradients()
            },
            onGradientPickerClicked = {
                viewModel.startPickingGradient(GradientPickerTarget.TEXT_FILL)
                viewModel.setPagingLocked(true)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.adjustmentsParentFragment, GradientEditorFragment().apply {
                        arguments = Bundle().apply { putBoolean("IS_EDIT", false) }
                    })
                    .addToBackStack(null)
                    .commit()
            }
        )

        binding.colors.apply {
            setHasFixedSize(true)
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            setHasFixedSize(true)
            adapter = gradientsAdapter
        }

        binding.solid.addPressEffect {
            if (!binding.colors.isVisible) togglePanels()
        }

        binding.gradient.addPressEffect {
            if (!binding.gradients.isVisible) togglePanels()
        }
    }

    private fun togglePanels() {
        val fadeDuration = 220L
        val showGradients = binding.gradients.isVisible

        if (showGradients) {
            binding.gradients.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                binding.gradients.visibility = View.GONE
                binding.colors.alpha = 0f
                binding.colors.visibility = View.VISIBLE
                binding.colors.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            binding.colors.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                binding.colors.visibility = View.GONE
                binding.gradients.alpha = 0f
                binding.gradients.visibility = View.VISIBLE
                binding.gradients.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()

            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    // ── Surface property sliders ──────────────────────────────────────────────

    private fun initSliders() {
        binding.sliderRoughness.apply {
            label = "Roughness"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.roughness = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderMetallic.apply {
            label = "Metallic"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.metallic = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderSpecular.apply {
            label = "Specular"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.specular = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderReflection.apply {
            label = "Reflection"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.reflection = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun initObservers() {
        mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
            if (!gradients.isNullOrEmpty()) {
                gradientsAdapter.updateList(gradients)
            }
        }

        viewModel.fillGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
        }

        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val mat = data?.material ?: return@observe
            surfaceAdapter.setSelectedId(mat.surface)
            surfaceAdapter.setBaseColor(
                runCatching { mat.frontColor.toColorInt() }.getOrDefault(Color.GRAY)
            )
            if (sameAsFront != mat.sameAsFront) {
                sameAsFront = mat.sameAsFront
                renderSameAsFrontTick()
            }
            setEditingSide(editingSide && !mat.sameAsFront)
            binding.sliderRoughness.value = mat.roughness.toInt()
            binding.sliderMetallic.value = mat.metallic.toInt()
            binding.sliderSpecular.value = mat.specular.toInt()
            binding.sliderReflection.value = mat.reflection.toInt()
        }
    }

    override fun onDestroyView() {
        viewModel.setPagingLocked(false)
        _binding?.rvSurfaces?.adapter = null
        _binding?.colors?.adapter = null
        _binding?.gradients?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Material3DFragment()
    }

    // ── Surface swatches ──────────────────────────────────────────────────────

    private inner class SurfaceAdapter(
        private val list: List<Text3DSurface>,
        private val onSelect: (String) -> Unit,
        private val onEditClick: () -> Unit
    ) : RecyclerView.Adapter<SurfaceAdapter.SurfaceViewHolder>() {

        private var selectedId: String = "plain"
        private var baseColor: Int = Color.GRAY

        /**
         * The first bind runs before the list has a height, so the cells would come out at
         * their layout default and only look right on a second visit. Hold the list and
         * re-bind once a real height lands, the way the style grid does.
         */
        private var attachedRv: RecyclerView? = null
        private var lastMeasuredHeight: Int = 0

        private val sizeOnLayout =
            View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
                val height = bottom - top
                if (height > 0 && height != lastMeasuredHeight &&
                    (bottom - top) != (oldBottom - oldTop)
                ) {
                    lastMeasuredHeight = height
                    v.post { if (itemCount > 0) notifyDataSetChanged() }
                }
            }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            attachedRv = recyclerView
            lastMeasuredHeight = recyclerView.height
            recyclerView.addOnLayoutChangeListener(sizeOnLayout)
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            recyclerView.removeOnLayoutChangeListener(sizeOnLayout)
            if (attachedRv == recyclerView) attachedRv = null
        }

        fun setSelectedId(id: String) {
            if (selectedId != id) {
                selectedId = id
                notifyDataSetChanged()
            }
        }

        /** Previews are tinted with the live front colour so they show the real result. */
        fun setBaseColor(color: Int) {
            if (baseColor != color) {
                baseColor = color
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurfaceViewHolder {
            val itemBinding = ItemMaterialSurfaceBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SurfaceViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: SurfaceViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class SurfaceViewHolder(private val itemBinding: ItemMaterialSurfaceBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: Text3DSurface) {
                val isSelected = item.id == selectedId
                itemBinding.tvSurfaceLabel.text = item.label

                if (isSelected) {
                    itemBinding.surfaceBox.background = ContextCompat.getDrawable(
                        itemBinding.root.context, R.drawable.bg_3d_preset_selected
                    )
                    itemBinding.tvSurfaceLabel.setTextColor("#005D28".toColorInt())
                    itemBinding.ivEdit.visibility = View.VISIBLE
                } else {
                    itemBinding.surfaceBox.background = ContextCompat.getDrawable(
                        itemBinding.root.context, R.drawable.bg_3d_preset_unselected
                    )
                    itemBinding.tvSurfaceLabel.setTextColor("#5F6368".toColorInt())
                    itemBinding.ivEdit.visibility = View.GONE
                }

                sizeToGrid()

                // The chip is built by the same code the canvas paints with, so the swatch
                // and the glyphs cannot drift apart when a surface is retuned. A surface
                // with its own preview colour shows that instead of the live front colour,
                // which is what keeps the grid from being one wall of the same hue.
                val chipColor = item.previewColor
                    ?.let { runCatching { it.toColorInt() }.getOrNull() }
                    ?: baseColor
                val density = resources.displayMetrics.density
                val boxPx = itemBinding.surfaceBox.layoutParams?.width?.takeIf { it > 0 }
                    ?: (46 * density).toInt()
                val px = (boxPx - 12 * density).toInt().coerceAtLeast((20 * density).toInt())
                itemBinding.surfaceBall.background = BitmapDrawable(
                    resources,
                    Text3DSurfaceShading.previewBitmap(item, chipColor, px)
                )

                itemBinding.ivEdit.setOnClickListener { onEditClick() }
                itemBinding.root.addPressEffect { onSelect(item.id) }
            }

            /**
             * Three rows have to fit whatever height the panel is at, so the cell is
             * measured off the list rather than pinned in the layout. The label keeps its
             * own line; the rest of the cell is the swatch.
             */
            private fun sizeToGrid() {
                val rv = (itemBinding.root.parent as? RecyclerView) ?: attachedRv ?: return
                val density = resources.displayMetrics.density
                val avail = rv.height - rv.paddingTop - rv.paddingBottom
                if (avail <= 0) return

                val rowGap = (6 * density).toInt()
                val labelBlock = (15 * density).toInt()
                val cell = ((avail - 3 * rowGap) / 3).coerceAtLeast((28 * density).toInt())
                val box = (cell - labelBlock).coerceAtLeast((24 * density).toInt())

                itemBinding.surfaceBox.layoutParams?.let { lp ->
                    if (lp.width != box || lp.height != box) {
                        lp.width = box
                        lp.height = box
                        itemBinding.surfaceBox.layoutParams = lp
                    }
                }
                itemBinding.root.layoutParams?.let { lp ->
                    if (lp.width != box) {
                        lp.width = box
                        itemBinding.root.layoutParams = lp
                    }
                }
            }
        }
    }
}
