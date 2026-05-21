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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentTextAdjustmentsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TextAdjustmentsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentTextAdjustmentsBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private val tabs = listOf("Font", "Appearance", "Format")
    private lateinit var adapter: TextAdjustmentsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = null
        setEvents()
    }

    private fun setEvents() {
        // childFragmentManager so FontsFragment can find FontsListFragment
        // via childFragmentManager tag lookup inside notifyVisiblePageFilter()
        adapter = TextAdjustmentsPagerAdapter(
            childFragmentManager,
            lifecycle,
            tabs
        )
        adapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        setupTabLayout()
        setupSearchBar()

        binding.back.addPressEffect {
            hideKeyboard()
            findNavController().navigateUp()
        }

        viewModel.openAppearanceTab.observe(viewLifecycleOwner) { openAppearance ->
            if (!isAdded || _binding == null) return@observe
            binding.viewPager.post {
                if (_binding == null) return@post
                val target = if (openAppearance == true) 1 else 0
                binding.viewPager.setCurrentItem(target, false)
                // Reset search to icon state when landing on non-Font tab
                if (target != 0) collapseSearch()
            }
        }
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private fun setupTabLayout() {
        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }
        mediator?.attach()

        binding.tabLayout.viewTreeObserver.addOnGlobalLayoutListener {
            if (isAdded && _binding != null) {
                for (i in 0 until binding.tabLayout.tabCount) {
                    val tabView = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)
                    tabView?.scaleX = 0.9f
                    tabView?.scaleY = 0.9f
                }
                binding.tabLayout.getTabAt(binding.tabLayout.selectedTabPosition)?.view?.apply {
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator())?.start()

                // Search only makes sense on Font tab
                if (tab?.position != 0) {
                    mainViewModel.setQuery("")
                    collapseSearch()
                    hideKeyboard()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(150)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ── Search — icon at end of TabLayout row, expands to 100dp bar ──────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSearchBar() {
        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible  = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            showKeyboard(binding.searchBar)
        }

        binding.searchBar.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBar.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mainViewModel.setQuery(binding.searchBar.text.toString())
                hideKeyboard()
                collapseSearch()
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
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
                    else null, null
                )
                mainViewModel.setQuery(s?.toString().orEmpty())
            }
        })

        // Tap X drawable to clear and collapse
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
                    collapseSearch()
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Collapse on focus lost if bar is empty
        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.searchBar.text.isNullOrEmpty()) {
                collapseSearch()
            }
        }
    }

    /** Hide search bar, restore search icon */
    private fun collapseSearch() {
        if (_binding == null) return
        binding.searchBar.isVisible  = false
        binding.searchIcon.isVisible = true
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    private fun showKeyboard(v: View) {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.searchBar.clearFocus()
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        binding.viewPager.adapter = null
        mainViewModel.setQuery("")
        super.onDestroyView()
        _binding = null
    }
}