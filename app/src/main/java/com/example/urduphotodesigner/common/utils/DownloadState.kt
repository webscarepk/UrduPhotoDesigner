package com.example.urduphotodesigner.common.utils

import com.example.urduphotodesigner.data.model.FontEntity
import java.io.File

sealed class DownloadState {
    data class Progress(val progress: Int, val fontEntity: FontEntity) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data class SuccessWithTypeface(val file: File, val fontEntity: FontEntity) : DownloadState()
}