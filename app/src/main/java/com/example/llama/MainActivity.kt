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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
            // More aggressively quantized model for faster inference
//            Downloadable(
//                "Llama3.2 1B (int3, 600 MB)",
//                Uri.parse("https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q3_K_M.gguf?download=true"),
//                File(filesDir, "Llama-3.2-1B-Instruct-Q3_K_M.gguf"),
//            ),

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
fun TypingIndicator() {
    val dots = remember { mutableStateOf(1) }
    
    // Animate the dots
    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // Change dots every 500ms
            dots.value = (dots.value % 3) + 1
        }
    }
    
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated dots
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index < dots.value) 
                            MaterialTheme.colorScheme.onTertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }
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

        if (text == "...") {
            // Show animated typing indicator instead of "..."
            TypingIndicator()
        } else {
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
}

@Composable
fun PerformanceMetricsDisplay(metrics: Message.PerformanceMetrics?) {
    metrics?.let {
        Column(
            modifier = Modifier.padding(top = 2.dp, start = 8.dp)
        ) {
            Text(
                text = "Prompt: ${it.promptTokenCount} tokens | TTFT: ${it.timeToFirstToken}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            Text(
                text = "Avg: ${String.format("%.1f", it.averageTimePerToken)}ms/token | " +
                      "Total: ${it.totalGenerationTime}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
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
    // Add a clear conversation button
    val scrollState = rememberLazyListState()

    // Track keyboard visibility
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Auto-scroll to the top (which is actually the bottom with reverseLayout=true)
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(0)
        }
    }

    // When keyboard visibility changes, ensure we can scroll the entire list
    LaunchedEffect(keyboardVisible) {
        // Small delay to let the keyboard animation complete
        delay(300)
        if (viewModel.messages.isNotEmpty()) {
            // If keyboard is visible, scroll to ensure we can see all content
            if (keyboardVisible && scrollState.firstVisibleItemIndex > 0) {
                scrollState.animateScrollToItem(scrollState.firstVisibleItemIndex)
            }
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

                // Model buttons and clear button
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Add clear conversation button
                    Button(
                        onClick = { viewModel.clear() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear Conversation")
                    }
                    
                    // Model buttons
                    for (model in models) {
                        Downloadable.Button(viewModel, model)
                    }
                }
            }
        }
    ) { paddingValues ->
        // Messages list with reversed layout and reversed items
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 8.dp
            ),
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true  // This makes newest items appear at the bottom
        ) {
            // Add spacer at the beginning (which is visually at the bottom with reverseLayout=true)
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Use reversed list so newest messages are at index 0
            items(viewModel.messages.reversed()) { message ->
                when {
                    message.startsWith("User: ") -> {
                        UserMessage(message.removePrefix("User: "))
                    }
                    message.startsWith("Assistant: ") -> {
                        val text = message.removePrefix("Assistant: ")
                        AssistantMessage(text)
                        
                        // Find the corresponding message in the session to get metrics
                        val sessionMessage = viewModel.getMessageForText(text, Message.SenderType.AI)
                        PerformanceMetricsDisplay(sessionMessage?.metadata?.performanceMetrics)
                    }
                    message.isNotEmpty() -> {
                        SystemMessage(message)
                    }
                }
            }

            // Add a spacer at the end to ensure all content can be scrolled into view
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
