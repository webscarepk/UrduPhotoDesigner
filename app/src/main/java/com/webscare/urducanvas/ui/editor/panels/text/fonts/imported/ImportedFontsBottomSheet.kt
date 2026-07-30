package com.webscare.urducanvas.ui.editor.panels.text.fonts.imported

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.databinding.BottomSheetImportedFontsBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ImportedFontsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImportedFontsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()

    private lateinit var adapter: ImportedFontsAdapter

    // Same contract as TextFragment — GetContent so the picker accepts all sources
    private val pickFont =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { importAndApplyFont(it) }
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImportedFontsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        initObservers()
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ImportedFontsAdapter { font ->
            // Tap on existing font → apply + dismiss immediately
            applyFont(font)
            dismiss()
        }
        binding.fontsRV.adapter = adapter
    }

    private fun setupFab() {
        binding.importFont.addPressEffect {
            pickFont.launch("*/*")  // same as TextFragment
        }

        binding.back.addPressEffect {
            dismiss()
        }
    }

    // ── Observers ────────────────────────────────────────────────────────────

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.localFonts.collect { fonts ->
                    val imported = fonts.filter {
                        it.font_category.equals("Imported", ignoreCase = true)
                    }

                    adapter.submitList(imported)

                    if (imported.isEmpty()) {
                        binding.noEmojis.visibility = View.VISIBLE
                    } else {
                        binding.noEmojis.visibility = View.GONE
                    }
                }
            }
        }

        // Keep selected highlight in sync with active canvas font
        canvasViewModel.currentFont.observe(viewLifecycleOwner) { font ->
            adapter.selectedFontId = font?.id?.toString()
        }
    }

    // ── Font import (mirrors TextFragment.handlePickedFontUri exactly) ────────

    private fun importAndApplyFont(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = resolveContentInfo(uri)
                val ext = info.extension.lowercase()

                if (ext !in setOf("ttf", "otf")) {
                    withContext(Dispatchers.Main) {
                        showSnack("Please select a .ttf or .otf font file")
                    }
                    return@launch
                }

                val fontFile = copyToTempWithExtension(uri, ".$ext")

                val exportDate = SimpleDateFormat(
                    "yyyy-MM-dd", Locale.getDefault()
                ).format(Date())

                val fontEntity = FontEntity(
                    id = System.currentTimeMillis().toInt(),
                    file_name = fontFile.name,
                    font_name = fontFile.nameWithoutExtension,
                    font_category = "Imported",
                    font_language = "Imported",
                    file_url = "",
                    file_size = fontFile.length().toString(),
                    font_image = null,
                    image_url = "",
                    alt_text = "Font sample image",
                    user_id = 0,
                    created_at = exportDate,
                    updated_at = exportDate,
                    is_selected = false,
                    is_downloaded = true,
                    is_downloading = false,
                    file_path = fontFile.absolutePath
                )

                // 1. Save to Room
                mainViewModel.insertFont(fontEntity)

                // 2. Apply to canvas + navigate to Imported tab + show snackbar + dismiss
                withContext(Dispatchers.Main) {
                    applyFont(fontEntity)
                    (parentFragment as? com.webscare.urducanvas.ui.editor.panels.text.TextFragment)?.selectImportedTab()
                    com.webscare.urducanvas.common.utils.GlobalSnackbar.showSuccess(
                        requireActivity(),
                        message = "Font '${fontEntity.font_name}' imported successfully!"
                    )
                    dismiss()
                }
            } catch (e: Exception) {
                Log.e("ImportedFontsBS", "Failed to handle font", e)
                withContext(Dispatchers.Main) {
                    showSnack("Failed to import font")
                }
            }
        }
    }

    // ── Apply font to canvas ─────────────────────────────────────────────────

    private fun applyFont(font: FontEntity) {
        canvasViewModel.setFont(font)
        adapter.selectedFontId = font.id.toString()
    }

    // ── Helpers (copied from TextFragment) ───────────────────────────────────

    private data class ContentInfo(val displayName: String, val extension: String)

    /**
     * Resolves display name and extension from a URI.
     * Falls back to MIME type if no extension is found in the file name —
     * identical to the logic in TextFragment.
     */
    private fun resolveContentInfo(uri: Uri): ContentInfo {
        val cr = requireContext().contentResolver

        var name: String? = null
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) name = c.getString(idx)
            }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "picked_file"
        }

        val lower = name!!.lowercase()
        val extFromName = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (extFromName.isNotBlank()) {
            return ContentInfo(displayName = name!!, extension = extFromName)
        }

        // No extension in file name — sniff from MIME type
        val mime = cr.getType(uri)?.lowercase().orEmpty()
        val guessedExt = when (mime) {
            "font/ttf", "application/x-font-ttf", "application/font-sfnt" -> "ttf"

            "font/otf", "application/x-font-otf", "application/font-otf" -> "otf"

            else -> ""
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

    private fun showSnack(message: String) {
        view?.let {
            val snack = Snackbar.make(it, message, Snackbar.LENGTH_SHORT)
            snack.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            snack.show()
        }
    }

    // ── Bottom sheet styling (identical to CreateFragment) ───────────────────

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.45f)
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
            @Suppress("DEPRECATION") decorView.setOnSystemUiVisibilityChangeListener { forceImmersiveMode() }
        }

        val bottomSheet =
            dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return

        val behavior = BottomSheetBehavior.from(bottomSheet)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                binding.importFont.translationY = -sheet.top.toFloat()
            }

            override fun onSlide(sheet: View, slideOffset: Float) {
                binding.importFont.translationY = -sheet.top.toFloat()
            }
        })

        bottomSheet.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_bg)
        bottomSheet.setBackgroundResource(android.R.color.transparent)

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        BottomSheetBehavior.from(bottomSheet).apply {
            isFitToContents = false
            expandedOffset = 0
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            halfExpandedRatio = 0.45f
            skipCollapsed = true
        }

        bottomSheet.post {
            bottomSheet.post {
                binding.importFont.translationY = -bottomSheet.top.toFloat()
                binding.noEmojis.translationY = (-bottomSheet.top.toFloat()) / 2
            }
        }
        forceImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        forceImmersiveMode()
    }

    private fun forceImmersiveMode() {
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    hide(WindowInsets.Type.navigationBars())
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImportedFontsBottomSheet"
        fun newInstance() = ImportedFontsBottomSheet()
    }
}