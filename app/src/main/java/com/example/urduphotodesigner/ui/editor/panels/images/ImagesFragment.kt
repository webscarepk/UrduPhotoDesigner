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

        binding.addImage.setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val resolver = requireContext().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    private fun handlePickedUri(uri: Uri) {
        // You probably don’t want to block the UI thread—do this in IO
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get the Bitmap from URI and compress it
                val bitmap = getBitmapFromUri(uri)
                val compressed = bitmapCompress(bitmap)

                // Convert the compressed Bitmap into Base64
                val base64EncodedImage = run {
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    compressed.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream) // JPEG compression with quality 80
                    val compressedBytes = byteArrayOutputStream.toByteArray()
                    Base64.encodeToString(compressedBytes, Base64.DEFAULT) // Convert to Base64
                }

                mainViewModel.insertImage(
                    ImageEntity(
                        id = System.currentTimeMillis().toInt(),  // Use current timestamp as unique ID
                        file_name = "",  // Set file name (if available)
                        file_url = "",   // Set file URL (if available)
                        file_size = "", // Optional field for description
                        alt_text = "",  // Set date (if needed)
                        category = "Imported",  // For example, background category
                        user_id = 0,        // Set image size if applicable
                        is_selected = false,  // Set the selected state
                        bitmapData = base64EncodedImage // Set the Base64 encoded image data
                    )
                )

                withContext(Dispatchers.Main) {
                    // Add sticker to the ViewModel, passing the Base64 encoded image data
                    viewModel.addSticker(bitmap, requireActivity())
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
                    .filterNot { it.equals("Background", true) || it.equals("Image", true) }
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