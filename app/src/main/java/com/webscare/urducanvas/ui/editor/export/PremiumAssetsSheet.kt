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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentPremiumAssetsSheetBinding
import com.webscare.urducanvas.viewmodels.MainViewModel

class PremiumAssetsSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPremiumAssetsSheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPremiumAssetsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val items = viewModel.getPremiumAssets()

        binding.assets.adapter = PremiumAssetsAdapter(items, mainViewModel.localFonts.value)

        binding.back.addPressEffect { dismiss() }
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
        fun newInstance() = PremiumAssetsSheet()
    }
}