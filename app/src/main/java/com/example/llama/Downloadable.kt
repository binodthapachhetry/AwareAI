package com.example.llama

import android.net.Uri
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Downloadable(val name: String, val source: Uri, val destination: File) {
    companion object {
        @JvmStatic
        private val tag: String? = this::class.qualifiedName

        sealed interface State
        data object Ready: State
        data class Downloading(val progress: Float): State
        data class Downloaded(val downloadable: Downloadable): State
        data class Error(val message: String): State

        @JvmStatic
        @Composable
        fun Button(viewModel: MainViewModel, item: Downloadable) {
            var status: State by remember {
                mutableStateOf(
                    if (item.destination.exists()) Downloaded(item)
                    else Ready
                )
            }
            var progress by remember { mutableFloatStateOf(0f) }

            val coroutineScope = rememberCoroutineScope()

            fun onClick() {
                when (val s = status) {
                    is Downloaded -> {
                        viewModel.load(item.destination.path)
                    }

                    is Downloading -> {
                        // Already downloading, do nothing
                    }

                    else -> {
                        // Start download
                        coroutineScope.launch(Dispatchers.IO) {
                            status = Downloading(0f)
                            
                            try {
                                // Ensure parent directories exist
                                item.destination.parentFile?.mkdirs()
                                
                                val url = URL(item.source.toString())
                                val connection = url.openConnection() as HttpURLConnection
                                connection.connectTimeout = 30000
                                connection.readTimeout = 30000
                                connection.connect()
                                
                                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                                    withContext(Dispatchers.Main) {
                                        status = Error("Server returned HTTP ${connection.responseCode}")
                                    }
                                    return@launch
                                }
                                
                                val fileSize = connection.contentLength
                                var downloadedSize = 0
                                
                                FileOutputStream(item.destination).use { output ->
                                    connection.inputStream.use { input ->
                                        val buffer = ByteArray(8192)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            downloadedSize += bytesRead
                                            
                                            // Update progress
                                            if (fileSize > 0) {
                                                val currentProgress = downloadedSize.toFloat() / fileSize
                                                withContext(Dispatchers.Main) {
                                                    progress = currentProgress
                                                    status = Downloading(currentProgress)
                                                }
                                            }
                                        }
                                        output.flush()
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    viewModel.log("Download complete: ${item.destination.path}")
                                    status = Downloaded(item)
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Download failed", e)
                                withContext(Dispatchers.Main) {
                                    viewModel.log("Download failed: ${e.message}")
                                    status = Error("Download failed: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = { onClick() }, enabled = status !is Downloading) {
                when (status) {
                    is Downloading -> Text(text = "Downloading ${(progress * 100).toInt()}%")
                    is Downloaded -> Text("Load ${item.name}")
                    is Ready -> Text("Download ${item.name}")
                    is Error -> Text("Download ${item.name}")
                }
            }
        }
    }
}
