package com.webscare.urducanvas.common.sealed

sealed class Response<out T> {
    data object Loading : Response<Nothing>()

    data class Processing<out T>(val data: T?) : Response<T>()

    data class Success<out T>(val data: T?) : Response<T>()

    data class Error(val message: String) : Response<Nothing>()
}
