package com.webscare.urducanvas.ui.editor.panels.shape

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ShapesData
import com.webscare.urducanvas.databinding.FragmentShapesParentBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShapesParentFragment : Fragment() {

    private var _binding: FragmentShapesParentBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    // show/hide fragment cache — same pattern as ObjectsFragment / ImagesFragment
    private val fragmentCache = LinkedHashMap<String, Fragment>()
    private val tabs = mutableListOf<String>()
    private var currentTabIndex = 0
    private var currentQuery = ""
    private var lastShapesData: ShapesData? = null
    private var tabListenerAttached = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapesParentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()

        val initial = mainViewModel.shapesData.value
        tabs.clear()
        tabs.add(ShapesListFragment.VECTORS_TAB)
        tabs.addAll(initial.tabs.filter { it != ShapesListFragment.VECTORS_TAB })

        currentTabIndex = mainViewModel.lastShapesTabCategory
            ?.let { saved -> tabs.indexOf(saved).takeIf { it >= 0 } }
            ?: 0

        if (tabs.isNotEmpty()) {
            rebuildTabLayout(selectIndex = currentTabIndex)
            showTab(currentTabIndex)
        }

        observeShapesData()
    }

    override fun onDestroyView() {
        tabListenerAttached = false
        _binding = null
        super.onDestroyView()
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private fun showTab(position: Int) {
        if (position < 0 || position >= tabs.size) return
        val category = tabs[position]
        currentTabIndex = position
        mainViewModel.lastShapesTabCategory = category

        val target: Fragment = if (category == ShapesListFragment.VECTORS_TAB) {
            fragmentCache.getOrPut(category) { VectorsTabFragment() }
        } else {
            fragmentCache.getOrPut(category) {
                ShapesListFragment.newInstance(category, currentQuery).also { f ->
                    f.onFilterResult = { cat, count -> onTabFilterResult(cat, count > 0) }
                }
            }
        }

        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                for (f in childFragmentManager.fragments) {
                    if (f !== target && !f.isHidden) hide(f)
                }
                if (!target.isAdded) {
                    add(R.id.fragmentContainer, target, category)
                } else if (target.isHidden) {
                    show(target)
                }
            }
            .commitNow()
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private fun rebuildTabLayout(selectIndex: Int) {
        tabListenerAttached = false
        binding.tabLayout.removeAllTabs()

        if (tabs.isEmpty()) return

        for (category in tabs) {
            val tab = binding.tabLayout.newTab()
            val tabView = LayoutInflater.from(context)
                .inflate(R.layout.custom_tab, binding.tabLayout, false)
            tabView.findViewById<TextView>(R.id.tabTitle).text = category
            tab.customView = tabView
            binding.tabLayout.addTab(tab, false)
        }

        val safeIndex = selectIndex.coerceIn(0, tabs.lastIndex)
        binding.tabLayout.getTabAt(safeIndex)?.select()

        binding.tabLayout.post {
            binding.tabLayout.getTabAt(safeIndex)?.view?.let { tabView ->
                binding.tabLayout.scrollTo(
                    (tabView.left - binding.tabLayout.width / 2 + tabView.width / 2)
                        .coerceAtLeast(0), 0
                )
            }
        }

        attachTabListener()
    }

    private fun attachTabListener() {
        if (tabListenerAttached) return
        tabListenerAttached = true

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: return
                tab.view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
                showTab(pos)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.animate()
                    ?.scaleX(0.9f)?.scaleY(0.9f)
                    ?.setDuration(100)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ── Data observation ──────────────────────────────────────────────────────

    private fun observeShapesData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.shapesData.collect { data ->
                    onShapesDataChanged(data)
                }
            }
        }
    }

    private fun onShapesDataChanged(data: ShapesData) {
        if (_binding == null) return
        if (lastShapesData === data) return
        lastShapesData = data

        // API tabs — "Vectors" always first, hardcoded
        val newApiTabs = data.tabs.filter { it != ShapesListFragment.VECTORS_TAB }
        val newTabs = mutableListOf(ShapesListFragment.VECTORS_TAB) + newApiTabs

        if (newTabs != tabs) {
            val currentCategory = tabs.getOrNull(currentTabIndex)
            tabs.clear()
            tabs.addAll(newTabs)

            if (tabs.isEmpty()) return

            val newIndex = currentCategory
                ?.let { tabs.indexOf(it) }
                ?.takeIf { it >= 0 } ?: 0

            currentTabIndex = newIndex
            rebuildTabLayout(selectIndex = newIndex)
            showTab(newIndex)
        }

        // Notify all API tab fragments
        for ((cat, frag) in fragmentCache) {
            if (cat != ShapesListFragment.VECTORS_TAB && frag is ShapesListFragment) {
                frag.onNewData(data)
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun applySearch(query: String) {
        currentQuery = query

        for ((cat, frag) in fragmentCache) {
            if (cat != ShapesListFragment.VECTORS_TAB && frag is ShapesListFragment) {
                frag.updateFilter(query)
            }
        }

        if (query.isBlank()) {
            showAllTabs()
            return
        }

        val data = mainViewModel.shapesData.value
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return

        for (i in tabs.indices) {
            val category = tabs[i]
            val hasResults = when (category) {
                ShapesListFragment.VECTORS_TAB -> true // Vectors tab always visible
                else -> data.imagesByCategory[category].orEmpty().any { it.matchesQuery(query) }
            }
            tabStrip.getChildAt(i)?.isVisible = hasResults
        }

        jumpToFirstVisibleTab()
    }

    private fun onTabFilterResult(category: String, hasResults: Boolean) {
        if (_binding == null) return
        val i = tabs.indexOf(category).takeIf { it >= 0 } ?: return
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return
        val tabView = tabStrip.getChildAt(i) ?: return
        if (tabView.isVisible == hasResults) return
        tabView.isVisible = hasResults
        if (!hasResults && binding.tabLayout.selectedTabPosition == i) {
            jumpToFirstVisibleTab()
        }
    }

    private fun jumpToFirstVisibleTab() {
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in tabs.indices) {
            if (tabStrip.getChildAt(i)?.isVisible == true) {
                showTab(i)
                binding.tabLayout.getTabAt(i)?.select()
                return
            }
        }
    }

    private fun showAllTabs() {
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until tabStrip.childCount) tabStrip.getChildAt(i)?.isVisible = true
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {

        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.searchIcon.isVisible = true
                binding.searchBar.isVisible = false
            }
        }

        binding.searchBar.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBar.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch(binding.searchBar.text.toString())
                hideKeyboard()
                binding.searchBar.isVisible = false
                binding.searchIcon.isVisible = true
                true
            } else false
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null, null,
                    if (!s.isNullOrEmpty())
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
                    else null,
                    null
                )
                applySearch(s?.toString().orEmpty())
            }
        })

        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val dr = binding.searchBar.compoundDrawables[2]
                if (dr != null &&
                    event.x >= binding.searchBar.width -
                    binding.searchBar.paddingRight - dr.bounds.width()
                ) {
                    binding.searchBar.text.clear()
                    applySearch("")
                    hideKeyboard()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }
}