package com.webscare.urducanvas.data.model

data class ProgressUi(
        val progress: Int,
        val isDownloading: Boolean,
        val isDownloaded: Boolean,
        val filePath: String? = null
    )