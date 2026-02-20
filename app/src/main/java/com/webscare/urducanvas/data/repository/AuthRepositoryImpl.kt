package com.webscare.urducanvas.data.repository

import android.content.Intent
import android.util.Log
import com.example.urduphotodesigner.common.sealed.GoogleSignInResult
import com.example.urduphotodesigner.common.datastore.PreferenceDataStoreAPI
import com.example.urduphotodesigner.common.datastore.PreferenceDataStoreKeysConstants
import com.example.urduphotodesigner.common.utils.Utils.await
import com.example.urduphotodesigner.data.model.LoginResponse
import com.example.urduphotodesigner.data.model.RegistrationResponse
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import com.example.urduphotodesigner.domain.repo.AuthRepo
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInResult
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.webscare.urducanvas.common.utils.Utils.await
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val oneTapClient: SignInClient,
    private val signInRequest: BeginSignInRequest,
    private val preferenceDataStoreAPI: com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI,
    private val authApiService: com.webscare.urducanvas.data.remote.EndPointsInterface
) : com.webscare.urducanvas.domain.repo.AuthRepo {

    private val TAG = "AuthRepositoryImpl"

    override suspend fun beginGoogleSignIn(): Result<BeginSignInResult> {
        return try {
            val result = oneTapClient.beginSignIn(signInRequest).await()
            if (result?.pendingIntent != null) {
                Result.success(result)
            } else {
                Log.e(TAG, "One Tap returned null PendingIntent")
                Result.failure(Exception("No accounts available"))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google API error: ${e.statusCode}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "One Tap sign-in failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    override fun handleGoogleSignInResult(data: Intent?): com.webscare.urducanvas.common.sealed.GoogleSignInResult {
        return try {
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken
            when {
                idToken != null -> {
                    Log.d(TAG, "ID Token received: $idToken")
                    _root_ide_package_.com.webscare.urducanvas.common.sealed.GoogleSignInResult.Success(credential.googleIdToken, credential.id, credential.displayName)
                }
                else -> {
                    Log.w(TAG, "No ID token!")
                    _root_ide_package_.com.webscare.urducanvas.common.sealed.GoogleSignInResult.Error(Exception("No ID token received"))
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Sign-in failed: ${e.localizedMessage}", e)
            _root_ide_package_.com.webscare.urducanvas.common.sealed.GoogleSignInResult.Error(e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in handleGoogleSignInResult: ${e.message}")
            _root_ide_package_.com.webscare.urducanvas.common.sealed.GoogleSignInResult.Error(e)
        }
    }

    override suspend fun saveUserIdToken(token: String) {
        preferenceDataStoreAPI.putPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_ID_TOKEN, token)
        Log.d(TAG, "User ID Token saved to DataStore")
    }

    override suspend fun saveUserDisplayName(displayName: String) {
        preferenceDataStoreAPI.putPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_DISPLAY_NAME, displayName)
        Log.d(TAG, "User Display Name saved to DataStore")
    }

    override suspend fun getUserIdToken(): String? {
        // Using .first() to get the current value from the Flow
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_ID_TOKEN, "").first().takeIf { it.isNotEmpty() }
    }

    override suspend fun getUserDisplayName(): String? {
        // Using .first() to get the current value from the Flow
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_DISPLAY_NAME, "").first().takeIf { it.isNotEmpty() }
    }

    override suspend fun clearUserData() {
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_ID_TOKEN)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_DISPLAY_NAME)
        Log.d(TAG, "User data cleared from DataStore")
    }

    override suspend fun loginUser(email: String, password: String): Result<com.webscare.urducanvas.data.model.LoginResponse> {
        return try {
            val response = authApiService.loginUser(email, password)
            Log.d(TAG, "Raw API LoginResponse: $response")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "API Login failed: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    override suspend fun saveAuthToken(token: String) {
        preferenceDataStoreAPI.putPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.AUTH_TOKEN, token)
        Log.d(TAG, "Auth Token saved to DataStore")
    }

    override suspend fun saveLoggedInUser(id: Int, email: String, role: String) {
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_ID_TOKEN)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.USER_DISPLAY_NAME)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.AUTH_TOKEN)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_ID)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_EMAIL)
        preferenceDataStoreAPI.removePreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_ROLE)
    }

    override suspend fun getAuthToken(): String? {
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.AUTH_TOKEN, "").first().takeIf { it.isNotEmpty() }
    }

    override suspend fun getLoggedInUserId(): Int? {
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_ID, "").first().toIntOrNull()
    }

    override suspend fun getLoggedInUserEmail(): String? {
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_EMAIL, "").first().takeIf { it.isNotEmpty() }
    }

    override suspend fun getLoggedInUserRole(): String? {
        return preferenceDataStoreAPI.getPreference(_root_ide_package_.com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.LOGGED_IN_USER_ROLE, "").first().takeIf { it.isNotEmpty() }
    }

    override suspend fun registerUser(
        name: String,
        email: String,
        password: String
    ): Result<com.webscare.urducanvas.data.model.RegistrationResponse> {
        return try {
            val response = authApiService.signUpUser(
                name = name,
                email = email,
                password = password
            )
            Log.d(TAG, "Registration successful: ${response.message}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}