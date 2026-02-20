package com.webscare.urducanvas.common.sealed

sealed class GoogleSignInResult {
    data class Success(val idToken: String?, val email: String?, val displayName: String?) : GoogleSignInResult()
    data class Error(val exception: Exception) : GoogleSignInResult()
    data object Canceled : GoogleSignInResult()
}