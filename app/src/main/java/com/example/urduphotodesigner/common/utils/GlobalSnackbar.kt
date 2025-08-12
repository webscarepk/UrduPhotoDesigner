// ui/common/GlobalSnackbar.kt
package com.example.urduphotodesigner.common.utils

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.urduphotodesigner.R
import com.google.android.material.snackbar.Snackbar

object GlobalSnackbar {
    private var current: Snackbar? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Shows a success snackbar attached to the Activity root.
     * Survives Fragment navigation within the same Activity.
     *
     * @param anchor optional view to anchor above (e.g., BottomNav)
     */
    fun showSuccess(
        activity: Activity,
        message: String,
        actionText: String? = null,
        duration: Int = 8000,
        anchor: View? = null,
        onAction: (() -> Unit)? = null,
        @ColorInt successColor: Int = ContextCompat.getColor(activity, R.color.appColor)
    ) {
        main.post {
            current?.dismiss()

            val root = activity.findViewById<View>(android.R.id.content)
            val snack = Snackbar.make(root, message, duration)

            snack.view.backgroundTintList = ColorStateList.valueOf(successColor)
            snack.setActionTextColor(ContextCompat.getColor(activity, android.R.color.white))
            snack.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )?.setTextColor(ContextCompat.getColor(activity, android.R.color.white))

            // optional anchor (keeps it above BottomNavigationView/FAB)
            if (anchor != null) snack.anchorView = anchor

            // action
            if (actionText != null && onAction != null) {
                snack.setAction(actionText) {
                    onAction.invoke()
                    snack.dismiss()
                }
            }

            snack.show()
            current = snack
        }
    }

    fun dismiss() = main.post { current?.dismiss(); current = null }
}
