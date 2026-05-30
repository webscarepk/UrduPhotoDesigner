package com.webscare.urducanvas.ui.editor.panels.layers

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.DialogUtils
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentLayersBinding
import com.webscare.urducanvas.databinding.LayoutLayerItemPopupBinding
import com.webscare.urducanvas.databinding.LayoutToolbarLayersNormalBinding
import com.webscare.urducanvas.databinding.LayoutToolbarLayersSelectionBinding
import com.webscare.urducanvas.ui.creation.CreateFragment
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LayersFragment : Fragment() {

    private var _binding: FragmentLayersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: LayersAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var normalToolbar: LayoutToolbarLayersNormalBinding
    private lateinit var selectionToolbar: LayoutToolbarLayersSelectionBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayersBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        normalToolbar   = LayoutToolbarLayersNormalBinding.bind(binding.toolbarNormalInclude.root)
        selectionToolbar = LayoutToolbarLayersSelectionBinding.bind(binding.toolbarSelectionInclude.root)

        setupRecyclerView()
        setupToolbarInitial()
        attachDragHandleSwipe()
        observeViewModel()
        observePanelExpanded()
    }

    // ── Drag handle ───────────────────────────────────────────────────────────

    private fun attachDragHandleSwipe() {
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) {
                f.attachDragHandle(binding.dragHandle)
                binding.root.post {
                    (f as EditorFragment).panelSheetBehavior()?.let { sheet ->
                        sheet.attachAdditionalHandle(binding.toolBarContainer)
                    }
                }
                return
            }
            f = f.parentFragment
        }
    }

    // ── Panel expansion observer ──────────────────────────────────────────────

    private fun observePanelExpanded() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.LAYERS }
                    .collect { expanded ->
                        normalToolbar.closePanel.isVisible = expanded
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applySlideOffset(offset: Float) {
        // No per-frame visual work needed for Layers currently.
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbarInitial() {
        if ((viewModel.selectedElements.value?.size ?: 0) > 1) {
            enterSelectionMode()
        } else {
            showNormalToolbar()
        }
    }

    private fun showNormalToolbar() {
        normalToolbar.root.visibility    = View.VISIBLE
        selectionToolbar.root.visibility = View.GONE

        normalToolbar.title.text = getString(R.string.layers)

        normalToolbar.closePanel.addPressEffect {
            mainViewModel.collapsePanel()
        }

        normalToolbar.canvasSizeBtn.addPressEffect {
            CreateFragment.newResizeInstance().show(parentFragmentManager, "resize_canvas")
        }
    }

    private fun showSelectionToolbar() {
        normalToolbar.root.visibility    = View.GONE
        selectionToolbar.root.visibility = View.VISIBLE

        updateSelectionToolbar()

        val count = viewModel.selectedElements.value?.size ?: 0
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        selectionToolbar.close.addPressEffect { exitSelectionMode() }

        selectionToolbar.lock.addPressEffect {
            viewModel.toggleLockOnSelected(); updateSelectionToolbar()
        }

        selectionToolbar.group.addPressEffect {
            val selected = viewModel.selectedElements.value.orEmpty()
            if (selected.any { it.groupId != null }) viewModel.ungroupElements()
            else viewModel.selectElementForGrouping()
            updateSelectionToolbar()
        }

        selectionToolbar.visibility.addPressEffect {
            viewModel.toggleVisibilityOnSelected(); updateSelectionToolbar()
        }

        selectionToolbar.delete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context      = requireContext(),
                titleText    = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_n_layers, count)
            ) {
                viewModel.removeSelectedElements()
                exitSelectionMode()
            }
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = LayersAdapter(
            onLockToggle        = { element ->
                if (element.type == ElementType.GROUP) viewModel.toggleGroupLock(element.id)
                else viewModel.updateElement(element)
            },
            onMoreOptions       = { element, anchor -> showItemPopupMenu(element, anchor) },
            onItemClick         = { element -> handleItemClick(element) },
            onItemLongClick     = { element -> handleItemLongClick(element) },
            onStartDrag         = { holder -> itemTouchHelper.startDrag(holder) },
            // Tapping a GROUP header row selects its children on canvas
            onGroupHeaderClick  = { element -> handleGroupHeaderClick(element) },
            // Chevron tap toggles collapse state
            onToggleCollapse    = { element -> handleToggleCollapse(element) }
        )
        binding.layers.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos   = target.adapterPosition
                // ── Block child rows from being dragged outside their group ───
                // A child may only move to another position that also belongs to
                // the same group (between its GroupHeader and the last sibling).
                // Standalone items and GroupHeaders can move freely.
                val movingItem  = adapter.currentList().getOrNull(fromPos)
                val targetItem  = adapter.currentList().getOrNull(toPos)
                if (movingItem != null && movingItem.groupId != null) {
                    // Only allow movement within the same group
                    if (targetItem?.groupId != movingItem.groupId &&
                        targetItem?.id != movingItem.groupId) {
                        return false
                    }
                }
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewModel.updateCanvasElementsOrderAndZIndex(adapter.getItems().reversed())
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.layers)
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            CoroutineScope(Dispatchers.IO).launch {
                val sorted      = elements.sortedBy { it.zIndex }.reversed()
                val displayList = buildDisplayList(sorted)
                withContext(Dispatchers.Main) { adapter.submitList(displayList) }
            }
        }

        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (enabled) { adapter.setSelectionMode(true); showSelectionToolbar() }
            else { adapter.setSelectionMode(false); clearSelection(); showNormalToolbar() }
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { selectedList ->
            if (!isAdded) return@observe
            updateSelectionToolbar()
            if (selectedList.isNotEmpty()) {
                val pos = adapter.currentList().indexOfFirst { it.id == selectedList.last().id }
                if (pos != -1) binding.layers.smoothScrollToPosition(pos)
            }
        }

        viewModel.canvasSize.observe(viewLifecycleOwner) { size ->
            size ?: return@observe
            normalToolbar.canvasSizeBtn.text =
                "${size.width.toInt()} × ${size.height.toInt()}"
        }
    }

    // ── Display list builder ──────────────────────────────────────────────────
    //
    // Converts the flat CanvasElement list (sorted by zIndex desc) into a
    // DisplayItem list that the adapter understands:
    //
    //   GroupHeader(groupA)        ← the GROUP sentinel
    //     Child(textElement)       ← shown only when !groupA.isGroupCollapsed
    //     Child(imageElement)
    //   Standalone(shapeElement)   ← no group
    //   Standalone(background)
    //
    // GROUP sentinel elements are placed as GroupHeader rows.
    // Their children (elements whose groupId == sentinel.id) are inserted
    // immediately after, indented, unless the group is collapsed.
    // Elements with a groupId but no matching sentinel are rendered as
    // Standalone (orphan guard — handles stale data).

    private fun buildDisplayList(sortedElements: List<CanvasElement>): List<DisplayItem> {
        val result         = mutableListOf<DisplayItem>()
        // Map groupId → children for quick lookup
        val childrenByGroup: Map<String, List<CanvasElement>> =
            sortedElements
                .filter { it.groupId != null && it.type != ElementType.GROUP }
                .groupBy { it.groupId!! }
        // Collect all valid group sentinel ids
        val sentinelIds = sortedElements
            .filter { it.type == ElementType.GROUP }
            .map { it.id }
            .toSet()

        for (element in sortedElements) {
            when {
                // ── GROUP sentinel → GroupHeader row ──────────────────────────
                element.type == ElementType.GROUP -> {
                    result.add(DisplayItem.GroupHeader(element))
                    // Append children unless collapsed
                    if (!element.isGroupCollapsed) {
                        childrenByGroup[element.id]?.forEach { child ->
                            result.add(DisplayItem.Child(child))
                        }
                    }
                }
                // ── Child element → skip here, already inserted above ─────────
                element.groupId != null && sentinelIds.contains(element.groupId) -> {
                    // Already appended under its GroupHeader — skip.
                    // (Children whose sentinel doesn't exist fall through to Standalone below.)
                }
                // ── Orphan child (no sentinel found) or standalone ────────────
                else -> result.add(DisplayItem.Standalone(element))
            }
        }
        return result
    }

    // ── Group header handlers ─────────────────────────────────────────────────

    private fun handleGroupHeaderClick(element: CanvasElement) {
        if (viewModel.inSelectionMode.value == true) {
            // In selection mode the GROUP sentinel is the selectable unit -- 1 item, not N
            val current = viewModel.canvasElements.value?.toMutableList() ?: return
            val target  = current.find { it.id == element.id } ?: return
            target.isSelected = !target.isSelected
            viewModel.setSelectedElements(current.filter { it.isSelected })
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) viewModel.exitSelectionMode()
        } else {
            // Normal mode -- select the sentinel; refreshSelectedElements will surface children
            viewModel.setSelectedElements(listOf(element))
        }
    }

    private fun handleToggleCollapse(element: CanvasElement) {
        element.isGroupCollapsed = !element.isGroupCollapsed
        // Trigger a list refresh so buildDisplayList re-runs
        viewModel.canvasElements.value?.let { elements ->
            CoroutineScope(Dispatchers.IO).launch {
                val sorted      = elements.sortedBy { it.zIndex }.reversed()
                val displayList = buildDisplayList(sorted)
                withContext(Dispatchers.Main) { adapter.submitList(displayList) }
            }
        }
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    private fun enterSelectionMode()  { viewModel.enterSelectionMode() }
    private fun exitSelectionMode()   { viewModel.exitSelectionMode() }
    private fun clearSelection()      { viewModel.setSelectedElements(emptyList()) }
    private fun selectElement(el: CanvasElement) { viewModel.setSelectedElements(listOf(el)) }

    private fun toggleSelection(element: CanvasElement) {
        val current = viewModel.canvasElements.value?.toMutableList() ?: return
        val target  = current.find { it.id == element.id } ?: return
        target.isSelected = !target.isSelected
        viewModel.setSelectedElements(current.filter { it.isSelected })
    }

    private fun handleItemClick(element: CanvasElement) {
        if (viewModel.inSelectionMode.value == true) {
            toggleSelection(element)
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) viewModel.exitSelectionMode()
        } else {
            selectElement(element)
        }
    }

    private fun handleItemLongClick(element: CanvasElement) {
        if (!element.isSelected) toggleSelection(element)
        viewModel.enterSelectionMode()
    }

    private fun updateSelectionToolbar() {
        if (!::selectionToolbar.isInitialized) return
        val selected = viewModel.selectedElements.value.orEmpty()
        val count    = selected.size
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        val allLocked  = selected.isNotEmpty() && selected.all { it.isLocked }
        val allVisible = selected.isNotEmpty() && selected.all { it.isVisible }
        val allHidden  = selected.isNotEmpty() && selected.all { !it.isVisible }
        val anyGrouped = selected.any { it.groupId != null }

        selectionToolbar.lock.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (allLocked) R.drawable.ic_lock else R.drawable.ic_unlock
            )
        )
        selectionToolbar.lock.contentDescription =
            if (allLocked) getString(R.string.unlock_all) else getString(R.string.lock_all)

        selectionToolbar.visibility.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                if (allHidden) R.drawable.ic_hide_pass else R.drawable.ic_show_pass
            )
        )
        selectionToolbar.visibility.contentDescription =
            if (allHidden) getString(R.string.show_all) else getString(R.string.hide_all)

        selectionToolbar.group.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (anyGrouped) R.drawable.ic_group else R.drawable.ic_un_group
            )
        )
        selectionToolbar.group.contentDescription =
            if (anyGrouped) getString(R.string.un_group_all) else getString(R.string.group_all)
    }

    // ── Popup menu ────────────────────────────────────────────────────────────

    private fun showItemPopupMenu(element: CanvasElement, anchorView: View) {
        val popupBinding = LayoutLayerItemPopupBinding.inflate(
            LayoutInflater.from(requireActivity())
        )
        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation          = 2f
            isOutsideTouchable = true
        }

        // ── Visibility toggle ─────────────────────────────────────────────
        popupBinding.visibility.apply {
            text = if (element.isVisible) getString(R.string.hide) else getString(R.string.show)
            addPressEffect {
                viewModel.toggleVisibility(element)
                popupWindow.dismiss()
            }
        }

        // ── Rename ────────────────────────────────────────────────────────
        popupBinding.actionRename.addPressEffect {
            popupWindow.dismiss()
            showRenameDialog(element)
        }

        // ── Ungroup (only shown for GROUP sentinel rows) ───────────────────
        // If this popup is triggered from a GroupHeader, offer Ungroup directly.
        if (element.type == ElementType.GROUP) {
            popupBinding.actionUngroup.visibility  = View.VISIBLE
            popupBinding.ungroupDivider.visibility = View.VISIBLE
            popupBinding.actionUngroup.addPressEffect {
                val children = viewModel.canvasElements.value
                    ?.filter { it.groupId == element.id }
                    ?: emptyList()
                viewModel.setSelectedElements(children + element)
                viewModel.ungroupElements()
                popupWindow.dismiss()
            }
        } else {
            popupBinding.actionUngroup.visibility  = View.GONE
            popupBinding.ungroupDivider.visibility = View.GONE
        }

        // ── Delete ────────────────────────────────────────────────────────
        popupBinding.actionDelete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context      = requireContext(),
                titleText    = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_layer)
            ) {
                viewModel.removeElement(element)
            }
            popupWindow.dismiss()
        }

        // ── Smart positioning ─────────────────────────────────────────────
        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val location     = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop    = location[1]
            val anchorBottom = anchorTop + anchorView.height

            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            val spaceBelow  = screenHeight - anchorBottom

            if (spaceBelow >= popupHeight) {
                popupWindow.showAsDropDown(anchorView)
            } else {
                popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY, location[0], anchorTop - popupHeight
                )
            }
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────

    private fun showRenameDialog(element: CanvasElement) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_text)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        editText.apply {
            setText(element.customName ?: defaultNameFor(element))
            selectAll()
            hint = getString(R.string.layer_name_hint)
            requestFocus()
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    element.customName = newName
                    viewModel.updateElement(element)
                    val pos = adapter.currentList().indexOfFirst { it.id == element.id }
                    if (pos != -1) adapter.notifyItemChanged(pos)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun defaultNameFor(element: CanvasElement): String = when (element.type) {
        ElementType.TEXT       -> element.text ?: "Text"
        ElementType.IMAGE      -> "Image"
        ElementType.STICKER    -> "Sticker"
        ElementType.DRAW       -> "Brush"
        ElementType.SHAPE      -> element.shapeType?.displayName ?: "Shape"
        ElementType.GROUP      -> element.customName ?: "Group"
        else                   -> "Background"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}