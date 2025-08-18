package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.data.model.FontLanguages
import com.example.urduphotodesigner.databinding.FragmentFontsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FontsFragment : Fragment() {
    private var _binding: FragmentFontsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FontLanguagesAdapter
    private lateinit var languages: ArrayList<FontLanguages>
    private lateinit var pagerAdapter: FontsPagerAdapter

    // keep track of chosen category per language
    private val chosenCategoryByLang = mutableMapOf<String, String?>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
    }

    private fun setupRecyclerViews() {
        languages = ArrayList()

        adapter = FontLanguagesAdapter(
            onLanguageExpanded = { lang, collapse ->
                if (collapse) {
                    collapseLanguage(lang)
                    deliverFilter(lang, chosenCategoryByLang[lang]) // still deliver current filter
                } else {
                    expandOnly(lang)
                    deliverFilter(lang, chosenCategoryByLang[lang])
                }
            },
            onCategorySelected = { lang, category ->
                chosenCategoryByLang[lang] = category // keep null when none
                markCategoryUi(lang, chosenCategoryByLang[lang])
                deliverFilter(lang, chosenCategoryByLang[lang])
            }
        )
        binding.languages.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pagerAdapter = FontsPagerAdapter(this@FontsFragment, languages)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                languages.getOrNull(position)?.let { row ->
                    expandOnly(row.name)
                    binding.languages.smoothScrollToPosition(position)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                val grouped = fonts.groupBy { it.font_language.ifBlank { "Unknown" } }

                val langRows = grouped.entries
                    .sortedBy { it.key.lowercase() }
                    .mapIndexed { idx, (lang, list) ->

                        val cats = list.map { it.font_category.ifBlank { "Uncategorized" } }
                            .distinct()
                            .sorted()
                            .map { catName ->
                                FontCategory(
                                    name = catName,
                                    isSelected = (chosenCategoryByLang[lang] == catName)
                                )
                            }

                        val wasSelected = languages.firstOrNull { it.name == lang }?.is_selected ?: false

                        FontLanguages(
                            id = idx + 1,
                            name = lang,
                            is_selected = wasSelected,
                            categories = cats
                        )
                    }

                // Special "All languages" row
                val allSelected = languages.firstOrNull { it.name == "All" }?.is_selected ?: true
                val allRow = FontLanguages(
                    id = 0,
                    name = "All",
                    is_selected = allSelected,
                    categories = emptyList() // no categories under All
                )

                val incoming = arrayListOf(allRow).apply { addAll(langRows) }

                languages.clear()
                languages.addAll(incoming)
                adapter.submitList(ArrayList(languages))
                pagerAdapter.updateCategories(languages)

                // ensure pager shows selected language
                val idx = languages.indexOfFirst { it.is_selected }.let { if (it == -1) 0 else it }
                if (binding.viewPager.currentItem != idx) {
                    binding.viewPager.setCurrentItem(idx, false)
                }

                // push current filter to page
                val currentLang = languages.getOrNull(idx)?.name ?: "All"
                deliverFilter(currentLang, chosenCategoryByLang[currentLang])
            }
        }
    }

    private fun collapseLanguage(lang: String) {
        val updated = languages.map { it.copy(is_selected = false) }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    /** Make only one language selected/expanded */
    private fun expandOnly(lang: String) {
        val updated = languages.map { it.copy(is_selected = it.name == lang) }
        languages.clear(); languages.addAll(updated)
        adapter.submitList(ArrayList(languages))
    }

    /** Update category selection UI for one language row */
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

    /** Deliver the current (lang, categoryOrNull) filter to active FontsListFragment */
    private fun deliverFilter(lang: String, categoryOrNull: String?) {
        val current = binding.viewPager.currentItem
        val tag = "f$current" // ViewPager2 fragment tag convention
        (childFragmentManager.findFragmentByTag(tag) as? FontsListFragment)
            ?.applyFilter(language = lang, category = categoryOrNull)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FontsFragment = FontsFragment()
    }
}
