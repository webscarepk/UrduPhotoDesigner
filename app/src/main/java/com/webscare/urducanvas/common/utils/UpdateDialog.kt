package com.webscare.urducanvas.common.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.webscare.urducanvas.databinding.DialogUpdateAvailableBinding

// UpdateDialog.kt
class UpdateDialog(
    private val context: Context,
    private val onUpdateNow: () -> Unit,
    private val onRemindLater: (() -> Unit)? = null, // null = hide "Remind me Later"
    private val isCancelable: Boolean = true,
) {

    private var dialog: Dialog? = null

    fun show() {
        dialog = Dialog(context)
        val binding = DialogUpdateAvailableBinding.inflate(LayoutInflater.from(context))

        dialog?.apply {
            setContentView(binding.root)
            setCancelable(isCancelable)

            // Transparent background + proper width
            window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.70).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
                setGravity(Gravity.CENTER)
            }
        }

        // Handle "Remind Me Later" visibility
        if (onRemindLater == null) {
            binding.subOptionBtn.visibility = View.GONE
        } else {
            binding.subOptionBtn.visibility = View.VISIBLE
            binding.subOptionBtn.setOnClickListener {
                dismiss()
                onRemindLater.invoke()
            }
        }

        binding.continueBtn.setOnClickListener {
            dismiss()
            onUpdateNow.invoke()
        }

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun isShowing(): Boolean = dialog?.isShowing == true
}
