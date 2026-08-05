package com.arthapilot.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiExceptionTest {

    private fun server(code: String?, statusCode: Int = 422, message: String = "Request failed") =
        ApiException.Server(message = message, code = code, statusCode = statusCode)

    @Test
    fun `unauthorized exposes session message`() {
        assertEquals(
            "Your session has expired. Please sign in again.",
            ApiException.Unauthorized().userMessage,
        )
    }

    @Test
    fun `network exposes connection message`() {
        assertEquals(
            "Cannot reach the server. Check your connection.",
            ApiException.Network().userMessage,
        )
    }

    @Test
    fun `unknown error code falls back to server message`() {
        assertEquals(
            "Something went wrong",
            server(code = "unexpected_code", message = "Something went wrong").userMessage,
        )
    }

    @Test
    fun `null error code falls back to server message`() {
        assertEquals("boom", server(code = null, message = "boom").userMessage)
    }

    @Test
    fun `known error codes map to user-friendly text`() {
        val expectations = mapOf(
            "email_already_registered" to "An account with this email already exists.",
            "invalid_credentials" to "Invalid email or password.",
            "invalid_refresh_token" to "Your session has expired. Please sign in again.",
            "invalid_token" to "Your session has expired. Please sign in again.",
            "missing_token" to "Your session has expired. Please sign in again.",
            "unsupported_file_type" to "Only .csv and .pdf bank statements are supported.",
            "file_too_large" to "The file is larger than the 10 MB limit.",
            "statement_parse_failed" to "The statement could not be parsed. Try another file.",
            "invalid_category" to "That category is not valid.",
            "transaction_not_found" to "Transaction not found.",
            "not_found" to "The requested resource was not found.",
            "account_disabled" to "This account has been disabled.",
        )
        expectations.forEach { (code, expected) ->
            assertEquals("code=$code", expected, server(code = code).userMessage)
        }
    }
}
