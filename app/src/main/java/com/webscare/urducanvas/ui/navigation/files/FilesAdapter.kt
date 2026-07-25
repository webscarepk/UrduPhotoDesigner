package com.webscare.urducanvas.ui.navigation.files

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.addPressEffectWithLongClick
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.LayoutFilesGridBinding
import com.webscare.urducanvas.databinding.LayoutFilesRowBinding

class FilesAdapter(
    private var items: List<Any>,
    private var isGrid: Boolean = true,
    private val onItemClick: (Any) -> Unit,
    private val onItemLongClick: (Any) -> Unit,
    private val onOptionsClick: (Any, View) -> Unit,
    private val onRename: ((Any, String) -> Unit)? = null,
    var onSelectionChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    var multiSelectMode = false
        private set

    private val selectedItems = mutableSetOf<Any>()
    private var editingItemId: Long? = null

    fun startEditing(itemId: Long) {
        editingItemId = itemId
        notifyDataSetChanged()
    }

    fun stopEditing() {
        editingItemId = null
        notifyDataSetChanged()
    }

    fun toggleMultiSelectMode(enabled: Boolean) {
        multiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
            onSelectionChanged?.invoke(false)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        multiSelectMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(false)
    }

    fun getSelectedItems() = selectedItems.toList()

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = LayoutFilesGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridViewHolder(binding)
        } else {
            val binding = LayoutFilesRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is GridViewHolder -> holder.bind(item)
            is ListViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<Any>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun toggleViewType(isGrid: Boolean) {
        this.isGrid = isGrid
        notifyDataSetChanged()
    }

    private fun handleSelection(item: Any) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }

        if (selectedItems.isEmpty()) {
            toggleMultiSelectMode(false)
            onSelectionChanged?.invoke(false)
        } else {
            notifyDataSetChanged()
            onSelectionChanged?.invoke(true)
        }
    }

    inner class GridViewHolder(private val binding: LayoutFilesGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {

            bindItem(binding, item)

            binding.root.addPressEffectWithLongClick(onClick = {
                if (editingItemId != null) return@addPressEffectWithLongClick
                if (multiSelectMode) {
                    handleSelection(item)
                } else {
                    onItemClick(item)
                }
            }, onLongClick = {
                if (editingItemId != null) return@addPressEffectWithLongClick
                if (!multiSelectMode) {
                    toggleMultiSelectMode(true)
                }
                onItemLongClick(item)
                handleSelection(item)
            })

            binding.moreOptions.addPressEffect {
                if (editingItemId != null) return@addPressEffect
                onOptionsClick(item, binding.moreOptions)
            }
        }
    }

    inner class ListViewHolder(private val binding: LayoutFilesRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {
            bindItem(binding, item)

            binding.root.addPressEffectWithLongClick(onClick = {
                if (editingItemId != null) return@addPressEffectWithLongClick
                if (multiSelectMode) {
                    handleSelection(item)
                } else {
                    onItemClick(item)
                }
            }, onLongClick = {
                if (editingItemId != null) return@addPressEffectWithLongClick
                if (!multiSelectMode) {
                    toggleMultiSelectMode(true)
                }
                onItemLongClick(item)
                handleSelection(item)
            })

            binding.moreOptions.addPressEffect {
                if (editingItemId != null) return@addPressEffect
                onOptionsClick(item, binding.moreOptions)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindItem(binding: Any, item: Any) {
        val itemId = when (item) {
            is ImageEntity -> item.id
            is FontEntity -> item.id
            is ExportResult -> item.id
            else -> null
        }

        val isEditing = (itemId != null && itemId.toLong() == editingItemId)

        val setupEditMode: (editText: android.widget.EditText, asset: View, meta: View, options: View) -> Unit =
            { editText, asset, meta, options ->
                if (isEditing) {
                    asset.visibility = View.INVISIBLE
                    meta.visibility = View.INVISIBLE
                    options.visibility = View.GONE
                    editText.visibility = View.VISIBLE

                    editText.setText(
                        when (item) {
                            is ImageEntity -> item.file_name
                            is FontEntity -> item.font_name
                            is ExportResult -> item.fileName
                            else -> ""
                        }
                    )

                    editText.maxLines = 1
                    editText.isSingleLine = true
                    editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    editText.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE

                    editText.post {
                        editText.requestFocus()
                        if (editText.text != null) {
                            editText.setSelection(editText.text.length)
                        }
                        val imm =
                            editText.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(
                            editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                        )
                    }

                    // IME Done
                    editText.setOnEditorActionListener { v, actionId, event ->
                        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                            (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                            val newName = v.text.toString().trim()
                            if (newName.isNotEmpty()) onRename?.invoke(item, newName)
                            closeKeyboard(v)
                            stopEditing()
                            true
                        } else false
                    }

                    // DrawableEnd Cross Icon click: ONLY cross icon or IME Done exits editing mode
                    editText.setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            val drawableEnd = editText.compoundDrawablesRelative[2] ?: editText.compoundDrawables[2]
                            if (drawableEnd != null) {
                                val touchX = event.x.toInt()
                                val iconWidth = drawableEnd.bounds.width()
                                val iconStart = editText.width - editText.paddingRight - iconWidth
                                if (touchX >= iconStart) {
                                    closeKeyboard(v)
                                    stopEditing()
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        false
                    }
                } else {
                    asset.visibility = View.VISIBLE
                    meta.visibility = View.VISIBLE
                    options.visibility = if (multiSelectMode) View.GONE else View.VISIBLE
                    editText.visibility = View.GONE
                }
            }

        when (binding) {
            is LayoutFilesGridBinding -> {
                setupEditMode(
                    binding.editName, binding.assetName, binding.metaData, binding.moreOptions
                )
                if (!isEditing) bindFileData(
                    binding.assetName, binding.metaData, binding.image, item
                )
                updateSelectionUI(binding, item, isEditing)
            }

            is LayoutFilesRowBinding -> {
                setupEditMode(
                    binding.editName, binding.assetName, binding.metaData, binding.moreOptions
                )
                if (!isEditing) bindFileData(
                    binding.assetName, binding.metaData, binding.image, item, binding.imageCard
                )
                updateSelectionUI(binding, item, isEditing)
            }
        }
    }

    private fun closeKeyboard(view: View) {
        val imm =
            view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun bindFileData(
        nameView: android.widget.TextView,
        metaView: android.widget.TextView,
        imageView: android.widget.ImageView,
        item: Any,
        card: com.google.android.material.card.MaterialCardView? = null
    ) {
        when (item) {
            is ImageEntity -> {
                nameView.text = item.file_name
                metaView.text = "Image - ${formatSize(item.file_size)}"

                val isPng = item.file_name.endsWith(".png", ignoreCase = true) || item.file_name.endsWith(".svg", ignoreCase = true)
                imageView.scaleType =
                    if (isPng) android.widget.ImageView.ScaleType.FIT_CENTER else android.widget.ImageView.ScaleType.CENTER_CROP
                val url = Constants.BASE_URL_GLIDE + item.file_url
                if (item.bitmapData != null) {
                    Glide.with(imageView).load(item.bitmapData).into(imageView)
                } else {
                    if (url.endsWith(".svg", true)) {

                        Glide.with(imageView).`as`(PictureDrawable::class.java).load(url)
                            .diskCacheStrategy(DiskCacheStrategy.DATA).into(imageView)

                    } else {

                        Glide.with(imageView).load(url).diskCacheStrategy(DiskCacheStrategy.ALL)
                            .thumbnail(0.1f).into(imageView)
                    }
                }
                if (card != null) setCardStyle(card, isTransparent = isPng)
            }

            is FontEntity -> {
                nameView.text = item.font_name
                metaView.text = "Font - ${formatSize(item.file_size)}"
                imageView.scaleType = android.widget.ImageView.ScaleType.CENTER
                imageView.setImageResource(R.drawable.ic_font_thumbnail)

                if (card != null) setCardStyle(card, isTransparent = true)
            }

            is ExportResult -> {
                nameView.text = item.fileName
                metaView.text = "Project - ${formatSize(item.fileSizeMB)}"
                imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(imageView).load(item.imagePath).into(imageView)

                if (card != null) setCardStyle(card, isTransparent = false)
            }
        }
    }

    private fun setCardStyle(
        card: com.google.android.material.card.MaterialCardView, isTransparent: Boolean
    ) {
        if (isTransparent) {
            card.cardElevation = 0f
            card.setCardBackgroundColor(Color.TRANSPARENT)
            card.strokeWidth = 0
        } else {
            card.cardElevation = 3f
            card.setCardBackgroundColor(card.context.getColor(R.color.selection))
            card.strokeWidth = 1
            card.strokeColor = card.context.getColor(R.color.white)
        }
    }

    private fun updateSelectionUI(binding: Any, item: Any, isEditing: Boolean = false) {
        val isSelected = selectedItems.contains(item)
        val context = when (binding) {
            is LayoutFilesGridBinding -> binding.root.context
            is LayoutFilesRowBinding -> binding.root.context
            else -> return
        }

        when (binding) {
            is LayoutFilesGridBinding -> {
                binding.selection.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
                binding.selection.setImageResource(if (isSelected) R.drawable.ic_selected_radio else R.drawable.ic_unselected_radio)
                binding.imageCard.strokeWidth = if (isSelected) 2 else 0
                binding.imageCard.strokeColor = context.getColor(R.color.appColor)

                binding.moreOptions.visibility =
                    if (multiSelectMode || isEditing) View.GONE else View.VISIBLE

                binding.shimmerLayout.hideShimmer()
            }

            is LayoutFilesRowBinding -> {
                binding.selection.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
                binding.selection.setImageResource(if (isSelected) R.drawable.ic_selected_radio else R.drawable.ic_unselected_radio)
                binding.itemCard.strokeWidth = if (isSelected) 2 else 0
                binding.itemCard.strokeColor = context.getColor(R.color.appColor)

                binding.moreOptions.visibility =
                    if (multiSelectMode || isEditing) View.GONE else View.VISIBLE

                binding.shimmerLayout.hideShimmer()
            }
        }
    }

    fun isEditing(): Boolean {
        return editingItemId != null
    }

    private fun formatSize(size: Any?): String {
        if (size == null) return ""
        val bytes = when (size) {
            is String -> size.toLongOrNull() ?: return size
            is Int -> size.toLong()
            is Long -> size
            is Float -> (size * 1024 * 1024).toLong()
            is Double -> (size * 1024 * 1024).toLong()
            else -> return size.toString()
        }
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }
}
