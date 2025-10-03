package com.example.urduphotodesigner.ui.editor.panels.background

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentBackgroundsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BackgroundsFragment : Fragment() {
    private var _binding: FragmentBackgroundsBinding? = null
    private val binding get() = _binding!!

    private var tabs = mutableListOf<String>()
    private lateinit var adapter: BackgroundPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Setup adapter once
        tabs = mutableListOf("Images", "Colors") // base tabs

        adapter = BackgroundPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        setupTabLayout()
        observeCategories()

        binding.addImage.addPressEffect { pickImage.launch("image/*") }
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                val hasRecents = images.any {
                    it.is_recent && (
                            it.category.equals("Backgrounds", true) ||
                                    it.category.equals("Backgrounds Imported", true)
                            )
                }

                val newTabs = mutableListOf<String>().apply {
                    if (hasRecents) add("Recents")
                    addAll(listOf("Images", "Colors"))
                }

                if (newTabs != tabs) {
                    // structure changed → rebuild
                    tabs.clear()
                    tabs.addAll(newTabs)
                    adapter.setTabs(tabs)
                } else {
                    // only data changed → refresh fragments
                    adapter.refreshData(images)
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
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath =
                    ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                withContext(Dispatchers.Main) {
                    viewModel.setCanvasBackgroundImage(
                        ImageProcessor.filePathToBitmap(filePath!!)
                    )
                }
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed compressing image", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}