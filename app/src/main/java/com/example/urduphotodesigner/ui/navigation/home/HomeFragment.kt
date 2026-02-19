package com.example.urduphotodesigner.ui.navigation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.FontDownloadState
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.showGlobalSuccessSnack
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.example.urduphotodesigner.ui.creation.CreateFragment
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var bundle: Bundle = Bundle()
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private lateinit var recentAdapter: RecentAdapter
    private lateinit var fontsAdapter: FontsAdapter
    private lateinit var trendsAdapter: TrendsAdapter
    private lateinit var popularTemplatesAdapter: PopularTemplatesAdapter
    private var downloadingTemplate: TemplateEntity? = null
    private var rotationAnimator: ObjectAnimator? = null

    private var isNavigatingToSearch = false
    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val widthVal = bitmap.width.toFloat()
                val heightVal = bitmap.height.toFloat()

                val canvasSize = CanvasSize("From Image", widthVal, heightVal)

                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                viewModel.setCanvasBackgroundImage(bitmap)
                val editorNavOptions = NavOptions.Builder().setLaunchSingleTop(true)
                    .setPopUpTo(R.id.editorFragment, inclusive = true) // 🔥 this line is key
                    .build()
                findNavController().navigate(R.id.editorFragment, null, editorNavOptions)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.show(
                WindowInsets.Type.statusBars()
            )
        }

        setEvents()
        initObservers()
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(false)
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

    private fun setEvents() {

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
                viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
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
            view?.post { findNavController().navigate(R.id.templatesListFragment, args) }
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
                val exportResult = template.toExportResultFinal()
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
                        ProgressUi(progress = 0, isDownloading = true, isDownloaded = false)
                    )
                    mainViewModel.downloadTemplate(template)
                    return@PopularTemplatesAdapter
                } else {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                        }
                    }
                }
            } else {
                downloadingTemplate = template
                popularTemplatesAdapter.updateProgress(
                    template.id,
                    ProgressUi(progress = 0, isDownloading = true, isDownloaded = false)
                )
                mainViewModel.downloadTemplate(template)
            }
        })

        binding.popularTemplateRV.apply {
            adapter = popularTemplatesAdapter
            setHasFixedSize(true)
        }

        binding.create.addPressEffect {
            pickImageLauncher.launch("image/*")
        }

        binding.blankCanvas.addPressEffect {
            val bottomSheet = CreateFragment()
            bottomSheet.show(parentFragmentManager, "CreateBottomSheet")
        }

        binding.templates.addPressEffect {
            view?.post { findNavController().navigate(R.id.templatesFragment) }
        }

        binding.popularFonts.addPressEffect {
            view?.post { findNavController().navigate(R.id.popularFontsFragment) }
        }

        binding.popularTemplate.addPressEffect {
            view?.post { findNavController().navigate(R.id.templatesListFragment) }
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

        binding.subscriptions.addPressEffect { view?.post { findNavController().navigate(R.id.subscriptionsFragment) } }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()

                if (!isNavigatingToSearch && query.isNotEmpty()) {
                    isNavigatingToSearch = true

                    mainViewModel.setQuery(query)
                    val bundle = Bundle().apply {
                        putString("initialQuery", query)
                    }

                    val options =
                        NavOptions.Builder().setEnterAnim(R.anim.slide_in_up).setExitAnim(0)
                            .setPopEnterAnim(0).setPopExitAnim(R.anim.slide_out_down).build()

                    findNavController().navigate(R.id.searchFragment, bundle, options)
                } else if (query.isEmpty()) {
                    // Reset when user clears text
                    isNavigatingToSearch = false
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.templateDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is TemplateDownloadState.Progress -> {
                            val ui = ProgressUi(
                                state.progress, isDownloading = true, isDownloaded = false
                            )
                            trendsAdapter.updateTemplateProgress(
                                state.template.id, state.progress, true, false
                            )
                            popularTemplatesAdapter.updateProgress(state.template.id, ui)
                        }

                        is TemplateDownloadState.SuccessWithTemplate -> {
                            val t = state.template
                            val ui = ProgressUi(100, isDownloading = false, isDownloaded = true)
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
                                val exportResult = t.toExportResultFinal()
                                lifecycleScope.launch {
                                    viewModel.loadTemplateFromJsonFile(
                                        exportResult, requireContext()
                                    )
                                }
                            }
                        }

                        is TemplateDownloadState.Error -> {
                            val ui = ProgressUi(0, isDownloading = false, isDownloaded = false)
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

        lifecycleScope.launch {
            mainViewModel.trendRows.collect { rows ->
                trendsAdapter.submitList(rows)
            }
        }

        lifecycleScope.launch {
            mainViewModel.localTemplates.collect { all ->
                val premiumTemplates = all.filter { it.is_popular }

                popularTemplatesAdapter.submitList(premiumTemplates)

                binding.popularTemplate.visibility =
                    if (premiumTemplates.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                val filteredFonts = fonts.filter { !it.font_category.equals("Imported", true) }
                fontsAdapter.submitList(filteredFonts)
                binding.popularFonts.visibility =
                    if (filteredFonts.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            mainViewModel.fontDownloadStates.collect { downloadState ->
                downloadState.values.forEach { state ->
                    when (state) {
                        is FontDownloadState.Progress -> {
                            val font = state.fontEntity
                            fontsAdapter.updateProgress(
                                font.id, ProgressUi(
                                    progress = state.progress,
                                    isDownloading = true,
                                    isDownloaded = false
                                )
                            )
                        }

                        is FontDownloadState.SuccessWithTypeface -> {
                            val font = state.fontEntity

                            fontsAdapter.updateProgress(
                                font.id, ProgressUi(100, isDownloading = false, isDownloaded = true)
                            )

                            showGlobalSuccessSnack("Font downloaded") {
                                lifecycleScope.launch {
                                    viewModel.clearCanvas()
                                    viewModel.clearLoading()
                                    findNavController().popBackStack(R.id.editorFragment, true)

                                    viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
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
                            }
                            mainViewModel.clearFontDownloadState()
                        }

                        is FontDownloadState.Error -> {
                            val font = state.fontEntity
                            fontsAdapter.updateProgress(
                                font.id, ProgressUi(
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
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToSearch = false
        binding.searchBar.text?.clear()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            viewModel.clearCanvas()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}