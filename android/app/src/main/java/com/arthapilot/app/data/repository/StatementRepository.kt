package com.arthapilot.app.data.repository

import com.arthapilot.app.data.local.StatementDao
import com.arthapilot.app.data.local.TransactionDao
import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.data.remote.toEntity
import com.arthapilot.app.data.remote.toStatementEntity
import com.arthapilot.app.domain.model.StatementUploadResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

interface StatementRepository {
    suspend fun upload(fileName: String, mimeType: String, bytes: ByteArray): StatementUploadResult
}

@Singleton
class StatementRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
    private val statementDao: StatementDao,
    private val transactionDao: TransactionDao,
) : StatementRepository {

    override suspend fun upload(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): StatementUploadResult {
        val result = try {
            val part = MultipartBody.Part.createFormData(
                "file",
                fileName,
                bytes.toRequestBody(
                    mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType(),
                ),
            )
            api.uploadStatement(part)
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }

        statementDao.upsert(result.toStatementEntity())
        transactionDao.upsertAll(result.transactions.map { it.toEntity() })
        return result.toDomain()
    }
}
