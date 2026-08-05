package com.arthapilot.app.ui.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementUploadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
                val bytes = readAllBytes(currentUri)
                val fileName = _uiState.value.selectedFileName ?: "statement.pdf"
                val mime = "application/pdf"
                val result: StatementUploadResult = statementRepository.upload(fileName, mime, bytes)
                _uiState.update { it.copy(success = result) }
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

    private suspend fun readAllBytes(currentUri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(currentUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
    }
}