package com.webscare.urducanvas.ui.editor.panels.layers

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
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
import com.webscare.urducanvas.common.utils.MorphGridLayoutManager
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

        normalToolbar    = LayoutToolbarLayersNormalBinding.bind(binding.toolbarNormalInclude.root)
        selectionToolbar = LayoutToolbarLayersSelectionBinding.bind(binding.toolbarSelectionInclude.root)

        setupRecyclerView()
        setupToolbarInitial()
        attachDragHandleSwipe()
        setupSwipeToCollapse()
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
                    val b = _binding ?: return@post
                    (f as EditorFragment).panelSheetBehavior()
                        ?.attachAdditionalHandle(b.toolBarContainer)
                }
                return
            }
            f = f.parentFragment
        }
    }

    private fun findPanelSheet(): com.webscare.urducanvas.ui.editor.PanelSheetBehavior? {
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) return f.panelSheetBehavior()
            f = f.parentFragment
        }
        return null
    }

    private fun setupSwipeToCollapse() {
        val slop = 8f * resources.displayMetrics.density

        var downX = 0f
        var downY = 0f
        var trackingPanel = false
        var decided = false

        binding.layers.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                val isExpanded = mainViewModel.isPanelExpanded(PanelType.LAYERS)
                if (!isExpanded) return false

                val sheet = findPanelSheet() ?: return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        trackingPanel = false
                        decided = false
                        // Pre-register drag start position so the sheet has an anchor point if we decide to intercept
                        sheet.externalDragBegin(downRawY = downY, currentRawY = event.rawY)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downY // positive = finger moving down
                        val dx = event.rawX - downX

                        if (!decided) {
                            if (kotlin.math.abs(dy) < slop && kotlin.math.abs(dx) < slop) return false

                            // Downward swipe when already at the top of the list
                            val isAtTop = !binding.layers.canScrollVertically(-1)
                            if (dy > 0f && isAtTop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                trackingPanel = true
                                decided = true
                                return true // Intercept touch events, cancel children touches
                            }
                            decided = true
                        }
                    }
                }
                return trackingPanel
            }

            override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
                val sheet = findPanelSheet() ?: return

                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        sheet.externalDragBy(event.rawY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        sheet.externalDragEnd()
                        trackingPanel = false
                        decided = false
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applySlideOffset(offset: Float) {
        // No-op: layers fragment is always a full-width vertical list, no morphing needed
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
            val groupIds = selected.mapNotNull { it.groupId }.toSet()
            // Ungroup only when every selected element belongs to the same single group
            // (i.e. user selected the whole group and wants to dissolve it).
            // In every other case (mix of standalone + grouped, or elements from different
            // groups) we merge everything into one new group — Photoshop / Illustrator style.
            val allSameSingleGroup = groupIds.size == 1 &&
                    selected.all { it.groupId == groupIds.first() || it.type == ElementType.GROUP }
            if (allSameSingleGroup) viewModel.ungroupElements()
            else viewModel.mergeIntoGroup()
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
            onGroupHeaderClick  = { element -> handleGroupHeaderClick(element) },
            onToggleCollapse    = { element -> handleToggleCollapse(element) }
        )

        adapter.isExpanded = true

        binding.layers.apply {
            this.adapter = this@LayersFragment.adapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(),
                androidx.recyclerview.widget.RecyclerView.VERTICAL,
                false
            )
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos   = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_ID.toInt() || toPos == RecyclerView.NO_ID.toInt()) return false

                val movingItem = adapter.getDisplayItemAt(fromPos) ?: return false
                val targetItem = adapter.getDisplayItemAt(toPos)

                when (movingItem) {

                    // ── GroupHeader: moves as a single unit ───────────────────────
                    // Blocked from landing on any Child row.
                    is DisplayItem.GroupHeader -> {
                        if (targetItem is DisplayItem.Child) return false
                    }

                    // ── Child: retype to Standalone as soon as it leaves group territory
                    is DisplayItem.Child -> {
                        val myGroupId = movingItem.element.groupId
                        when (targetItem) {
                            // Same-group sibling → reorder, keep as Child
                            is DisplayItem.Child -> {
                                if (targetItem.element.groupId != myGroupId) return false
                            }
                            // Own GroupHeader → still in group boundary, keep as Child
                            is DisplayItem.GroupHeader -> {
                                if (targetItem.element.id == myGroupId) {
                                    // Moving above own header → EXIT group immediately
                                    adapter.retypeItem(fromPos, asChild = false, newGroupId = null)
                                } else {
                                    // Different group header → EXIT group, don't enter other group
                                    adapter.retypeItem(fromPos, asChild = false, newGroupId = null)
                                }
                            }
                            // Standalone territory → EXIT group
                            is DisplayItem.Standalone, null -> {
                                adapter.retypeItem(fromPos, asChild = false, newGroupId = null)
                            }
                        }
                    }

                    // ── Standalone: can only join a group by landing ON the GroupHeader row
                    is DisplayItem.Standalone -> {
                        when (targetItem) {
                            // Landing ON GroupHeader → join that group
                            is DisplayItem.GroupHeader -> {
                                adapter.retypeItem(
                                    fromPos,
                                    asChild = true,
                                    newGroupId = targetItem.element.id
                                )
                            }
                            // Can't land inside a group's children — blocked
                            is DisplayItem.Child -> return false
                            // Standalone ↔ Standalone reorder — allowed
                            else -> { /* no change */ }
                        }
                    }
                }

                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                // Pass the current display-item list (in display order, top→bottom)
                // to the ViewModel. It resolves groupId changes, cleans up empty
                // sentinels, and assigns z-indices — all in one place.
                viewModel.applyLayerReorder(adapter.getDisplayItems())
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
    // DisplayItem list the adapter understands:
    //
    //   GroupHeader(groupA)       ← GROUP sentinel
    //     Child(textElement)      ← only when !groupA.isGroupCollapsed
    //     Child(imageElement)
    //   Standalone(shapeElement)  ← no group
    //   Standalone(background)
    //
    // Elements with a groupId but no matching sentinel → Standalone (orphan guard).

    private fun buildDisplayList(sortedElements: List<CanvasElement>): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        val childrenByGroup: Map<String, List<CanvasElement>> =
            sortedElements
                .filter { it.groupId != null && it.type != ElementType.GROUP }
                .groupBy { it.groupId!! }
        val sentinelIds = sortedElements
            .filter { it.type == ElementType.GROUP }
            .map { it.id }
            .toSet()

        for (element in sortedElements) {
            when {
                element.type == ElementType.GROUP -> {
                    result.add(DisplayItem.GroupHeader(element))
                    if (!element.isGroupCollapsed) {
                        childrenByGroup[element.id]?.forEach { child ->
                            result.add(DisplayItem.Child(child))
                        }
                    }
                }
                element.groupId != null && sentinelIds.contains(element.groupId) -> {
                    // Already inserted under its GroupHeader — skip.
                }
                else -> result.add(DisplayItem.Standalone(element))
            }
        }
        return result
    }

    // ── Group header handlers ─────────────────────────────────────────────────

    private fun handleGroupHeaderClick(element: CanvasElement) {
        if (viewModel.inSelectionMode.value == true) {
            val current = viewModel.canvasElements.value?.toMutableList() ?: return
            val target  = current.find { it.id == element.id } ?: return
            target.isSelected = !target.isSelected
            viewModel.setSelectedElements(current.filter { it.isSelected })
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) viewModel.exitSelectionMode()
        } else {
            viewModel.setSelectedElements(listOf(element))
        }
    }

    private fun handleToggleCollapse(element: CanvasElement) {
        element.isGroupCollapsed = !element.isGroupCollapsed
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

        // ── Ungroup — only visible for GROUP sentinel rows ──────────────────
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
            maxLines = 1
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setText(element.customName ?: defaultNameFor(element))
            selectAll()
            hint = getString(R.string.layer_name_hint)
            requestFocus()
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    dialog.dismiss()
                    true
                } else false
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    element.customName = newName
                    element.tag = newName
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

    private fun defaultNameFor(element: CanvasElement): String {
        val list = viewModel.canvasElements.value ?: emptyList()
        val uniqueMap = com.webscare.urducanvas.common.utils.ImageUtils.computeUniqueLayerNames(list)
        return uniqueMap[element.id] ?: com.webscare.urducanvas.common.utils.ImageUtils.getBaseLayerName(element)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}