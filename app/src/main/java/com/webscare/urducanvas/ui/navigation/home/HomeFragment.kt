package com.webscare.urducanvas.ui.navigation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.webscare.ads.NativeSize
import com.webscare.ads.WebsCareAds
import com.webscare.urducanvas.BuildConfig
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ErrorType
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.HomeUiState
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.showGlobalSuccessSnack
import com.webscare.urducanvas.data.model.orderWithUrduFirst
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.toExportResultFinal
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentHomeBinding
import com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
import com.webscare.urducanvas.ui.creation.CreateFragment
import com.webscare.urducanvas.ui.navigation.templates.TemplateCategoriesAdapter
import com.webscare.urducanvas.viewmodels.FiltersViewModel
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
    private val filtersVM: FiltersViewModel by activityViewModels()
    private var bundle: Bundle = Bundle()
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var recentAdapter: RecentAdapter
    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var popularTemplatesAdapter: PopularTemplatesAdapter
    private lateinit var canvasSizeAdapter: CanvasSizeAdapter
    private lateinit var categoryAdapter: TemplateCategoriesAdapter
    private lateinit var wrappedCategoryAdapter: RecyclerView.Adapter<*>
    private var downloadingTemplate: TemplateEntity? = null
    private var rotationAnimator: ObjectAnimator? = null

    // ── Header state ────────────────────────────────────────────────────────
    private var expandedHeaderHeight = 0
    private var isHeaderCollapsed = false
    private var headerAnimator: ValueAnimator? = null

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

        binding.homeNativeAd.setAdUnitIdAndSize(BuildConfig.AD_NATIVE_HOME, NativeSize.SMALL)

        setupHeader()
        setupSectionHeaders()
        setEvents()
        initObservers()
    }

    // ─── Header: status-bar bleed + expanded/collapsed states ────────────────

    private fun getStatusBarHeight(): Int {
        val rootInsets = ViewCompat.getRootWindowInsets(binding.root)
            ?: activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
        val insetTop = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (insetTop > 0) return insetTop

        val mainActInset = (activity as? MainActivity)?.statusBarInsetPx ?: 0
        if (mainActInset > 0) return mainActInset

        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val dimenPx = resources.getDimensionPixelSize(resourceId)
            if (dimenPx > 0) return dimenPx
        }

        return dp(32f)
    }

    private fun updateStatusBarHeight(explicitHeight: Int? = null) {
        val top = explicitHeight?.takeIf { it > 0 } ?: getStatusBarHeight()
        if (top > 0 && binding.statusSpacer.layoutParams.height != top) {
            binding.statusSpacer.updateLayoutParams { height = top }
            expandedHeaderHeight = 0
        }
    }

    /**
     * The header paints behind the status bar, so it pads itself by the inset
     * instead of relying on the activity's root padding (which is zeroed on home).
     */
    private fun setupHeader() {
        updateStatusBarHeight()
        binding.root.post { updateStatusBarHeight() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            updateStatusBarHeight(top)
            insets
        }

        // Collapsed row is a pure overlay — keep it out of the touch path until used.
        binding.collapsedContent.visibility = View.INVISIBLE

        binding.header.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (!isHeaderCollapsed && headerAnimator == null) {
                val h = bottom - top
                if (h > 0) expandedHeaderHeight = h
            }
        }

        val collapseAt = dp(28f)
        val expandAt = dp(8f)
        binding.contentScroll.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                when {
                    scrollY > collapseAt -> setHeaderCollapsed(true)
                    scrollY < expandAt -> setHeaderCollapsed(false)
                }
            }
        )
    }

    private fun collapsedHeaderHeight(): Int {
        val spacerH = if (binding.statusSpacer.height > 0) binding.statusSpacer.height else getStatusBarHeight()
        return spacerH + dp(56f) + dp(12f)
    }

    private fun setHeaderCollapsed(collapsed: Boolean) {
        if (_binding == null || isHeaderCollapsed == collapsed) return
        if (collapsed && expandedHeaderHeight <= 0) return
        isHeaderCollapsed = collapsed

        val header = binding.header
        val from = header.height
        val to = if (collapsed) collapsedHeaderHeight() else expandedHeaderHeight
        if (to <= 0) return

        headerAnimator?.cancel()

        if (collapsed) {
            binding.collapsedContent.visibility = View.VISIBLE
        } else {
            binding.expandedContent.visibility = View.VISIBLE
        }

        headerAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 260L
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val b = _binding ?: return@addUpdateListener
                val value = anim.animatedValue as Int
                b.header.updateLayoutParams { height = value }

                val fraction = anim.animatedFraction
                val collapseProgress = if (collapsed) fraction else 1f - fraction
                b.expandedContent.alpha = 1f - collapseProgress
                b.expandedContent.translationY = -dp(10f) * collapseProgress
                b.collapsedContent.alpha = collapseProgress
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    headerAnimator = null
                    val b = _binding ?: return
                    if (collapsed) {
                        b.expandedContent.visibility = View.INVISIBLE
                    } else {
                        b.collapsedContent.visibility = View.INVISIBLE
                        b.header.updateLayoutParams {
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                }
            })
            start()
        }
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).toInt()

    // ─── Section headers ─────────────────────────────────────────────────────

    private fun setupSectionHeaders() = binding.apply {
        recentProjects.sectionTitle.setText(R.string.recent_projects)
        popularTemplate.sectionTitle.setText(R.string.popular_templates)
        popularFonts.sectionTitle.setText(R.string.popular_fonts)
        sizesSection.sectionTitle.setText(R.string.browse_by_size)
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(true)
            setOnCancelListener {
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

    // ─── Navigation shortcuts ────────────────────────────────────────────────

    private fun openSearch() {
        val options = NavOptions.Builder().setEnterAnim(R.anim.slide_in_up).setExitAnim(0)
            .setPopEnterAnim(0).setPopExitAnim(R.anim.slide_out_down).build()
        findNavController().navigate(R.id.searchFragment, null, options)
    }

    private fun openNewCanvasSheet() {
        CreateFragment().show(parentFragmentManager, "CreateBottomSheet")
    }

    private fun openTemplate(template: TemplateEntity) {
        viewModel.setProjectSourceName(template.category ?: template.subcategory)
        val exportResult = template.toExportResultFinal()
            .copy(fileName = viewModel.buildProjectFileName())
        viewModel.loadTemplateFromJsonFile(
            exportResult, requireContext(), titleHint = "Loading Template"
        ) { success ->
            if (success && isAdded) {
                findNavController().navigate(R.id.editorFragment)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {

        binding.searchBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                openSearch()
                true
            } else {
                false
            }
        }

        // ── Header actions ───────────────────────────────────────────────────
        binding.filesBtn.addPressEffect {
            view?.post { findNavController().navigate(R.id.filesFragment, null, navOptions) }
        }

        binding.settingsBtn.addPressEffect {
            view?.post { findNavController().navigate(R.id.settingsFragment, null, navOptions) }
        }

        binding.create.addPressEffect { pickImageLauncher.launch("image/*") }
        binding.blankCanvas.addPressEffect { openNewCanvasSheet() }

        binding.collapsedSearch.addPressEffect { openSearch() }
        binding.collapsedAddPhoto.addPressEffect { pickImageLauncher.launch("image/*") }
        binding.collapsedNewCanvas.addPressEffect { openNewCanvasSheet() }

        // ── Recents ──────────────────────────────────────────────────────────
        recentAdapter = RecentAdapter(onClick = { exportResult ->
            viewModel.loadTemplateFromJsonFile(exportResult, requireContext(), titleHint = "Loading Project") { success ->
                if (success && isAdded) {
                    findNavController().navigate(R.id.editorFragment)
                }
            }
        })
        binding.recentsRV.adapter = recentAdapter

        // ── Popular templates ────────────────────────────────────────────────
        popularTemplatesAdapter = PopularTemplatesAdapter { template, isDownloaded ->
            if (template.is_downloading) return@PopularTemplatesAdapter

            if (isDownloaded) {
                if (!template.file_path.isNullOrBlank()) openTemplate(template)
            } else {
                downloadingTemplate = template
                popularTemplatesAdapter.updateProgress(
                    template.id, ProgressUi(
                        progress = 0, isDownloading = true, isDownloaded = false
                    )
                )
                mainViewModel.downloadTemplate(template)
            }
        }
        binding.popularTemplateRV.apply {
            adapter = popularTemplatesAdapter
            setHasFixedSize(true)
        }

        // ── Fonts ────────────────────────────────────────────────────────────
        fontsAdapter = FontsAdapter(onFontClick = { font, isDownloaded ->
            if (!isDownloaded) {
                mainViewModel.downloadFont(font)
            } else {
                viewModel.setCanvasSize(CanvasSize(id = 0, "", 2000f, 2000f))
                viewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText), font, requireActivity()
                )

                view?.post { findNavController().navigate(R.id.editorFragment, null, navOptions) }
            }
        }, onDownload = {
            mainViewModel.downloadFont(it)
        })
        binding.fontsRV.adapter = fontsAdapter

        // ── Browse by size — seeds the shared template filter, then opens it ──
        canvasSizeAdapter = CanvasSizeAdapter(emptyList(), onClick = { selected ->
            filtersVM.setCategory("All")
            filtersVM.setSize(selected)
            canvasSizeAdapter.selectedSizeName = selected.name
            view?.post {
                findNavController().navigate(R.id.templateCategoriesFragment, null, navOptions)
            }
        }, false)
        binding.sizesRV.adapter = canvasSizeAdapter

        // ── All templates, grouped by category ───────────────────────────────
        categoryAdapter = TemplateCategoriesAdapter(
            onSeeAll = { category ->
                val args = Bundle().apply { putString("CATEGORY_NAME", category) }
                view?.post { findNavController().navigate(R.id.templatesFragment, args) }
            },
            onTemplateClick = { template, isDownloaded ->
                if (template.is_downloading) return@TemplateCategoriesAdapter
                if (!isDownloaded) {
                    downloadingTemplate = template
                    categoryAdapter.updateTemplateProgress(template.id, 0, true, false)
                    mainViewModel.downloadTemplate(template)
                } else {
                    openTemplate(template)
                }
            }
        )
        wrappedCategoryAdapter = WebsCareAds.wrapWithNativeAds(
            originalAdapter = categoryAdapter,
            adUnitId = BuildConfig.AD_NATIVE_CATEGORIES,
            interval = 5,
            startOffset = 3,
            nativeSize = NativeSize.SMALL
        )
        binding.categoriesRV.apply {
            adapter = wrappedCategoryAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        // ── "See all" affordances ────────────────────────────────────────────
        binding.recentProjects.sectionSeeAllBtn.addPressEffect {
            val args = Bundle().apply { putInt("targetPage", 1) }  // 1 = "Projects"
            view?.post { findNavController().navigate(R.id.filesFragment, args) }
        }

        binding.popularTemplate.sectionSeeAllBtn.addPressEffect {
            val args = Bundle().apply { putString("FILTER_TYPE", "popular") }
            view?.post { findNavController().navigate(R.id.templatesListFragment, args) }
        }

        binding.popularFonts.sectionSeeAllBtn.addPressEffect {
            view?.post { findNavController().navigate(R.id.popularFontsFragment) }
        }

        binding.sizesSection.sectionSeeAllBtn.addPressEffect {
            filtersVM.clearFilters()
            view?.post {
                findNavController().navigate(R.id.templateCategoriesFragment, null, navOptions)
            }
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
                                val ui = ProgressUi(
                                    state.progress, isDownloading = true, isDownloaded = false
                                )
                                categoryAdapter.updateTemplateProgress(
                                    state.template.id, state.progress,
                                    isDownloading = true,
                                    isDownloaded = false
                                )
                                popularTemplatesAdapter.updateProgress(state.template.id, ui)
                            }

                            is TemplateDownloadState.SuccessWithTemplate -> {
                                val t = state.template
                                val ui = ProgressUi(
                                    100, isDownloading = false, isDownloaded = true
                                )
                                categoryAdapter.updateTemplateProgress(
                                    t.id, 100, isDownloading = false, isDownloaded = true
                                )
                                categoryAdapter.notifyTemplateStateChanged(
                                    t.copy(is_downloading = false, is_downloaded = true)
                                )
                                popularTemplatesAdapter.updateProgress(state.template.id, ui)

                                mainViewModel.clearTemplateDownloadState()
                                viewModel.clearCanvas()
                                viewModel.clearLoading()

                                findNavController().popBackStack(R.id.editorFragment, true)

                                showGlobalSuccessSnack("Template ready") { openTemplate(t) }
                            }

                            is TemplateDownloadState.Error -> {
                                val ui = ProgressUi(
                                    0, isDownloading = false, isDownloaded = false
                                )
                                downloadingTemplate?.let { t ->
                                    categoryAdapter.updateTemplateProgress(
                                        t.id, 0, isDownloading = false, isDownloaded = false
                                    )
                                    popularTemplatesAdapter.updateProgress(t.id, ui)
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

                mainViewModel.localCanvasSizes.collect { entities ->
                    val sizes = entities.map {
                        CanvasSize(id = it.id, name = it.name, width = it.width, height = it.height)
                    }
                    canvasSizeAdapter.submitList(sizes)
                    binding.sizesSection.root.visibility =
                        if (sizes.isEmpty()) View.GONE else View.VISIBLE
                    binding.sizesRV.visibility =
                        if (sizes.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.localTemplates.collect { all ->
                    val premiumTemplates = all.filter { it.is_popular }

                    popularTemplatesAdapter.submitList(premiumTemplates)

                    binding.popularTemplate.root.visibility =
                        if (premiumTemplates.isEmpty()) View.GONE else View.VISIBLE
                    binding.popularTemplateRV.visibility =
                        if (premiumTemplates.isEmpty()) View.GONE else View.VISIBLE

                    // Full templates feed — same category rows as the Templates screen.
                    val rows = all.groupBy { it.category?.trim()?.ifEmpty { "Others" } ?: "Others" }
                        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        .map { (title, templates) ->
                            HomeRow.CategoryRow(title, templates.distinctBy { it.id }.take(10))
                        }
                    categoryAdapter.submitList(rows)
                    binding.categoriesRV.visibility =
                        if (rows.isEmpty()) View.GONE else View.VISIBLE
                }

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                mainViewModel.localFonts.collect { fonts ->
                    val filteredFonts = fonts.filter { !it.font_category.equals("Imported", true) }
                    fontsAdapter.submitList(filteredFonts.orderWithUrduFirst())
                    binding.popularFonts.root.visibility =
                        if (filteredFonts.isEmpty()) View.GONE else View.VISIBLE
                    binding.fontsRV.visibility =
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
                                                id = 0, "", 2000f, 2000f
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
            val empty = results.isNullOrEmpty()
            binding.recentProjects.root.visibility = if (empty) View.GONE else View.VISIBLE
            binding.recentsRV.visibility = if (empty) View.GONE else View.VISIBLE
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
                    Snackbar.make(binding.root, "Failed to load template", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusBarHeight()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            viewModel.clearCanvas()
        }
    }

    override fun onDestroyView() {
        headerAnimator?.cancel()
        headerAnimator = null
        _binding?.recentsRV?.adapter = null
        _binding?.fontsRV?.adapter = null
        _binding?.sizesRV?.adapter = null
        _binding?.categoriesRV?.adapter = null
        _binding?.popularTemplateRV?.adapter = null
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
    }
}
