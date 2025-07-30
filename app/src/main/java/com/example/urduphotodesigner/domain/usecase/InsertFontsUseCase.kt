package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.domain.repo.FontsRepo
import javax.inject.Inject

class InsertFontsUseCase @Inject constructor(
    private val fontsRepo: FontsRepo
) {
    suspend operator fun invoke(fontsResponse: FontsResponse) {
        fontsResponse.fonts.forEach { font ->
            fontsRepo.insertFonts(font)
        }
    }

    suspend fun insertSingleFont(fontEntity: FontEntity) {
        fontsRepo.insertFonts(fontEntity)
    }
}