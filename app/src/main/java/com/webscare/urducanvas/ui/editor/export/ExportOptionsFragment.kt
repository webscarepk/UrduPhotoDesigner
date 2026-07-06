package com.webscare.urducanvas.ui.editor.export

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentExportOptionsBinding

class ExportOptionsFragment : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {

    private var _binding: FragmentExportOptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewType: ExportViewType
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private val subscriptionViewModel: com.webscare.urducanvas.viewmodels.SubscriptionsViewModel by viewModels()

    private lateinit var items: List<Any>
    private lateinit var adapter: ExportOptionAdapter<Any>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewType = ExportViewType.valueOf(requireArguments().getString(ARG_VIEW_TYPE)!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {

        items = when (viewType) {
            ExportViewType.RESOLUTION -> viewModel.availableResolutions
            ExportViewType.QUALITY -> viewModel.qualityOptions
            ExportViewType.FORMAT -> viewModel.formatOptions
        }

        binding.title.text = when (viewType) {
            ExportViewType.RESOLUTION -> "Choose Resolution"
            ExportViewType.QUALITY -> "Choose Quality"
            ExportViewType.FORMAT -> "Choose Format"
        }

        adapter = ExportOptionAdapter(
            items,
            viewType,
            true
        ) { selected ->
            when (selected) {
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(resolution = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(quality = selected)
                )

                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> viewModel.updateExportOptionsInMemory(
                    viewModel.exportOptions.value!!.copy(format = selected)
                )
            }
            binding.options.postDelayed({
                if (isAdded && view != null) {
                    dismissAllowingStateLoss()
                }
            }, 500)
        }

        binding.options.adapter = adapter

        binding.back.addPressEffect { dismiss() }
    }

    private fun initObservers() {
        viewModel.exportOptions.observe(viewLifecycleOwner) { options ->
            val items = when (viewType) {
                ExportViewType.RESOLUTION -> viewModel.availableResolutions
                ExportViewType.QUALITY -> viewModel.qualityOptions
                ExportViewType.FORMAT -> viewModel.formatOptions
            }
            adapter.updateList(items)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            subscriptionViewModel.isSubscribed.collect { subscribed ->
                adapter.isSubscribed = subscribed
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        forceImmersiveMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun forceImmersiveMode() {
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    hide(WindowInsets.Type.navigationBars()
                    )
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

    override fun onStart() {
        super.onStart()

        // 1. Clear the Window background (The very back layer)
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

            decorView.setOnSystemUiVisibilityChangeListener {
                forceImmersiveMode()
            }
        }

        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.background = ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_bg)
        bottomSheet.setBackgroundResource(android.R.color.transparent)

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
            WindowInsetsCompat.CONSUMED
        }

        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            isDraggable = true
            peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            skipCollapsed = true
        }

        forceImmersiveMode()
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialog
    }

    companion object {
        private const val ARG_VIEW_TYPE = "arg_view_type"

        fun newInstance(viewType: ExportViewType): ExportOptionsFragment {
            val fragment = ExportOptionsFragment()
            val args = Bundle()
            args.putString(ARG_VIEW_TYPE, viewType.name)
            fragment.arguments = args
            return fragment
        }
    }

}
