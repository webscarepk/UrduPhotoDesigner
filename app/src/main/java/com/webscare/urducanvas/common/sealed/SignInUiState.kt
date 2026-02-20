package com.webscare.urducanvas.common.sealed

import com.google.android.gms.auth.api.identity.BeginSignInResult

sealed class SignInUiState {
        data object Idle : SignInUiState()
        data object Loading : SignInUiState()
        data class GoogleSignInFlow(val beginSignInResult: BeginSignInResult) : SignInUiState()
        data class Success(val idToken: String?, val email: String?, val displayName: String?) : SignInUiState()
        data class Error(val message: String) : SignInUiState()

        data class RegistrationSuccess(
                val email: String,
                val name: String
        ) : SignInUiState()
    }