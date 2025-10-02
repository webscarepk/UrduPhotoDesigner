package com.example.urduphotodesigner.ui.editor.panels.background

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentBackgroundsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class BackgroundsFragment : Fragment() {
    private var _binding: FragmentBackgroundsBinding? = null
    private val binding get() = _binding!!
    private var tabs = emptyList<String>()
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

        setEvents()
    }

    private fun setEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                val hasRecents = images.any {
                    it.is_recent && (
                            it.category.equals("Backgrounds", true) ||
                                    it.category.equals("Backgrounds Imported", true)
                            )
                }

                tabs = mutableListOf("Images", "Colors")
                if (hasRecents) {
                    (tabs as MutableList).add(0, "Recents") // put Recents first
                }

                val adapter = BackgroundPagerAdapter(
                    requireActivity().supportFragmentManager,
                    lifecycle,
                    tabs
                )
                binding.viewPager.adapter = adapter
                binding.viewPager.isUserInputEnabled = false

                TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                    val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
                    tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
                    tab.customView = tabView
                }.attach()
            }
        }

        binding.addImage.addPressEffect { pickImage.launch("image/*") }
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath

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