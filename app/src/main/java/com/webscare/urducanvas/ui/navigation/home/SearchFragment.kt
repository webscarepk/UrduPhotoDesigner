package com.webscare.urducanvas.ui.navigation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.FragmentSearchBinding
import com.webscare.urducanvas.ui.navigation.files.FilesAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.asFlow
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()
    private lateinit var templatesAdapter: PopularTemplatesAdapter
    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var filesAdapter: FilesAdapter
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private var rotationAnimator: ObjectAnimator? = null

    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
    private var bundle: Bundle = Bundle()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchBar.requestFocus()

        // Force keyboard to remain open
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.postDelayed({
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }, 150)

        setupAdapters()
        setupSearchBar()
        observeSearchResults()
    }


    private fun setupAdapters() {
        // Templates
        templatesAdapter = PopularTemplatesAdapter(onClick = { template, isDownloaded ->
            if (template.is_downloading) return@PopularTemplatesAdapter
            if (isDownloaded) {
                if (template.file_path.isNullOrEmpty()) {
                    templatesAdapter.updateProgress(
                        template.id,
                        ProgressUi(
                            progress = 0, isDownloading = true, isDownloaded = false
                        )
                    )
                    mainViewModel.downloadTemplate(template)
                    return@PopularTemplatesAdapter
                } else {
                    canvasViewModel.setProjectSourceName(template.category ?: template.subcategory)
                    val exportResult = template.toExportResultFinal().copy(fileName = canvasViewModel.buildProjectFileName())
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            canvasViewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                        }
                    }
                }
            } else {
                templatesAdapter.updateProgress(
                    template.id, ProgressUi(
                        progress = 0, isDownloading = true, isDownloaded = false
                    )
                )
                mainViewModel.downloadTemplate(template)
            }
        })

        binding.popularTemplateRV.apply {
            adapter = templatesAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        // Fonts
        fontsAdapter = FontsAdapter(onFontClick = { font, isDownloaded ->
            if (!isDownloaded) {
                mainViewModel.downloadFont(font)
            } else {
                canvasViewModel.setCanvasSize(
                    CanvasSize(
                        "", 2000f, 2000f
                    )
                )
                canvasViewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText), font, requireActivity()
                )

                view?.post { findNavController().navigate(R.id.editorFragment, null, navOptions) }
            }
        }, onDownload = {
            mainViewModel.downloadFont(it)
        })

        binding.fontsRV.apply {
            adapter = fontsAdapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        filesAdapter = FilesAdapter(
            emptyList(),
            isGrid = false,
            onItemClick = { openItem(it) },
            onItemLongClick = {},
            onOptionsClick = { _, _ -> },
            onRename = { _, _ -> },
            onSelectionChanged = {})
        binding.filesRV.apply {
            adapter = filesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun openItem(item: Any) {
        when (item) {
            is ExportResult -> {
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        canvasViewModel.loadTemplateFromJsonFile(item, requireContext())
                    }
                }
            }

            is FontEntity -> {
                canvasViewModel.setCanvasSize(
                    CanvasSize(
                        "",
                        2000f,
                        2000f
                    )
                )
                canvasViewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText),
                    item,
                    requireActivity()
                )
                view?.post {
                    findNavController().navigate(R.id.editorFragment, null, navOptions)
                }
            }

            is ImageEntity -> {
                val bitmap = BitmapFactory.decodeFile(item.bitmapData)

                bitmap?.let {
                    val widthVal = it.width.toFloat()
                    val heightVal = it.height.toFloat()

                    val canvasSize =
                        CanvasSize(
                            "From Image",
                            widthVal,
                            heightVal
                        )

                    canvasViewModel.clearCanvas()
                    canvasViewModel.setCanvasSize(canvasSize)
                    canvasViewModel.setCanvasBackgroundImage(it, requireActivity())
                    view?.post {
                        findNavController().navigate(R.id.editorFragment, null, navOptions)
                    }
                }
            }
        }
    }

    private fun setupSearchBar() {
        binding.back.addPressEffect {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
            findNavController().navigateUp()
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                mainViewModel.setQuery(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                mainViewModel.localTemplates,
                mainViewModel.localFonts,
                mainViewModel.localImages,
                mainViewModel.exportResults.asFlow(),
                mainViewModel.queryDebounced.debounce(250).distinctUntilChanged()
            ) { templates, fonts, images, exports, query ->
                val q = query.trim().lowercase()

                val filteredTemplates = templates.filter { t ->
                    q.isNotEmpty() && t.template_name.lowercase().contains(q)
                }

                val filteredFonts = fonts.filter { f ->
                    q.isNotEmpty() && f.font_name.lowercase().contains(q)
                }

                val filteredFiles = exports.filter { e ->
                    q.isNotEmpty() && e.fileName.lowercase().contains(q)
                }

                val filteredImages = images.filter { i ->
                    q.isNotEmpty() && i.file_name.lowercase().contains(q)
                }

                SearchResults(
                    templates = filteredTemplates,
                    fonts = filteredFonts,
                    files = filteredFiles + filteredImages
                )
            }.collectLatest { result ->
                updateUI(result)
            }
        }

        canvasViewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                lifecycleScope.launch {
                    delay(500)
                    if (findNavController().currentDestination?.id != R.id.editorFragment) {
                        view?.post {
                            findNavController().navigate(
                                R.id.editorFragment, bundle, navOptions
                            )
                        }
                    }
                }
            }
        }

        canvasViewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            dialogBinding?.apply {
                progressBar.progress = percent
                subtitle.text = "$message... $percent%"
                tvProgressPercent.text = "$percent% complete"
            }
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(true)
            setOnCancelListener { dialog ->
                canvasViewModel.clearLoading()
            }
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 80% width
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params
            window?.setGravity(Gravity.CENTER)
            show()
        }

        dialogBinding?.title?.text = "Loading Project"

        startIconRotation()
    }

    private fun startIconRotation() {
        dialogBinding?.view4?.let { icon ->
            rotationAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopIconRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun dismissLoadingDialog() {
        stopIconRotation()
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun updateUI(result: SearchResults) {
        // Templates
        templatesAdapter.submitList(result.templates)
        binding.popularTemplate.isVisible = result.templates.isNotEmpty()
        binding.popularTemplateRV.isVisible = result.templates.isNotEmpty()

        // Fonts
        fontsAdapter.submitList(result.fonts)
        binding.popularFonts.isVisible = result.fonts.isNotEmpty()
        binding.fontsRV.isVisible = result.fonts.isNotEmpty()

        // Files
        filesAdapter.updateList(result.files)
        binding.assets.isVisible = result.files.isNotEmpty()
        binding.filesRV.isVisible = result.files.isNotEmpty()

        // If all empty → show “No Results”
        val noResults =
            result.templates.isEmpty() && result.fonts.isEmpty() && result.files.isEmpty()
        binding.noEmojis.isVisible = noResults
    }

    data class SearchResults(
        val templates: List<TemplateEntity>, val fonts: List<FontEntity>, val files: List<Any>
    )

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}