package com.example.urduphotodesigner.ui.navigation.templates

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

class TemplateCategoriesAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<HomeRow, TemplateCategoriesAdapter.CategoryVH>(Diff()) {

    init {
        setHasStableIds(true)
    }
    override fun getItemId(position: Int): Long {
        val row = currentList[position] as HomeRow.CategoryRow
        return row.title.hashCode().toLong() // stable per category
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryVH {
        val binding = LayoutCategoryRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryVH(binding)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int) {
        holder.bind(getItem(position) as HomeRow.CategoryRow)
    }

    inner class CategoryVH(private val b: LayoutCategoryRowBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val miniAdapter = TemplatesMiniAdapter(
            onClick = onTemplateClick)

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun bind(row: HomeRow.CategoryRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }
            miniAdapter.submitList(row.templates)
        }

        fun updateChildProgress(templateId: Int, state: ProgressUi) {
            // This updates the internal cache so new binds get the state
            progressById[templateId] = state
            miniAdapter.updateProgress(templateId, state) // Pass payload to child
        }

        fun notifyChildChanged(template: TemplateEntity) {
            miniAdapter.updateItem(template)
        }

    }

    fun updateTemplateProgress(templateId: Int, progress: Int, isDownloading: Boolean, isDownloaded: Boolean) {
        val state = ProgressUi(progress, isDownloading, isDownloaded)
        progressById[templateId] = state

        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.CategoryRow && row.templates.any { it.id == templateId }
        }
        if (rowIndex != -1) {
            val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
            holder?.updateChildProgress(templateId, state)
        }
    }

    fun notifyTemplateStateChanged(template: TemplateEntity) {
        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.CategoryRow &&
                    row.templates.any { it.id == template.id }
        }
        if (rowIndex == -1) return

        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? CategoryVH
        holder?.notifyChildChanged(template)
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(o: HomeRow, n: HomeRow) =
            (o as? HomeRow.CategoryRow)?.title == (n as? HomeRow.CategoryRow)?.title
        override fun areContentsTheSame(o: HomeRow, n: HomeRow) = o == n
    }
}
