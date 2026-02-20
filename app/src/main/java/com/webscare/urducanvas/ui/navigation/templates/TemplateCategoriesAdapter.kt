package com.webscare.urducanvas.ui.navigation.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ProgressUi
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.databinding.LayoutCategoryRowBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class TemplateCategoriesAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (com.webscare.urducanvas.data.model.TemplateEntity, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.webscare.urducanvas.common.canvas.sealed.HomeRow, TemplateCategoriesAdapter.CategoryVH>(Diff()) {

    init {
        setHasStableIds(true)
    }
    override fun getItemId(position: Int): Long {
        val row = currentList[position] as com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow
        return row.title.hashCode().toLong() // stable per category
    }

    private var hostRv: RecyclerView? = null
    private val progressById = mutableMapOf<Int, com.webscare.urducanvas.data.model.ProgressUi>()

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        hostRv = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        hostRv = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryVH {
        val binding = LayoutCategoryRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryVH(binding)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int) {
        holder.bind(getItem(position) as com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val item = getItem(position) as com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow
            holder.refreshTemplates(item.templates)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class CategoryVH(private val b: LayoutCategoryRowBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val miniAdapter = TemplatesMiniAdapter(
            onClick = onTemplateClick)

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun refreshTemplates(templates: List<com.webscare.urducanvas.data.model.TemplateEntity>) {
            miniAdapter.submitList(templates)
        }

        fun bind(row: com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }
            if (miniAdapter.currentList != row.templates) {
                miniAdapter.submitList(row.templates)
            }
        }

        fun updateChildProgress(templateId: Int, state: com.webscare.urducanvas.data.model.ProgressUi) {
            // This updates the internal cache so new binds get the state
            progressById[templateId] = state
            miniAdapter.updateProgress(templateId, state) // Pass payload to child
        }

        fun notifyChildChanged(template: com.webscare.urducanvas.data.model.TemplateEntity) {
            miniAdapter.updateItem(template)
        }

    }

    fun updateTemplateProgress(templateId: Int, progress: Int, isDownloading: Boolean, isDownloaded: Boolean) {
        val state = _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
            progress,
            isDownloading,
            isDownloaded
        )
        progressById[templateId] = state

        val rowIndex = currentList.indexOfFirst { row ->
            row is com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow && row.templates.any { it.id == templateId }
        }
        if (rowIndex != -1) {
            val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
            holder?.updateChildProgress(templateId, state)
        }
    }

    fun notifyTemplateStateChanged(updatedTemplate: com.webscare.urducanvas.data.model.TemplateEntity) {
        val rowIndex = currentList.indexOfFirst { row ->
            row is com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow && row.templates.any { it.id == updatedTemplate.id }
        }
        if (rowIndex == -1) return

        val row = currentList[rowIndex] as com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow
        val templateIdx = row.templates.indexOfFirst { it.id == updatedTemplate.id }
        if (templateIdx != -1) {
            (row.templates as? MutableList)?.set(templateIdx, updatedTemplate)
        }

        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
        holder?.notifyChildChanged(updatedTemplate)
    }

    class Diff : DiffUtil.ItemCallback<com.webscare.urducanvas.common.canvas.sealed.HomeRow>() {
        override fun areItemsTheSame(old: com.webscare.urducanvas.common.canvas.sealed.HomeRow, new: com.webscare.urducanvas.common.canvas.sealed.HomeRow): Boolean {
            return (old as? com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow)?.title == (new as? com.webscare.urducanvas.common.canvas.sealed.HomeRow.CategoryRow)?.title
        }

        override fun areContentsTheSame(old: com.webscare.urducanvas.common.canvas.sealed.HomeRow, new: com.webscare.urducanvas.common.canvas.sealed.HomeRow): Boolean {
            // Agar templates ki list badli hai (download state change), to false den
            // taake getChangePayload chale aur onBind(payloads) trigger ho
            return old == new
        }

        override fun getChangePayload(oldItem: com.webscare.urducanvas.common.canvas.sealed.HomeRow, newItem: com.webscare.urducanvas.common.canvas.sealed.HomeRow): Any? {
            return Any() // Empty payload to trigger partial bind
        }
    }
}
