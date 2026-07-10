package com.webscare.urducanvas.common.canvas

import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import javax.inject.Inject

class ManageElementsUseCase @Inject constructor() {

    fun reorderAndAssignZIndices(
        oldList: List<CanvasElement>,
        reorderedList: List<CanvasElement>
    ): List<CanvasElement> {
        val reorderedIds = reorderedList.map { it.id }.toSet()

        // Collapsed children: present in canvasElements but absent from reorderedList
        val collapsedChildrenByGroup: Map<String, List<CanvasElement>> =
            oldList
                .filter { it.groupId != null && it.id !in reorderedIds }
                .groupBy { it.groupId!! }

        // Build the full ordered list: after each GROUP sentinel inject its
        // collapsed children (preserving their relative old z-index order)
        val fullOrderedList = mutableListOf<CanvasElement>()
        for (element in reorderedList) {
            fullOrderedList.add(element)
            if (element.type == ElementType.GROUP) {
                val collapsed = collapsedChildrenByGroup[element.id]
                    ?.sortedByDescending { it.zIndex }
                    ?: emptyList()
                fullOrderedList.addAll(collapsed)
            }
        }

        // Assign z-indices: position 0 (top of layers panel) = highest z
        val totalCount = fullOrderedList.size
        return fullOrderedList.mapIndexed { index, element ->
            val newZ = totalCount - 1 - index
            element.copy(zIndex = newZ, context = element.context)
        }
    }
}
