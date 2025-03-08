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


    fun send() {
        val text = message
        message = ""

//        Log.d(tag, "send() text: $text")

        // Add to messages console for UI
        messages += "User: $text"
        messages += ""  // Empty string for AI response placeholder

//        Log.d(tag, "send() messages: $messages")

        viewModelScope.launch {
            // Create and add user message to session
            val userMessage = createMessage(text, Message.SenderType.USER)
            sessionManager.addMessage(userMessage)

            // Prepare context with history and relevant memories
            val context = prepareContext()

            Log.d(tag, "send() context to the LLM: $context")

            // Variables to track the complete response
            val responseBuilder = StringBuilder()
            var tempAiMessage: Message? = null

            // Only update UI during streaming, don't modify session
            llamaAndroid.send(context, formatChat = true)
                .catch { /* error handling */ }
                .collect { response ->
                    responseBuilder.append(response)

                    // Update UI only, don't modify session
                    if (messages.last().isEmpty()) {
                        messages = messages.dropLast(1) + "Assistant: $response"
                    } else {
                        messages = messages.dropLast(1) + (messages.last() + response)
                    }

                }

            val userTagIndex = responseBuilder.toString().indexOf("User:")
            if (userTagIndex > 0) {
                val finalAiMessage = createMessage(responseBuilder.toString().substring(0, userTagIndex).trim(), Message.SenderType.AI)
                sessionManager.addMessage(finalAiMessage)
//                Log.d(tag, "send() created final message: ${finalAiMessage.text}")
                updateMemory(finalAiMessage)
            }else{
                val finalAiMessage = createMessage(responseBuilder.toString(), Message.SenderType.AI)
                sessionManager.addMessage(finalAiMessage)
//                Log.d(tag, "send() created final message: ${finalAiMessage.text}")
                updateMemory(finalAiMessage)
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
            // No BOS token - let llama.cpp add it automatically
            append("<|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>")

//            if (relevantMemories.isNotEmpty()) {
//                append("<CONTEXT>\n")
//                relevantMemories.forEach { append("- $it\n") }
//                append("</CONTEXT>\n\n")
//            }

            // Apply context window strategy
            when (contextConfig.strategy) {
                ContextStrategy.SLIDING_WINDOW -> {
                    session.messages
                        .takeLast(contextConfig.nContext)
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
                        .takeLast(contextConfig.nContext)
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
