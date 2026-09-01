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
import com.webscare.urducanvas.common.utils.setupPanelTabs
import com.webscare.urducanvas.common.utils.setTabEdited
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
    }

    private fun setupTabLayout() {
        mediator?.detach()
        binding.tabLayout.setupPanelTabs(binding.viewPager, tabs) { position ->
            if (position != 0) {
                mainViewModel.setQuery("")
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchIcon.addPressEffect {
            com.webscare.urducanvas.ui.editor.panels.adjustments.PanelSearchDialogFragment.newInstance()
                .show(childFragmentManager, "panel_search_dialog")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                mainViewModel.searchQuery.collect { query ->
                    val hasQuery = query.isNotEmpty()
                    binding.searchIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(),
                            if (hasQuery) R.color.appColor else R.color.gray
                        )
                    )
                }
            }
        }
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
