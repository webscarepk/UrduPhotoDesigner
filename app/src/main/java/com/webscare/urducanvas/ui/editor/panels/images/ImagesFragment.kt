package com.webscare.urducanvas.ui.editor.panels.images

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
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

    private lateinit var adapter: ImagesPagerAdapter

    private var tabs = mutableListOf<String>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setEvents()
        observeCategories()
    }

    // --------------------------------------------------
    // TAB SETUP
    // --------------------------------------------------

    private fun setupTabs() {

        tabs.addAll(listOf("Images", "Colors", "Gradient"))

        adapter = ImagesPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        setupTabLayout()
    }

    // --------------------------------------------------
    // EVENTS
    // --------------------------------------------------

    private fun setEvents() {

        binding.addImage.addPressEffect {
            pickImage.launch("image/*")
        }

        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchBar.text.toString()
                adapter.filter(query)
                hideKeyboard()
                binding.searchBar.isVisible = false
                binding.searchIcon.isVisible = true
                true
            } else false
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.searchBar.isVisible = false
                binding.searchIcon.isVisible = true
            }
        }
    }

    // --------------------------------------------------
    // KEYBOARD
    // --------------------------------------------------

    private fun hideKeyboard() {

        val imm = requireContext().getSystemService(InputMethodManager::class.java)

        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)

        binding.searchBar.clearFocus()
    }

    // --------------------------------------------------
    // IMAGE PICKER
    // --------------------------------------------------

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                val rawBitmap = ImageProcessor.filePathToBitmap(filePath!!) ?: return@launch

                val canvasW = viewModel.canvasSize.value?.width ?: rawBitmap.width
                val canvasH = viewModel.canvasSize.value?.height ?: rawBitmap.height

                val maxW = (canvasW.toInt() * 2).coerceAtLeast(1024)
                val maxH = (canvasH.toInt() * 2).coerceAtLeast(1024)

                val bitmap = downsampleIfNeeded(rawBitmap, maxW, maxH)

                withContext(Dispatchers.Main) {
                    val selectedTab =
                        binding.tabLayout.getTabAt(binding.tabLayout.selectedTabPosition)?.text

                    if (selectedTab?.equals("Backgrounds") == true) {
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

    // --------------------------------------------------
    // OBSERVE CATEGORIES
    // --------------------------------------------------

    private fun observeCategories() {

        lifecycleScope.launch {

            mainViewModel.localImages.collect { images ->

                val imageTabs = images.map { it.category.trim() }
                    .filter {
                        it.equals("Images", true) ||
                                it.equals("Images Imported", true) ||
                                it.equals("Backgrounds", true) ||
                                it.equals("Backgrounds Imported", true)
                    }
                    .distinct()

                val hasRecents = images.any {

                    it.is_recent && (
                            it.category.equals("Images", true) ||
                                    it.category.equals("Images Imported", true) ||
                                    it.category.equals("Backgrounds", true) ||
                                    it.category.equals("Backgrounds Imported", true)
                            )
                }

                val newTabs = mutableListOf<String>().apply {

                    if (hasRecents) add("Recents")

                    addAll(imageTabs)
                }

                if (newTabs != tabs) {

                    tabs.clear()
                    tabs.addAll(newTabs)

                    adapter.setTabs(tabs)

                    binding.noEmojis.isVisible = tabs.isEmpty()

                } else {

                    binding.noEmojis.isVisible = images.isEmpty()

                    adapter.refreshData(images)
                }
            }
        }
    }

    // --------------------------------------------------
    // TAB LAYOUT UI
    // --------------------------------------------------

    private fun setupTabLayout() {

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->

            val tabView = LayoutInflater.from(context)
                .inflate(R.layout.custom_tab, null)

            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]

            tab.customView = tabView

        }.attach()

        binding.tabLayout.viewTreeObserver.addOnGlobalLayoutListener {

            if (isAdded) {

                for (i in 0 until binding.tabLayout.tabCount) {

                    val tabView =
                        (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)

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

                tab?.view?.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(150)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator())
                    ?.start()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

                tab?.view?.animate()
                    ?.scaleX(0.9f)
                    ?.scaleY(0.9f)
                    ?.setDuration(150)
                    ?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}