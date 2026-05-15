package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.databinding.FragmentFontsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FontsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentFontsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()

    private lateinit var adapter: FontLanguagesAdapter
    private lateinit var languages: ArrayList<com.webscare.urducanvas.data.model.FontLanguages>
    private lateinit var pagerAdapter: FontsPagerAdapter

    private val chosenCategoryByLang = mutableMapOf<String, String?>()
    private var isRebuilding = false
    private var isPanelExpanded = false

    // ── The language that is "active" in the left panel ──────────────────────
    // Collapsed: whichever the user last tapped / ViewPager scrolled to
    // Expanded : single-select radio; defaults to Urdu (or first available)
    private var activeLangInExpanded: String = "Urdu"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val saved = canvasViewModel.getFontPanelState()
        if (saved.selectedCategory != null) {
            chosenCategoryByLang[saved.selectedLanguage] = saved.selectedCategory
        }

        setupRecyclerViews()
        initObservers()
        observePanelExpanded()
    }

    // ── Panel expansion ───────────────────────────────────────────────────────

    private fun observePanelExpanded() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.FONTS }
                    .collect { expanded -> applyExpansion(expanded) }
            }
        }
    }

    private fun applyExpansion(expanded: Boolean) {
        if (isPanelExpanded == expanded) return
        isPanelExpanded = expanded
        adapter.isExpandedMode = expanded          // ← tell adapter which mode we're in

        if (_binding == null || languages.isEmpty()) return

        if (expanded) {
            binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
            expandWithRadioDefault()
        } else {
            binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
            collapseToSingleLanguage()
        }

        for (i in languages.indices) {
            (childFragmentManager.findFragmentByTag("f$i") as? FontsListFragment)
                ?.onPanelExpanded(expanded)
        }
    }

    // ── Expanded: single-select, default = Urdu (or first non-All lang) ──────

    private fun expandWithRadioDefault() {
        // Pick the default: prefer "Urdu", fall back to first real language, then "All"
        val preferred = languages.firstOrNull {
            it.name.equals("Urdu", ignoreCase = true)
        }?.name
            ?: languages.firstOrNull { it.name != "All" && it.name != "Imported" }?.name
            ?: "All"

        activeLangInExpanded = preferred

        val updated = languages.map { it.copy(is_selected = it.name == preferred) }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))

        val targetIdx = languages.indexOfFirst { it.is_selected }.coerceAtLeast(0)
        if (binding.viewPager.currentItem != targetIdx) {
            binding.viewPager.setCurrentItem(targetIdx, false)
        }
        deliverFilterSafe(preferred, chosenCategoryByLang[preferred])
    }

    private fun collapseToSingleLanguage() {
        val saved = canvasViewModel.getFontPanelState()
        val activeLang = saved.selectedLanguage.ifBlank { "All" }
        val updated = languages.map { it.copy(is_selected = it.name == activeLang) }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))

        val targetIdx = languages.indexOfFirst { it.is_selected }.coerceAtLeast(0)
        if (binding.viewPager.currentItem != targetIdx) {
            binding.viewPager.setCurrentItem(targetIdx, false)
        }
        deliverFilterSafe(activeLang, chosenCategoryByLang[activeLang])
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerViews() {
        languages = ArrayList()

        adapter = FontLanguagesAdapter(
            onLanguageExpanded = { lang, collapse ->
                if (isRebuilding) return@FontLanguagesAdapter
                if (isPanelExpanded) {
                    // Expanded mode: radio-select the tapped language
                    activeLangInExpanded = lang
                    val targetIdx = languages.indexOfFirst { it.name == lang }
                    if (targetIdx >= 0 && binding.viewPager.currentItem != targetIdx) {
                        binding.viewPager.setCurrentItem(targetIdx, false)
                    }
                    saveCurrentState()
                    deliverFilter(lang, chosenCategoryByLang[lang])
                } else {
                    // Collapsed mode: original toggle behaviour
                    if (collapse) {
                        collapseLanguage(lang)
                        deliverFilter(lang, chosenCategoryByLang[lang])
                    } else {
                        expandOnly(lang)
                        saveCurrentState()
                        deliverFilter(lang, chosenCategoryByLang[lang])
                    }
                }
            },
            onCategorySelected = { lang, category ->
                if (isRebuilding) return@FontLanguagesAdapter
                chosenCategoryByLang[lang] = category
                markCategoryUi(lang, category)
                saveCurrentState()
                deliverFilter(lang, category)
            }
        )
        adapter.isExpandedMode = isPanelExpanded
        binding.languages.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pagerAdapter = FontsPagerAdapter(this@FontsFragment, languages)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isRebuilding) return
                languages.getOrNull(position)?.let { row ->
                    if (isPanelExpanded) {
                        // In expanded mode, sync left panel selection to pager
                        if (activeLangInExpanded != row.name) {
                            activeLangInExpanded = row.name
                            val updated = languages.map { it.copy(is_selected = it.name == row.name) }
                            languages.clear(); languages.addAll(updated)
                            adapter.submitList(ArrayList(languages))
                        }
                    } else {
                        expandOnly(row.name)
                    }
                    binding.languages.smoothScrollToPosition(position)
                    saveCurrentState()
                    deliverFilterSafe(row.name, chosenCategoryByLang[row.name])
                }
                val frag = childFragmentManager.findFragmentByTag("f$position") as? FontsListFragment
                frag?.onPanelExpanded(isPanelExpanded)
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                isRebuilding = true

                val grouped = fonts.groupBy { it.font_language.ifBlank { "Unknown" } }

                val langRows = grouped.entries
                    .sortedWith(
                        compareBy<Map.Entry<String, List<com.webscare.urducanvas.data.model.FontEntity>>> { entry ->
                            when (entry.key.lowercase()) {
                                "urdu"    -> 0
                                "english" -> 1
                                else      -> 2
                            }
                        }.thenBy { entry -> entry.key.lowercase() }
                    )
                    .mapIndexed { idx, (lang, list) ->
                        val savedCatForLang = chosenCategoryByLang[lang]
                        val cats = list
                            .map { it.font_category.ifBlank { "Uncategorized" } }
                            .distinct()
                            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                            .map { catName ->
                                com.webscare.urducanvas.data.model.FontCategory(
                                    name       = catName,
                                    isSelected = catName.equals(savedCatForLang, true)
                                )
                            }

                        val wasSelected = if (isPanelExpanded) {
                            lang == activeLangInExpanded
                        } else {
                            languages.firstOrNull { it.name == lang }?.is_selected ?: false
                        }

                        com.webscare.urducanvas.data.model.FontLanguages(
                            id          = idx + 1,
                            name        = lang,
                            is_selected = wasSelected,
                            categories  = cats
                        )
                    }

                val allSelected = if (isPanelExpanded) {
                    "All" == activeLangInExpanded
                } else {
                    languages.firstOrNull { it.name == "All" }?.is_selected ?: false
                }

                val allRow = com.webscare.urducanvas.data.model.FontLanguages(
                    id          = 0,
                    name        = "All",
                    is_selected = allSelected,
                    categories  = emptyList()
                )

                val incoming = arrayListOf(allRow).apply { addAll(langRows) }

                val isFirstLoad = languages.isEmpty()
                languages.clear()
                languages.addAll(incoming)

                if (isFirstLoad) {
                    val saved     = canvasViewModel.getFontPanelState()
                    val savedLang = saved.selectedLanguage
                    val savedCat  = saved.selectedCategory

                    if (savedCat != null) chosenCategoryByLang[savedLang] = savedCat

                    // On first load in expanded mode, default to Urdu
                    val expandedDefault = if (isPanelExpanded) {
                        languages.firstOrNull { it.name.equals("Urdu", true) }?.name
                            ?: languages.firstOrNull { it.name != "All" }?.name
                            ?: "All"
                    } else null

                    val restored = languages.map { row ->
                        val shouldExpand = when {
                            isPanelExpanded -> row.name == (expandedDefault ?: activeLangInExpanded)
                            else            -> row.name == savedLang
                        }
                        val restoredCats = row.categories.map { cat ->
                            cat.copy(isSelected = savedCat != null && cat.name.equals(savedCat, true))
                        }
                        row.copy(is_selected = shouldExpand, categories = restoredCats)
                    }
                    if (expandedDefault != null) activeLangInExpanded = expandedDefault
                    languages.clear()
                    languages.addAll(restored)
                }

                adapter.submitList(ArrayList(languages))
                pagerAdapter.updateCategories(languages)

                val targetIdx = languages.indexOfFirst { it.is_selected }.coerceAtLeast(0)
                if (binding.viewPager.currentItem != targetIdx) {
                    binding.viewPager.setCurrentItem(targetIdx, false)
                } else {
                    val langName = languages.getOrNull(targetIdx)?.name ?: "All"
                    deliverFilterSafe(langName, chosenCategoryByLang[langName])
                }

                isRebuilding = false

                if (isPanelExpanded) {
                    expandWithRadioDefault()
                    for (i in languages.indices) {
                        (childFragmentManager.findFragmentByTag("f$i") as? FontsListFragment)
                            ?.onPanelExpanded(true)
                    }
                }
            }
        }
    }

    // ── Filter delivery ───────────────────────────────────────────────────────

    private fun deliverFilterSafe(lang: String, category: String?) {
        binding.viewPager.post {
            if (!tryDeliverFilter(lang, category)) {
                binding.viewPager.post { tryDeliverFilter(lang, category) }
            }
        }
    }

    private fun tryDeliverFilter(lang: String, category: String?): Boolean {
        val current  = binding.viewPager.currentItem
        val fragment = childFragmentManager.findFragmentByTag("f$current") as? FontsListFragment
        fragment?.applyFilter(language = lang, category = category)
        return fragment != null
    }

    private fun deliverFilter(lang: String, category: String?) {
        val current = binding.viewPager.currentItem
        (childFragmentManager.findFragmentByTag("f$current") as? FontsListFragment)
            ?.applyFilter(language = lang, category = category)
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private fun saveCurrentState() {
        val activeLang = if (isPanelExpanded) activeLangInExpanded
        else languages.firstOrNull { it.is_selected }?.name ?: "All"
        val activeCat  = chosenCategoryByLang[activeLang]
        canvasViewModel.saveFontPanelState(language = activeLang, category = activeCat)
    }

    private fun collapseLanguage(lang: String) {
        val updated = languages.map { it.copy(is_selected = false) }
        languages.clear(); languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    private fun expandOnly(lang: String) {
        val updated = languages.map {
            if (it.name == lang) it.copy(is_selected = true)
            else it.copy(is_selected = false)
        }
        languages.clear(); languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    private fun markCategoryUi(lang: String, categoryOrNull: String?) {
        val pos = languages.indexOfFirst { it.name == lang }
        if (pos == -1) return
        val row     = languages[pos]
        val newCats = row.categories.map {
            it.copy(isSelected = it.name.equals(categoryOrNull, true))
        }
        languages[pos] = row.copy(categories = newCats)
        adapter.submitList(ArrayList(languages))
    }

    override fun onDestroyView() {
        saveCurrentState()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FontsFragment = FontsFragment()
    }
}