package com.arthapilot.app.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context?,
    private val statementRepository: StatementRepository,
    private val invalidationBus: DataInvalidationBus,
) : ViewModel() {

    constructor(
        context: Context,
        statementRepository: StatementRepository,
    ) : this(context, statementRepository, NoOpDataInvalidationBus)

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var uri: Uri? = null

    fun onFileSelected(uri: Uri) {
        this.uri = uri
        _uiState.update {
            it.copy(
                fileName = resolveFileName(
                    context?.let { ctx -> queryDisplayName(ctx, uri) },
                    uri.lastPathSegment,
                ) ?: uri.toString(),
                mimeType = context?.contentResolver?.getType(uri),
            )
        }
    }

    fun upload() {
        val currentUri = uri as? Uri
        if (_uiState.value.fileName == null || _uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            try {
                val state = _uiState.value
                val fileName = state.fileName ?: "statement.pdf"
                val mimeType = resolveMimeType(fileName, state.mimeType)
                val result = statementRepository.upload(currentUri, fileName, mimeType)
                _uiState.update { it.copy(success = result) }
                invalidationBus.notifyDataChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not read the selected file.") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getString(index)
            } else {
                null
            }
        }
}

internal fun mimeTypeFor(fileName: String): String? = when {
    fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
    fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    else -> null
}

internal fun resolveFileName(displayName: String?, lastPathSegment: String?): String? =
    displayName?.takeIf { it.isNotBlank() }
        ?: lastPathSegment?.takeIf { it.isNotBlank() }?.substringAfterLast('/')

internal fun resolveMimeType(fileName: String, providerMime: String?): String =
    mimeTypeFor(fileName) ?: providerMime ?: "application/octet-stream"