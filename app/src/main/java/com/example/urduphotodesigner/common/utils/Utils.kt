package com.example.urduphotodesigner.common.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

object Utils {
    @SuppressLint("ClickableViewAccessibility")
    fun View.addPressEffect(onClick: (() -> Unit)? = null) {
        var isInside = false

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isInside = true
                    v.animate()
                        .scaleX(0.9f) // more noticeable shrink
                        .scaleY(0.9f)
                        .setDuration(80)
                        .start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Check if finger is still inside view bounds
                    val insideNow = event.x >= 0 && event.x <= v.width &&
                            event.y >= 0 && event.y <= v.height
                    if (isInside && !insideNow) {
                        // Finger moved out → cancel press effect
                        isInside = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start()
                    } else if (!isInside && insideNow) {
                        // Finger moved back in → reapply press effect
                        isInside = true
                        v.animate()
                            .scaleX(0.9f)
                            .scaleY(0.9f)
                            .setDuration(80)
                            .start()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .withEndAction {
                            if (isInside) {
                                onClick?.invoke() ?: v.performClick()
                            }
                        }
                        .start()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isInside = false
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()
                    true
                }

                else -> false
            }
        }
    }

    fun Context.copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener {
                if (it.isCanceled) {
                    cont.cancel()
                } else if (it.isSuccessful) {
                    cont.resume(it.result, null)
                } else {
                    cont.resumeWithException(it.exception!!)
                }
            }
        }
    }

}