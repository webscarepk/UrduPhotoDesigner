package com.webscare.urducanvas.common.canvas.sealed

import com.webscare.urducanvas.data.model.FontEntity
import java.io.File

sealed class FontDownloadState {
    data class Progress(val progress: Int, val fontEntity: FontEntity) : FontDownloadState()
    data class Success(val file: File) : FontDownloadState()
    data class Error(val message: String, val fontEntity: FontEntity) : FontDownloadState()
    data class SuccessWithTypeface(val file: File, val fontEntity: FontEntity) : FontDownloadState()
}
