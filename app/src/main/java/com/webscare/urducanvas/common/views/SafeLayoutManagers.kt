package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

open class SafeLinearLayoutManager : LinearLayoutManager {
    constructor(context: Context) : super(context)
    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(context, orientation, reverseLayout)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.e("SafeLinearLayoutManager", "IOOBE in RecyclerView onLayoutChildren", e)
        }
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}

open class SafeGridLayoutManager : GridLayoutManager {
    constructor(context: Context, spanCount: Int) : super(context, spanCount)
    constructor(context: Context, spanCount: Int, orientation: Int, reverseLayout: Boolean) : super(context, spanCount, orientation, reverseLayout)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.e("SafeGridLayoutManager", "IOOBE in RecyclerView onLayoutChildren", e)
        }
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}

open class SafeStaggeredGridLayoutManager : StaggeredGridLayoutManager {
    constructor(spanCount: Int, orientation: Int) : super(spanCount, orientation)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.e("SafeStaggeredGridLayoutManager", "IOOBE in RecyclerView onLayoutChildren", e)
        }
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}
