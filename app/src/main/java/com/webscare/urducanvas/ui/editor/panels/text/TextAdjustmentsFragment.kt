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
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.setupPanelTabs
import com.webscare.urducanvas.common.utils.setTabEdited
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.webscare.urducanvas.databinding.FragmentTextAdjustmentsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TextAdjustmentsFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentTextAdjustmentsBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private val tabs = listOf("Styles", "Font", "Appearance", "3D", "Format")
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
        binding.title.text = getString(R.string.text_properties)
        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = null

        val isMixedGroup = arguments?.getBoolean("isMixedGroup") ?: false
        val groupId = arguments?.getString("groupId")
        val elementId = arguments?.getString("elementId")
        if (isMixedGroup) {
            binding.groupToggleContainer.visibility = View.VISIBLE
            val toggleAction = {
                val bundle = Bundle().apply {
                    putString("elementId", elementId)
                    putBoolean("isMixedGroup", true)
                    putString("groupId", groupId)
                }
                val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
                findNavController().navigate(R.id.adjustmentsParentFragment, bundle, navOptions)
            }
            binding.btnPrevGroupTab.addPressEffect { toggleAction() }
            binding.btnNextGroupTab.addPressEffect { toggleAction() }
        }

        adapter = TextAdjustmentsPagerAdapter(
            childFragmentManager,
            viewLifecycleOwner.lifecycle,
            tabs
        )
        adapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        setupTabLayout()
        setupSearchBar()

        binding.back.addPressEffect {
            findNavController().navigateUp()
        }

        viewModel.openAppearanceTab.observe(viewLifecycleOwner) { openAppearance ->
            if (!isAdded || _binding == null) return@observe
            if (openAppearance == true) {
                binding.viewPager.post {
                    if (_binding == null) return@post
                    binding.viewPager.setCurrentItem(2, false)
                }
            }
        }

        viewModel.open3DTab.observe(viewLifecycleOwner) { open3d ->
            if (!isAdded || _binding == null) return@observe
            if (open3d == true) {
                binding.viewPager.post {
                    if (_binding == null) return@post
                    binding.viewPager.setCurrentItem(3, false)
                }
            }
        }
    }

    // ── TabLayout ─────────────────────────────────────────────────────────────

    private fun setupTabLayout() {
        mediator?.detach()
        binding.tabLayout.setupPanelTabs(binding.viewPager, tabs) { position ->
            if (position != 1) {
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