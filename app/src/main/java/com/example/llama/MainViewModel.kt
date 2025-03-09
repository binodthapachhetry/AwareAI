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

    // Helper function to find a message by text and sender type
    fun getMessageForText(text: String, senderType: Message.SenderType): Message? {
        return try {
            // Use a non-suspend way to get the current session
            val session = sessionManager.sessions.value.find {
                it.id == sessionManager.activeSessionId.value
            } ?: return null

            session.messages.lastOrNull {
                it.sender == senderType && it.text.trim() == text.trim()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Add a response cache for common queries
    private val responseCache = mutableMapOf<String, String>()

    private val tag: String? = this::class.simpleName

    // Define the system prompt
    private val systemPrompt = """You are a helpful, concise assistant. Respond directly without disclaimers.""".trimIndent()

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



    // Helper function to find similar queries in cache
    private fun findSimilarQuery(query: String): String? {
        // Normalize the query
        val normalizedQuery = query.lowercase().trim()

        // First try exact match
        responseCache.entries.firstOrNull {
            it.key.lowercase().trim() == normalizedQuery
        }?.let { return it.value }

        // Then try fuzzy matching for short queries (more likely to have similar variants)
        if (normalizedQuery.length < 20) {
            responseCache.entries.firstOrNull {
                val similarity = calculateSimilarity(it.key.lowercase().trim(), normalizedQuery)
                similarity > 0.8 // 80% similarity threshold
            }?.let { return it.value }
        }

        return null
    }

    // Simple string similarity calculation (Jaccard similarity)
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // Create sets of words
        val words1 = s1.split(Regex("\\s+")).toSet()
        val words2 = s2.split(Regex("\\s+")).toSet()

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return intersection.toDouble() / union.toDouble()
    }

    fun send() {
        val text = message
        message = ""

        // Add user message to UI
        messages += "User: $text"

        // Check cache for quick response
        val cachedResponse = findSimilarQuery(text)
        if (cachedResponse != null) {
            // Use cached response for immediate feedback
            messages += "Assistant: $cachedResponse"

            // Still add to session history
            viewModelScope.launch {
                val userMessage = createMessage(text, Message.SenderType.USER)
                sessionManager.addMessage(userMessage)

                val aiMessage = createMessage(cachedResponse, Message.SenderType.AI)
                sessionManager.addMessage(aiMessage)
            }
            return
        }

        // Add typing indicator as a temporary message
        messages += "Assistant: ..." // Typing indicator

        viewModelScope.launch {
            try {
                // Create and add user message to session
                val userMessage = createMessage(text, Message.SenderType.USER)
                sessionManager.addMessage(userMessage)

                // Prepare context with history and relevant memories
                val context = prepareContext()

                // Count tokens in the prompt for performance metrics
                val promptTokenCount = countTokens(context)

                // Timing variables for performance metrics
                val startTime = System.currentTimeMillis()
                var firstTokenTime: Long = 0
                var tokenCount = 0

                // Variables to track the complete response
                val responseBuilder = StringBuilder()
                var firstChunkReceived = false

                // Stream the response with improved error handling
                llamaAndroid.send(context, formatChat = true)
                    .catch { e ->
                        Log.e(tag, "Error generating response", e)
                        
                        // Check for binder errors
                        if (e.message?.contains("ProcessState") == true || 
                            e.message?.contains("binder") == true) {
                            
                            // Handle binder error specifically
                            messages = messages.dropLast(1) + 
                                "Assistant: Sorry, I encountered a system limitation. " +
                                "Please clear the conversation and try again."
                            
                            // Try to reset the model
                            try {
                                llamaAndroid.clearCache()
                            } catch (ex: Exception) {
                                Log.e(tag, "Error resetting model cache", ex)
                            }
                        } else {
                            // Handle other errors
                            messages = messages.dropLast(1) + "Assistant: Error: ${e.message}"
                        }
                    }
                    .collect { response ->
                        val currentTime = System.currentTimeMillis()

                        // Track first token time
                        if (!firstChunkReceived) {
                            firstTokenTime = currentTime - startTime
                            Log.d(tag, "Time to first token: $firstTokenTime ms")
                        }

                        // Count tokens in the response chunk
                        val chunkTokens = countTokens(response)
                        tokenCount += chunkTokens

                        responseBuilder.append(response)

                        // Update UI with the response
                        val currentMessages = messages.toMutableList()

                        if (!firstChunkReceived) {
                            // Replace typing indicator with first chunk of actual response
                            currentMessages[currentMessages.lastIndex] = "Assistant: " + response
                            firstChunkReceived = true
                        } else {
                            // Append to the existing message
                            currentMessages[currentMessages.lastIndex] =
                                currentMessages.last() + response
                        }

                        messages = currentMessages
                    }

                // Calculate final performance metrics
                val endTime = System.currentTimeMillis()
                val totalTime = endTime - startTime

                // Calculate average time per token (excluding first token)
                val averageTimePerToken = if (tokenCount > 1) {
                    (totalTime - firstTokenTime).toFloat() / (tokenCount - 1)
                } else {
                    0f
                }

                Log.d(tag, "Performance metrics: promptTokens=$promptTokenCount, responseTokens=$tokenCount, " +
                           "ttft=${firstTokenTime}ms, avg=${averageTimePerToken}ms/token, total=${totalTime}ms")

                // Process the final response
                val userTagIndex = responseBuilder.toString().indexOf("User:")
                val finalResponseText = if (userTagIndex > 0) {
                    responseBuilder.toString().substring(0, userTagIndex).trim()
                } else {
                    responseBuilder.toString()
                }

                // Add to cache for future use
                responseCache[text] = finalResponseText

                // Create performance metrics
                val metrics = Message.PerformanceMetrics(
                    promptTokenCount = promptTokenCount,
                    responseTokenCount = tokenCount,
                    timeToFirstToken = firstTokenTime,
                    averageTimePerToken = averageTimePerToken,
                    totalGenerationTime = totalTime
                )

                // Create the final AI message with performance metrics
                val finalAiMessage = createMessage(
                    text = finalResponseText,
                    sender = Message.SenderType.AI,
                    performanceMetrics = metrics
                )

                sessionManager.addMessage(finalAiMessage)
                updateMemory(finalAiMessage)
            } catch (e: Exception) {
                // Global error handler
                Log.e(tag, "Error in send()", e)
                messages = messages.dropLast(1) + "Assistant: Sorry, an error occurred. Please try again."
            }
        }
    }

    private suspend fun prepareContext(): String {
        val session = sessionManager.requireActiveSession()
        
        // Set a strict token budget
        val MAX_CONTEXT_TOKENS = 512 // Very aggressive limit
        var tokenBudget = MAX_CONTEXT_TOKENS
        
        return buildString {
            // Add system prompt (essential)
            val systemPromptTokens = countTokens(systemPrompt)
            append("<|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>")
            tokenBudget -= systemPromptTokens
            
            // Get the most recent user message (must include)
            val lastUserMessage = session.messages.lastOrNull { it.sender == Message.SenderType.USER }
            if (lastUserMessage != null) {
                val lastUserTokens = countTokens(lastUserMessage.text)
                tokenBudget -= lastUserTokens
            }
            
            // Add conversation history within remaining budget
            val relevantMessages = mutableListOf<Message>()
            
            // Start from most recent and work backwards
            for (message in session.messages.reversed()) {
                // Skip the last user message (already accounted for)
                if (message == lastUserMessage) continue
                
                val messageTokens = countTokens(message.text)
                if (tokenBudget - messageTokens >= 0) {
                    relevantMessages.add(0, message) // Add to front to maintain order
                    tokenBudget -= messageTokens
                } else {
                    // No more budget for additional messages
                    break
                }
            }
            
            Log.d(tag, "Context includes ${relevantMessages.size} messages within token budget")
            
            // Add the messages that fit within budget
            for (message in relevantMessages) {
                when (message.sender) {
                    Message.SenderType.USER -> append("<|start_header_id|>user<|end_header_id|>${message.text}<|eot_id|>")
                    Message.SenderType.AI -> append("<|start_header_id|>assistant<|end_header_id|>${message.text}<|eot_id|>")
                    else -> append("${message.sender}: ${message.text}")
                }
            }
            
            // Add the last user message (always include)
            if (lastUserMessage != null) {
                append("<|start_header_id|>user<|end_header_id|>${lastUserMessage.text}<|eot_id|>")
            }
            
            // Add a final prompt for the AI to respond
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
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
        modelParams: Message.ModelParams? = null,
        performanceMetrics: Message.PerformanceMetrics? = null
    ): Message {
        return Message(
            text = text,
            sender = sender,
            metadata = Message.MessageMetadata(
                tokens = countTokens(text),
                contextReferences = emptyList(),
                modelParams = modelParams,
                performanceMetrics = performanceMetrics
            )
        )
    }

    private fun countTokens(text: String): Int {
        // Improved token counting implementation
        // This is still an approximation but better than simple word splitting
        // A proper tokenizer would be more accurate but more complex
        
        // Count words (basic approximation)
        val wordCount = text.split(Regex("\\s+")).size
        
        // Count punctuation (each is typically a separate token)
        val punctuationCount = text.count { it in ".,!?;:()[]{}\"'" }
        
        // Count special tokens in the format (each is typically a separate token)
        val specialTokenCount = text.count { it == '<' || it == '>' || it == '|' }
        
        return wordCount + punctuationCount + (specialTokenCount / 2) // Divide by 2 as we count pairs
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
            try {
                // Clear KV cache to prevent memory issues
                llamaAndroid.clearCache()
                
                // Clear session messages
                val sessionId = sessionManager.requireActiveSession().id
                sessionManager.clearSessionMessages(sessionId)
                
                log("Conversation cleared and memory reset")
            } catch (e: Exception) {
                Log.e(tag, "Error clearing conversation", e)
                log("Error clearing conversation: ${e.message}")
            }
        }
    }

    fun log(message: String) {
        messages += message
    }
}
