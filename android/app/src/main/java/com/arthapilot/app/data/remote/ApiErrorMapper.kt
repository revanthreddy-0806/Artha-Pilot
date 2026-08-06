package com.arthapilot.app.data.remote

import com.arthapilot.app.data.remote.dto.ErrorResponseDto
import com.arthapilot.app.domain.model.ApiException
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import java.io.IOException

object ApiErrorMapper {

    private val gson = Gson()

    /** Converts any thrown exception into a typed [ApiException]. */
    fun map(throwable: Throwable): ApiException = when (throwable) {
        is ApiException -> throwable
        is HttpException -> mapHttp(throwable)
        is IOException -> ApiException.Network()
        else -> ApiException.Network()
    }

    fun mapHttp(e: HttpException): ApiException {
        val code = e.code()
        val raw = e.response()?.errorBody()?.string()
        val parsed = raw?.let { parseError(it) }
        val errorCode = parsed?.error?.code
        val message = parsed?.error?.message ?: "Request failed ($code)"
        if (code == 401) {
            return when (errorCode) {
                "invalid_credentials" -> ApiException.Server(message, errorCode, code)
                "invalid_token", "missing_token", "invalid_refresh_token", "unauthorized" ->
                    ApiException.Unauthorized()
                null -> ApiException.Unauthorized()
                else -> ApiException.Server(message, errorCode, code)
            }
        }
        return ApiException.Server(message, errorCode, code)
    }

    private fun parseError(raw: String): ErrorResponseDto? = try {
        gson.fromJson(raw, ErrorResponseDto::class.java)
    } catch (_: JsonSyntaxException) {
        null
    }
}
