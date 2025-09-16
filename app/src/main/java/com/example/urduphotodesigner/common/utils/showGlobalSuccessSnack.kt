package com.example.urduphotodesigner.common.utils

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R

fun Fragment.showGlobalSuccessSnack(
    message: String,
    actionText: String = "Open",
    duration: Int = 8000,
    anchor: android.view.View? = null,
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
