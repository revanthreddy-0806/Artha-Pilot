package com.arthapilot.app.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val statementRepository: StatementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var uri: Uri? = null

    fun onFileSelected(uri: Uri) {
        this.uri = uri
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        _uiState.update { it.copy(selectedFileName = name) }
    }

    fun upload() {
        val currentUri = uri
        if (currentUri == null || _uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            try {
                val fileName = _uiState.value.selectedFileName ?: "statement.pdf"
                val result = statementRepository.upload(currentUri, fileName, mimeTypeFor(fileName))
                _uiState.update { it.copy(success = result) }
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

    private fun mimeTypeFor(fileName: String): String = when {
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        else -> "application/octet-stream"
    }
}