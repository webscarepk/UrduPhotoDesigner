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
import com.webscare.urducanvas.databinding.ItemMaterialSwatchBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The 3D material panel.
 *
 * One screen, not three: the face pill, the Solid / Gradient / Materials segment and the
 * swatch grid. Picking a finish and picking a colour used to be two screens deep apart,
 * which made the common case - "make it brushed silver" - a three-tap journey.
 *
 * Roughness, metallic, specular and reflection still sit one level deeper behind the
 * settings glyph; that screen locks the parent pager so a vertical drag edits a slider
 * rather than flicking to the next 3D section.
 */
@AndroidEntryPoint
class Material3DFragment : Fragment() {

    private var _binding: Fragment3dMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter
    private lateinit var materialSwatchAdapter: MaterialSwatchAdapter

    /** false = editing the front face, true = editing the extrusion side. */
    private var editingSide: Boolean = false

    /** Mirrors [com.webscare.urducanvas.common.canvas.model.Text3DMaterial.sameAsFront]. */
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

        setupColorAndGradientLists()
        setupFaceTargeting()
        initSliders()
        initHeaderButtons()
        initObservers()
    }

    // ── Screen switching ──────────────────────────────────────────────────────

    private enum class Screen { MAIN, PROPERTIES }

    private fun showScreen(screen: Screen) {
        val b = _binding ?: return
        b.layoutAdjustments.visibility = if (screen == Screen.MAIN) View.VISIBLE else View.GONE
        b.layoutProperties.visibility = if (screen == Screen.PROPERTIES) View.VISIBLE else View.GONE
        // Only the deeper screen locks the pager; the main one is the panel itself.
        viewModel.setPagingLocked(screen != Screen.MAIN)
    }

    private fun initHeaderButtons() {
        binding.btnSettings.addPressEffect { showScreen(Screen.PROPERTIES) }
        binding.btnPropertiesDone.addPressEffect { showScreen(Screen.MAIN) }
    }

    // ── Front / side targeting ────────────────────────────────────────────────

    /** Which face the swatch lists below are editing. */
    private enum class Face { FRONT, SIDE, BOTH }

    private var face: Face = Face.BOTH

    private fun setupFaceTargeting() {
        binding.tabFront.addPressEffect { setFace(Face.FRONT, userInitiated = true) }
        binding.tabSide.addPressEffect { setFace(Face.SIDE, userInitiated = true) }
        binding.tabBoth.addPressEffect { setFace(Face.BOTH, userInitiated = true) }
        // Opening the panel is not an edit, so this pass must not switch the material on.
        setFace(Face.BOTH, userInitiated = false)
    }

    /**
     * Three states where there used to be two plus a checkbox.
     *
     * "Same as front" was the checkbox's whole job, and it said the same thing the pill
     * already implied: Both *is* the synced state. Folding it in returns a row of height
     * to the swatches and removes a control that could contradict the pill.
     */
    private fun setFace(target: Face, userInitiated: Boolean) {
        face = target
        editingSide = target == Face.SIDE
        sameAsFront = target == Face.BOTH

        val b = _binding ?: return
        val ctx = context ?: return
        val active = ContextCompat.getColor(ctx, R.color.white)
        val inactive = ContextCompat.getColor(ctx, R.color.contrast)
        b.tabFront.backgroundTintList =
            ColorStateList.valueOf(if (target == Face.FRONT) active else inactive)
        b.tabSide.backgroundTintList =
            ColorStateList.valueOf(if (target == Face.SIDE) active else inactive)
        b.tabBoth.backgroundTintList =
            ColorStateList.valueOf(if (target == Face.BOTH) active else inactive)

        viewModel.updateText3D(
            pushToUndo = false,
            enableSection = if (userInitiated) CanvasViewModel.Text3DSection.MATERIAL else null
        ) { data ->
            data.material.sameAsFront = sameAsFront
            if (sameAsFront) data.material.extrusionColor = data.material.frontColor
        }
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
        viewModel.updateText3D(
            pushToUndo = true,
            enableSection = CanvasViewModel.Text3DSection.MATERIAL
        ) { data ->
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

        setupMaterialSwatches()

        binding.solid.addPressEffect { showFillTab(FillTab.SOLID) }
        binding.gradient.addPressEffect { showFillTab(FillTab.GRADIENT) }
        binding.materials.addPressEffect { showFillTab(FillTab.MATERIALS) }

        showFillTab(FillTab.SOLID, animate = false)
    }

    private enum class FillTab { SOLID, GRADIENT, MATERIALS }

    private var fillTab = FillTab.SOLID

    /**
     * Three-way segment. Solid and Gradient set the colour alone; Materials sets a colour
     * *and* the finish it belongs in, which is the pairing the swatch sheets are built on.
     */
    private fun showFillTab(tab: FillTab, animate: Boolean = true) {
        val b = _binding ?: return
        if (fillTab == tab && animate) return
        fillTab = tab

        val lists = mapOf(
            FillTab.SOLID to b.colors,
            FillTab.GRADIENT to b.gradients,
            FillTab.MATERIALS to b.materialsList
        )
        val chips = mapOf(
            FillTab.SOLID to b.solid,
            FillTab.GRADIENT to b.gradient,
            FillTab.MATERIALS to b.materials
        )

        val fade = 220L
        lists.forEach { (key, view) ->
            val show = key == tab
            if (show) {
                view.alpha = if (animate) 0f else 1f
                view.visibility = View.VISIBLE
                if (animate) view.animate().alpha(1f).setDuration(fade).start()
            } else if (view.isVisible) {
                if (animate) {
                    view.animate().alpha(0f).setDuration(fade)
                        .withEndAction { view.visibility = View.GONE }.start()
                } else {
                    view.visibility = View.GONE
                }
            }
        }

        val ctx = requireContext()
        chips.forEach { (key, chip) ->
            chip.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, if (key == tab) R.color.white else R.color.contrast)
            )
        }
    }

    private fun setupMaterialSwatches() {
        materialSwatchAdapter = MaterialSwatchAdapter { entry ->
            // A named tone carries its own colour; a plain finish keeps whatever colour is
            // already applied and only changes what the surface is made of.
            entry.color?.let { applyPickedColor(it) }
            viewModel.updateText3D(pushToUndo = true, enableSection = CanvasViewModel.Text3DSection.MATERIAL) { it.material.surface = entry.surface.id }
            materialSwatchAdapter.setSelected(entry.label)
        }
        binding.materialsList.apply {
            layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = materialSwatchAdapter
        }
    }

    // ── Surface property sliders ──────────────────────────────────────────────

    private fun initSliders() {
        binding.sliderRoughness.apply {
            label = "Roughness"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.MATERIAL) { it.material.roughness = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderMetallic.apply {
            label = "Metallic"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.MATERIAL) { it.material.metallic = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderSpecular.apply {
            label = "Specular"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.MATERIAL) { it.material.specular = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderReflection.apply {
            label = "Reflection"
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false, enableSection = CanvasViewModel.Text3DSection.MATERIAL) { it.material.reflection = v.toFloat() }
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
            materialSwatchAdapter.setBaseColor(
                runCatching { mat.frontColor.toColorInt() }.getOrDefault(Color.BLACK)
            )
            materialSwatchAdapter.setSelectedSurface(mat.surface)
            // A preset or an undo can flip the sync flag behind the panel's back; move the
            // pill to match, but only when it actually disagrees, or setFace would write
            // the model back on every emission.
            if (sameAsFront != mat.sameAsFront) {
                setFace(if (mat.sameAsFront) Face.BOTH else Face.FRONT, userInitiated = false)
            }
            binding.sliderRoughness.value = mat.roughness.toInt()
            binding.sliderMetallic.value = mat.metallic.toInt()
            binding.sliderSpecular.value = mat.specular.toInt()
            binding.sliderReflection.value = mat.reflection.toInt()
        }
    }

    override fun onDestroyView() {
        viewModel.setPagingLocked(false)
        _binding?.colors?.adapter = null
        _binding?.gradients?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Material3DFragment()
    }

    // ── Material swatches ─────────────────────────────────────────────────────

    /**
     * One swatch entry: either an abstract finish (Glossy, Chrome, Velvet), tinted by the
     * live front colour, or a named tone (Pearl, Cobalt, Espresso) that carries its own
     * colour *and* the finish it belongs in.
     *
     * Both live in one list because the panel no longer has a separate surfaces screen —
     * picking "what it is made of" and "which grey" is one decision, made in one place.
     */
    private data class MaterialEntry(
        val label: String,
        val surface: Text3DSurface,
        /** null = tint with whatever colour is currently applied. */
        val color: Int?
    )

    /**
     * Rendered with the colour item's construction — an outer round card that carries the
     * selection ring and an inner card that insets when selected, so the chip shrinks
     * inside the ring exactly the way a colour swatch does. The preview itself comes from
     * the same shading code the canvas paints with, so a swatch cannot lie about the
     * material.
     */
    private inner class MaterialSwatchAdapter(
        private val onSelect: (MaterialEntry) -> Unit
    ) : RecyclerView.Adapter<MaterialSwatchAdapter.SwatchViewHolder>() {

        private val items: List<MaterialEntry> = buildList {
            Text3DData.SURFACES.forEach { add(MaterialEntry(it.label, it, null)) }
            com.webscare.urducanvas.common.canvas.model.Text3DPalette.ALL.forEach { sw ->
                add(
                    MaterialEntry(
                        sw.name,
                        com.webscare.urducanvas.common.canvas.model.Text3DPalette.surfaceFor(sw),
                        runCatching { sw.hex.toColorInt() }.getOrNull()
                    )
                )
            }
        }

        private var selectedLabel: String? = null
        private var baseColor: Int = Color.BLACK

        fun setSelected(label: String?) {
            if (selectedLabel != label) {
                selectedLabel = label
                notifyDataSetChanged()
            }
        }

        /**
         * Moves the ring onto whichever entry matches the applied finish.
         *
         * A named tone and a plain finish share a surface — Espresso *is* matte — so if
         * what is already selected uses this surface, leave it alone. Without this check
         * the next model update after picking a tone dragged the ring off the tone and
         * onto the bare finish, which read as the selection vanishing.
         */
        fun setSelectedSurface(surfaceId: String) {
            val current = items.firstOrNull { it.label == selectedLabel }
            if (current != null && current.surface.id == surfaceId) return
            val match = items.firstOrNull { it.color == null && it.surface.id == surfaceId }
            setSelected(match?.label ?: selectedLabel)
        }

        fun setBaseColor(color: Int) {
            if (baseColor != color) {
                baseColor = color
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SwatchViewHolder(
            ItemMaterialSwatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: SwatchViewHolder, position: Int) =
            holder.bind(items[position])

        inner class SwatchViewHolder(private val itemBinding: ItemMaterialSwatchBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: MaterialEntry) {
                val isSelected = item.label == selectedLabel
                val ctx = itemBinding.root.context
                val density = resources.displayMetrics.density

                itemBinding.swatchLabel.text = item.label
                itemBinding.swatchLabel.setTextColor(
                    (if (isSelected) "#005D28" else "#5F6368").toColorInt()
                )

                // Selection ring, sized and inset exactly as ColorsAdapter does it.
                val strokePx = (2.0f * density + 0.5f).toInt()
                val marginPx = (3.5f * density + 0.5f).toInt()
                val lp = itemBinding.cardInner.layoutParams as ViewGroup.MarginLayoutParams
                if (isSelected) {
                    itemBinding.cardOuter.strokeWidth = strokePx
                    itemBinding.cardOuter.strokeColor =
                        ContextCompat.getColor(ctx, R.color.appColor)
                    lp.setMargins(marginPx, marginPx, marginPx, marginPx)
                } else {
                    itemBinding.cardOuter.strokeWidth = (1f * density + 0.5f).toInt()
                    itemBinding.cardOuter.strokeColor = "#D0D5DD".toColorInt()
                    lp.setMargins(0, 0, 0, 0)
                }
                itemBinding.cardInner.layoutParams = lp

                val chipPx = (44 * density).toInt()
                itemBinding.swatchView.setImageDrawable(
                    BitmapDrawable(
                        resources,
                        Text3DSurfaceShading.previewBitmap(
                            item.surface, item.color ?: baseColor, chipPx
                        )
                    )
                )

                itemBinding.root.addPressEffect { onSelect(item) }
            }
        }
    }
}
