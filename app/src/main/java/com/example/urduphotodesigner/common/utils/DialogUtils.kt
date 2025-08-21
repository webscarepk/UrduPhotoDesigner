package com.example.urduphotodesigner.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.example.urduphotodesigner.R

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

        val dialog = android.app.AlertDialog.Builder(context, R.style.CustomDialog)
            .setView(view)
            .setCancelable(false)
            .create()

        val title = view.findViewById<TextView>(R.id.title)
        val subTitle = view.findViewById<TextView>(R.id.subTitle)
        val cancel = view.findViewById<TextView>(R.id.cancel)
        val confirm = view.findViewById<TextView>(R.id.confirm)

        title.text = titleText
        subTitle.text = subtitleText

        cancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

}