package com.arthapilot.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arthapilot.app.data.local.StatementDao
import com.arthapilot.app.data.local.TransactionDao
import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.data.remote.toEntity
import com.arthapilot.app.data.remote.toStatementEntity
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementUploadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

interface StatementRepository {
    /**
     * Uploads the picked statement file. Reading, size validation and the multipart
     * encoding all live here so the UI layer stays free of ContentResolver IO.
     */
    suspend fun upload(fileUri: Uri, fileName: String, mimeType: String): StatementUploadResult
}

@Singleton
class StatementRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
    private val statementDao: StatementDao,
    private val transactionDao: TransactionDao,
    @ApplicationContext private val context: Context,
) : StatementRepository {

    override suspend fun upload(
        fileUri: Uri,
        fileName: String,
        mimeType: String,
    ): StatementUploadResult {
        val bytes = readAllBytes(fileUri)
        val result = try {
            val part = MultipartBody.Part.createFormData(
                "file",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
            )
            api.uploadStatement(part)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }

        statementDao.upsert(result.toStatementEntity())
        transactionDao.upsertAll(result.transactions.map { it.toEntity() })
        return result.toDomain()
    }

    private suspend fun readAllBytes(fileUri: Uri): ByteArray = withContext(Dispatchers.IO) {
        rejectIfTooLarge(fileUri)
        context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
    }

    private fun rejectIfTooLarge(fileUri: Uri) {
        val size = context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else -1L
        } ?: -1L
        if (size > MAX_UPLOAD_BYTES) {
            throw ApiException.Server(
                message = "The file is larger than the 10 MB limit.",
                code = "file_too_large",
                statusCode = 413,
            )
        }
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024
    }
}
