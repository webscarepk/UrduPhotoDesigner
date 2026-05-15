package com.webscare.urducanvas.ui.editor.panels.text

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
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentTextBinding
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    private var tabs = emptyList<String>()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var currentTabPosition = 0
    private var isPanelExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        attachDragHandleSwipe()
        initObservers()
        observePanelExpanded()
    }

    // ── Drag handle ───────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandleSwipe() {
        val thresholdPx = 30 * resources.displayMetrics.density
        var startY = 0f

        binding.dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY
                    if (abs(dy) >= thresholdPx) {
                        when {
                            dy > 0 && !mainViewModel.isPanelExpanded(PanelType.FONTS) ->
                                mainViewModel.togglePanel(PanelType.FONTS)
                            dy < 0 && mainViewModel.isPanelExpanded(PanelType.FONTS) ->
                                mainViewModel.togglePanel(PanelType.FONTS)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    // ── Panel expansion ───────────────────────────────────────────────────────

    private fun observePanelExpanded() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.FONTS }
                    .collect { expanded ->
                        isPanelExpanded = expanded
                        applyExpandedUi(expanded)
                        // FontsFragment self-observes mainViewModel.expandedPanel —
                        // no need to call fontsFragment()?.onPanelExpanded(expanded) here
                    }
            }
        }
    }

    private fun applyExpandedUi(expanded: Boolean) {
        // FIX 1: panelTitle and closePanel only visible in expanded state
        binding.panelTitle.isVisible = expanded
        binding.closePanel.isVisible = expanded

        if (expanded) {
            binding.searchIcon.isVisible = false
            // Search row: visible only on Font tab
            binding.searchRow.isVisible = (currentTabPosition == 0)
        } else {
            // Collapsed: searchIcon visible on Font tab
            binding.searchIcon.isVisible = (currentTabPosition == 0)
            binding.searchRow.isVisible  = false
            // Clear search state
            binding.searchBar.text?.clear()
            mainViewModel.setQuery("")
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun initObservers() {
        viewModel.openAppearanceTab.observe(viewLifecycleOwner) { openTab ->
            if (isAdded) {
                binding.viewPager.post {
                    binding.viewPager.currentItem = if (openTab == true) 1 else 0
                }
            }
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        tabs = listOf("Font", "Appearance", "Format")

        val adapter = TextPagerAdapter(requireActivity().supportFragmentManager, lifecycle, tabs)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTabPosition = position
                val isOnFontTab = (position == 0)

                if (isPanelExpanded) {
                    binding.searchIcon.isVisible = false
                    binding.searchRow.isVisible  = isOnFontTab
                } else {
                    binding.searchIcon.isVisible = isOnFontTab
                    binding.searchRow.isVisible  = false
                }

                if (!isOnFontTab) {
                    binding.searchBar.text?.clear()
                    mainViewModel.setQuery("")
                }
            }
        })

        // closePanel is now only visible when expanded, but wiring stays the same
        binding.closePanel.addPressEffect {
            mainViewModel.collapsePanel()
        }

        binding.addText.addPressEffect {
            viewModel.addText(requireActivity().getString(R.string.dummyText), requireActivity())
        }
        binding.addFont.addPressEffect {
            ImportedFontsBottomSheet.newInstance()
                .show(childFragmentManager, ImportedFontsBottomSheet.TAG)
        }

        // Collapsed: search icon opens inline search bar
        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible  = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            showKeyboard(binding.searchBar)
        }

        setupSearchBar()
        setupViewPagerSwipeExpand()
    }

    private fun setupSearchBar() {
        binding.searchBar.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBar.setRawInputType(InputType.TYPE_CLASS_TEXT)
        binding.searchBar.setImeActionLabel("🔍", EditorInfo.IME_ACTION_SEARCH)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mainViewModel.setQuery(binding.searchBar.text.toString())
                hideKeyboard()
                if (!isPanelExpanded) {
                    binding.searchBar.isVisible  = false
                    binding.searchIcon.isVisible = true
                }
                true
            } else false
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null, null,
                    if (s?.isNotEmpty() == true)
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
                    else null, null
                )
                mainViewModel.setQuery(s?.toString().orEmpty())
            }
        })

        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val dr = binding.searchBar.compoundDrawables[2]
                if (dr != null && event.x >= binding.searchBar.width -
                    binding.searchBar.paddingRight - dr.bounds.width()
                ) {
                    binding.searchBar.text.clear()
                    mainViewModel.setQuery("")
                    hideKeyboard()
                    binding.searchBar.clearFocus()
                    if (!isPanelExpanded) {
                        binding.searchBar.isVisible  = false
                        binding.searchIcon.isVisible = true
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isPanelExpanded) {
                binding.searchIcon.isVisible = (currentTabPosition == 0)
                binding.searchBar.isVisible  = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViewPagerSwipeExpand() {
        val thresholdPx = 40 * resources.displayMetrics.density
        var startY = 0f; var startX = 0f

        binding.viewPager.setOnTouchListener { _, event ->
            if (isPanelExpanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; startX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val dy = startY - event.rawY
                    val dx = abs(startX - event.rawX)
                    if (dy > thresholdPx && dy > dx * 1.5f) {
                        mainViewModel.togglePanel(PanelType.FONTS); true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun showKeyboard(v: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.searchBar.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}