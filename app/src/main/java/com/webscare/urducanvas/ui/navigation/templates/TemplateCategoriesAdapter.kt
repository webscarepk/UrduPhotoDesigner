package com.webscare.urducanvas.ui.navigation.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutCategoryRowBinding

class TemplateCategoriesAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (com.webscare.urducanvas.data.model.TemplateEntity, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<HomeRow, TemplateCategoriesAdapter.CategoryVH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val row = currentList[position] as HomeRow.CategoryRow
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
        holder.bind(getItem(position) as HomeRow.CategoryRow)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val item = getItem(position) as HomeRow.CategoryRow
            holder.refreshTemplates(item.templates)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class CategoryVH(private val b: LayoutCategoryRowBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val miniAdapter = TemplatesMiniAdapter(
            onClick = onTemplateClick
        )

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun refreshTemplates(templates: List<com.webscare.urducanvas.data.model.TemplateEntity>) {
            miniAdapter.submitList(templates)
        }

        fun bind(row: HomeRow.CategoryRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }
            if (miniAdapter.currentList != row.templates) {
                miniAdapter.submitList(row.templates)
            }
        }

        fun updateChildProgress(
            templateId: Int, state: com.webscare.urducanvas.data.model.ProgressUi
        ) {
            // This updates the internal cache so new binds get the state
            progressById[templateId] = state
            miniAdapter.updateProgress(templateId, state) // Pass payload to child
        }

        fun notifyChildChanged(template: com.webscare.urducanvas.data.model.TemplateEntity) {
            miniAdapter.updateItem(template)
        }

    }

    fun updateTemplateProgress(
        templateId: Int, progress: Int, isDownloading: Boolean, isDownloaded: Boolean
    ) {
        val state = _root_ide_package_.com.webscare.urducanvas.data.model.ProgressUi(
            progress, isDownloading, isDownloaded
        )
        progressById[templateId] = state

        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.CategoryRow && row.templates.any { it.id == templateId }
        }
        if (rowIndex != -1) {
            val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
            holder?.updateChildProgress(templateId, state)
        }
    }

    fun notifyTemplateStateChanged(updatedTemplate: com.webscare.urducanvas.data.model.TemplateEntity) {
        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.CategoryRow && row.templates.any { it.id == updatedTemplate.id }
        }
        if (rowIndex == -1) return

        // Do NOT mutate row.templates in-place -- it is owned by ListAdapter and is
        // unmodifiable. Forward the update to the visible ViewHolder directly;
        // the next submitList() call will carry the full corrected state.
        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
        holder?.notifyChildChanged(updatedTemplate)
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(old: HomeRow, new: HomeRow): Boolean {
            return (old as? HomeRow.CategoryRow)?.title == (new as? HomeRow.CategoryRow)?.title
        }

        override fun areContentsTheSame(old: HomeRow, new: HomeRow): Boolean {
            return old == new
        }

        override fun getChangePayload(oldItem: HomeRow, newItem: HomeRow): Any? {
            return Any() // Empty payload to trigger partial bind
        }
    }
}