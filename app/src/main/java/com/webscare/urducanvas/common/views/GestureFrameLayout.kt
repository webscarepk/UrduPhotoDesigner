package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class GestureFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface DragListener {
        fun onDragBegin(downRawY: Float, currentRawY: Float)
        fun onDragBy(currentRawY: Float)
        fun onDragEnd()
    }

    var dragListener: DragListener? = null
    var dragHandles: List<View> = emptyList()
    var isSwipeUpEnabled: () -> Boolean = { true }

    private var downRawY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInsideAnyHandle(ev)) {
                    downRawY = ev.rawY
                    isDragging = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = Math.abs(ev.rawY - downRawY)
                if (!isDragging && isSwipeUpEnabled() && isTouchInsideAnyHandle(ev) && dy > 10) {
                    isDragging = true
                    dragListener?.onDragBegin(downRawY, ev.rawY)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragListener?.onDragBy(event.rawY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    dragListener?.onDragEnd()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isTouchInsideAnyHandle(ev: MotionEvent): Boolean {
        if (dragHandles.isEmpty()) return true
        val location = IntArray(2)
        for (handle in dragHandles) {
            if (handle.visibility != View.VISIBLE) continue
            handle.getLocationOnScreen(location)
            val x = location[0]
            val y = location[1]
            val w = handle.width
            val h = handle.height
            if (ev.rawX >= x && ev.rawX <= x + w && ev.rawY >= y && ev.rawY <= y + h) {
                return true
            }
        }
        return false
    }
}
