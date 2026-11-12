package com.arthapilot.app.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.StatementUploadResult

data class UploadUiState(
    val fileName: String? = null,
    val mimeType: String? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
    val success: StatementUploadResult? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatementScreen(
    onBack: () -> Unit,
    onUploadComplete: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFileSelected(uri)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success != null) {
            onUploadComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload statement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.FileUpload,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Upload a bank statement",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "We'll parse the transactions and categorize them automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.success != null) {
                Text(
                    text = "Statement uploaded",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${uiState.success!!.transactionCount} transactions imported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View transactions")
                }
            } else if (uiState.isUploading) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Uploading and parsing…")
            } else {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("application/pdf", "text/csv", "text/comma-separated-values")) },
                    enabled = !uiState.isUploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(uiState.fileName ?: "Choose PDF or CSV file")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::upload,
                    enabled = uiState.fileName != null && !uiState.isUploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Upload")
                }
            }
        }
    }
}
