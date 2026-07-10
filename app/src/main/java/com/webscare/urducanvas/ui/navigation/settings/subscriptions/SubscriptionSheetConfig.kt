package com.webscare.urducanvas.ui.navigation.settings.subscriptions

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.urducanvas.R
import com.webscare.urducanvas.databinding.FragmentSubscriptionBottomSheetBinding
import dagger.hilt.android.AndroidEntryPoint

data class SubscriptionSheetConfig(
    @DrawableRes val iconRes: Int,
    val title: String,
    val message: String,
    val primaryText: String,
    val showBg: Boolean = false,
    val secondaryText: String? = null,
    val cancelable: Boolean = true,
    val onPrimary: () -> Unit = {},
    val onSecondary: (() -> Unit)? = null,
)

@AndroidEntryPoint
class SubscriptionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSubscriptionBottomSheetBinding? = null
    private val binding get() = _binding!!

    var config: SubscriptionSheetConfig? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSubscriptionBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val c = config ?: return
        isCancelable = c.cancelable

        with(binding) {
            subscribedBg.isVisible = c.showBg
            icon.setImageResource(c.iconRes)
            title.text = c.title
            subTitle.text = c.message
            continueBtn.text = c.primaryText

            continueBtn.setOnClickListener {
                c.onPrimary()
                dismiss()
            }

            if (c.secondaryText != null || c.secondaryText == " ") {
                subOptionBtn.isVisible = true
                subOptionBtn.text = c.secondaryText
                subOptionBtn.setOnClickListener {
                    c.onSecondary?.invoke()
                    dismiss()
                }
            } else {
                subOptionBtn.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun forceImmersiveMode() {
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    hide(
                        WindowInsets.Type.navigationBars(),
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }

            decorView.setOnSystemUiVisibilityChangeListener {
                forceImmersiveMode()
            }
        }

        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
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

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onResume() {
        super.onResume()
        forceImmersiveMode()
    }

    companion object {
        fun newInstance(config: SubscriptionSheetConfig) = SubscriptionBottomSheet().apply { this.config = config }
    }
}
