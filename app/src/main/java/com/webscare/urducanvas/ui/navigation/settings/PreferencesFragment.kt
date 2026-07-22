package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentPreferencesBinding
import com.webscare.urducanvas.ui.editor.export.ExportOptionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreferencesFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val subscriptionViewModel: com.webscare.urducanvas.viewmodels.SubscriptionsViewModel by activityViewModels()

    private var resolutionAdapter: ExportOptionAdapter<Any>? = null
    private var qualityAdapter: ExportOptionAdapter<Any>? = null
    private var formatAdapter: ExportOptionAdapter<Any>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {

        viewModel.fetchExportOptionsFromDataStore()

        viewModel.exportOptions.observe(viewLifecycleOwner) { opts ->
            binding.resolution.text = opts.resolution.name
            binding.quality.text = opts.quality.label
            binding.format.text = opts.format.name

            val subscribed = subscriptionViewModel.isSubscribed.value
            binding.resolutionPremiumBadge.isVisible = opts.resolution.isPremium && !subscribed
            binding.formatPremiumBadge.isVisible = opts.format.isPremium && !subscribed

            resolutionAdapter?.notifyDataSetChanged()
            qualityAdapter?.notifyDataSetChanged()
            formatAdapter?.notifyDataSetChanged()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                subscriptionViewModel.isSubscribed.collect { subscribed ->
                    resolutionAdapter?.isSubscribed = subscribed
                    qualityAdapter?.isSubscribed = subscribed
                    formatAdapter?.isSubscribed = subscribed

                    resolutionAdapter?.notifyDataSetChanged()
                    qualityAdapter?.notifyDataSetChanged()
                    formatAdapter?.notifyDataSetChanged()

                    val opts = viewModel.exportOptions.value
                    if (opts != null) {
                        binding.resolutionPremiumBadge.isVisible = opts.resolution.isPremium && !subscribed
                        binding.formatPremiumBadge.isVisible = opts.format.isPremium && !subscribed
                    }
                }
            }
        }

        mainViewModel.isDarkMode.observe(viewLifecycleOwner) { isChecked ->
            if (binding.darkModeSwitch.isChecked != isChecked) {
                binding.darkModeSwitch.isChecked = isChecked
            }
        }
    }

    private fun setupRecycler(
        rv: RecyclerView,
        type: ExportViewType,
        dropdownIcon: ImageView
    ): ExportOptionAdapter<Any> {
        val items = when (type) {
            ExportViewType.RESOLUTION -> viewModel.availableResolutions
            ExportViewType.QUALITY -> viewModel.qualityOptions
            ExportViewType.FORMAT -> viewModel.formatOptions
        }

        val adapter = ExportOptionAdapter(
            items,
            type,
            displayMode = false   // ✅ compact mode
        ) { selected ->
            val isSubscribed = subscriptionViewModel.isSubscribed.value
            val isPremiumOption = when (selected) {
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> selected.isPremium
                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> selected.isPremium
                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> selected.isPremium
                else -> false
            }

            if (isPremiumOption && !isSubscribed) {
                findNavController().navigate(com.webscare.urducanvas.R.id.subscriptionsFragment)
                return@ExportOptionAdapter
            }

            when (selected) {
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> viewModel.updateExportOptionsAndSave(
                    viewModel.exportOptions.value!!.copy(resolution = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> viewModel.updateExportOptionsAndSave(
                    viewModel.exportOptions.value!!.copy(quality = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> viewModel.updateExportOptionsAndSave(
                    viewModel.exportOptions.value!!.copy(format = selected)
                )
            }
            toggle(rv, dropdownIcon)
        }
        adapter.isSubscribed = subscriptionViewModel.isSubscribed.value
        rv.adapter = adapter
        return adapter
    }

    private fun toggle(rv: RecyclerView, dropdownIcon: ImageView) {
        val isExpanding = !rv.isVisible
        rv.visibility = if (isExpanding) View.VISIBLE else View.GONE
        dropdownIcon.animate().rotation(if (isExpanding) 180f else 0f).setDuration(200).start()
    }

    private fun setEvents() {
        formatAdapter = setupRecycler(binding.formatList, ExportViewType.FORMAT, binding.formatDropdownIcon)
        resolutionAdapter = setupRecycler(binding.resolutionList, ExportViewType.RESOLUTION, binding.resolutionDropdownIcon)
        qualityAdapter = setupRecycler(binding.qualityList, ExportViewType.QUALITY, binding.qualityDropdownIcon)

        // expand/collapse on click with dropdown arrow 180° flip
        val toggleFormat = { toggle(binding.formatList, binding.formatDropdownIcon) }
        binding.formatTitle.addPressEffect { toggleFormat() }
        binding.format.addPressEffect { toggleFormat() }
        binding.formatDropdownIcon.addPressEffect { toggleFormat() }
        binding.formatPremiumBadge.addPressEffect { toggleFormat() }

        val toggleResolution = { toggle(binding.resolutionList, binding.resolutionDropdownIcon) }
        binding.resolutionTitle.addPressEffect { toggleResolution() }
        binding.resolution.addPressEffect { toggleResolution() }
        binding.resolutionDropdownIcon.addPressEffect { toggleResolution() }
        binding.resolutionPremiumBadge.addPressEffect { toggleResolution() }

        val toggleQuality = { toggle(binding.qualityList, binding.qualityDropdownIcon) }
        binding.qualityTitle.addPressEffect { toggleQuality() }
        binding.quality.addPressEffect { toggleQuality() }
        binding.qualityDropdownIcon.addPressEffect { toggleQuality() }

        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (mainViewModel.isDarkMode.value != isChecked) {
                mainViewModel.updateDarkMode(isChecked)
                if (isChecked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }

        binding.back.addPressEffect {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}