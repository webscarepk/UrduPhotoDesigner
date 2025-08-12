// ui/common/SnackbarExt.kt
package com.example.urduphotodesigner.common.utils

import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun Fragment.showGlobalSuccessSnack(
    message: String,
    actionText: String = "Open",
    duration: Int = 8000,
    anchor: android.view.View? = null,
    onAction: () -> Unit
) {
    GlobalSnackbar.showSuccess(
        requireActivity(),
        message = message,
        actionText = actionText,
        duration = duration,
        anchor = anchor,
        onAction = onAction
    )
}
