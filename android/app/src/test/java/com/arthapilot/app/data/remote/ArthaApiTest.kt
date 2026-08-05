package com.arthapilot.app.data.remote

import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.domain.model.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ArthaApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ArthaApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArthaApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register posts snake_case body and parses token response`() = runBlocking {
        server.enqueue(tokenResponse())
        val tokens = api.register(RegisterRequestDto("Ada", "ada@x.com", "password123"))
        assertEquals("access-1", tokens.accessToken)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/register", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"full_name\":\"Ada\""))
        assertTrue(body.contains("\"email\":\"ada@x.com\""))
    }

    @Test
    fun `login hits auth login endpoint`() = runBlocking {
        server.enqueue(tokenResponse())
        val tokens = api.login(LoginRequestDto("ada@x.com", "password123"))
        assertEquals("access-1", tokens.accessToken)
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun `refresh hits auth refresh endpoint with refresh token`() = runBlocking {
        server.enqueue(tokenResponse())
        val tokens = api.refresh(com.arthapilot.app.data.remote.dto.RefreshRequestDto("refresh-1"))
        assertEquals("access-1", tokens.accessToken)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"refresh_token\":\"refresh-1\""))
    }

    @Test
    fun `transactions sends pagination and filter query params`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(pageJson()))
        val page = api.getTransactions(2, 50, "Food & Dining", "debit", null, null)
        assertEquals(1, page.items.size)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/transactions", recorded.requestUrl!!.encodedPath)
        val query = recorded.requestUrl!!.query!!
        assertTrue(query.contains("page=2"))
        assertTrue(query.contains("page_size=50"))
        assertTrue(query.contains("transaction_type=debit"))
    }

    @Test
    fun `upload statement posts multipart file`() = runBlocking {
        server.enqueue(uploadResponse())
        val body = "bytes".toByteArray()
        val part = MultipartBody.Part.createFormData(
            "file",
            "jan.pdf",
            body.toRequestBody("application/pdf".toMediaType()),
        )
        val result = api.uploadStatement(part)
        assertEquals("jan.pdf", result.filename)
        assertEquals(1, result.transactions.size)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/statements/upload", recorded.path)
    }

    @Test
    fun `update category issues PATCH to transaction path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(transactionJson("Rent")))
        val updated = api.updateCategory(7, CategoryUpdateDto("Rent"))
        assertEquals("Rent", updated.category)

        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("/api/v1/transactions/7/category", recorded.path)
    }

    @Test
    fun `error contract surfaces mapped user message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"error":{"code":"invalid_category","message":"Invalid category"}}""",
            ),
        )
        try {
            api.updateCategory(7, CategoryUpdateDto("Nope"))
            throw AssertionError("Expected HttpException")
        } catch (e: HttpException) {
            val exception = ApiErrorMapper.map(e)
            assertTrue(exception is ApiException.Server)
            assertEquals("That category is not valid.", exception.userMessage)
        }
    }

    private fun tokenResponse() = MockResponse().setResponseCode(200).setBody(
        """{"access_token":"access-1","refresh_token":"refresh-1","token_type":"bearer"}""",
    )

    private fun uploadResponse() = MockResponse().setResponseCode(200).setBody(
        """{"id":3,"filename":"jan.pdf","file_type":"pdf","bank_name":"HDFC","account_number":null,"period_start":"2026-01-01","period_end":"2026-01-31","parse_status":"parsed","transaction_count":1,"error_message":null,"transactions":[{"id":1,"statement_id":3,"date":"2026-01-01","description":"Salary","amount":1000.0,"transaction_type":"credit","balance":2000.0,"category":"Salary","confidence":0.99,"is_recurring":true}]}""",
    )

    private fun pageJson() =
        """{"items":[{"id":1,"statement_id":3,"date":"2026-01-01","description":"Swiggy","amount":214.5,"transaction_type":"debit","balance":null,"category":"Food & Dining","confidence":0.91,"is_recurring":false}],"total":1,"page":2,"page_size":50}"""

    private fun transactionJson(category: String) =
        """{"id":7,"statement_id":3,"date":"2026-01-01","description":"Rent","amount":800.0,"transaction_type":"debit","balance":100.0,"category":"$category","confidence":0.85,"is_recurring":true}"""
}