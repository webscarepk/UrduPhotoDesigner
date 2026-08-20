package com.webscare.urducanvas.ui.editor.panels.table

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.TablePresetPreviewView
import com.webscare.urducanvas.data.repository.TablePresetStyle
import com.webscare.urducanvas.databinding.LayoutTablePresetItemCardBinding
import com.webscare.urducanvas.databinding.LayoutTablePresetItemExpandedBinding

class TablePresetsMainAdapter(
    private val onPresetClick: (TablePresetStyle) -> Unit
) : ListAdapter<TablePresetStyle, TablePresetsMainAdapter.PresetViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_COLLAPSED = 0
        private const val TYPE_EXPANDED = 1
    }

    var isExpanded: Boolean = false
    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

    var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        attachedRecyclerView = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        attachedRecyclerView = null
    }

    override fun getItemViewType(position: Int): Int =
        if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EXPANDED) {
            PresetViewHolder.Expanded(
                LayoutTablePresetItemExpandedBinding.inflate(inflater, parent, false),
                this,
                onPresetClick
            )
        } else {
            PresetViewHolder.Collapsed(
                LayoutTablePresetItemCardBinding.inflate(inflater, parent, false),
                this,
                onPresetClick
            )
        }
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        holder.bind(getItem(position), slideOffset, recyclerViewWidth, recyclerViewPadding)
    }

    sealed class PresetViewHolder(
        itemView: View,
        val adapter: TablePresetsMainAdapter,
        val onPresetClick: (TablePresetStyle) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        abstract val cardRoot: MaterialCardView
        abstract val previewView: TablePresetPreviewView

        open fun bind(item: TablePresetStyle, slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            previewView.setPreset(item)

            cardRoot.addPressEffect {
                onPresetClick(item)
            }

            updateSize(slideOffset, rvWidth, rvPadding)
        }

        open fun updateSize(slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val recyclerView = (itemView.parent as? RecyclerView) ?: adapter.attachedRecyclerView

            if (this is Expanded) {
                val lp = cardRoot.layoutParams as? ViewGroup.MarginLayoutParams
                val margin3dp = (3 * density).toInt()
                if (lp != null) {
                    if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                        lp.height != ViewGroup.LayoutParams.WRAP_CONTENT ||
                        lp.leftMargin != margin3dp || lp.topMargin != margin3dp ||
                        lp.rightMargin != margin3dp || lp.bottomMargin != margin3dp
                    ) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.setMargins(margin3dp, margin3dp, margin3dp, margin3dp)
                        cardRoot.layoutParams = lp
                    }
                }
                return
            }

            val marginEndPx = (6 * density).toInt()
            val marginBottomPx = (6 * density).toInt()

            val lm = recyclerView?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
            val spanCount = lm?.spanCount?.coerceAtLeast(1) ?: 3

            val rvHeight = recyclerView?.height ?: 0
            val rvPaddingY = (recyclerView?.paddingTop ?: 0) + (recyclerView?.paddingBottom ?: 0)
            val availHeight = rvHeight - rvPaddingY

            val computedCollapsedHeight = if (availHeight > 0) {
                ((availHeight - ((spanCount - 1) * marginBottomPx)) / spanCount).coerceAtLeast((24 * density).toInt())
            } else {
                (50 * density).toInt()
            }

            val collapsedSize = computedCollapsedHeight

            val effectiveWidth = if (rvWidth > 0) rvWidth else (recyclerView?.width ?: 0)
            val columnWidth = if (effectiveWidth > 0) {
                val spanCountExpanded = 3
                val totalMarginW = (spanCountExpanded - 1) * marginEndPx
                ((effectiveWidth - rvPadding - totalMarginW) / spanCountExpanded).toInt()
            } else collapsedSize

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()
            val finalSize = currentSize.coerceAtLeast(1)

            val lp = cardRoot.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                if (lp.width != finalSize || lp.height != finalSize || lp.leftMargin != 0 || lp.topMargin != 0 || lp.rightMargin != marginEndPx || lp.bottomMargin != marginBottomPx) {
                    lp.width = finalSize
                    lp.height = finalSize
                    lp.leftMargin = 0
                    lp.topMargin = 0
                    lp.rightMargin = marginEndPx
                    lp.bottomMargin = marginBottomPx
                    cardRoot.layoutParams = lp
                }
            }
        }

        class Collapsed(
            private val binding: LayoutTablePresetItemCardBinding,
            adapter: TablePresetsMainAdapter,
            onPresetClick: (TablePresetStyle) -> Unit
        ) : PresetViewHolder(binding.root, adapter, onPresetClick) {
            override val cardRoot get() = binding.cardPreset
            override val previewView get() = binding.tablePreviewView
        }

        class Expanded(
            private val binding: LayoutTablePresetItemExpandedBinding,
            adapter: TablePresetsMainAdapter,
            onPresetClick: (TablePresetStyle) -> Unit
        ) : PresetViewHolder(binding.root, adapter, onPresetClick) {
            override val cardRoot get() = binding.cardPreset
            override val previewView get() = binding.tablePreviewView
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TablePresetStyle>() {
        override fun areItemsTheSame(oldItem: TablePresetStyle, newItem: TablePresetStyle): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TablePresetStyle, newItem: TablePresetStyle): Boolean =
            oldItem == newItem
    }
}
