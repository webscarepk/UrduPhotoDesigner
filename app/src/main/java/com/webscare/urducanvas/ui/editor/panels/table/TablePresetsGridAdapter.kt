package com.webscare.urducanvas.ui.editor.panels.table

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.repository.TablePresetStyle
import com.webscare.urducanvas.databinding.ItemTablePresetBinding

class TablePresetsGridAdapter(
    private val onPresetSelected: (TablePresetStyle) -> Unit
) : RecyclerView.Adapter<TablePresetsGridAdapter.VH>() {

    private val items = mutableListOf<TablePresetStyle>()
    private var selectedPresetId: String? = null

    var attachedRecyclerView: RecyclerView? = null
        private set

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView == recyclerView) {
            attachedRecyclerView = null
        }
    }

    fun submitList(newList: List<TablePresetStyle>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTablePresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemTablePresetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(preset: TablePresetStyle) {
            binding.tablePreviewView.setPreset(preset)

            val isSelected = preset.id == selectedPresetId
            val context = binding.root.context
            val strokePx = (1.5f * context.resources.displayMetrics.density + 0.5f).toInt()
            binding.cardPreset.strokeWidth = if (isSelected) strokePx else 0
            binding.cardPreset.strokeColor = ContextCompat.getColor(context, R.color.appColor)

            updateSize(attachedRecyclerView)

            binding.cardPreset.addPressEffect {
                selectedPresetId = preset.id
                notifyDataSetChanged()
                onPresetSelected(preset)
            }
        }

        fun updateSize(attachedRecyclerView: RecyclerView?) {
            val cardRoot = binding.cardPreset
            val context = cardRoot.context
            val density = context.resources.displayMetrics.density
            val recyclerView = (cardRoot.parent as? RecyclerView) ?: attachedRecyclerView

            val marginEndPx = (6 * density).toInt()
            val marginBottomPx = (6 * density).toInt()

            val lm = recyclerView?.layoutManager as? GridLayoutManager
            val spanCount = lm?.spanCount?.coerceAtLeast(1) ?: 3

            val rvHeight = recyclerView?.height ?: 0
            val rvPaddingY = (recyclerView?.paddingTop ?: 0) + (recyclerView?.paddingBottom ?: 0)
            val availHeight = rvHeight - rvPaddingY

            val computedCollapsedHeight = if (availHeight > 0) {
                ((availHeight - (spanCount * marginBottomPx)) / spanCount).coerceAtLeast((24 * density).toInt())
            } else {
                (70 * density).toInt()
            }

            val finalSize = computedCollapsedHeight.coerceAtLeast(1)

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
    }
}
