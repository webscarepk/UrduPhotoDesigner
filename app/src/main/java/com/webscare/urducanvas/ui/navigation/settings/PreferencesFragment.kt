package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.urducanvas.common.datastore.PreferencesDataStoreHelper
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.databinding.FragmentPreferencesBinding
import com.webscare.urducanvas.databinding.LayoutResolutionsItemPrefsBinding
import com.webscare.urducanvas.ui.editor.export.ExportOptionAdapter
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PreferencesFragment : Fragment() {
    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val subscriptionViewModel: com.webscare.urducanvas.viewmodels.SubscriptionsViewModel by activityViewModels()

    @Inject
    lateinit var dataStore: PreferencesDataStoreHelper

    private var resolutionAdapter: ExportOptionAdapter<Any>? = null
    private var qualityAdapter: ExportOptionAdapter<Any>? = null
    private var formatAdapter: ExportOptionAdapter<Any>? = null
    private var defaultFontAdapter: DefaultFontOptionAdapter? = null
    private var autoSaveAdapter: AutoSaveOptionAdapter? = null

    private var selectedFontId: String = ""
    private var selectedAutoSaveKey: String = "3"

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
        calculateAndDisplayCacheSize()
    }

    override fun onResume() {
        super.onResume()
        calculateAndDisplayCacheSize()
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

        // Custom fonts & Default font observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.localFonts.collect { fonts ->
                    val importedCount = fonts.count {
                        it.font_category.equals("Imported", ignoreCase = true)
                    }
                    binding.customFontsCount.text = getString(R.string.fonts_imported_count, importedCount)

                    updateDefaultFontList(fonts)
                }
            }
        }

        mainViewModel.isDarkMode.observe(viewLifecycleOwner) { isChecked ->
            if (binding.darkModeSwitch.isChecked() != isChecked) {
                binding.darkModeSwitch.setCheckedQuietly(isChecked)
            }
        }

        // Load saved preferences
        viewLifecycleOwner.lifecycleScope.launch {
            selectedFontId = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.KEY_DEFAULT_URDU_FONT_ID,
                ""
            )
            val fontName = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.KEY_DEFAULT_URDU_FONT_NAME,
                getString(R.string.default_font_system)
            )
            binding.defaultFont.text = fontName

            selectedAutoSaveKey = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.KEY_AUTO_SAVE_INTERVAL,
                "3"
            )
            binding.autoSave.text = getAutoSaveLabel(selectedAutoSaveKey)
            updateAutoSaveList()

            val isHapticEnabled = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.KEY_HAPTIC_FEEDBACK,
                true
            )
            binding.hapticSwitch.setCheckedQuietly(isHapticEnabled)
            com.webscare.urducanvas.common.utils.Utils.isHapticFeedbackGloballyEnabled = isHapticEnabled

            val isSnappingEnabled = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.KEY_SMART_SNAPPING,
                true
            )
            binding.smartSnappingSwitch.setCheckedQuietly(isSnappingEnabled)
            viewModel.setSmartSnappingEnabled(isSnappingEnabled)
        }
    }

    private fun updateDefaultFontList(fonts: List<FontEntity>) {
        val list = mutableListOf<DefaultFontOption>()
        list.add(
            DefaultFontOption(
                fontId = "0",
                fontName = getString(R.string.default_font_system),
                isSelected = selectedFontId.isBlank() || selectedFontId == "0"
            )
        )

        val localAndImported = fonts.filter {
            it.is_downloaded || it.font_category.equals("Imported", ignoreCase = true) || !it.file_path.isNullOrBlank()
        }

        localAndImported.forEach { font ->
            list.add(
                DefaultFontOption(
                    fontId = font.id.toString(),
                    fontName = font.font_name,
                    isSelected = selectedFontId == font.id.toString()
                )
            )
        }

        defaultFontAdapter?.updateList(list)
    }

    private fun updateAutoSaveList() {
        val options = listOf(
            AutoSaveOption("off", getString(R.string.auto_save_off), selectedAutoSaveKey == "off"),
            AutoSaveOption("1", getString(R.string.auto_save_1_min), selectedAutoSaveKey == "1"),
            AutoSaveOption("3", getString(R.string.auto_save_3_min), selectedAutoSaveKey == "3"),
            AutoSaveOption("5", getString(R.string.auto_save_5_min), selectedAutoSaveKey == "5")
        )
        autoSaveAdapter?.updateList(options)
    }

    private fun getAutoSaveLabel(key: String): String {
        return when (key) {
            "off" -> getString(R.string.auto_save_off)
            "1" -> getString(R.string.auto_save_1_min)
            "5" -> getString(R.string.auto_save_5_min)
            else -> getString(R.string.auto_save_3_min)
        }
    }

    private fun calculateAndDisplayCacheSize() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var size = 0L
            try {
                val cacheDir = requireContext().cacheDir
                if (cacheDir.exists()) {
                    size += cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                }
                val externalCacheDir = requireContext().externalCacheDir
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    size += externalCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                _binding?.cacheSizeText?.text = formatSize(size)
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024f
        val mb = kb / 1024f
        val gb = mb / 1024f
        return when {
            gb >= 1f -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1f -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1f -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun clearAppCache() {
        binding.cacheSizeText.text = getString(R.string.calculating)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Glide.get(requireContext()).clearDiskCache()
                val cacheDir = requireContext().cacheDir
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
                val externalCacheDir = requireContext().externalCacheDir
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    externalCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                try {
                    Glide.get(requireContext()).clearMemory()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                binding.cacheSizeText.text = "0 MB"
                Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show()
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
            displayMode = false
        ) { selected ->
            val isSubscribed = subscriptionViewModel.isSubscribed.value
            val isPremiumOption = when (selected) {
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> selected.isPremium
                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> selected.isPremium
                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> selected.isPremium
                else -> false
            }

            if (isPremiumOption && !isSubscribed) {
                findNavController().navigate(R.id.subscriptionsFragment)
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

        // Default Font Adapter
        defaultFontAdapter = DefaultFontOptionAdapter(emptyList()) { selected ->
            selectedFontId = selected.fontId
            binding.defaultFont.text = selected.fontName
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.putPreference(PreferenceDataStoreKeysConstants.KEY_DEFAULT_URDU_FONT_ID, selected.fontId)
                dataStore.putPreference(PreferenceDataStoreKeysConstants.KEY_DEFAULT_URDU_FONT_NAME, selected.fontName)
                viewModel.fetchDefaultPreferences()
            }
            updateDefaultFontList(mainViewModel.localFonts.value)
            toggle(binding.defaultFontList, binding.defaultFontDropdownIcon)
        }
        binding.defaultFontList.adapter = defaultFontAdapter

        // Auto Save Adapter
        autoSaveAdapter = AutoSaveOptionAdapter(emptyList()) { selected ->
            selectedAutoSaveKey = selected.key
            binding.autoSave.text = selected.label
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.putPreference(PreferenceDataStoreKeysConstants.KEY_AUTO_SAVE_INTERVAL, selected.key)
            }
            updateAutoSaveList()
            toggle(binding.autoSaveList, binding.autoSaveDropdownIcon)
        }
        binding.autoSaveList.adapter = autoSaveAdapter
        updateAutoSaveList()

        // Toggle clicks
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

        val toggleDefaultFont = { toggle(binding.defaultFontList, binding.defaultFontDropdownIcon) }
        binding.defaultFontTitle.addPressEffect { toggleDefaultFont() }
        binding.defaultFont.addPressEffect { toggleDefaultFont() }
        binding.defaultFontDropdownIcon.addPressEffect { toggleDefaultFont() }

        val toggleAutoSave = { toggle(binding.autoSaveList, binding.autoSaveDropdownIcon) }
        binding.autoSaveTitle.addPressEffect { toggleAutoSave() }
        binding.autoSave.addPressEffect { toggleAutoSave() }
        binding.autoSaveDropdownIcon.addPressEffect { toggleAutoSave() }

        // Custom Fonts Card
        binding.customFontsCard.addPressEffect {
            ImportedFontsBottomSheet().show(childFragmentManager, "ImportedFontsBottomSheet")
        }

        // Clear Cache Button
        binding.clearCacheBtn.addPressEffect {
            clearAppCache()
        }

        // Smart Snapping Switch
        binding.smartSnappingSwitch.onCheckedChangeListener = { isChecked ->
            viewModel.setSmartSnappingEnabled(isChecked)
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.putPreference(PreferenceDataStoreKeysConstants.KEY_SMART_SNAPPING, isChecked)
            }
        }

        // Haptic Feedback Switch
        binding.hapticSwitch.onCheckedChangeListener = { isChecked ->
            com.webscare.urducanvas.common.utils.Utils.isHapticFeedbackGloballyEnabled = isChecked
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.putPreference(PreferenceDataStoreKeysConstants.KEY_HAPTIC_FEEDBACK, isChecked)
            }
        }

        // Dark Mode Switch
        binding.darkModeSwitch.onCheckedChangeListener = { isChecked ->
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
        _binding?.formatList?.adapter = null
        _binding?.resolutionList?.adapter = null
        _binding?.qualityList?.adapter = null
        _binding?.defaultFontList?.adapter = null
        _binding?.autoSaveList?.adapter = null
        resolutionAdapter = null
        qualityAdapter = null
        formatAdapter = null
        defaultFontAdapter = null
        autoSaveAdapter = null
        super.onDestroyView()
        _binding = null
    }
}

data class DefaultFontOption(
    val fontId: String,
    val fontName: String,
    var isSelected: Boolean = false
)

class DefaultFontOptionAdapter(
    private var items: List<DefaultFontOption>,
    private val onSelected: (DefaultFontOption) -> Unit
) : RecyclerView.Adapter<DefaultFontOptionAdapter.ViewHolder>() {

    fun updateList(newItems: List<DefaultFontOption>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = LayoutResolutionsItemPrefsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.fontName
        holder.binding.isPremium.visibility = View.GONE
        val check = if (item.isSelected) ContextCompat.getDrawable(holder.binding.root.context, R.drawable.ic_done) else null
        holder.binding.title.setCompoundDrawablesWithIntrinsicBounds(null, null, check, null)
        holder.binding.root.addPressEffect {
            onSelected(item)
        }
    }

    class ViewHolder(val binding: LayoutResolutionsItemPrefsBinding) : RecyclerView.ViewHolder(binding.root)
}

data class AutoSaveOption(
    val key: String,
    val label: String,
    var isSelected: Boolean = false
)

class AutoSaveOptionAdapter(
    private var items: List<AutoSaveOption>,
    private val onSelected: (AutoSaveOption) -> Unit
) : RecyclerView.Adapter<AutoSaveOptionAdapter.ViewHolder>() {

    fun updateList(newItems: List<AutoSaveOption>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = LayoutResolutionsItemPrefsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.label
        holder.binding.isPremium.visibility = View.GONE
        val check = if (item.isSelected) ContextCompat.getDrawable(holder.binding.root.context, R.drawable.ic_done) else null
        holder.binding.title.setCompoundDrawablesWithIntrinsicBounds(null, null, check, null)
        holder.binding.root.addPressEffect {
            onSelected(item)
        }
    }

    class ViewHolder(val binding: LayoutResolutionsItemPrefsBinding) : RecyclerView.ViewHolder(binding.root)
}