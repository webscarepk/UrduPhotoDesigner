package com.example.urduphotodesigner.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.TextView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import androidx.core.graphics.drawable.toDrawable

object DialogUtils {

    @SuppressLint("InflateParams")
    fun showDeleteDialog(
        context: Context,
        titleText: String,
        subtitleText: String,
        onConfirm: () -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_dialog_delete, null)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        val title = view.findViewById<TextView>(R.id.title)
        val subTitle = view.findViewById<TextView>(R.id.subTitle)
        val cancel = view.findViewById<TextView>(R.id.cancel)
        val confirm = view.findViewById<TextView>(R.id.confirm)

        title.text = titleText
        subTitle.text = subtitleText

        cancel.addPressEffect { dialog.dismiss() }
        confirm.addPressEffect {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

    }

}