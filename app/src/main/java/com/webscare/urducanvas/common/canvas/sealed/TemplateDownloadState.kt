package com.webscare.urducanvas.common.canvas.sealed

import com.example.urduphotodesigner.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.TemplateEntity
import java.io.File

sealed class TemplateDownloadState {
    data class Progress(val progress: Int, val template: TemplateEntity) : TemplateDownloadState()
    data class Success(val file: File) : TemplateDownloadState()
    data class Error(val message: String) : TemplateDownloadState()
    data class SuccessWithTemplate(val file: File, val template: TemplateEntity) : TemplateDownloadState()
}