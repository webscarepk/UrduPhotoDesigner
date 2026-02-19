package com.example.urduphotodesigner.ui.navigation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutCategoryRowBinding
import com.example.urduphotodesigner.ui.navigation.templates.TemplateCategoriesAdapter.CategoryVH
import com.example.urduphotodesigner.ui.navigation.templates.TemplatesMiniAdapter

class TrendsAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<HomeRow, TrendsAdapter.TrendVH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val row = currentList[position] as? HomeRow.TrendRow
        return row?.title?.hashCode()?.toLong() ?: position.toLong()
    }

    private var hostRv: RecyclerView? = null
    private val progressById = mutableMapOf<Int, ProgressUi>()

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        hostRv = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        hostRv = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendVH {
        return TrendVH(LayoutCategoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: TrendVH, position: Int) {
        holder.bind(getItem(position) as HomeRow.TrendRow)
    }

    override fun onBindViewHolder(holder: TrendVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val item = getItem(position) as HomeRow.TrendRow

            holder.refreshTemplates(item.templates)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class TrendVH(private val b: LayoutCategoryRowBinding) : RecyclerView.ViewHolder(b.root) {
        private val miniAdapter = TemplatesMiniAdapter(onClick = onTemplateClick)

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun refreshTemplates(templates: List<TemplateEntity>) {
            miniAdapter.submitList(templates)
        }

        fun bind(row: HomeRow.TrendRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }

            if (miniAdapter.currentList != row.templates) {
                miniAdapter.submitList(row.templates)
            }
        }

        fun updateChildProgress(templateId: Int, state: ProgressUi) {
            progressById[templateId] = state
            miniAdapter.updateProgress(templateId, state)
        }

        fun notifyChildChanged(template: TemplateEntity) {
            miniAdapter.updateItem(template)
        }
    }

    fun updateTemplateProgress(templateId: Int, progress: Int, isDownloading: Boolean, isDownloaded: Boolean) {
        val state = ProgressUi(progress, isDownloading, isDownloaded)
        progressById[templateId] = state

        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.TrendRow && row.templates.any { it.id == templateId }
        }
        if (rowIndex != -1) {
            val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? TrendVH
            holder?.updateChildProgress(templateId, state)
        }
    }

    fun notifyTemplateStateChanged(updatedTemplate: TemplateEntity) {
        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.TrendRow && row.templates.any { it.id == updatedTemplate.id }
        }
        if (rowIndex == -1) return

        val row = currentList[rowIndex] as HomeRow.TrendRow
        val templateIdx = row.templates.indexOfFirst { it.id == updatedTemplate.id }
        if (templateIdx != -1) {
            (row.templates as? MutableList)?.set(templateIdx, updatedTemplate)
        }

        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? TrendVH
        holder?.notifyChildChanged(updatedTemplate)
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(old: HomeRow, new: HomeRow): Boolean {
            return when {
                old is HomeRow.TrendRow && new is HomeRow.TrendRow -> true // Ya unique ID agar hai
                old is HomeRow.CategoryRow && new is HomeRow.CategoryRow -> old.title == new.title
                else -> false
            }
        }
        override fun getChangePayload(oldItem: HomeRow, newItem: HomeRow): Any? = Any()
        override fun areContentsTheSame(old: HomeRow, new: HomeRow): Boolean {
            return old is HomeRow.TrendRow && new is HomeRow.TrendRow && old.templates.size == new.templates.size        }
    }
}