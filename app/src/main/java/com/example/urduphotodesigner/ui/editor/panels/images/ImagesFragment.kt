package com.example.urduphotodesigner.ui.editor.panels.images

import android.content.res.ColorStateList
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentImagesBinding
import com.example.urduphotodesigner.ui.editor.panels.background.BackgroundPagerAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

@AndroidEntryPoint
class ImagesFragment : Fragment() {
    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!
    private var tabs = mutableListOf<String>()
    private lateinit var adapter: ImagesPagerAdapter
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }

        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        observeCategories()
    }

    private fun setEvents() {
        tabs.addAll(listOf("Image", "Color", "Gradient"))

        adapter = ImagesPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        binding.addImage.addPressEffect {
            pickImage.launch("image/*")
        }
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath

                mainViewModel.insertImage(
                    ImageEntity(
                        id = System.currentTimeMillis().toInt(),
                        file_name = "",
                        file_url = "",
                        file_size = "",
                        alt_text = "",
                        category = "Imported",
                        user_id = 0,
                        is_selected = false,
                        bitmapData = filePath
                    )
                )

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(ImageProcessor.filePathToBitmap(filePath!!)
                        ?.let { bitmapCompress(it) }, requireActivity())
                }
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed compressing image", e)
            }
        }
    }

    private fun bitmapCompress(image: Bitmap): Bitmap {
        val canvasWidth = 300
        val canvasHeight = 300

        val widthRatio = canvasWidth.toFloat() / image.width
        val heightRatio = canvasHeight.toFloat() / image.height
        val minScale = minOf(1f, widthRatio, heightRatio)

        val newWidth = (image.width * minScale).toInt()
        val newHeight = (image.height * minScale).toInt()

        val resized = Bitmap.createScaledBitmap(image, newWidth, newHeight, true)
        return resized
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
                val additionalTabs = images
                    .map { it.category.trim() }
                    .filterNot { it.equals("Backgrounds", true) || it.equals("Images", true) }
                    .distinct()

                tabs.clear()
                tabs.addAll(additionalTabs)

                adapter.setTabs(tabs)
                setupTabLayout()
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}