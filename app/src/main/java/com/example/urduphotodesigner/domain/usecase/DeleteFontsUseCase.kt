package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.domain.repo.FontsRepo
import javax.inject.Inject

class DeleteFontsUseCase @Inject constructor(
    private val fontsRepo: FontsRepo
) {
    suspend operator fun invoke(fontEntity: FontEntity) {
        fontsRepo.deleteFont(fontEntity)
    }
}