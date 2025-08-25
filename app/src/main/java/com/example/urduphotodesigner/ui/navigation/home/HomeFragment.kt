package com.example.urduphotodesigner.ui.navigation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.FontDownloadState
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.utils.DialogUtils
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.showGlobalSuccessSnack
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.toExportResultFinal
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.example.urduphotodesigner.databinding.LayoutProjectPopupBinding
import com.example.urduphotodesigner.ui.creation.CreateFragment
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    private var downloadingTemplate: TemplateEntity? = null
    private var rotationAnimator: ObjectAnimator? = null

    val navOptions = NavOptions.Builder()
        .setPopUpTo(R.id.editorFragment, inclusive = true) // clear old EditorFragment
        .setLaunchSingleTop(true) // avoid duplicate if same fragment is on top
        .build()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val widthVal = bitmap.width.toFloat()
                val heightVal = bitmap.height.toFloat()

                val canvasSize = CanvasSize("From Image", widthVal, heightVal)
                val bundle = Bundle().apply {
                    putSerializable("canvas_size", canvasSize)
                    putSerializable("unit_type", UnitType.PIXELS)
                }

                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                viewModel.setCanvasBackgroundImage(bitmap)
                findNavController().navigate(R.id.editorFragment, bundle, navOptions)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setGravity(Gravity.CENTER)
            show()
        }

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
                    bundle = Bundle().apply {
                        putSerializable("canvas_size", exportResult.canvasSize)
                        putSerializable("unit_type", UnitType.PIXELS)
                    }
                }
            }
        }, onLongClick = { view, exportResult ->
            showPopupMenu(view, exportResult)
        })

        binding.recentsRV.adapter = recentAdapter

        fontsAdapter = FontsAdapter(onFontClick = { font, isDownloaded ->
            if (!isDownloaded) {
                mainViewModel.downloadFont(font)
            } else {
                viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
                viewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText),
                    font,
                    requireActivity()
                )
                bundle = Bundle().apply {
                    putSerializable("canvas_size", CanvasSize("", 2000f, 2000f))
                    putSerializable("unit_type", UnitType.PIXELS)
                }
                findNavController().navigate(R.id.editorFragment, bundle, navOptions)
            }
        }, onDownload = {
            mainViewModel.downloadFont(it)
        })

        binding.fontsRV.adapter = fontsAdapter

        trendsAdapter = TrendsAdapter(
            onSeeAll = { trendTitle ->
                val args = Bundle().apply { putString("TREND_NAME", trendTitle) }
                findNavController().navigate(R.id.templatesListFragment, args)
            },
            onTemplateClick = { template, isDownloaded ->
                if (isDownloaded) {
                    val exportResult = template.toExportResultFinal()
                    lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                            bundle = Bundle().apply {
                                putSerializable("canvas_size", exportResult.canvasSize)
                                putSerializable("unit_type", UnitType.PIXELS)
                            }
                        }
                    }
                    return@TrendsAdapter
                }
                downloadingTemplate = template
                // start download
                trendsAdapter.updateTemplateProgress(
                    template.id,
                    ProgressUi(
                        progress = 0,
                        isDownloading = true,
                        isDownloaded = false
                    )
                )
                mainViewModel.downloadTemplate(template)
            }
        )

        binding.trendsRV.apply {
            adapter = trendsAdapter
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
            findNavController().navigate(R.id.templatesFragment)
        }
    }

    private fun initObservers() {

        lifecycleScope.launch {
            mainViewModel.templateDownloadState.collect { state ->
                when (state) {
                    is TemplateDownloadState.Progress -> {
                        val t = state.template
                        trendsAdapter.updateTemplateProgress(
                            t.id,
                            ProgressUi(
                                progress = state.progress,
                                isDownloading = true,
                                isDownloaded = false
                            )
                        )
                    }

                    is TemplateDownloadState.SuccessWithTemplate -> {
                        val t = state.template
                        trendsAdapter.updateTemplateProgress(
                            t.id,
                            ProgressUi(
                                progress = 100,
                                isDownloading = false,
                                isDownloaded = true
                            )
                        )
                        mainViewModel.clearTemplateDownloadState()

                        showGlobalSuccessSnack("Template ready") {
                            val exportResult = t.toExportResultFinal()
                            lifecycleScope.launch {
                                viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                                bundle = Bundle().apply {
                                    putSerializable("canvas_size", exportResult.canvasSize)
                                    putSerializable("unit_type", UnitType.PIXELS)
                                }
                            }
                        }
                    }

                    is TemplateDownloadState.Error -> {
                        val t = downloadingTemplate ?: return@collect
                        trendsAdapter.updateTemplateProgress(
                            t.id,
                            ProgressUi(
                                progress = 0,
                                isDownloading = false,
                                isDownloaded = false
                            )
                        )
                    }

                    is TemplateDownloadState.Success -> {
                        mainViewModel.clearTemplateDownloadState()
                    }

                    null -> Unit
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.trendRows.collect { rows ->
                trendsAdapter.submitList(rows)
            }
        }

        lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
                fontsAdapter.submitList(fonts)
                binding.popularFonts.visibility =
                    if (fonts.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            mainViewModel.downloadState.collect { downloadState ->
                when (downloadState) {
                    is FontDownloadState.Progress -> {
                        val font = downloadState.fontEntity
                        fontsAdapter.updateProgress(
                            font.id,
                            ProgressUi(
                                progress = downloadState.progress,
                                isDownloading = true,
                                isDownloaded = false
                            )
                        )
                    }

                    is FontDownloadState.SuccessWithTypeface -> {
                        val font = downloadState.fontEntity

                        fontsAdapter.updateProgress(
                            font.id,
                            ProgressUi(100, isDownloading = false, isDownloaded = true)
                        )

                        showGlobalSuccessSnack("Font downloaded") {
                            lifecycleScope.launch {
                                viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
                                viewModel.addTextWithFont(
                                    requireActivity().getString(R.string.dummyText),
                                    font,
                                    requireActivity()
                                )
                                bundle = Bundle().apply {
                                    putSerializable("canvas_size", CanvasSize("", 2000f, 2000f))
                                    putSerializable("unit_type", UnitType.PIXELS)
                                }
                                findNavController().navigate(R.id.editorFragment, bundle, navOptions)
                            }
                        }
                        mainViewModel.clearDownloadState()
                    }

                    is FontDownloadState.Error -> {
                        val font = downloadState.fontEntity
                        fontsAdapter.updateProgress(
                            font.id,
                            ProgressUi(
                                progress = 0,
                                isDownloading = false,
                                isDownloaded = false
                            )
                        )

                        mainViewModel.clearDownloadState()
                        Snackbar.make(requireView(), "Download failed!", Snackbar.LENGTH_SHORT)
                            .show()
                    }

                    else -> {}
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
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                showLoadingDialog()
            } else if (isLoading == false) {
                dismissLoadingDialog()
                findNavController().navigate(R.id.editorFragment, bundle, navOptions)
            }
        }
    }

    private fun showPopupMenu(
        anchorView: View,
        item: ExportResult,
    ) {
        val binding = LayoutProjectPopupBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            binding.root,
            (150 * requireActivity().resources.displayMetrics.density).toInt(), // fixed width ~200dp
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.animationStyle = R.style.PopupFadeAnimation

        // -- Handle actions
        binding.actionOpen.addPressEffect {
            popupWindow.dismiss()
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    viewModel.loadTemplateFromJsonFile(item, requireContext())
                    bundle = Bundle().apply {
                        putSerializable("canvas_size", item.canvasSize)
                        putSerializable("unit_type", UnitType.PIXELS)
                    }
                }
            }
        }

        binding.actionDuplicate.addPressEffect {
            popupWindow.dismiss()
            lifecycleScope.launch {
                val srcImage = File(item.imagePath)
                val srcJson = File(item.jsonPath)

                // use same hierarchy as ImageProcessor
                val newImageFile =
                    ImageProcessor.newExportImageFile(requireActivity(), srcImage.name)
                val newJsonFile =
                    ImageProcessor.newExportJsonFile(requireActivity(), srcJson.name)

                ImageProcessor.copyFile(srcImage, newImageFile)
                ImageProcessor.copyFile(srcJson, newJsonFile)

                val newExport = item.copy(
                    id = 0,
                    imagePath = newImageFile.absolutePath,
                    jsonPath = newJsonFile.absolutePath,
                    fileName = "${item.fileName}_copy",
                    updatedDate = System.currentTimeMillis().toString()
                )
                mainViewModel.insertExportResult(newExport)
            }
        }

        binding.actionShare.addPressEffect {
            popupWindow.dismiss()
            // Share logic
        }

        binding.actionRename.addPressEffect {
            popupWindow.dismiss()
            // Rename logic
        }

        binding.actionDelete.addPressEffect {
            popupWindow.dismiss()
            DialogUtils.showDeleteDialog(
                requireActivity(),
                titleText = getString(R.string.delete_image),
                subtitleText = getString(R.string.your_asset_will_be_permanently_deleted)
            ) {
                mainViewModel.deleteExportResult(item)
            }
        }

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            viewModel.clearCanvas()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}