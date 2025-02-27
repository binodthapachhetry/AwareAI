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

    private val tag: String? = this::class.simpleName

    // Define the system prompt
    private val systemPrompt = """
        You are a helpful AI assistant. You aim to be accurate, informative, and engaging.
        You should be direct in your responses and avoid disclaimers.
        If you're unsure about something, admit it rather than making assumptions.
    """.trimIndent()

    // UI state
    var messages by mutableStateOf(listOf("Initializing..."))
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

    fun send() {
        val text = message
        message = ""

        // Add to messages console for UI
        messages += "User: $text"
        messages += ""

        viewModelScope.launch {
            // Create and add user message to session
            val userMessage = createMessage(text, Message.SenderType.USER)
            sessionManager.addMessage(userMessage)

            // Prepare context with history and relevant memories
            val context = prepareContext()

            // Send to LLM
            llamaAndroid.send(context, formatChat = true)
                .catch {
                    Log.e(tag, "send() failed", it)
                    messages += it.message ?: "Error during LLM processing"
                }
                .collect { response ->
                    // Create and store AI message
                    val aiMessage = createMessage(response, Message.SenderType.AI)
                    sessionManager.addMessage(aiMessage)
                    updateMemory(aiMessage)
                    
                    // Update UI
                    if (messages.last().isEmpty()) {
                        messages = messages.dropLast(1) + response
                    } else {
                        messages = messages.dropLast(1) + (messages.last() + response)
                    }
                }
        }
    }

    private suspend fun prepareContext(): String {
        val session = sessionManager.requireActiveSession()
        val relevantMemories = if (session.messages.isNotEmpty()) {
            memoryManager.getRelatedMemories(
                session.messages.last().text,
                session.id
            )
        } else {
            emptyList()
        }

        return buildString {
            append("System: $systemPrompt\n\n")
            
            if (relevantMemories.isNotEmpty()) {
                append("Relevant context:\n")
                relevantMemories.forEach { append("- $it\n") }
                append("\n")
            }

            // Apply context window strategy
            when (contextConfig.strategy) {
                ContextStrategy.SLIDING_WINDOW -> {
                    session.messages
                        .takeLast(contextConfig.nContext)
                        .forEach { message ->
                            append("${message.sender}: ${message.text}\n")
                        }
                }
                ContextStrategy.SUMMARY -> {
                    // Implement summarization in the future
                    session.messages
                        .takeLast(contextConfig.nContext)
                        .forEach { message ->
                            append("${message.sender}: ${message.text}\n")
                        }
                }
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
