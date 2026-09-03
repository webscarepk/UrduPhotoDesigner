package com.webscare.urducanvas.ui.editor.panels.text.threed.childs

import android.graphics.Color
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
import com.webscare.urducanvas.common.canvas.model.Text3DPreset
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.Fragment3dPresetsBinding
import com.webscare.urducanvas.databinding.Item3dPresetBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Presets3DFragment : Fragment() {

    private var _binding: Fragment3dPresetsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var adapter: PresetsAdapter
    private var selectedPresetId: String = "bold"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PresetsAdapter(Text3DData.PRESETS) { preset ->
            selectedPresetId = preset.id
            viewModel.apply3DPreset(preset.id)
            updateActivePresetCard(preset)
            adapter.setSelectedId(preset.id)
        }

        binding.rvPresets.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPresets.adapter = adapter

        binding.btnApplyPreset.addPressEffect {
            viewModel.apply3DPreset(selectedPresetId)
        }

        binding.btnSeeAll.addPressEffect {
            // Optional expanded dialog or notification
        }

        initObservers()
    }

    private fun initObservers() {
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val presetId = data?.selectedPreset ?: "bold"
            selectedPresetId = presetId
            adapter.setSelectedId(presetId)
            val preset = Text3DData.PRESETS.find { it.id == presetId } ?: Text3DData.PRESETS.first()
            updateActivePresetCard(preset)
        }
    }

    private fun updateActivePresetCard(preset: Text3DPreset) {
        binding.tvActivePresetTitle.text = preset.title
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Presets3DFragment()
    }

    private inner class PresetsAdapter(
        private val list: List<Text3DPreset>,
        private val onSelect: (Text3DPreset) -> Unit
    ) : RecyclerView.Adapter<PresetsAdapter.PresetViewHolder>() {

        private var selectedId: String = "bold"

        fun setSelectedId(id: String) {
            if (selectedId != id) {
                selectedId = id
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
            val itemBinding = Item3dPresetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return PresetViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class PresetViewHolder(private val itemBinding: Item3dPresetBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: Text3DPreset) {
                val isSelected = item.id == selectedId

                itemBinding.tvPresetLabel.text = item.label
                itemBinding.tvPresetSample.setTextColor(
                    try { item.frontColor.toColorInt() } catch (e: Exception) { Color.BLACK }
                )

                if (isSelected) {
                    itemBinding.presetPreviewBox.background =
                        ContextCompat.getDrawable(itemBinding.root.context, R.drawable.bg_3d_preset_selected)
                    itemBinding.tvPresetLabel.setTextColor("#005D28".toColorInt())
                } else {
                    itemBinding.presetPreviewBox.background =
                        ContextCompat.getDrawable(itemBinding.root.context, R.drawable.bg_3d_preset_unselected)
                    itemBinding.tvPresetLabel.setTextColor("#5F6368".toColorInt())
                }

                itemBinding.root.addPressEffect {
                    onSelect(item)
                }
            }
        }
    }
}
