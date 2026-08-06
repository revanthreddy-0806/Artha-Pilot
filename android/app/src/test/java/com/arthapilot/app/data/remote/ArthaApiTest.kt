package com.arthapilot.app.data.remote

import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.dto.RiskQuestionnaireSubmitDto
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

    @Test
    fun `categories endpoint parses taxonomy`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"categories":["Rent","Salary"]}"""))
        val categories = api.getCategories().categories
        assertEquals(listOf("Rent", "Salary"), categories)
        assertEquals("/api/v1/categories", server.takeRequest().path)
    }

    @Test
    fun `statements endpoint parses history page`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":3,"filename":"jan.pdf","file_type":"pdf","bank_name":"HDFC","period_start":"2026-01-01","period_end":"2026-01-31","parse_status":"processed","transaction_count":5,"error_message":null,"created_at":"2026-08-01T10:00:00Z"}],"total":1,"page":1,"page_size":50}""",
            ),
        )
        val page = api.getStatements(1, 50)
        assertEquals(1, page.total)
        assertEquals("jan.pdf", page.items[0].filename)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/statements", recorded.requestUrl!!.encodedPath)
        assertTrue(recorded.requestUrl!!.query!!.contains("page=1"))
    }

    @Test
    fun `budgets list parses items`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"category":"Rent","monthly_limit":20000.0,"spent":15000.0,"percentage":75.0,"overrun":false}],"total":1}""",
            ),
        )
        val budgets = api.getBudgets().items
        assertEquals(1, budgets.size)
        assertEquals(75.0, budgets[0].percentage, 0.0)
    }

    @Test
    fun `set budget issues PUT with monthly_limit body`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"category":"Rent","monthly_limit":25000.0,"spent":0.0,"percentage":0.0,"overrun":false}""",
            ),
        )
        val budget = api.setBudget("Rent", com.arthapilot.app.data.remote.dto.BudgetUpsertDto(25000.0))
        assertEquals(25000.0, budget.monthlyLimit, 0.0)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/api/v1/budgets/Rent", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"monthly_limit\":25000.0"))
    }

    @Test
    fun `delete budget issues DELETE to budget path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteBudget("Rent")
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/v1/budgets/Rent", recorded.path)
    }

    @Test
    fun `insights summary parses response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"month":"2026-07","income":100000.0,"expenses":50000.0,"savings":50000.0,"savings_rate":0.5,"transaction_count":5,"top_categories":[{"category":"Rent","amount":25000.0,"percentage":50.0}]}""",
            ),
        )
        val summary = api.getInsightsSummary("2026-07")
        assertEquals(0.5, summary.savingsRate!!, 0.0)
        assertEquals(1, summary.topCategories.size)
    }

    @Test
    fun `monthly trends sends months query`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"months":[{"month":"2026-07","income":100.0,"expenses":40.0,"savings":60.0}]}""",
            ),
        )
        val trends = api.getMonthlyTrends(6).months
        assertEquals(1, trends.size)
        assertEquals("2026-07", trends[0].month)
        assertTrue(server.takeRequest().requestUrl!!.query!!.contains("months=6"))
    }

    @Test
    fun `recurring endpoint parses detected items`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"transaction_id":11,"description":"SBI CAR LOAN EMI","amount":12500.0,"category":"Bills & Recurring","kind":"emi","interval_days":30,"occurrences":3,"confidence":0.75,"first_date":"2026-05-05","last_date":"2026-07-05","next_expected_date":"2026-08-04"}]}""",
            ),
        )
        val items = api.getRecurring().items
        assertEquals(1, items.size)
        assertEquals("emi", items[0].kind)
    }

    @Test
    fun `risk profile parses score and tolerance fields`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"score":70,"band":"Aggressive","summary":"Good","factors":[{"name":"Risk questionnaire","value":"90/100 (Aggressive)","impact":"positive"}],"income":100000.0,"monthly_expenses":40000.0,"savings_rate":0.6,"emi_load_ratio":0.1,"safe_emi_amount":30000.0,"max_loan_amount":1398000.0,"max_loan_tenure_months":60,"tolerance_score":90,"tolerance_band":"Aggressive","behavior_score":50,"questionnaire_completed":true}""",
            ),
        )
        val profile = api.getRiskProfile()
        assertEquals(70, profile.score)
        assertEquals("Aggressive", profile.band)
        assertEquals(1, profile.factors.size)
        assertEquals(90, profile.toleranceScore)
        assertEquals("Aggressive", profile.toleranceBand)
        assertEquals(50, profile.behaviorScore)
        assertEquals(true, profile.questionnaireCompleted)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/risk/profile", recorded.path)
    }

    @Test
    fun `risk questions parse question set`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"questions":[{"id":"horizon","text":"How soon?","options":[{"id":"under-1y","label":"Within 1 year"},{"id":"7y-plus","label":"Beyond 7 years"}]}]}""",
            ),
        )
        val questions = api.getRiskQuestions().questions
        assertEquals(1, questions.size)
        assertEquals("horizon", questions[0].id)
        assertEquals(2, questions[0].options.size)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/risk/questions", recorded.path)
    }

    @Test
    fun `questionnaire submit posts answers and parses result`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"tolerance_score":25,"tolerance_band":"Conservative","assessment":{"score":13,"band":"Conservative","summary":"S","factors":[],"income":null,"monthly_expenses":0.0,"savings_rate":null,"emi_load_ratio":null,"safe_emi_amount":0.0,"max_loan_amount":null,"max_loan_tenure_months":60,"tolerance_score":25,"tolerance_band":"Conservative","behavior_score":0,"questionnaire_completed":true}}""",
            ),
        )
        val result = api.submitRiskQuestionnaire(RiskQuestionnaireSubmitDto(mapOf("horizon" to "under-1y")))
        assertEquals(25, result.toleranceScore)
        assertEquals("Conservative", result.toleranceBand)
        assertEquals(13, result.assessment.score)
        assertEquals(true, result.assessment.questionnaireCompleted)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/risk/questionnaire", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"horizon\":\"under-1y\""))
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