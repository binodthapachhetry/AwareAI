package com.example.llama

import ContextConfig
import MemoryManager
import Message
import SessionManager
import android.llama.cpp.LLamaAndroid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


 class MainViewModel(
     private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance(),
     private val sessionManager: SessionManager,
     private val memoryManager: MemoryManager,
     private val contextConfig: ContextConfig
 ) : ViewModel() {

     private val _messages = MutableStateFlow<List<Message>>(emptyList())
     val messages: StateFlow<List<Message>> = _messages.asStateFlow()

     fun send(text: String) {
         viewModelScope.launch {
             val userMessage = createMessage(text, Message.SenderType.USER)
             sessionManager.addMessage(userMessage)

             val context = prepareContext()

             llamaAndroid.send(context, formatChat = true)
                 .catch { /* Handle errors */ }
                 .collect { response ->
                     val aiMessage = createMessage(response, Message.SenderType.AI)
                     sessionManager.addMessage(aiMessage)
                     updateMemory(aiMessage)
                 }
         }
     }

     private suspend fun prepareContext(): String {
         val session = sessionManager.requireActiveSession()
         val relevantMemories = memoryManager.getRelatedMemories(
             session.messages.last().text,
             session.id
         )

         return buildString {
             append(systemPrompt)
             append("\n\n")
             append("Relevant context:\n")
             relevantMemories.forEach { append("- $it\n") }
             append("\n")

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
                     // Implement summarization
                 }
             }
         }
     }

     private suspend fun updateMemory(message: Message) {
         // Extract key information and store in memory
         // This could be enhanced with ML-based fact extraction
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
         // Implement token counting
         // Could use external tokenizer library
         return text.split(" ").size // Naive implementation
     }
 }


//class MainViewModel(
//    private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()
//    private val sessionManager: SessionManager,
//    private val memoryManager: MemoryManager,
//    private val contextConfig: ContextConfig
//    ): ViewModel() {
//
//    companion object {
//        @JvmStatic
//        private val NanosPerSecond = 1_000_000_000.0
//    }
//
//    // Define the system prompt
//     private val systemPrompt = """
//         You are a helpful AI assistant. You aim to be accurate, informative, and engaging.
//         You should be direct in your responses and avoid disclaimers.
//         If you're unsure about something, admit it rather than making assumptions.
//     """.trimIndent()
//
//    // Keep track of the conversation history
//    private var conversationHistory = mutableListOf<ChatMessage>()
//
//    // Define a data class for chat messages
//     private data class ChatMessage(
//         val role: String,
//         val content: String
//     )
//
//    // Format the entire conversation including system prompt
//    private fun formatConversation(): String {
//        val conversation = StringBuilder()
//
//        // Add system prompt
//        conversation.append("System: ${systemPrompt}\n\n")
//
//        // Add conversation history
//        conversationHistory.forEach { message ->
//            conversation.append("${message.role}: ${message.content}\n")
//        }
//
//        return conversation.toString()
//    }
//
//    private val tag: String? = this::class.simpleName
//
//    var messages by mutableStateOf(listOf("Initializing..."))
//        private set
//
//    var message by mutableStateOf("")
//        private set
//
//    override fun onCleared() {
//        super.onCleared()
//
//        viewModelScope.launch {
//            try {
//                llamaAndroid.unload()
//            } catch (exc: IllegalStateException) {
//                messages += exc.message!!
//            }
//        }
//    }
//
//    fun send() {
//        val text = message
//        message = ""
//
//        // Add user message to history
//         conversationHistory.add(ChatMessage("User", text))
//
//         // Add to messages console
//         messages += "User: $text"
//         messages += ""
//
//        viewModelScope.launch {
//            llamaAndroid.send(message = formatConversation(),
//                 formatChat = true
//                 )
//                .catch {
//                    Log.e(tag, "send() failed", it)
//                    messages += it.message!!
//                }
//                .collect { response ->
//                 // Store assistant's response in history
//                 if (messages.last().isEmpty()) {
//                     conversationHistory.add(ChatMessage("Assistant", response))
//                 }
//
//                 // Update UI
//                 messages = messages.dropLast(1) + (messages.last() + response)
//             }
//        }
//    }
//
//    // Optional: Add method to clear conversation
//     fun clearConversation() {
//         conversationHistory.clear()
//         clear()
//     }
//
//    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
//        viewModelScope.launch {
//            try {
//                val start = System.nanoTime()
//                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
//                val end = System.nanoTime()
//
//                messages += warmupResult
//
//                val warmup = (end - start).toDouble() / NanosPerSecond
//                messages += "Warm up time: $warmup seconds, please wait..."
//
//                if (warmup > 5.0) {
//                    messages += "Warm up took too long, aborting benchmark"
//                    return@launch
//                }
//
//                messages += llamaAndroid.bench(512, 128, 1, 3)
//            } catch (exc: IllegalStateException) {
//                Log.e(tag, "bench() failed", exc)
//                messages += exc.message!!
//            }
//        }
//    }
//
//    fun load(pathToModel: String) {
//        viewModelScope.launch {
//            try {
//                llamaAndroid.load(pathToModel)
//                messages += "Loaded $pathToModel"
//            } catch (exc: IllegalStateException) {
//                Log.e(tag, "load() failed", exc)
//                messages += exc.message!!
//            }
//        }
//    }
//
//    fun updateMessage(newMessage: String) {
//        message = newMessage
//    }
//
//    fun clear() {
//        messages = listOf()
//    }
//
//    fun log(message: String) {
//        messages += message
//    }
//}
