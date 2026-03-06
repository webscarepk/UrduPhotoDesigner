package com.webscare.urducanvas.ui.editor.panels.text.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.databinding.FragmentFontsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import dagger.hilt.android.AndroidEntryPoint
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

    // chosen category per language — populated from CanvasViewModel on view creation
    private val chosenCategoryByLang = mutableMapOf<String, String?>()

    // True while we are rebuilding the languages list from a DB emission,
    // so that pager callbacks don't fire competing deliverFilter calls.
    private var isRebuilding = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore map from persisted state so chosenCategoryByLang is ready
        // before the first localFonts emission builds the language list.
        val saved = canvasViewModel.getFontPanelState()
        if (saved.selectedCategory != null) {
            chosenCategoryByLang[saved.selectedLanguage] = saved.selectedCategory
        }

        setupRecyclerViews()
        initObservers()
    }

    private fun setupRecyclerViews() {
        languages = ArrayList()

        adapter = FontLanguagesAdapter(
            onLanguageExpanded = { lang, collapse ->
                if (isRebuilding) return@FontLanguagesAdapter
                if (collapse) {
                    // User tapped an already-expanded language to collapse it
                    collapseLanguage(lang)
                    deliverFilter(lang, chosenCategoryByLang[lang])
                } else {
                    // User expanded a new language
                    expandOnly(lang)
                    // Do NOT clear the saved category — user may have one saved
                    saveCurrentState()
                    deliverFilter(lang, chosenCategoryByLang[lang])
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
        binding.languages.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pagerAdapter = FontsPagerAdapter(this@FontsFragment, languages)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isRebuilding) return
                languages.getOrNull(position)?.let { row ->
                    expandOnly(row.name)
                    binding.languages.smoothScrollToPosition(position)
                    saveCurrentState()
                    // Deliver with the existing saved category for this language
                    deliverFilterSafe(row.name, chosenCategoryByLang[row.name])
                }
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
                                "urdu" -> 0
                                "english" -> 1
                                else -> 2
                            }
                        }.thenBy { entry -> entry.key.lowercase() }
                    )
                    .mapIndexed { idx, (lang, list) ->
                        val savedCatForLang = chosenCategoryByLang[lang]
                        val cats = list.map { it.font_category.ifBlank { "Uncategorized" } }
                            .distinct()
                            .sortedWith(categoryComparator(lang))
                            .map { catName ->
                                _root_ide_package_.com.webscare.urducanvas.data.model.FontCategory(
                                    name = catName,
                                    isSelected = catName.equals(savedCatForLang, true)
                                )
                            }

                        // Preserve expansion state from the live list (not from saved state,
                        // because the user may have changed it since last save).
                        val wasSelected = languages.firstOrNull { it.name == lang }?.is_selected ?: false

                        _root_ide_package_.com.webscare.urducanvas.data.model.FontLanguages(
                            id = idx + 1,
                            name = lang,
                            is_selected = wasSelected,
                            categories = cats
                        )
                    }

                val allSelected = languages.firstOrNull { it.name == "All" }?.is_selected ?: false
                val allRow = _root_ide_package_.com.webscare.urducanvas.data.model.FontLanguages(
                    id = 0,
                    name = "All",
                    is_selected = allSelected,
                    categories = emptyList()
                )

                val incoming = arrayListOf(allRow).apply { addAll(langRows) }

                val isFirstLoad = languages.isEmpty()
                languages.clear()
                languages.addAll(incoming)

                if (isFirstLoad) {
                    // First population after view creation — restore saved selection.
                    val saved = canvasViewModel.getFontPanelState()
                    val savedLang = saved.selectedLanguage
                    val savedCat = saved.selectedCategory

                    // Ensure map is populated
                    if (savedCat != null) chosenCategoryByLang[savedLang] = savedCat

                    val restored = languages.map { row ->
                        val shouldExpand = row.name == savedLang
                        val restoredCats = row.categories.map { cat ->
                            cat.copy(isSelected = savedCat != null && cat.name.equals(savedCat, true))
                        }
                        row.copy(is_selected = shouldExpand, categories = restoredCats)
                    }
                    languages.clear()
                    languages.addAll(restored)
                }

                adapter.submitList(ArrayList(languages))
                pagerAdapter.updateCategories(languages)

                // Move pager to the selected language page
                val targetIdx = languages.indexOfFirst { it.is_selected }
                    .let { if (it == -1) 0 else it }

                if (binding.viewPager.currentItem != targetIdx) {
                    // setCurrentItem will trigger onPageSelected → deliverFilterSafe
                    binding.viewPager.setCurrentItem(targetIdx, false)
                } else {
                    // Pager is already on the right page but onPageSelected won't fire,
                    // so we must push the filter ourselves — deferred so the pager's
                    // fragment has time to attach.
                    val langName = languages.getOrNull(targetIdx)?.name ?: "All"
                    deliverFilterSafe(langName, chosenCategoryByLang[langName])
                }

                isRebuilding = false
            }
        }
    }

    /**
     * Posts deliverFilter on the next frame so ViewPager2 has time to create/attach
     * the target FontsListFragment before we try to call applyFilter on it.
     * Falls back to a second post if the fragment still isn't ready (e.g. first cold start).
     */
    private fun deliverFilterSafe(lang: String, category: String?) {
        // First attempt after current frame
        binding.viewPager.post {
            if (!tryDeliverFilter(lang, category)) {
                // Fragment not attached yet — try once more on the next frame
                binding.viewPager.post {
                    tryDeliverFilter(lang, category)
                }
            }
        }
    }

    /** Returns true if the fragment was found and filter was delivered. */
    private fun tryDeliverFilter(lang: String, category: String?): Boolean {
        val current = binding.viewPager.currentItem
        val tag = "f$current"
        val fragment = childFragmentManager.findFragmentByTag(tag) as? FontsListFragment
        fragment?.applyFilter(language = lang, category = category)
        return fragment != null
    }

    // Keep the synchronous version for immediate user-driven interactions
    // (category tap, language tap) where the fragment is guaranteed to exist.
    private fun deliverFilter(lang: String, category: String?) {
        val current = binding.viewPager.currentItem
        val tag = "f$current"
        (childFragmentManager.findFragmentByTag(tag) as? FontsListFragment)
            ?.applyFilter(language = lang, category = category)
    }

    private fun saveCurrentState() {
        val activeLang = languages.firstOrNull { it.is_selected }?.name ?: "All"
        val activeCat = chosenCategoryByLang[activeLang]
        canvasViewModel.saveFontPanelState(language = activeLang, category = activeCat)
    }

    private fun collapseLanguage(lang: String) {
        val updated = languages.map { it.copy(is_selected = false) }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    private fun expandOnly(lang: String) {
        val updated = languages.map {
            if (it.name == lang) it.copy(is_selected = true)
            else it.copy(is_selected = false)
        }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    private fun markCategoryUi(lang: String, categoryOrNull: String?) {
        val pos = languages.indexOfFirst { it.name == lang }
        if (pos == -1) return
        val row = languages[pos]
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

    /**
     * Returns a comparator that sorts category names in the predefined display order
     * for the given language. Categories not in the list are appended alphabetically at the end.
     */
    private fun categoryComparator(language: String): Comparator<String> {
        val order = when (language.lowercase()) {
            "urdu" -> listOf(
                "Nastaleeq", "Thin", "Bold", "Round", "Modern", "Regular",
                "Wide", "Condensed", "Italic", "Decorated", "Handwriting",
                "Outline", "Dotted", "Tech", "Quran"
            )
            "english" -> listOf(
                "Regular", "Bold", "Thin", "Script", "Rounded", "Wide"
            )
            else -> emptyList()
        }

        val indexMap = order.mapIndexed { idx, name -> name.lowercase() to idx }.toMap()

        return Comparator { a, b ->
            val idxA = indexMap[a.lowercase()]
            val idxB = indexMap[b.lowercase()]
            when {
                idxA != null && idxB != null -> idxA - idxB  // both in list → follow order
                idxA != null -> -1                            // a is in list, b is not → a first
                idxB != null -> 1                             // b is in list, a is not → b first
                else -> a.compareTo(b, ignoreCase = true)     // neither in list → alphabetical
            }
        }
    }

    companion object {
        fun newInstance(): FontsFragment = FontsFragment()
    }
}