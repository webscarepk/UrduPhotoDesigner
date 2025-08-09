package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontCategory
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentFontsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        binding.addFont.addPressEffect {
            pickFont.launch("*/*")
        }
    }

    private data class ContentInfo(val displayName: String, val extension: String)

    private fun resolveContentInfo(uri: Uri): ContentInfo {
        val cr = requireContext().contentResolver

        // Try display name via query
        var name: String? = null
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) {
                    name = c.getString(idx)
                }
            }

        // Fallback to lastPathSegment
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "picked_file"
        }

        val lower = name!!.lowercase()
        val extFromName = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (extFromName.isNotBlank()) {
            return ContentInfo(displayName = name!!, extension = extFromName)
        }

        // If no extension in name, try mime type
        val mime = cr.getType(uri)?.lowercase().orEmpty()
        val guessedExt = when (mime) {
            "font/ttf", "application/x-font-ttf", "application/font-sfnt" -> "ttf"
            "font/otf", "application/x-font-otf", "application/font-otf", "application/font-sfnt" -> "otf"
            else -> "" // unknown
        }

        return ContentInfo(displayName = name!!, extension = guessedExt)
    }

    private fun copyToTempWithExtension(uri: Uri, dotExt: String): File {
        val tempFile = File.createTempFile(
            "font_${System.currentTimeMillis()}", dotExt, requireContext().cacheDir
        )
        requireContext().contentResolver.openInputStream(uri).use { input ->
            tempFile.outputStream().use { out -> input?.copyTo(out) }
        }
        return tempFile
    }

    private fun handlePickedFontUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = resolveContentInfo(uri)
                val ext = info.extension.lowercase()

                val allowed = setOf("ttf", "otf")
                if (ext !in allowed) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(
                            binding.root,
                            "Please select a .ttf or .otf font file",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val fontFile = copyToTempWithExtension(uri, ".$ext")
                val typeface = Typeface.createFromFile(fontFile)

                val fontImageBitmap = createFontSampleBitmap(typeface)
                val bitmapData = ImageProcessor.bitmapToFilePath(requireActivity(), fontImageBitmap)

                val fontEntity = FontEntity(
                    id = System.currentTimeMillis().toInt(),
                    file_name = fontFile.name,
                    font_name = fontFile.nameWithoutExtension,
                    font_category = "Imported",
                    file_url = "",
                    file_size = fontFile.length().toString(),
                    font_image = bitmapData,     // path you saved
                    image_url = "",
                    alt_text = "Font sample image",
                    user_id = 0,
                    created_at = System.currentTimeMillis().toString(),
                    updated_at = System.currentTimeMillis().toString(),
                    is_selected = false,
                    is_downloaded = true,
                    is_downloading = false,
                    file_path = fontFile.absolutePath
                )
                mainViewModel.insertFont(fontEntity)
                withContext(Dispatchers.Main) {
                    viewModel.setFont(fontEntity)
                }
            } catch (e: Exception) {
                Log.e("FontPicker", "Failed to handle font", e)
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Failed to import font", Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun createFontSampleBitmap(typeface: Typeface): Bitmap {
        val paint = Paint()
        paint.typeface = typeface
        paint.textSize = 100f
        paint.color = ContextCompat.getColor(requireContext(), R.color.appColor)
        paint.textAlign = Paint.Align.LEFT

        val width = paint.measureText("Ab").toInt()
        val height = (paint.descent() - paint.ascent()).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val x = (bitmap.width - width) / 2f
        val y = (bitmap.height - height) / 2f - paint.ascent()

        canvas.drawText("Ab", x, y, paint)

        return bitmap
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
                } else {
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
                            id = index, font_category = category, is_selected = false
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