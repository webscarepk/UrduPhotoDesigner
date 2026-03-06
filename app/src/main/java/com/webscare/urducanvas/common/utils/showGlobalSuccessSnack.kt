package com.webscare.urducanvas.common.utils

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R

fun Fragment.showGlobalSuccessSnack(
    message: String,
    actionText: String = "Open",
    duration: Int = 2000,
    anchor: View? = null,
    onAction: () -> Unit
) {
    val navController = findNavController()
    if (navController.currentDestination?.id == R.id.editorFragment) {
        return
    }
    GlobalSnackbar.showSuccess(
        requireActivity(),
        message = message,
        actionText = actionText,
        duration = duration,
        anchor = anchor,
        onAction = onAction
    )
}
