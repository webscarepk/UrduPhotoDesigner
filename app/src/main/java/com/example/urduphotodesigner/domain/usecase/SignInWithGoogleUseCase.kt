package com.example.urduphotodesigner.domain.usecase

import android.content.Intent
import com.example.urduphotodesigner.common.sealed.GoogleSignInResult
import com.example.urduphotodesigner.domain.repo.AuthRepo
import com.google.android.gms.auth.api.identity.BeginSignInResult
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepo
) {
    // Initiates the Google One Tap sign-in flow
    suspend fun beginSignIn(): Result<BeginSignInResult> {
        return authRepository.beginGoogleSignIn()
    }

    // Handles the result from the Google One Tap sign-in intent and saves data
    suspend fun handleSignInResultAndSaveData(data: Intent?): GoogleSignInResult {
        val signInResult = authRepository.handleGoogleSignInResult(data)
        if (signInResult is GoogleSignInResult.Success) {
            signInResult.idToken?.let { authRepository.saveUserIdToken(it) }
            signInResult.displayName?.let { authRepository.saveUserDisplayName(it) }
        }
        return signInResult
    }

    // Retrieves the saved ID token
    suspend fun getSavedIdToken(): String? {
        return authRepository.getUserIdToken()
    }

    // Retrieves the saved display name
    suspend fun getSavedDisplayName(): String? {
        return authRepository.getUserDisplayName()
    }

    // Clears saved user data
    suspend fun clearSavedUserData() {
        authRepository.clearUserData()
    }
}
