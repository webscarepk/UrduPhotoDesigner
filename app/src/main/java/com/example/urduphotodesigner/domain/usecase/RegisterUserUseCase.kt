package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.RegistrationResponse
import com.example.urduphotodesigner.domain.repo.AuthRepo
import javax.inject.Inject

class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepo
) {
    suspend fun execute(
        name: String,
        email: String,
        password: String
    ): Result<RegistrationResponse> {
        return authRepository.registerUser(name, email, password).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { registrationResponse ->
                    registrationResponse.user.let { user ->
                        authRepository.saveLoggedInUser(
                            user.id,
                            user.email,
                            registrationResponse.role
                        )
                    }
                }
            }
        }
    }
}