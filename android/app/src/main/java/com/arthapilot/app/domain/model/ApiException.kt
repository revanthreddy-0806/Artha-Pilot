package com.arthapilot.app.domain.model

import java.io.IOException

/**
 * Typed API failure carrying the backend error contract:
 * { "error": { "code", "message", "details?" } }
 */
sealed class ApiException(message: String) : IOException(message) {

    /** Backend/constructor-provided message, kept free of Throwable's nullable override. */
    private val messageText: String = message

    class Network(message: String = "Cannot reach the server. Check your connection.") :
        ApiException(message)

    class Server(message: String, val code: String?, val statusCode: Int?) : ApiException(message)

    class Unauthorized(message: String = "Your session has expired. Please sign in again.") :
        ApiException(message)

    /** User-friendly message derived from the backend error code. */
    val userMessage: String
        get() = when (this) {
            is Unauthorized -> messageText
            is Network -> messageText
            is Server -> when (code) {
                "email_already_registered" -> "An account with this email already exists."
                "invalid_credentials" -> "Invalid email or password."
                "invalid_refresh_token" -> "Your session has expired. Please sign in again."
                "invalid_token", "missing_token" -> "Your session has expired. Please sign in again."
                "unsupported_file_type" -> "Only .csv and .pdf bank statements are supported."
                "file_too_large" -> "The file is larger than the 10 MB limit."
                "statement_parse_failed" -> "The statement could not be parsed. Try another file."
                "invalid_category" -> "That category is not valid."
                "transaction_not_found" -> "Transaction not found."
                "not_found" -> "The requested resource was not found."
                "account_disabled" -> "This account has been disabled."
                else -> messageText
            }
        }
}
