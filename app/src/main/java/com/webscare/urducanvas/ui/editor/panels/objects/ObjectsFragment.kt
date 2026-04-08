package com.webscare.urducanvas.ui.editor.panels.objects

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentObjectsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ObjectsFragment : Fragment() {
    private var _binding: FragmentObjectsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ObjectsPagerAdapter
    private var tabs = mutableListOf<String>()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val tabResultMap = mutableMapOf<String, Boolean>()

    private var tabLayoutListenerAttached = false
    private var tabSelectedListenerAttached = false

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()
        initObservers()
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {          // ← view-scoped, not fragment-scoped
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {   // ← cancels on STOP
                mainViewModel.localImages.collect { images ->

                    val baseTabs = listOf(
                        "Emoticons", "Animals", "Nature", "Food", "Sports",
                        "Transport", "Objects", "Alchemy", "Shapes",
                        "Arrows", "Letters", "Flags"
                    )

                    val extraTabs = withContext(Dispatchers.Default) {
                        images.map { it.category.trim() }
                            .filterNot {
                                it.equals("Backgrounds", ignoreCase = true) ||
                                        it.equals("Backgrounds Imported", ignoreCase = true) ||
                                        it.equals("Images", ignoreCase = true) ||
                                        it.equals("Images Imported", ignoreCase = true)
                            }
                            .distinct()
                    }

                    val hasObjectRecents = withContext(Dispatchers.Default) {
                        images.any { img ->
                            img.is_recent &&
                                    !img.category.equals("Backgrounds", ignoreCase = true) &&
                                    !img.category.equals("Backgrounds Imported", ignoreCase = true) &&
                                    !img.category.equals("Images", ignoreCase = true) &&
                                    !img.category.equals("Images Imported", ignoreCase = true)
                        }
                    }

                    val newTabs = buildList {
                        if (hasObjectRecents) add("Recents")
                        addAll(extraTabs + baseTabs)
                    }

                    if (newTabs != tabs) {
                        tabs.clear()
                        tabs.addAll(newTabs)

                        adapter = ObjectsPagerAdapter(
                            requireActivity().supportFragmentManager, lifecycle, tabs
                        )
                        adapter.onTabVisibilityChanged = { category, hasResults ->
                            setTabVisible(category, hasResults)
                        }
                        binding.viewPager.adapter = adapter
                        binding.viewPager.isUserInputEnabled = false

                        setupTabLayout()
                    } else {
                        adapter.refreshData(images)
                    }
                }
            }
        }
    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        // GlobalLayoutListener — register only once per view lifetime
        if (!tabLayoutListenerAttached) {
            tabLayoutListenerAttached = true
            binding.tabLayout.viewTreeObserver.addOnGlobalLayoutListener {
                if (!isAdded) return@addOnGlobalLayoutListener
                val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return@addOnGlobalLayoutListener
                for (i in 0 until tabStrip.childCount) {
                    tabStrip.getChildAt(i)?.apply {
                        scaleX = 0.9f
                        scaleY = 0.9f
                    }
                }
                binding.tabLayout.getTabAt(binding.tabLayout.selectedTabPosition)?.view?.apply {
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
        }

        // Tab selected listener — register only once per view lifetime
        if (!tabSelectedListenerAttached) {
            tabSelectedListenerAttached = true
            binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.view?.animate()
                        ?.scaleX(1.0f)?.scaleY(1.0f)
                        ?.setDuration(150)
                        ?.setInterpolator(android.view.animation.OvershootInterpolator())
                        ?.start()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                    tab?.view?.animate()
                        ?.scaleX(0.9f)?.scaleY(0.9f)
                        ?.setDuration(150)
                        ?.start()
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
        binding.searchBar.setImeActionLabel("🔍", EditorInfo.IME_ACTION_SEARCH)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                adapter.filter(binding.searchBar.text.toString())
                applySearchFilter(binding.searchBar.text.toString())
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
                val hasText = !s.isNullOrEmpty()
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null, null,
                    if (hasText) ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close) else null,
                    null
                )
                if (!hasText) {
                    tabResultMap.clear()
                    showAllTabs()
                    applySearchFilter("")
                } else {
                    tabResultMap.clear() // reset so we collect fresh results for new query
                }
            }
        })

        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableRight = binding.searchBar.compoundDrawables[2]
                if (drawableRight != null &&
                    event.x >= binding.searchBar.width - binding.searchBar.paddingRight - drawableRight.bounds.width()
                ) {
                    binding.searchBar.text.clear()
                    applySearchFilter("")
                    adapter.filter("")
                    tabResultMap.clear()
                    showAllTabs()
                    binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_search),
                        null, null, null
                    )
                    hideKeyboard()
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.addImage.addPressEffect {
            pickImage.launch("image/*")
        }
    }

    private fun showAllTabs() {
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until tabStrip.childCount) {
            tabStrip.getChildAt(i)?.isVisible = true
        }
    }

    private fun applySearchFilter(query: String) {
        adapter.filter(query)

        if (query.isBlank()) {
            showAllTabs()
            return
        }

        val images = mainViewModel.localImages.value ?: return
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return

        var firstVisibleIndex = -1

        for (i in tabs.indices) {
            val category = tabs[i]
            val hasResults = when {
                // Base emoji tabs
                ObjectsListFragment.isBaseTab(category) -> {
                    val emojiData = ObjectsListFragment.emojiDataForCategory(category)
                    emojiData.any { it.name.contains(query, ignoreCase = true) }
                }
                // Recents tab
                category.equals("Recents", ignoreCase = true) -> {
                    images.any { img ->
                        img.is_recent &&
                                !img.category.equals("Backgrounds", ignoreCase = true) &&
                                !img.category.equals("Backgrounds Imported", ignoreCase = true) &&
                                !img.category.equals("Images", ignoreCase = true) &&
                                !img.category.equals("Images Imported", ignoreCase = true) &&
                                img.alt_text?.contains(query, ignoreCase = true) == true
                    }
                }
                // All other DB-backed tabs
                else -> {
                    images.any { img ->
                        img.category.equals(category, ignoreCase = true) &&
                                img.alt_text?.contains(query, ignoreCase = true) == true
                    }
                }
            }

            tabStrip.getChildAt(i)?.isVisible = hasResults
            if (hasResults && firstVisibleIndex == -1) firstVisibleIndex = i
        }

        // Jump to first visible tab if current one is now hidden
        val currentTab = binding.tabLayout.selectedTabPosition
        if (tabStrip.getChildAt(currentTab)?.isVisible == false && firstVisibleIndex != -1) {
            binding.viewPager.setCurrentItem(firstVisibleIndex, false)
        }
    }

    private fun setTabVisible(category: String, hasResults: Boolean) {
        tabResultMap[category] = hasResults

        // Wait until ALL tabs have reported before updating visibility
        if (tabResultMap.size < tabs.size) return

        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return

        var firstVisibleIndex = -1
        for (i in tabs.indices) {
            val hasData = tabResultMap[tabs[i]] == true
            tabStrip.getChildAt(i)?.isVisible = hasData
            if (hasData && firstVisibleIndex == -1) firstVisibleIndex = i
        }

        // If current tab is now hidden, jump to first visible tab
        val currentTab = binding.tabLayout.selectedTabPosition
        if (tabStrip.getChildAt(currentTab)?.isVisible == false && firstVisibleIndex != -1) {
            binding.viewPager.setCurrentItem(firstVisibleIndex, false)
        }
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath!!) ?: return@launch

                val canvasW = viewModel.canvasSize.value?.width ?: rawBitmap.width.toFloat()
                val canvasH = viewModel.canvasSize.value?.height ?: rawBitmap.height.toFloat()

                val maxW = (canvasW * 2).toInt().coerceAtLeast(1024)
                val maxH = (canvasH * 2).toInt().coerceAtLeast(1024)

                val bitmap = downsampleIfNeeded(rawBitmap, maxW, maxH)

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE)
                }
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed compressing image", e)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabLayoutListenerAttached = false
        tabSelectedListenerAttached = false
        _binding = null
    }
}