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

    inner class TrendVH(private val b: LayoutCategoryRowBinding) : RecyclerView.ViewHolder(b.root) {
        private val miniAdapter = TemplatesMiniAdapter(onClick = onTemplateClick)

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun bind(row: HomeRow.TrendRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }

            miniAdapter.submitList(row.templates)
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

    fun notifyTemplateStateChanged(template: TemplateEntity) {
        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.TrendRow &&
                    row.templates.any { it.id == template.id }
        }
        if (rowIndex == -1) return

        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? TrendVH
        holder?.notifyChildChanged(template)
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(o: HomeRow, n: HomeRow) =
            (o as? HomeRow.TrendRow)?.title == (n as? HomeRow.TrendRow)?.title

        override fun areContentsTheSame(o: HomeRow, n: HomeRow): Boolean {
            val oldRow = o as? HomeRow.TrendRow ?: return false
            val newRow = n as? HomeRow.TrendRow ?: return false

            return oldRow.title == newRow.title &&
                    oldRow.templates.size == newRow.templates.size &&
                    oldRow.templates.firstOrNull()?.id == newRow.templates.firstOrNull()?.id
        }
    }
}