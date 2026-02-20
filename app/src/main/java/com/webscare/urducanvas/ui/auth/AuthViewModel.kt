package com.webscare.urducanvas.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.urduphotodesigner.common.sealed.GoogleSignInResult
import com.example.urduphotodesigner.common.sealed.SignInUiState
import com.example.urduphotodesigner.domain.usecase.LoginUserUseCase
import com.example.urduphotodesigner.domain.usecase.RegisterUserUseCase
import com.example.urduphotodesigner.domain.usecase.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithGoogleUseCase: com.webscare.urducanvas.domain.usecase.SignInWithGoogleUseCase,
    private val loginUserUseCase: com.webscare.urducanvas.domain.usecase.LoginUserUseCase,
    private val registerUserUseCase: com.webscare.urducanvas.domain.usecase.RegisterUserUseCase
) : ViewModel() {

    private val _signInState = MutableStateFlow<com.webscare.urducanvas.common.sealed.SignInUiState>(
        _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Idle)
    val signInState: StateFlow<com.webscare.urducanvas.common.sealed.SignInUiState> = _signInState

    // Represents the UI state for the sign-in process

    // Initiates the Google Sign-In flow
    fun startGoogleSignIn() {
        viewModelScope.launch {
            _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Loading
            val result = signInWithGoogleUseCase.beginSignIn() // This is kotlin.Result
            if (result.isSuccess) {
                // If successful, get the value using getOrThrow()
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.GoogleSignInFlow(result.getOrThrow())

            } else {
                // If failed, get the exception using exceptionOrNull()
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Error(result.exceptionOrNull()?.localizedMessage ?: "Unknown error")
            }
        }
    }

    // Handles the result from the Google One Tap intent
    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Loading
            when (val result = signInWithGoogleUseCase.handleSignInResultAndSaveData(data)) {
                is com.webscare.urducanvas.common.sealed.GoogleSignInResult.Success -> {
                    _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Success(result.idToken, result.email, result.displayName)
                }
                is com.webscare.urducanvas.common.sealed.GoogleSignInResult.Error -> {
                    _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Error(result.exception.localizedMessage ?: "Sign-in failed")
                }
                is com.webscare.urducanvas.common.sealed.GoogleSignInResult.Canceled -> {
                    _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Error("Sign-in canceled")
                }
            }
        }
    }

    fun performApiLogin(email: String, password: String) {
        viewModelScope.launch {
            _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Loading
            val result = loginUserUseCase.execute(email, password)
            if (result.isSuccess) {
                val loginResponse = result.getOrThrow()
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Success(
                    idToken = loginResponse.token,
                    email = loginResponse.user?.email,
                    displayName =  loginResponse.user?.email
                )
            } else {
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Error(result.exceptionOrNull()?.localizedMessage ?: "API Login failed")
            }
        }
    }

    // Function to retrieve saved user ID token (example usage)
    fun getSavedIdToken() {
        viewModelScope.launch {
            val token = signInWithGoogleUseCase.getSavedIdToken()
            // You can expose this token via another StateFlow if needed for UI
            // For now, just logging for demonstration
            println("Saved ID Token: $token")
        }
    }

    // Function to retrieve saved user display name (example usage)
    fun getSavedDisplayName() {
        viewModelScope.launch {
            val displayName = signInWithGoogleUseCase.getSavedDisplayName()
            // You can expose this display name via another StateFlow if needed for UI
            // For now, just logging for demonstration
            println("Saved Display Name: $displayName")
        }
    }

    fun getSavedAuthToken() {
        viewModelScope.launch {
            val token = loginUserUseCase.getSavedAuthToken()
            println("Saved API Auth Token: $token")
        }
    }

    // Function to retrieve saved API logged-in user email (example usage)
    fun getSavedLoggedInUserEmail() {
        viewModelScope.launch {
            val email = loginUserUseCase.getSavedLoggedInUserEmail()
            println("Saved API Logged In User Email: $email")
        }
    }

    // Function to clear user data (e.g., on logout)
    fun clearUserData() {
        viewModelScope.launch {
            signInWithGoogleUseCase.clearSavedUserData() // Clears Google sign-in data
            loginUserUseCase.clearSavedUserData() // Clears API login data
            _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Idle // Reset state after logout
        }
    }

    fun performRegistration(name: String, email: String, password: String) {
        viewModelScope.launch {
            _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Loading
            val result = registerUserUseCase.execute(name, email, password)
            if (result.isSuccess) {
                val registrationResponse = result.getOrThrow()
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.RegistrationSuccess(
                    email = registrationResponse.user.email,
                    name = registrationResponse.user.name
                )
            } else {
                _signInState.value = _root_ide_package_.com.webscare.urducanvas.common.sealed.SignInUiState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Registration failed"
                )
            }
        }
    }

}
