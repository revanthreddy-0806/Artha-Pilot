package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequestDto(
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val password: String,
)

data class LoginRequestDto(
    val email: String,
    val password: String,
)

data class RefreshRequestDto(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
)

data class UserDto(
    val id: Long,
    val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class ApiErrorDto(
    val code: String,
    val message: String,
    val details: Any? = null,
)

data class ErrorResponseDto(
    val error: ApiErrorDto,
)
