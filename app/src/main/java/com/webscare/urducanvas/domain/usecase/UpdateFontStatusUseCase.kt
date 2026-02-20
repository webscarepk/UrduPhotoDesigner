package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.domain.repo.FontsRepo
import javax.inject.Inject

class UpdateFontStatusUseCase @Inject constructor(
    private val fontsRepo: com.webscare.urducanvas.domain.repo.FontsRepo
) {

    suspend operator fun invoke(id: String, isDownloading: Boolean) {
        fontsRepo.updateStatusFont(id, isDownloading)
    }
}