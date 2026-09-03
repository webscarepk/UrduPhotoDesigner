package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.model.Text3DData
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.Text3DPadView
import com.webscare.urducanvas.databinding.Fragment3dShadowBinding
import com.webscare.urducanvas.databinding.Item3dColorSwatchBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Shadow3DFragment : Fragment() {

    private var _binding: Fragment3dShadowBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var colorAdapter: ColorSwatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dShadowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSegmentedControl()
        setupPad()
        initSliders()
        setupColorStrip()
        initObservers()
    }

    private fun setupSegmentedControl() {
        binding.segmentedControl.setItems(listOf("Shadow", "More"), defaultIndex = 0)
        binding.segmentedControl.onSegmentSelected = { index ->
            binding.layoutShadowMain.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.layoutShadowMore.visibility = if (index == 1) View.VISIBLE else View.GONE
        }
    }

    private fun setupPad() {
        binding.shadowPad.mode = Text3DPadView.Mode.ANGLE
        binding.shadowPad.onDragStateChanged = { dragging ->
            viewModel.setPagingLocked(dragging)
        }
        binding.shadowPad.onAngleChanged = { angle ->
            viewModel.updateText3D(pushToUndo = false) {
                it.shadow.angle = angle
                it.shadow.enabled = true
            }
        }
    }

    private fun initSliders() {
        binding.sliderOpacity.apply {
            label = "Opacity"
            unit = ""
            minValue = 0
            maxValue = 255
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) {
                    it.shadow.opacity = v
                    it.shadow.enabled = v > 0
                }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderRadius.apply {
            label = "Radius"
            unit = ""
            minValue = 0
            maxValue = 40
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) {
                    it.shadow.blur = v.toFloat()
                    it.shadow.enabled = true
                }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderDistance.apply {
            label = "Distance"
            unit = ""
            minValue = 0
            maxValue = 60
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) {
                    it.shadow.distance = v.toFloat()
                    it.shadow.enabled = true
                }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }

        binding.sliderShadowScale.apply {
            label = "Shadow Scale"
            unit = "%"
            minValue = 50
            maxValue = 150
            onValueChanged = { v ->
                viewModel.updateText3D(pushToUndo = false) {
                    it.shadow.scale = v.toFloat()
                }
            }
            onDragStateChanged = { dragging -> viewModel.setPagingLocked(dragging) }
        }
    }

    private fun setupColorStrip() {
        colorAdapter = ColorSwatchAdapter(Text3DData.SHADOW_COLORS) { hex ->
            viewModel.updateText3D(pushToUndo = true) {
                it.shadow.color = hex
                it.shadow.enabled = true
            }
            colorAdapter.setSelectedColor(hex)
        }
        binding.rvShadowColors.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvShadowColors.adapter = colorAdapter

        binding.btnShadowNone.addPressEffect {
            viewModel.updateText3D(pushToUndo = true) {
                it.shadow.opacity = 0
                it.shadow.enabled = false
            }
            binding.sliderOpacity.value = 0
        }

        binding.btnShadowColorPicker.addPressEffect {
            viewModel.startPicking(PickerTarget.COLOR_PICKER_SHADOW)
        }

        binding.btnShadowEyeDropper.addPressEffect {
            viewModel.startPicking(PickerTarget.EYE_DROPPER_SHADOW)
        }
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val sh = data?.shadow
            if (sh != null) {
                binding.shadowPad.angle = sh.angle
                binding.sliderOpacity.value = sh.opacity
                binding.sliderRadius.value = sh.blur.toInt()
                binding.sliderDistance.value = sh.distance.toInt()
                binding.sliderShadowScale.value = sh.scale.toInt()
                colorAdapter.setSelectedColor(sh.color)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Shadow3DFragment()
    }

    private inner class ColorSwatchAdapter(
        private val colors: List<String>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<ColorSwatchAdapter.ColorViewHolder>() {

        private var selectedHex: String = "#000000"

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
