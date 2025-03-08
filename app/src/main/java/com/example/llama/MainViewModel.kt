package com.example.llama

import android.llama.cpp.LLamaAndroid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(
    private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance(),
    private val sessionManager: SessionManager = SessionManager(),
    private val memoryManager: MemoryManager = MemoryManager(),
    private val contextConfig: ContextConfig = ContextConfig()
) : ViewModel() {
    
    // Add a response cache for common queries
    private val responseCache = mutableMapOf<String, String>()

    private val tag: String? = this::class.simpleName

    // Define the system prompt
    private val systemPrompt = """
        You are a helpful AI assistant. You aim to be accurate, informative, and engaging.
        You should be direct in your responses and avoid disclaimers.
        If you're unsure about something, admit it rather than making assumptions.
        Remember the conversation context and respond appropriately to follow-up questions.
        Keep your responses concise but complete.

        Format your responses with single line breaks between paragraphs. Avoid using multiple consecutive line breaks.

        IMPORTANT: Only respond as the assistant. DO NOT generate user messages or continue the conversation beyond your response.
        Simply provide your response without any XML tags.
    """.trimIndent()

    // UI state
    var messages by mutableStateOf(listOf<String>())
        private set

    var message by mutableStateOf("")
        private set

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            try {
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                messages += exc.message!!
            }
        }
    }


    // Track last UI update time for throttling
    private var lastUpdateTime = 0L
    
    // Helper function to determine when to update UI
    private fun shouldUpdateUI(response: String): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime > 50) { // Update at most every 50ms
            lastUpdateTime = currentTime
            return true
        }
        // Also update on sentence boundaries for more responsive feel
        return response.contains(". ") || response.contains("? ") || response.contains("! ")
    }
    
    // Helper function to find similar queries in cache
    private fun findSimilarQuery(query: String): String? {
        // Simple implementation - exact match only for now
        return responseCache.entries.firstOrNull { 
            it.key.lowercase().trim() == query.lowercase().trim() 
        }?.value
    }
    
    fun send() {
        val text = message
        message = ""
        
        // Check cache for similar queries
        val cachedResponse = findSimilarQuery(text)
        if (cachedResponse != null) {
            // Use cached response for immediate feedback
            messages += "User: $text"
            messages += "Assistant: $cachedResponse"
            return
        }

        // Add to messages console for UI
        messages += "User: $text"
        messages += ""  // Empty string for AI response placeholder

        viewModelScope.launch {
            // Create and add user message to session
            val userMessage = createMessage(text, Message.SenderType.USER)
            sessionManager.addMessage(userMessage)

            // Prepare context with history and relevant memories
            val context = prepareContext()

            // Variables to track the complete response
            val responseBuilder = StringBuilder()
            var currentResponse = ""

            // Only update UI during streaming, don't modify session
            llamaAndroid.send(context, formatChat = true)
                .catch { /* error handling */ }
                .collect { response ->
                    responseBuilder.append(response)
                    currentResponse += response
                    
                    // Update UI less frequently for better performance
                    if (shouldUpdateUI(currentResponse)) {
                        // Update UI only, don't modify session
                        if (messages.last().isEmpty()) {
                            messages = messages.dropLast(1) + "Assistant: $responseBuilder"
                        } else {
                            // More efficient update that doesn't recreate the entire list
                            val currentMessages = messages.toMutableList()
                            currentMessages[currentMessages.lastIndex] = responseBuilder.toString()
                            messages = currentMessages
                        }
                        currentResponse = "" // Reset current response after update
                    }
                }

            // Ensure final response is displayed
            if (currentResponse.isNotEmpty()) {
                val currentMessages = messages.toMutableList()
                currentMessages[currentMessages.lastIndex] = responseBuilder.toString()
                messages = currentMessages
            }

            val userTagIndex = responseBuilder.toString().indexOf("User:")
            if (userTagIndex > 0) {
                val finalAiMessage = createMessage(responseBuilder.toString().substring(0, userTagIndex).trim(), Message.SenderType.AI)
                sessionManager.addMessage(finalAiMessage)
                updateMemory(finalAiMessage)
            } else {
                val finalAiMessage = createMessage(responseBuilder.toString(), Message.SenderType.AI)
                sessionManager.addMessage(finalAiMessage)
                updateMemory(finalAiMessage)
            }
        }
    }

    private suspend fun prepareContext(): String {
        val session = sessionManager.requireActiveSession()
        // Removed relevantMemories lookup to reduce latency
        
        return buildString {
            // No BOS token - let llama.cpp add it automatically
            append("<|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>")

            // Apply context window strategy with reduced context size
            when (contextConfig.strategy) {
                ContextStrategy.SLIDING_WINDOW -> {
                    session.messages
                        .takeLast(10) // Reduced from contextConfig.nContext for faster processing
                        .forEach { message ->
                            when (message.sender) {
                                Message.SenderType.USER -> append("<|start_header_id|>user<|end_header_id|>${message.text}<|eot_id|>")
                                Message.SenderType.AI -> append("<|start_header_id|>assistant<|end_header_id|>${message.text}<|eot_id|>")
                                else -> append("${message.sender}: ${message.text}")
                            }
                        }
                }
                ContextStrategy.SUMMARY -> {
                    // Implement summarization in the future
                    session.messages
                        .takeLast(10) // Reduced from contextConfig.nContext for faster processing
                        .forEach { message ->
                            when (message.sender) {
                                Message.SenderType.USER -> append("User: ${message.text}\n")
                                Message.SenderType.AI -> append("Assistant: ${message.text}\n")
                                else -> append("${message.sender}: ${message.text}\n")
                            }
                        }
                }
            }

            // Add a final prompt for the AI to respond
            if (session.messages.lastOrNull()?.sender == Message.SenderType.USER) {
                append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
        }
    }

    private suspend fun updateMemory(message: Message) {
        // Extract key information and store in memory
        memoryManager.setMemory(
            "fact_${message.id}",
            message.text,
            sessionManager.requireActiveSession().id
        )
    }

    private fun createMessage(
        text: String,
        sender: Message.SenderType,
        modelParams: Message.ModelParams? = null
    ): Message {
        return Message(
            text = text,
            sender = sender,
            metadata = Message.MessageMetadata(
                tokens = countTokens(text),
                contextReferences = emptyList(),
                modelParams = modelParams
            )
        )
    }

    private fun countTokens(text: String): Int {
        // Simple token counting implementation
        return text.split(" ").size
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        viewModelScope.launch {
            try {
                val start = System.nanoTime()
                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
                val end = System.nanoTime()

                messages += warmupResult

                val warmup = (end - start).toDouble() / 1_000_000_000.0
                messages += "Warm up time: $warmup seconds, please wait..."

                if (warmup > 5.0) {
                    messages += "Warm up took too long, aborting benchmark"
                    return@launch
                }

                messages += llamaAndroid.bench(512, 128, 1, 3)
            } catch (exc: IllegalStateException) {
                Log.e(tag, "bench() failed", exc)
                messages += exc.message!!
            }
        }
    }

    fun load(pathToModel: String) {
        viewModelScope.launch {
            try {
                llamaAndroid.load(pathToModel)
                messages += "Loaded $pathToModel"
            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
                messages += exc.message!!
            }
        }
    }

    fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun clear() {
        messages = listOf()

        viewModelScope.launch {
            val sessionId = sessionManager.requireActiveSession().id
            sessionManager.clearSessionMessages(sessionId)
        }
    }

    fun log(message: String) {
        messages += message
    }
}
