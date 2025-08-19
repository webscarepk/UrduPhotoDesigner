package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.LoginResponse
import com.example.urduphotodesigner.domain.repo.AuthRepo
import javax.inject.Inject

class SignUpUserUseCase @Inject constructor(
    private val authRepository: AuthRepo
) {
    // Executes the API login and saves the token and user details
    suspend fun execute(email: String, password: String): Result<LoginResponse> {
        return authRepository.loginUser(email, password).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { loginResponse ->
                    loginResponse.token?.let { authRepository.saveAuthToken(it) }
                    // Safely access user properties using ?.
                    loginResponse.user.let { user ->
                        authRepository.saveLoggedInUser(
                            user!!.id,
                            user.email,
                            loginResponse.role!!
                        )
                    }
                }
            }
        }
    }

    // Retrieves the saved API auth token
    suspend fun getSavedAuthToken(): String? {
        return authRepository.getAuthToken()
    }

    // Retrieves the saved logged-in user email
    suspend fun getSavedLoggedInUserEmail(): String? {
        return authRepository.getLoggedInUserEmail()
    }

    // Retrieves the saved logged-in user role
    suspend fun getSavedLoggedInUserRole(): String? {
        return authRepository.getLoggedInUserRole()
    }

    // Clears saved user data (API specific, or all if AuthRepository.clearUserData() is called)
    suspend fun clearSavedUserData() {
        // This will clear all user data, including Google sign-in data, as AuthRepository.clearUserData() does.
        // If you need to clear only API-specific data, you'd need a more granular method in AuthRepository.
        authRepository.clearUserData()
    }
}
