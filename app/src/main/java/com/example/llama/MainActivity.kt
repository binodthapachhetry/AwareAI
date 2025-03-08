package com.example.llama

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.example.llama.ui.theme.LlamaAndroidTheme
import java.io.File

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
): ComponentActivity() {
    private val tag: String? = this::class.simpleName

    private val activityManager by lazy { activityManager ?: getSystemService<ActivityManager>()!! }
    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy { clipboardManager ?: getSystemService<ClipboardManager>()!! }

    private val viewModel: MainViewModel by viewModels()

    // Get a MemoryInfo object for the device's current memory status.
    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)

        viewModel.log("Current memory: $free / $total")
        viewModel.log("Models directory: ${filesDir.absolutePath}")

        // Use app-private storage for model files
        val models = listOf(

            Downloadable(
                "Llama3.2 1B (int4, 808 MB)",
                Uri.parse("https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf?download=true"),
                File(filesDir, "Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
            ),
        )

        setContent {
            LlamaAndroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainCompose(
                        viewModel,
                        clipboardManager,
                        downloadManager,
                        models,
                    )
                }

            }
        }
    }
}

@Composable
fun UserMessage(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "You",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp, end = 4.dp)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(6.dp)
                .fillMaxWidth(0.8f)
        )
    }
}

@Composable
fun AssistantMessage(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Assistant",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
        )

        Text(
            text = text.replace("\n\n+".toRegex(), "\n\n").trim(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(6.dp)
                .fillMaxWidth(0.8f)
        )
    }
}

@Composable
fun SystemMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp), // Reduced vertical padding
        contentAlignment = Alignment.CenterStart // Already left-aligned
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp) // Adjusted padding
        )
    }
}

@Composable
fun MainCompose(
    viewModel: MainViewModel,
    clipboard: ClipboardManager,
    dm: DownloadManager, // Keep this parameter even though we don't use it
    models: List<Downloadable>
) {
    val scrollState = rememberLazyListState()
    
    // Auto-scroll to the bottom when new messages arrive
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        bottomBar = {
            Column {
                // Text field with send button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.message,
                        onValueChange = { viewModel.updateMessage(it) },
                        label = { Text("Message") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    
                    Button(
                        onClick = { viewModel.send() },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send"
                        )
                    }
                }
                
                // Model buttons
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (model in models) {
                        Downloadable.Button(viewModel, model)
                    }
                }
            }
        }
    ) { paddingValues ->
        // Messages list
        LazyColumn(
            state = scrollState,
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = false
        ) {
            items(viewModel.messages) { message ->
                when {
                    message.startsWith("User: ") -> {
                        UserMessage(message.removePrefix("User: "))
                    }
                    message.startsWith("Assistant: ") -> {
                        AssistantMessage(message.removePrefix("Assistant: "))
                    }
                    message.isNotEmpty() -> {
                        SystemMessage(message)
                    }
                }
            }
            
            // Add spacer at the end to push content up from the bottom
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
