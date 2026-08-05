package com.arthapilot.app.data.remote

import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.RefreshRequestDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.UserDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ArthaApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): TokenResponseDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @POST("auth/refresh")
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
}
