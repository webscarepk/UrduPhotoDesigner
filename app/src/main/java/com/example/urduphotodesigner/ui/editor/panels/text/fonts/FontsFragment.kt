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

        // IMPORTANT: adapter now has TWO callbacks.
        adapter = FontLanguagesAdapter(
            onLanguageExpanded = { lang -> onLanguageClicked(lang) },
            onCategorySelected = { lang, category -> onCategorySelected(lang, category) }
        )
        binding.languages.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pagerAdapter = FontsPagerAdapter(this@FontsFragment, languages)
        binding.viewPager.adapter = pagerAdapter

        // Swiping the pager should only sync the expanded language row.
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val row = languages.getOrNull(position) ?: return
                // expand this language, collapse others (no category selection here)
                expandOnly(row.name)
                binding.languages.smoothScrollToPosition(position)
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                // Group by language, then build categories per language
                val grouped = fonts.groupBy { it.font_language.ifBlank { "Unknown" } }

                val incoming = grouped.entries
                    .sortedBy { it.key.lowercase() }
                    .mapIndexed { index, (lang, list) ->
                        // Build categories: "All" + distinct font_category
                        val catNames = buildList {
                            add("All")
                            addAll(list.map { it.font_category.ifBlank { "Uncategorized" } }
                                .distinct()
                                .sorted())
                        }

                        // keep previous UI state if exists
                        val old = languages.firstOrNull { it.name == lang }
                        FontLanguages(
                            id = index,
                            name = lang,
                            is_selected = old?.is_selected ?: (index == 0),
                            categories = catNames.map { name ->
                                val wasSelected = old?.categories?.any { it.name == name && it.isSelected } == true
                                FontCategory(
                                    name = name,
                                    isSelected = when {
                                        wasSelected -> true
                                        old == null && name.equals("All", true) -> true
                                        else -> false
                                    }
                                )
                            }
                        )
                    }

                if (incoming != languages) {
                    languages.clear()
                    languages.addAll(incoming)
                    adapter.submitList(ArrayList(languages))

                    // Pager pages = languages (not categories)
                    pagerAdapter.updateCategories(languages)

                    // Ensure pager is on expanded language
                    val expandedIdx = languages.indexOfFirst { it.is_selected }.let { if (it == -1) 0 else it }
                    if (binding.viewPager.currentItem != expandedIdx) {
                        binding.viewPager.setCurrentItem(expandedIdx, false)
                    }
                }
            }
        }
    }

    /** LANGUAGE CLICK: expand/collapse only. No category selection, no pager moves except syncing the expanded row. */
    private fun onLanguageClicked(lang: String) = expandOnly(lang)

    private fun expandOnly(lang: String) {
        val updated = languages.map {
            it.copy(is_selected = it.name == lang)
        }
        languages.clear()
        languages.addAll(updated)
        adapter.submitList(ArrayList(languages))

        // Move pager to this language page
        val idx = languages.indexOfFirst { it.name == lang }
        if (idx >= 0 && binding.viewPager.currentItem != idx) {
            binding.viewPager.setCurrentItem(idx, true)
        }
    }

    /** CATEGORY CLICK: select category within the already-expanded language, then move pager to that language page. */
    private fun onCategorySelected(lang: String, category: String) {
        val langIdx = languages.indexOfFirst { it.name == lang }
        if (langIdx == -1) return

        val row = languages[langIdx]
        val newCats = row.categories.map { it.copy(isSelected = it.name.equals(category, true)) }

        val updatedRow = row.copy(categories = newCats, is_selected = true)
        languages[langIdx] = updatedRow
        adapter.submitList(ArrayList(languages))

        if (binding.viewPager.currentItem != langIdx) {
            binding.viewPager.setCurrentItem(langIdx, true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FontsFragment {
            return FontsFragment()
        }
    }
}