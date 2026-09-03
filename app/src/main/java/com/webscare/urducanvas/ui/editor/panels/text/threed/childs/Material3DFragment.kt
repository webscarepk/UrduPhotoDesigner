package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.Text3DData
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.Fragment3dMaterialBinding
import com.webscare.urducanvas.databinding.Item3dColorSwatchBinding
import com.webscare.urducanvas.databinding.ItemMaterialSurfaceBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Material3DFragment : Fragment() {

    private var _binding: Fragment3dMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var surfaceAdapter: SurfaceAdapter
    private lateinit var frontColorAdapter: ColorSwatchAdapter
    private lateinit var depthColorAdapter: ColorSwatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dMaterialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSegmentedControl()
        setupSurfaceList()
        setupColorLists()
        initSliders()
        initObservers()
    }

    private fun setupSegmentedControl() {
        binding.segmentedControl.setItems(listOf("Surface", "Colours", "More"), defaultIndex = 0)
        binding.segmentedControl.onSegmentSelected = { index ->
            binding.layoutSurface.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.layoutColours.visibility = if (index == 1) View.VISIBLE else View.GONE
            binding.layoutMore.visibility = if (index == 2) View.VISIBLE else View.GONE
        }
    }

    private fun setupSurfaceList() {
        surfaceAdapter = SurfaceAdapter(Text3DData.SURFACES) { surfaceId ->
            viewModel.updateText3D(pushToUndo = true) { it.material.surface = surfaceId }
            surfaceAdapter.setSelectedId(surfaceId)
        }
        binding.rvSurfaces.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSurfaces.adapter = surfaceAdapter
    }

    private fun setupColorLists() {
        frontColorAdapter = ColorSwatchAdapter(Text3DData.SWATCHES) { hex ->
            viewModel.updateText3D(pushToUndo = true) { it.material.frontColor = hex }
            frontColorAdapter.setSelectedColor(hex)
        }
        binding.rvFrontColors.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvFrontColors.adapter = frontColorAdapter

        depthColorAdapter = ColorSwatchAdapter(Text3DData.SWATCHES) { hex ->
            viewModel.updateText3D(pushToUndo = true) { it.material.extrusionColor = hex }
            depthColorAdapter.setSelectedColor(hex)
        }
        binding.rvDepthColors.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvDepthColors.adapter = depthColorAdapter

        binding.btnSameAsFront.addPressEffect {
            val frontHex = viewModel.text3dData.value?.material?.frontColor ?: "#2E7D4F"
            viewModel.updateText3D(pushToUndo = true) { it.material.extrusionColor = frontHex }
            depthColorAdapter.setSelectedColor(frontHex)
        }
    }

    private fun initSliders() {
        binding.sliderRoughness.apply {
            label = "Roughness"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.roughness = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderMetallic.apply {
            label = "Metallic"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.metallic = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderSpecular.apply {
            label = "Specular"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.specular = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderReflection.apply {
            label = "Reflection"
            unit = ""
            minValue = 0
            maxValue = 100
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) { it.material.reflection = v.toFloat() }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val mat = data?.material
            if (mat != null) {
                surfaceAdapter.setSelectedId(mat.surface)
                frontColorAdapter.setSelectedColor(mat.frontColor)
                depthColorAdapter.setSelectedColor(mat.extrusionColor)

                binding.sliderRoughness.value = mat.roughness.toInt()
                binding.sliderMetallic.value = mat.metallic.toInt()
                binding.sliderSpecular.value = mat.specular.toInt()
                binding.sliderReflection.value = mat.reflection.toInt()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Material3DFragment()
    }

    // ── ADAPTERS ──────────────────────────────────────────────────────────────
    private inner class SurfaceAdapter(
        private val list: List<Pair<String, String>>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<SurfaceAdapter.SurfaceViewHolder>() {

        private var selectedId: String = "matte"

        fun setSelectedId(id: String) {
            if (selectedId != id) {
                selectedId = id
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

            fun bind(item: Pair<String, String>) {
                val isSelected = item.first == selectedId
                itemBinding.tvSurfaceLabel.text = item.second

                if (isSelected) {
                    itemBinding.surfaceBox.background =
                        ContextCompat.getDrawable(itemBinding.root.context, R.drawable.bg_3d_preset_selected)
                    itemBinding.tvSurfaceLabel.setTextColor("#005D28".toColorInt())
                } else {
                    itemBinding.surfaceBox.background =
                        ContextCompat.getDrawable(itemBinding.root.context, R.drawable.bg_3d_preset_unselected)
                    itemBinding.tvSurfaceLabel.setTextColor("#5F6368".toColorInt())
                }

                // Render gradient ball
                val ballDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 32f * resources.displayMetrics.density
                    setGradientCenter(0.32f, 0.26f)
                    when (item.first) {
                        "matte" -> colors = intArrayOf("#A9B2B8".toColorInt(), "#6E767C".toColorInt())
                        "glossy" -> colors = intArrayOf("#FFFFFF".toColorInt(), "#8FA0AC".toColorInt(), "#4A5A66".toColorInt())
                        "metal" -> colors = intArrayOf("#F3DFAF".toColorInt(), "#C79A2E".toColorInt(), "#7A5A10".toColorInt())
                        "chrome" -> colors = intArrayOf("#FFFFFF".toColorInt(), "#B9C4CB".toColorInt(), "#5D6B75".toColorInt())
                        "glass" -> colors = intArrayOf("#FAFCFD".toColorInt(), "#CFE2E8".toColorInt(), "#9BB4BE".toColorInt())
                        else -> colors = intArrayOf("#A9B2B8".toColorInt(), "#6E767C".toColorInt())
                    }
                }
                itemBinding.surfaceBall.background = ballDrawable

                itemBinding.root.addPressEffect {
                    onSelect(item.first)
                }
            }
        }
    }

    private inner class ColorSwatchAdapter(
        private val colors: List<String>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<ColorSwatchAdapter.ColorViewHolder>() {

        private var selectedHex: String = "#2E7D4F"

        fun setSelectedColor(hex: String) {
            if (!selectedHex.equals(hex, ignoreCase = true)) {
                selectedHex = hex
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val itemBinding = Item3dColorSwatchBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ColorViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            holder.bind(colors[position])
        }

        override fun getItemCount(): Int = colors.size

        inner class ColorViewHolder(private val itemBinding: Item3dColorSwatchBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(hex: String) {
                val isSelected = hex.equals(selectedHex, ignoreCase = true)

                val col = try { hex.toColorInt() } catch (e: Exception) { Color.BLACK }
                val colorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(col)
                    setStroke(1, "#14000000".toColorInt())
                }
                itemBinding.vColorCircle.background = colorDrawable

                if (isSelected) {
                    val ringDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(2 * resources.displayMetrics.density.toInt(), "#005D28".toColorInt())
                    }
                    itemBinding.vSelectedRing.background = ringDrawable
                    itemBinding.vSelectedRing.visibility = View.VISIBLE
                } else {
                    itemBinding.vSelectedRing.visibility = View.GONE
                }

                itemBinding.root.addPressEffect {
                    onSelect(hex)
                }
            }
        }
    }
}
