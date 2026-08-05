package com.arthapilot.app.data.remote

import com.arthapilot.app.domain.model.ApiException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ApiErrorMapperTest {

    private fun httpError(code: Int, raw: String?): HttpException {
        val body = raw?.toResponseBody("application/json".toMediaType())
            ?: "".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }

    @Test
    fun `401 maps to Unauthorized`() {
        assertTrue(ApiErrorMapper.map(httpError(401, null)) is ApiException.Unauthorized)
    }

    @Test
    fun `error contract with code is parsed`() {
        val mapped = ApiErrorMapper.map(
            httpError(422, """{"error":{"code":"invalid_category","message":"Invalid category"}}"""),
        )
        assertTrue(mapped is ApiException.Server)
        val server = mapped as ApiException.Server
        assertEquals("invalid_category", server.code)
        assertEquals(422, server.statusCode)
        assertEquals("That category is not valid.", server.userMessage)
    }

    @Test
    fun `unparsable error body falls back to status message`() {
        val mapped = ApiErrorMapper.map(httpError(500, "internal error, not json"))
        assertTrue(mapped is ApiException.Server)
        assertEquals("Request failed (500)", mapped.userMessage)
    }

    @Test
    fun `network errors map to Network`() {
        assertTrue(ApiErrorMapper.map(IOException("socket closed")) is ApiException.Network)
    }

    @Test
    fun `unknown exceptions map to Network`() {
        assertTrue(ApiErrorMapper.map(RuntimeException("boom")) is ApiException.Network)
    }

    @Test
    fun `already mapped ApiException passes through`() {
        val original = ApiException.Unauthorized()
        assertEquals(original, ApiErrorMapper.map(original))
    }
}