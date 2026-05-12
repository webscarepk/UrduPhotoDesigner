package com.webscare.urducanvas.common.canvas.sealed

import com.webscare.urducanvas.common.canvas.enums.ErrorType

sealed class HomeUiState {
    object Loading : HomeUiState()
    object Content : HomeUiState()      // we have at least some data
    object Empty : HomeUiState()        // all APIs ok but DB empty
    data class Error(
        val type: ErrorType,
        val message: String
    ) : HomeUiState()
}