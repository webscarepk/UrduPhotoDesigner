package com.webscare.urducanvas.viewmodels

import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.domain.repo.DownloadRepo
import com.webscare.urducanvas.domain.usecase.GetFontsUseCase
import com.webscare.urducanvas.domain.usecase.UpdateFontsUseCase
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontGate @Inject constructor(
    private val getFontsUseCase: com.webscare.urducanvas.domain.usecase.GetFontsUseCase,
    private val downloadRepo: com.webscare.urducanvas.domain.repo.DownloadRepo,
    private val updateFontsUseCase: com.webscare.urducanvas.domain.usecase.UpdateFontsUseCase
) {

    suspend fun ensureFonts(fontIds: List<String>) {

        if (fontIds.isEmpty()) return

        val currentFonts = getFontsUseCase().first()

        val missingFonts = currentFonts.filter { font ->
            fontIds.contains(font.id.toString()) && !font.is_downloaded
        }

        if (missingFonts.isEmpty()) return

        coroutineScope {
            missingFonts.map { font ->
                async { downloadSingleFont(font) }
            }.awaitAll()
        }

        waitUntilFontsReady(fontIds)
    }

    private suspend fun downloadSingleFont(font: com.webscare.urducanvas.data.model.FontEntity) {
        val downloadedFile = downloadRepo.downloadAssets(
            url = _root_ide_package_.com.webscare.urducanvas.common.utils.Constants.BASE_URL_GLIDE + font.file_url,
            fileName = font.font_name + ".ttf",
            onProgress = {}
        )

        updateFontsUseCase.invoke(
            font.id.toString(),
            isDownloaded = true,
            isDownloading = false,
            filePath = downloadedFile.absolutePath
        )
    }

    private suspend fun waitUntilFontsReady(fontIds: List<String>) {
        getFontsUseCase().first { fonts ->
            fonts
                .filter { fontIds.contains(it.id.toString()) }
                .all { it.is_downloaded }
        }
    }

}
