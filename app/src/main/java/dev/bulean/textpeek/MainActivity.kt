package dev.bulean.textpeek

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

data class FileItem(val name: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    private var recentFiles by mutableStateOf(listOf<FileItem>())

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val fileName = getFileNameFromUri(uri)

                    if (fileName.endsWith(".txt", ignoreCase = true) ||
                        fileName.endsWith(".pim", ignoreCase = true) ||
                        fileName.endsWith(".pit", ignoreCase = true) ||
                        fileName.endsWith(".gcode", ignoreCase = true)
                    ) {
                        val fileContent = readFileContent(uri)

                        if (recentFiles.none { it.uri == uri }) {
                            recentFiles = recentFiles + FileItem(name = fileName, uri = uri)
                            saveRecentFiles(recentFiles)
                        }

                        val intent = Intent(this, FileContentActivity::class.java).apply {
                            putExtra("fileName", fileName)
                            putExtra("fileContent", fileContent)
                        }
                        startActivity(intent)
                    } else {
                        // Notify unsupported file type
                        onUnsupportedFile()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    private var onUnsupportedFile: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load recent files
        recentFiles = loadRecentFiles()

        setContent {
            var snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            // Callback for unsupported files
            onUnsupportedFile = {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Unsupported file type",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Short
                    )
                }
            }

            TextPeekApp(
                recentFiles = recentFiles,
                snackbarHostState = snackbarHostState,
                onOpenFile = { openFilePicker() },
                onFileClick = { fileItem ->
                    val fileContent = readFileContent(fileItem.uri)

                    val intent = Intent(this, FileContentActivity::class.java).apply {
                        putExtra("fileName", fileItem.name)
                        putExtra("fileContent", fileContent)
                    }
                    startActivity(intent)
                }
            )
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "Unknown"
    }

    private fun readFileContent(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun saveRecentFiles(files: List<FileItem>) {
        val sharedPreferences = getSharedPreferences("TextPeekPrefs", Context.MODE_PRIVATE)
        val fileUris = files.map { it.uri.toString() }.toSet()
        sharedPreferences.edit().putStringSet("RecentFileUris", fileUris).apply()
    }

    private fun loadRecentFiles(): List<FileItem> {
        val sharedPreferences = getSharedPreferences("TextPeekPrefs", Context.MODE_PRIVATE)
        val fileUris = sharedPreferences.getStringSet("RecentFileUris", emptySet()) ?: emptySet()
        return fileUris.mapNotNull { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val name = getFileNameFromUri(uri)
                FileItem(name = name, uri = uri)
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun TextPeekApp(
    recentFiles: List<FileItem>,
    snackbarHostState: SnackbarHostState,
    onOpenFile: () -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "TextPeek",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Recent Files List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(recentFiles) { file ->
                    RecentFileItem(fileName = file.name) {
                        onFileClick(file)
                    }
                }
            }

            // Open File Button
            Button(
                onClick = onOpenFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(text = "Open File")
            }
        }
    }
}

@Composable
fun RecentFileItem(fileName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.LightGray, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}
