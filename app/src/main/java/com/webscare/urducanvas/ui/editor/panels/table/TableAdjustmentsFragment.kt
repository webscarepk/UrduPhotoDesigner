package com.webscare.urducanvas.ui.editor.panels.table

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
import android.widget.EditText
import android.widget.ImageView

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.TableScope
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentTableAdjustmentsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableAdjustmentsFragment : Fragment() {

    private var _binding: FragmentTableAdjustmentsBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private val tabs = listOf("Font", "Appearance", "Format", "Structure", "Styles")
    private lateinit var adapter: TableAdjustmentsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = getString(R.string.table_properties)
        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = null

        setEvents()
    }

    private fun setEvents() {
        adapter = TableAdjustmentsPagerAdapter(
            childFragmentManager,
            viewLifecycleOwner.lifecycle,
            tabs
        )
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        setupTabLayout()
        setupSearchBar()

        binding.back.addPressEffect {
            hideKeyboard()
            findNavController().navigateUp()
        }
    }

    private fun setupTabLayout() {
        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }
        mediator?.attach()

        binding.tabLayout.doOnLayout {
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSearchBar() {
        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true
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

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.searchBar.text.isNullOrEmpty()) {
                collapseSearch()
            }
        }
    }

    private fun collapseSearch() {
        if (_binding == null) return
        binding.searchBar.isVisible = false
        binding.searchIcon.isVisible = true
    }

    private fun showKeyboard(v: View) {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val b = _binding ?: return
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.root.windowToken, 0)
        b.searchBar.clearFocus()
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        _binding?.viewPager?.adapter = null
        mainViewModel.setQuery("")
        super.onDestroyView()
        _binding = null
    }
}
