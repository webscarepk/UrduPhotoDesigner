package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.data.model.FontCategory
import com.webscare.urducanvas.data.model.FontLanguages
import com.webscare.urducanvas.databinding.FragmentFontsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FontsFragment : Fragment() {

    private var _binding: FragmentFontsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var languagesAdapter: FontLanguagesAdapter
    private lateinit var pagerAdapter: FontsPagerAdapter

    private var standaloneMode: Boolean = false
    private var selectedLanguage: String = "All"

    // Pending category to apply once the pager settles on the correct page
    private var pendingCategory: String? = null
    private var pendingLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        standaloneMode = arguments?.getBoolean(ARG_STANDALONE_MODE, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLanguagesRecyclerView()
        setupViewPager()
        observeExpansion()
        observeLocalFontsForLanguages()
        observeFontImported()
    }

    // ── Language side list ────────────────────────────────────────────────────

    private fun setupLanguagesRecyclerView() {
        languagesAdapter = FontLanguagesAdapter(
            onLanguageExpanded = { language, collapse ->
                val position = pagerAdapter.categories.indexOfFirst { it.name == language }
                if (position >= 0) {
                    selectedLanguage = language
                    // No animation — instant so currentItem is immediately correct
                    binding.viewPager.setCurrentItem(position, false)
                }
            },
            onCategorySelected = { language, category ->
                val position = pagerAdapter.categories.indexOfFirst { it.name == language }
                if (position >= 0) {
                    selectedLanguage = language

                    if (binding.viewPager.currentItem == position) {
                        // Already on the right page — apply filter directly, no pager move needed
                        applyFilterToPage(position, language, category)
                    } else {
                        // Store as pending — will be applied in onPageSelected once pager settles
                        pendingLanguage  = language
                        pendingCategory  = category
                        // No animation — instant so onPageSelected fires synchronously
                        binding.viewPager.setCurrentItem(position, false)
                    }
                }
            }
        )

        binding.languages.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.languages.adapter = languagesAdapter
    }

    // ── ViewPager ─────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        pagerAdapter = FontsPagerAdapter(this, emptyList(), standaloneMode)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Keep language side-list in sync
                val currentCategories = pagerAdapter.categories
                val updated = currentCategories.mapIndexed { i, lang ->
                    lang.copy(is_selected = i == position)
                }
                selectedLanguage = currentCategories.getOrNull(position)?.name ?: "All"
                languagesAdapter.submitList(updated)

                // Apply any pending category filter now that the page has settled
                val lang = pendingLanguage
                val cat  = pendingCategory
                if (lang != null) {
                    applyFilterToPage(position, lang, cat)
                    pendingLanguage = null
                    pendingCategory = null
                }
            }
        })
    }

    // ── Apply filter to the FontsListFragment at the given pager position ─────

    private fun applyFilterToPage(position: Int, language: String, category: String?) {
        // FragmentStateAdapter tags fragments as "f{itemId}" where itemId = getItemId(position)
        // FontsPagerAdapter.getItemId returns categories[position].id.toLong()
        val itemId   = pagerAdapter.categories.getOrNull(position)?.id?.toLong() ?: return
        val tag      = "f$itemId"
        val fragment = childFragmentManager.findFragmentByTag(tag) as? FontsListFragment

        if (fragment != null) {
            fragment.applyFilter(language, category)
        } else {
            // Fragment not yet created — retry after ViewPager2 inflates it
            binding.viewPager.post {
                if (_binding == null) return@post
                val f = childFragmentManager.findFragmentByTag("f$itemId") as? FontsListFragment
                f?.applyFilter(language, category)
            }
        }
    }

    // ── Expansion observer ────────────────────────────────────────────────────

    private fun observeExpansion() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.FONTS }
                    .collect { expanded ->
                        languagesAdapter.isExpandedMode = expanded
                        languagesAdapter.notifyDataSetChanged()
                    }
            }
        }
    }

    // ── Derive FontLanguages from localFonts ──────────────────────────────────

    private var pendingSelectImported = false

    private fun observeFontImported() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.fontImportedEvent.collect {
                    selectImportedCategory()
                }
            }
        }
    }

    private fun selectImportedCategory() {
        val categories = pagerAdapter.categories
        val position = categories.indexOfFirst { it.name.equals("Imported", ignoreCase = true) }
        if (position >= 0) {
            selectedLanguage = "Imported"
            binding.viewPager.setCurrentItem(position, false)
            val updated = categories.mapIndexed { i, lang ->
                lang.copy(is_selected = i == position)
            }
            languagesAdapter.submitList(updated)
        } else {
            pendingSelectImported = true
        }
    }

    private fun observeLocalFontsForLanguages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    mainViewModel.localFonts,
                    mainViewModel.recentFonts
                ) { fonts, _ -> fonts }
                    .collect { fonts ->
                        val languages = buildFontLanguages(fonts)
                        languagesAdapter.submitList(languages)
                        pagerAdapter.updateCategories(languages)
                        if (pendingSelectImported) {
                            val pos = languages.indexOfFirst { it.name.equals("Imported", ignoreCase = true) }
                            if (pos >= 0) {
                                pendingSelectImported = false
                                selectedLanguage = "Imported"
                                binding.viewPager.setCurrentItem(pos, false)
                            }
                        }
                    }
            }
        }
    }

    private fun buildFontLanguages(
        fonts: List<com.webscare.urducanvas.data.model.FontEntity>
    ): List<FontLanguages> {
        val byLanguage = fonts
            .filter { it.font_language.isNotBlank() }
            .groupBy { it.font_language.trim() }

        val result = mutableListOf<FontLanguages>()

        result.add(
            FontLanguages(
                id         = 0,
                name       = "All",
                categories = emptyList(),
                is_selected = selectedLanguage == "All"
            )
        )

        // "Recents" tab — only shown when there are recently used fonts
        val recentFonts = mainViewModel.recentFonts.value
        if (recentFonts.isNotEmpty()) {
            result.add(
                FontLanguages(
                    id          = -1,
                    name        = "Recents",
                    categories  = emptyList(),
                    is_selected = selectedLanguage == "Recents"
                )
            )
        }

        // Preferred order: Urdu first, then English, then rest alphabetically
        val preferredOrder  = listOf("Urdu", "English")
        val allLangs        = byLanguage.keys.filter { !it.equals("Imported", ignoreCase = true) }
        val preferred       = preferredOrder.filter { p -> allLangs.any { it.equals(p, ignoreCase = true) } }
        val rest            = allLangs.filter { l -> preferredOrder.none { it.equals(l, ignoreCase = true) } }.sorted()
        val orderedLanguages = preferred + rest

        orderedLanguages.forEachIndexed { index, langName ->
            val fontsForLang = byLanguage[langName] ?: return@forEachIndexed
            val catNames = fontsForLang
                .map { it.font_category.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val categories = if (catNames.isNotEmpty()) {
                (listOf("All") + catNames).mapIndexed { catIndex, catName ->
                    FontCategory(id = catIndex, name = catName, isSelected = catIndex == 0)
                }
            } else {
                emptyList()
            }

            result.add(
                FontLanguages(
                    id          = index + 1,
                    name        = langName,
                    categories  = categories,
                    is_selected = selectedLanguage == langName
                )
            )
        }

        if (byLanguage.keys.any { it.equals("Imported", ignoreCase = true) }) {
            result.add(
                FontLanguages(
                    id          = result.size,
                    name        = "Imported",
                    categories  = emptyList(),
                    is_selected = selectedLanguage == "Imported"
                )
            )
        }

        return result
    }

    override fun onDestroyView() {
        _binding?.languages?.adapter = null
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STANDALONE_MODE = "standalone_mode"

        fun newInstance(standaloneMode: Boolean = false): FontsFragment {
            return FontsFragment().also {
                it.arguments = Bundle().apply {
                    putBoolean(ARG_STANDALONE_MODE, standaloneMode)
                }
            }
        }
    }
}