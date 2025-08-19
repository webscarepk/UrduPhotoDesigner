package com.example.urduphotodesigner.domain.repo

import android.content.Intent
import com.example.urduphotodesigner.common.sealed.GoogleSignInResult
import com.example.urduphotodesigner.data.model.LoginResponse
import com.example.urduphotodesigner.data.model.RegistrationResponse
import com.google.android.gms.auth.api.identity.BeginSignInResult

interface AuthRepo {
    // Initiates the Google One Tap sign-in flow
    suspend fun beginGoogleSignIn(): Result<BeginSignInResult>

    // Handles the result from the Google One Tap sign-in intent
    fun handleGoogleSignInResult(data: Intent?): GoogleSignInResult

    // Saves the user's ID token to DataStore
    suspend fun saveUserIdToken(token: String)

    // Saves the user's display name to DataStore
    suspend fun saveUserDisplayName(displayName: String)

    // Retrieves the user's ID token from DataStore
    suspend fun getUserIdToken(): String?

    // Retrieves the user's display name from DataStore
    suspend fun getUserDisplayName(): String?

    // Clears all user preferences from DataStore (e.g., on logout)
    suspend fun clearUserData()

    suspend fun loginUser(email: String, password: String): Result<LoginResponse>

    // New methods to save API login user data
    suspend fun saveAuthToken(token: String)
    suspend fun saveLoggedInUser(id: Int, email: String, role: String)

    // New methods to retrieve API login user data
    suspend fun getAuthToken(): String?
    suspend fun getLoggedInUserId(): Int?
    suspend fun getLoggedInUserEmail(): String?
    suspend fun getLoggedInUserRole(): String?

    suspend fun registerUser(
        name: String,
        email: String,
        password: String
    ): Result<RegistrationResponse>
}