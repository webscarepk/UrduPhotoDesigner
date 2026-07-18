package com.webscare.urducanvas.ui.navigation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ErrorType
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.canvas.sealed.HomeUiState
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentHomeBinding
import com.webscare.urducanvas.ui.creation.CreateFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private var bundle: Bundle = Bundle()
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var recentAdapter: RecentAdapter
    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var trendsAdapter: TrendsAdapter
    private lateinit var popularTemplatesAdapter: PopularTemplatesAdapter
    private var downloadingTemplate: TemplateEntity? = null
    private var rotationAnimator: ObjectAnimator? = null

    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                // Decode off the main thread — BitmapFactory.decodeStream can block for
                // several seconds on large photos and would ANR if called here directly.
                lifecycleScope.launch(Dispatchers.IO) {
                    val rawBitmap = requireContext().contentResolver.openInputStream(it)
                        ?.use { stream -> android.graphics.BitmapFactory.decodeStream(stream) }
                        ?: return@launch

                    // Apply GPU hard limit — same cap used by every other image entry point.
                    val bitmap = com.webscare.urducanvas.common.utils.ImageProcessor
                        .downsampleIfNeeded(rawBitmap, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)

                    val widthVal = bitmap.width.toFloat()
                    val heightVal = bitmap.height.toFloat()

                    withContext(Dispatchers.Main) {
                        val canvasSize = CanvasSize(id = 0, "From Image", widthVal, heightVal)
                        viewModel.clearCanvas()
                        viewModel.setCanvasSize(canvasSize)
                        viewModel.setCanvasBackgroundImage(bitmap, requireActivity())
                        val editorNavOptions = NavOptions.Builder().setLaunchSingleTop(true)
                            .setPopUpTo(R.id.editorFragment, inclusive = true)
                            .build()
                        findNavController().navigate(R.id.editorFragment, null, editorNavOptions)
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
        (activity as? MainActivity)?.bindScrollToNav(binding.contentScroll)
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(true)
            setOnCancelListener { dialog ->
                viewModel.clearLoading()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {

        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val options = NavOptions.Builder().setEnterAnim(R.anim.slide_in_up).setExitAnim(0)
                    .setPopEnterAnim(0).setPopExitAnim(R.anim.slide_out_down).build()
                findNavController().navigate(R.id.searchFragment, null, options)
                true
            } else {
                false
            }
        }

        recentAdapter = RecentAdapter(onClick = { exportResult ->
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                }
            }
        })

        binding.recentsRV.adapter = recentAdapter

        fontsAdapter = FontsAdapter(onFontClick = { font, isDownloaded ->
            if (!isDownloaded) {
                mainViewModel.downloadFont(font)
            } else {
                viewModel.setCanvasSize(
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.model.CanvasSize(
                        id = 0,"", 2000f, 2000f
                    )
                )
                viewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText), font, requireActivity()
                )

                view?.post { findNavController().navigate(R.id.editorFragment, null, navOptions) }
            }
        }, onDownload = {
            mainViewModel.downloadFont(it)
        })

        binding.fontsRV.adapter = fontsAdapter

        trendsAdapter = TrendsAdapter(onSeeAll = { trendTitle ->
            val args = Bundle().apply { putString("TREND_NAME", trendTitle) }
            view?.post { findNavController().navigate(R.id.templatesFragment, args) }
        }, onTemplateClick = { template, isDownloaded ->
            if (template.is_downloading) return@TrendsAdapter
            if (!isDownloaded) {
                downloadingTemplate = template
                trendsAdapter.updateTemplateProgress(
                    template.id, progress = 0, isDownloading = true, isDownloaded = false
                )
                mainViewModel.downloadTemplate(template)
                return@TrendsAdapter
            } else {
                viewModel.setProjectSourceName(template.category ?: template.subcategory)
                val exportResult = template.toExportResultFinal().copy(fileName = viewModel.buildProjectFileName())
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                    }
                }
                return@TrendsAdapter
            }
        })

        binding.trendsRV.apply {
            adapter = trendsAdapter
            setHasFixedSize(true)
        }

        popularTemplatesAdapter = PopularTemplatesAdapter(onClick = { template, isDownloaded ->
            if (template.is_downloading) return@PopularTemplatesAdapter
            if (isDownloaded) {
                if (template.file_path.isNullOrEmpty()) {
                    popularTemplatesAdapter.updateProgress(
                        template.id,
                        ProgressUi(
                            progress = 0, isDownloading = true, isDownloaded = false
                        )
                    )
                    mainViewModel.downloadTemplate(template)
                    return@PopularTemplatesAdapter
                } else {
                    viewModel.setProjectSourceName(template.category ?: template.subcategory)
                    val exportResult = template.toExportResultFinal().copy(fileName = viewModel.buildProjectFileName())
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                        }
                    }
                }
            } else {
                downloadingTemplate = template
                popularTemplatesAdapter.updateProgress(
                    template.id, ProgressUi(
                        progress = 0, isDownloading = true, isDownloaded = false
                    )
                )
                mainViewModel.downloadTemplate(template)
            }
        })

        binding.popularTemplateRV.apply {
            adapter = popularTemplatesAdapter
            setHasFixedSize(true)
        }

        binding.subscriptions.addPressEffect {
            view?.post { findNavController().navigate(R.id.subscriptionsFragment) }
        }

        binding.create.addPressEffect {
            pickImageLauncher.launch("image/*")
        }

        binding.blankCanvas.addPressEffect {
            val bottomSheet = CreateFragment()
            bottomSheet.show(parentFragmentManager, "CreateBottomSheet")
        }

        binding.templates.addPressEffect {
            view?.post { findNavController().navigate(R.id.templateCategoriesFragment) }
        }

        binding.popularFonts.addPressEffect {
            view?.post { findNavController().navigate(R.id.popularFontsFragment) }
        }

        binding.popularTemplate.addPressEffect {
            val args = Bundle().apply { putString("FILTER_TYPE", "popular") }
            view?.post { findNavController().navigate(R.id.templatesListFragment, args) }
        }

        binding.recentProjects.addPressEffect {
            val bundle = Bundle().apply {
                putInt("targetPage", 1)  // 1 = "Projects"
            }
            view?.post { findNavController().navigate(R.id.filesFragment, bundle) }
        }

        binding.fileTab.addPressEffect {
            view?.post { findNavController().navigate(R.id.filesFragment) }
        }
    }

    private fun renderHomeState(state: HomeUiState) {
        binding.apply {
            when (state) {
                is HomeUiState.Loading -> {
                    Log.d("HomeState", "→ LOADING branch")
                    loadingState.root.visibility = View.VISIBLE
                    errorState.root.visibility = View.GONE
                    contentScroll.visibility = View.GONE
                }
                is HomeUiState.Content -> {
                    Log.d("HomeState", "→ CONTENT branch")
                    loadingState.root.visibility = View.GONE
                    errorState.root.visibility = View.GONE
                    contentScroll.visibility = View.VISIBLE
                }
                is HomeUiState.Empty -> {
                    Log.d("HomeState", "→ EMPTY branch")
                    loadingState.root.visibility = View.GONE
                    contentScroll.visibility = View.GONE
                    errorState.root.visibility = View.VISIBLE
                    errorState.errorIcon.setImageResource(R.drawable.ic_nothing_found) // your empty icon
                    errorState.errorTitle.text = "Nothing here yet"
                    errorState.errorMessage.text = "Check back soon for new templates and fonts"
                    errorState.retryButton.addPressEffect {
                        mainViewModel.retryHomeData()
                    }
                }
                is HomeUiState.Error -> {
                    Log.d("HomeState", "→ ERROR branch, type=${state.type}")
                    loadingState.root.visibility = View.GONE
                    contentScroll.visibility = View.GONE
                    errorState.root.visibility = View.VISIBLE

                    when (state.type) {
                        ErrorType.NO_INTERNET -> {
                            errorState.errorIcon.setImageResource(R.drawable.ic_no_internet)
                            errorState.errorTitle.text = "No connection"
                            errorState.errorMessage.text = "Check your internet and try again"
                        }
                        ErrorType.SERVER_ERROR, ErrorType.UNKNOWN -> {
                            errorState.errorIcon.setImageResource(R.drawable.ic_info)
                            errorState.errorTitle.text = "Something went wrong"
                            errorState.errorMessage.text = state.message.ifEmpty { "Please try again in a moment" }
                        }
                    }
                    errorState.retryButton.addPressEffect {
                        mainViewModel.retryHomeData()
                    }
                }
            }
        }
    }

    private fun initObservers() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.homeUiState.collect { state ->
                    Log.d("HomeState", "Fragment received: $state")
                    renderHomeState(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.templateDownloadStates.collect { downloadState ->
                    downloadState.values.forEach { state ->
                        when (state) {
                            is TemplateDownloadState.Progress -> {
                                val ui =
                                    ProgressUi(
                                        state.progress, isDownloading = true, isDownloaded = false
                                    )
                                trendsAdapter.updateTemplateProgress(
                                    state.template.id, state.progress,
                                    isDownloading = true,
                                    isDownloaded = false
                                )
                                popularTemplatesAdapter.updateProgress(state.template.id, ui)
                            }

                            is TemplateDownloadState.SuccessWithTemplate -> {
                                val t = state.template
                                val ui =
                                    ProgressUi(
                                        100, isDownloading = false, isDownloaded = true
                                    )
                                trendsAdapter.updateTemplateProgress(
                                    t.id, 100, isDownloading = false, isDownloaded = true
                                )
                                trendsAdapter.notifyTemplateStateChanged(t)
                                popularTemplatesAdapter.updateProgress(state.template.id, ui)

                                mainViewModel.clearTemplateDownloadState()
                                viewModel.clearCanvas()
                                viewModel.clearLoading()

                                findNavController().popBackStack(R.id.editorFragment, true)

                                showGlobalSuccessSnack("Template ready") {
                                    viewModel.setProjectSourceName(t.category ?: t.subcategory)
                                    val exportResult = t.toExportResultFinal().copy(fileName = viewModel.buildProjectFileName())
                                    lifecycleScope.launch {
                                        viewModel.loadTemplateFromJsonFile(
                                            exportResult, requireContext()
                                        )
                                    }
                                }
                            }

                            is TemplateDownloadState.Error -> {
                                val ui =
                                    ProgressUi(
                                        0, isDownloading = false, isDownloaded = false
                                    )
                                downloadingTemplate?.let { t ->
                                    trendsAdapter.updateTemplateProgress(
                                        t.id, 0, isDownloading = false, isDownloaded = false
                                    )
                                    popularTemplatesAdapter.updateProgress(
                                        t.id, ui
                                    )
                                }
                            }

                            is TemplateDownloadState.Success -> {
                                mainViewModel.clearTemplateDownloadState()
                            }
                        }
                    }
                }

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.trendRows.collect { rows ->
                    trendsAdapter.submitList(rows)
                }

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.localTemplates.collect { all ->
                    val premiumTemplates = all.filter { it.is_popular }

                    popularTemplatesAdapter.submitList(premiumTemplates)

                    binding.popularTemplate.visibility =
                        if (premiumTemplates.isEmpty()) View.GONE else View.VISIBLE
                }

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.localFonts.collect { fonts ->
                    val filteredFonts = fonts.filter { !it.font_category.equals("Imported", true) }
                    fontsAdapter.submitList(filteredFonts.reversed())
                    binding.popularFonts.visibility =
                        if (filteredFonts.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.fontDownloadStates.collect { downloadState ->
                    downloadState.values.forEach { state ->
                        when (state) {
                            is FontDownloadState.Progress -> {
                                val font = state.fontEntity
                                fontsAdapter.updateProgress(
                                    font.id,
                                    ProgressUi(
                                        progress = state.progress,
                                        isDownloading = true,
                                        isDownloaded = false
                                    )
                                )
                            }

                            is FontDownloadState.SuccessWithTypeface -> {
                                val font = state.fontEntity

                                fontsAdapter.updateProgress(
                                    font.id,
                                    ProgressUi(
                                        100, isDownloading = false, isDownloaded = true
                                    )
                                )

                                showGlobalSuccessSnack("Font downloaded") {
                                    lifecycleScope.launch {
                                        viewModel.clearCanvas()
                                        viewModel.clearLoading()
                                        findNavController().popBackStack(R.id.editorFragment, true)

                                        viewModel.setCanvasSize(
                                            CanvasSize(
                                                id = 0,"", 2000f, 2000f
                                            )
                                        )
                                        viewModel.addTextWithFont(
                                            requireActivity().getString(R.string.dummyText),
                                            font,
                                            requireActivity()
                                        )

                                        if (isAdded && findNavController().currentDestination?.id != R.id.editorFragment) {
                                            view?.post {
                                                findNavController().navigate(
                                                    R.id.editorFragment, bundle, navOptions
                                                )
                                            }
                                        }
                                    }
                                    mainViewModel.clearFontDownloadState()
                                }
                            }

                            is FontDownloadState.Error -> {
                                val font = state.fontEntity
                                fontsAdapter.updateProgress(
                                    font.id,
                                    ProgressUi(
                                        progress = 0, isDownloading = false, isDownloaded = false
                                    )
                                )

                                mainViewModel.clearFontDownloadState()
                                if (isAdded) {
                                    Snackbar.make(
                                        requireView(), "Download failed!", Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            else -> {}
                        }
                    }
                }

            }
        }

        mainViewModel.exportResults.observe(viewLifecycleOwner) { results ->
            recentAdapter.submitList(results)
            binding.recentProjects.visibility =
                if (results.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            dialogBinding?.apply {
                progressBar.progress = percent
                subtitle.text = "$message... $percent%"
                tvProgressPercent.text = "$percent% complete"
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                if (viewModel.canvasSize.value != null) {
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
                } else {
                    android.widget.Toast.makeText(context, "Failed to load template", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            viewModel.clearCanvas()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
    }
}