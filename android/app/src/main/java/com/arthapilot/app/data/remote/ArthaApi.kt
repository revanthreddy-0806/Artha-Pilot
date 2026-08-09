package com.arthapilot.app.data.remote

import com.arthapilot.app.data.remote.dto.BudgetDto
import com.arthapilot.app.data.remote.dto.BudgetListDto
import com.arthapilot.app.data.remote.dto.BudgetUpsertDto
import com.arthapilot.app.data.remote.dto.CategoriesDto
import com.arthapilot.app.data.remote.dto.CategoryBreakdownDto
import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.dto.InsightsSummaryDto
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.MonthlyTrendsDto
import com.arthapilot.app.data.remote.dto.RecommendationDto
import com.arthapilot.app.data.remote.dto.RecurringListDto
import com.arthapilot.app.data.remote.dto.RefreshRequestDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.dto.RiskProfileDto
import com.arthapilot.app.data.remote.dto.RiskQuestionListDto
import com.arthapilot.app.data.remote.dto.RiskQuestionnaireResultDto
import com.arthapilot.app.data.remote.dto.RiskQuestionnaireSubmitDto
import com.arthapilot.app.data.remote.dto.StatementListDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.UserDto
import com.arthapilot.app.data.remote.dto.WhatIfRequestDto
import com.arthapilot.app.data.remote.dto.WhatIfResultDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ArthaApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): TokenResponseDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @POST(REFRESH_PATH)
    suspend fun refresh(@Body body: RefreshRequestDto): TokenResponseDto

    @GET("auth/me")
    suspend fun me(): UserDto

    @Multipart
    @POST("statements/upload")
    suspend fun uploadStatement(@Part file: MultipartBody.Part): StatementUploadResponseDto

    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("category") category: String? = null,
        @Query("transaction_type") transactionType: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): TransactionPageDto

    @PATCH("transactions/{id}/category")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @Body body: CategoryUpdateDto,
    ): TransactionDto

    @GET("categories")
    suspend fun getCategories(): CategoriesDto

    @GET("statements")
    suspend fun getStatements(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): StatementListDto

    @GET("budgets")
    suspend fun getBudgets(): BudgetListDto

    @PUT("budgets/{category}")
    suspend fun setBudget(
        @Path("category") category: String,
        @Body body: BudgetUpsertDto,
    ): BudgetDto

    @DELETE("budgets/{category}")
    suspend fun deleteBudget(@Path("category") category: String)

    @GET("insights/summary")
    suspend fun getInsightsSummary(@Query("month") month: String? = null): InsightsSummaryDto

    @GET("insights/monthly-trends")
    suspend fun getMonthlyTrends(@Query("months") months: Int = 6): MonthlyTrendsDto

    @GET("insights/category-breakdown")
    suspend fun getCategoryBreakdown(@Query("month") month: String? = null): CategoryBreakdownDto

    @GET("insights/recurring")
    suspend fun getRecurring(): RecurringListDto

    @GET("risk/questions")
    suspend fun getRiskQuestions(): RiskQuestionListDto

    @PUT("risk/questionnaire")
    suspend fun submitRiskQuestionnaire(@Body body: RiskQuestionnaireSubmitDto): RiskQuestionnaireResultDto

    @GET("risk/profile")
    suspend fun getRiskProfile(): RiskProfileDto

    @GET("recommendations")
    suspend fun getRecommendations(): RecommendationDto

    @POST("recommendations/what-if")
    suspend fun postWhatIf(@Body body: WhatIfRequestDto): WhatIfResultDto

    companion object {
        const val REFRESH_PATH = "auth/refresh"
    }
}
