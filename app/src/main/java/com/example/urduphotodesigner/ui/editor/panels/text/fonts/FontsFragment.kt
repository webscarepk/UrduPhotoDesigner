package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentFontsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@AndroidEntryPoint
class FontsFragment : Fragment() {
    private var _binding: FragmentFontsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var adapter: FontCategoryAdapter
    private lateinit var categories: ArrayList<FontCategory>
    private lateinit var pagerAdapter: FontsPagerAdapter

    private val pickFont =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedFontUri(it) }
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        initObservers()
        setEvents()
    }

    private fun setEvents() {
        // Open font picker when button is clicked
        binding.addFont.setOnClickListener {
            pickFont.launch("*/*")
        }
    }

    private fun handlePickedFontUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if the file is either TTF or OTF based on the file extension
                val fontFile = getFontFileFromUri(uri)
                val extension = fontFile.extension.lowercase()

                if (extension == "ttf" || extension == "otf") {
                    // Proceed with processing the font
                    val typeface = Typeface.createFromFile(fontFile)

                    // Create the bitmap image for the font sample text
                    val fontImageBitmap = createFontSampleBitmap(typeface)

                    // Convert the bitmap to Base64 string
                    val base64EncodedImage = bitmapToBase64(fontImageBitmap)

                    // Prepare the FontEntity
                    val fontEntity = FontEntity(
                        id = System.currentTimeMillis().toInt(),  // Use current timestamp as unique ID
                        file_name = fontFile.name,
                        font_name = fontFile.nameWithoutExtension,
                        font_category = "Imported",  // Set category
                        file_url = "",  // Optional: If there's a file URL
                        file_size = fontFile.length().toString(),
                        font_image = base64EncodedImage,  // Base64 encoded font image
                        image_url = "",  // Same Base64 string for image_url
                        alt_text = "Font sample image",  // Optional: add text description
                        user_id = 0,
                        created_at = System.currentTimeMillis().toString(),
                        updated_at = System.currentTimeMillis().toString(),
                        is_selected = false,
                        is_downloaded = true,  // Set as true since the font is processed and saved
                        is_downloading = false,
                        file_path = fontFile.absolutePath // Path where the font is saved
                    )

                    // Insert into the ViewModel
                    mainViewModel.insertFont(fontEntity)

                    // Optionally, add the font to the Canvas ViewModel or apply it to the canvas
                    withContext(Dispatchers.Main) {
                        // Apply the font to the canvas or use it as needed
                        viewModel.setFont(fontEntity)
                    }
                } else {
                    Snackbar.make(binding.root, "Unsupported font file type: $extension", Snackbar.LENGTH_SHORT).show()
                    Log.e("FontPicker", "Unsupported font file type: $extension")
                }

            } catch (e: Exception) {
                Log.e("FontPicker", "Failed to handle font", e)
            }
        }
    }

    private fun getFontFileFromUri(uri: Uri): File {
        val resolver = requireContext().contentResolver
        val inputStream = resolver.openInputStream(uri)
        val tempFile = File.createTempFile("font", ".ttf", requireContext().cacheDir)

        inputStream?.copyTo(tempFile.outputStream())  // Copy font file to temp location

        return tempFile
    }

    private fun createFontSampleBitmap(typeface: Typeface): Bitmap {
        val paint = Paint()
        paint.typeface = typeface
        paint.textSize = 100f  // Size for the font sample text
        paint.color = ContextCompat.getColor(requireContext(), R.color.appColor)
        paint.textAlign = Paint.Align.LEFT  // Set text alignment to left for easier centering

        // Measure the text width and height
        val width = paint.measureText("Ab").toInt()
        val height = (paint.descent() - paint.ascent()).toInt()

        // Create a bitmap of the required size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Calculate the horizontal and vertical centering
        val x = (bitmap.width - width) / 2f  // Center horizontally
        val y = (bitmap.height - height) / 2f - paint.ascent()  // Center vertically

        // Draw the text on the canvas
        canvas.drawText("Ab", x, y, paint)

        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream)
        val compressedBytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(compressedBytes, Base64.DEFAULT)
    }


    private fun setupRecyclerViews() {
        categories = ArrayList()
        adapter = FontCategoryAdapter { font ->
            handleFontSelection(font)
        }
        binding.categories.adapter = adapter

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        pagerAdapter = FontsPagerAdapter(this@FontsFragment, categories)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = categories[position]
                handleFontSelection(selectedCategory)
                binding.categories.smoothScrollToPosition(position)

                if (position >= 1) {
                    binding.categories.smoothScrollToPosition(4)
                }else{
                    binding.categories.smoothScrollToPosition(0)
                }
            }
        })
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                val distinctCategories = fonts.map { it.font_category }.distinct()
                val newCategories = mutableListOf<FontCategory>()

                // Add "All" category at the top
                newCategories.add(FontCategory(id = -1, font_category = "All", is_selected = true))

                // Add actual categories
                distinctCategories.forEachIndexed { index, category ->
                    newCategories.add(
                        FontCategory(
                            id = index,
                            font_category = category,
                            is_selected = false
                        )
                    )
                }

                if (newCategories != categories) {
                    categories.clear()
                    categories.addAll(newCategories)
                    adapter.submitList(ArrayList(categories))
                    pagerAdapter.updateCategories(categories)
                    handleFontSelection(categories.firstOrNull())
                }
            }
        }
    }

    private fun handleFontSelection(selectedCategory: FontCategory?) {
        selectedCategory?.let { category ->
            val selectedIndex =
                categories.indexOfFirst { it.font_category == category.font_category }

            // Update selected item visuals
            val updatedCategories = categories.map {
                it.copy(is_selected = it.font_category == category.font_category)
            }
            adapter.submitList(updatedCategories)

            // Switch ViewPager page
            binding.viewPager.setCurrentItem(selectedIndex, true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FontsFragment {
            return FontsFragment()
        }
    }
}