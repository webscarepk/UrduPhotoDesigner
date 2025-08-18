package com.example.urduphotodesigner.ui.editor.panels.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentTextBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class TextFragment : Fragment() {
    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!
    private var tabs = emptyList<String>()

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val pickFont =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedFontUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        tabs = listOf("Font", "Appearance", "Format", "Style")

        val adapter = TextPagerAdapter(
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

        binding.addText.addPressEffect { viewModel.addText("Tap to edit", requireActivity()) }
        binding.addFont.addPressEffect {
            pickFont.launch("*/*")
        }

        binding.searchIcon.setOnClickListener {
            // hide icon
            updateIconVisibility(
                binding.searchIcon,
                shouldBeVisible = false,
                animHide = R.anim.slide_out
            )

            // show search bar
            updateIconVisibility(
                binding.searchBar,
                shouldBeVisible = true,
                animShow = R.anim.slide_in
            )

            binding.searchBar.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.searchBar.text.isNullOrEmpty()) {
                updateIconVisibility(
                    binding.searchBar,
                    shouldBeVisible = false,
                    animHide = R.anim.slide_out
                )
                updateIconVisibility(
                    binding.searchIcon,
                    shouldBeVisible = true,
                    animShow = R.anim.slide_in
                )
            }
        }

        binding.searchBar.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBar.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBar.setImeActionLabel("🔍", EditorInfo.IME_ACTION_SEARCH)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchBar.text.toString()
                mainViewModel.setQuery(query)
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val hasText = charSequence?.isNotEmpty() == true
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    if (hasText) {
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
                    } else {
                        null
                    },
                    null
                )
            }

            override fun afterTextChanged(charSequence: Editable?) {}
        })

        binding.searchBar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableRight = binding.searchBar.compoundDrawables[2]
                if (drawableRight != null && event.x >= binding.searchBar.width - binding.searchBar.paddingRight - drawableRight.bounds.width()) {
                    binding.searchBar.text.clear()
                    mainViewModel.setQuery("")
                    binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_search),
                        null,
                        null,
                        null
                    )
                    hideKeyboard()
                    binding.searchBar.clearFocus()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2
    ) {
        val isVisible = view.visibility == View.VISIBLE

        if (shouldBeVisible && !isVisible) {
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
        } else if (!shouldBeVisible && isVisible) {
            if (view == binding.searchIcon) {
                binding.searchBar.isVisible = false
            }
            val anim = AnimationUtils.loadAnimation(view.context, animHide)
            view.startAnimation(anim)
            val duration = anim.duration
            view.postDelayed({ view.visibility = View.GONE }, duration)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
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
                    font_language = "English",
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}