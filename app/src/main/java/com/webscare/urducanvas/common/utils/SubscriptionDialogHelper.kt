package com.webscare.urducanvas.common.utils

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutSubscriptionDialogBinding

object SubscriptionDialogHelper {

    fun show(
        context: Context,
        iconRes: Int,
        iconTint: Int? = null,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String? = null,
        cancelable: Boolean = true,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val binding = LayoutSubscriptionDialogBinding.inflate(LayoutInflater.from(context))

        binding.title.text = title
        binding.subTitle.text = message
        binding.confirm.text = confirmText

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(cancelable)
            .create()
            .also { it.window?.setBackgroundDrawableResource(android.R.color.transparent) }

        binding.confirm.addPressEffect {
            dialog.dismiss()
            onConfirm()
        }

        if (cancelText != null) {
            binding.cancel.visibility = View.VISIBLE
            binding.cancel.text = cancelText
            binding.cancel.addPressEffect {
                dialog.dismiss()
                onCancel?.invoke()
            }
        } else {
            binding.cancel.visibility = View.GONE
        }

        dialog.show()
    }
}