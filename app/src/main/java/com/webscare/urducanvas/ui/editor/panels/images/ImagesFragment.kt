package com.webscare.urducanvas.ui.editor.panels.images

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
import android.view.animation.OvershootInterpolator
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
import com.google.android.material.tabs.TabLayout
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.databinding.FragmentImagesBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ImagesFragment : Fragment() {

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    // Fragment cache — created lazily on first visit, never destroyed.
    // show()/hide() is instant, data is preserved, scroll is preserved.
    private val fragmentCache = LinkedHashMap<String, ImagesListFragment>()
    private val tabs = mutableListOf<String>()
    private var currentTabIndex = 0
    private var currentFragment: ImagesListFragment? = null
    private var currentQuery = ""
    private var lastImagesData: ImagesData? = null
    private var tabListenerAttached = false

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setEvents()

        val initial = mainViewModel.imagesData.value
        tabs.clear()
        tabs.addAll(initial.tabs)

        currentTabIndex = mainViewModel.lastImagesTabCategory
            ?.let { savedCategory -> tabs.indexOf(savedCategory).takeIf { it >= 0 } }
            ?: 0

        // ← tabs empty guard — rebuildTabLayout mat karo agar tabs nahi hain
        if (tabs.isNotEmpty()) {
            rebuildTabLayout(selectIndex = currentTabIndex)
            showTab(currentTabIndex.coerceIn(0, tabs.lastIndex))
        }

        observeImagesData()
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

        mainViewModel.lastImagesTabCategory = category

        val target = fragmentCache.getOrPut(category) {
            ImagesListFragment.newInstance(category, currentQuery).also { f ->
                f.onFilterResult = { cat, count -> onTabFilterResult(cat, count > 0) }
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

        currentFragment = target
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

        // Scroll to selected tab so it's visible
        binding.tabLayout.post {
            binding.tabLayout.getTabAt(safeIndex)?.view?.let { tabView ->
                binding.tabLayout.scrollTo(
                    (tabView.left - binding.tabLayout.width / 2 + tabView.width / 2)
                        .coerceAtLeast(0),
                    0
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
                    ?.setDuration(100)
                    ?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ── Data observation ──────────────────────────────────────────────────────

    private fun observeImagesData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.imagesData.collect { data ->
                    onImagesDataChanged(data)
                }
            }
        }
    }

    private fun onImagesDataChanged(data: ImagesData) {
        if (_binding == null) return
        if (lastImagesData === data) return
        lastImagesData = data

        val tabsChanged = data.tabs != tabs
        if (tabsChanged) {
            val currentCategory = tabs.getOrNull(currentTabIndex)
            tabs.clear()
            tabs.addAll(data.tabs)

            if (tabs.isEmpty()) return  // abhi data nahi aaya

            val newIndex = currentCategory
                ?.let { tabs.indexOf(it) }
                ?.takeIf { it >= 0 }
                ?: 0

            currentTabIndex = newIndex
            rebuildTabLayout(selectIndex = newIndex)
            showTab(newIndex)  // ← FIX: tabs rebuild ke baad fragment bhi show karo
        }

        for ((_, fragment) in fragmentCache) {
            fragment.onNewData(data)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun applySearch(query: String) {
        currentQuery = query

        for ((_, fragment) in fragmentCache) {
            fragment.updateFilter(query)
        }

        if (query.isBlank()) {
            showAllTabs()
            return
        }

        val data = mainViewModel.imagesData.value
        val tabStrip = binding.tabLayout.getChildAt(0) as? ViewGroup ?: return

        for (i in tabs.indices) {
            val category = tabs[i]
            val hasResults = when {
                category.equals("Recents", ignoreCase = true) ->
                    data.recents.any { it.matchesQuery(query) }
                else ->
                    data.imagesByCategory[category].orEmpty()
                        .any { it.matchesQuery(query) }
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
        binding.addImage.addPressEffect { pickImage.launch("image/*") }

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

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)
                    ?.absolutePath ?: return@launch
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath) ?: return@launch
                val canvasW = viewModel.canvasSize.value?.width ?: rawBitmap.width.toFloat()
                val canvasH = viewModel.canvasSize.value?.height ?: rawBitmap.height.toFloat()
                val maxW = (canvasW * 2).toInt().coerceAtLeast(1024)
                val maxH = (canvasH * 2).toInt().coerceAtLeast(1024)
                val bitmap = downsampleIfNeeded(rawBitmap, maxW, maxH)
                withContext(Dispatchers.Main) {
                    // Current tab se decide karo — background ya image
                    val currentCategory = tabs.getOrNull(currentTabIndex).orEmpty()
                    if (currentCategory.equals("Backgrounds", ignoreCase = true) ||
                        currentCategory.equals("My Backgrounds", ignoreCase = true)
                    ) {
                        viewModel.ensureBackgroundElement(requireActivity())
                        viewModel.setCanvasBackgroundImage(bitmap, requireActivity())
                    } else {
                        viewModel.addSticker(bitmap, requireActivity(), ElementType.IMAGE)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImagesFragment", "Failed to import image", e)
            }
        }
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }
}