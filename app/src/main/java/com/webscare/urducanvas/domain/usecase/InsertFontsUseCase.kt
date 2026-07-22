package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.domain.repo.FontsRepo
import javax.inject.Inject

class InsertFontsUseCase @Inject constructor(
    private val fontsRepo: com.webscare.urducanvas.domain.repo.FontsRepo
) {
    suspend operator fun invoke(fontsResponse: com.webscare.urducanvas.data.model.FontsResponse) {
        fontsResponse.fonts.forEach { font ->
            val cleanedFont = font.copy(font_name = com.webscare.urducanvas.common.utils.Utils.cleanFontName(font.font_name))
            fontsRepo.insertFonts(cleanedFont)
        }
    }

    suspend fun insertSingleFont(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        val cleanedFont = fontEntity.copy(font_name = com.webscare.urducanvas.common.utils.Utils.cleanFontName(fontEntity.font_name))
        fontsRepo.insertFonts(cleanedFont)
    }
}