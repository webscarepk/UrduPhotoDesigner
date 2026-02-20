package com.webscare.urducanvas.domain.usecase

import android.content.Intent
import com.webscare.urducanvas.common.sealed.GoogleSignInResult
import com.webscare.urducanvas.domain.repo.AuthRepo
import com.google.android.gms.auth.api.identity.BeginSignInResult
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: com.webscare.urducanvas.domain.repo.AuthRepo
) {
    // Initiates the Google One Tap sign-in flow
    suspend fun beginSignIn(): Result<BeginSignInResult> {
        return authRepository.beginGoogleSignIn()
    }

    // Handles the result from the Google One Tap sign-in intent and saves data
    suspend fun handleSignInResultAndSaveData(data: Intent?): com.webscare.urducanvas.common.sealed.GoogleSignInResult {
        val signInResult = authRepository.handleGoogleSignInResult(data)
        if (signInResult is com.webscare.urducanvas.common.sealed.GoogleSignInResult.Success) {
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
