package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.domain.repo.FontsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFontsUseCase @Inject constructor(
    private val fontsRepo: com.webscare.urducanvas.domain.repo.FontsRepo
) {
    operator fun invoke(): Flow<List<com.webscare.urducanvas.data.model.FontEntity>> {
        return fontsRepo.fetchFonts()
    }
}
