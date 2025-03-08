package com.example.llama

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MessageMetadata? = null
) {
    enum class SenderType {
        USER, AI, SYSTEM
    }
    
    data class MessageMetadata(
        val tokens: Int = 0,
        val contextReferences: List<String> = emptyList(),
        val modelParams: ModelParams? = null
    )
    
    data class ModelParams(
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val repetitionPenalty: Float = 1.1f
    )
}

class SessionManager {
    data class Session(
        val id: String = UUID.randomUUID().toString(),
        val name: String = "New Conversation",
        val messages: List<Message> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val metadata: Map<String, String> = emptyMap()
    )

    private val _sessions = MutableStateFlow<List<Session>>(listOf(Session()))
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>(_sessions.value.first().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    suspend fun requireActiveSession(): Session = withContext(Dispatchers.IO) {
        return@withContext _sessions.value.find { it.id == _activeSessionId.value }
            ?: throw IllegalStateException("No active session found")
    }

    suspend fun createNewSession(name: String = "New Conversation"): String = withContext(Dispatchers.IO) {
        val newSession = Session(name = name)
        _sessions.update { it + newSession }
        _activeSessionId.value = newSession.id
        return@withContext newSession.id
    }

    suspend fun setActiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        } else {
            throw IllegalArgumentException("Session with ID $sessionId not found")
        }
    }

    suspend fun addMessage(message: Message) = withContext(Dispatchers.IO) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == _activeSessionId.value) {
                    session.copy(
                        messages = session.messages + message,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    session
                }
            }
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        _sessions.update { it.filter { session -> session.id != sessionId } }
        if (_activeSessionId.value == sessionId && _sessions.value.isNotEmpty()) {
            _activeSessionId.value = _sessions.value.first().id
        }
    }

    suspend fun renameSession(sessionId: String, newName: String) = withContext(Dispatchers.IO) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(name = newName)
                } else {
                    session
                }
            }
        }
    }

    suspend fun clearSessionMessages(sessionId: String) = withContext(Dispatchers.IO) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(messages = emptyList())
                } else {
                    session
                }
            }
        }
    }

    suspend fun removeMessage(messageId: String) = withContext(Dispatchers.IO) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == _activeSessionId.value) {
                    session.copy(
                        messages = session.messages.filterNot { it.id == messageId }
                    )
                } else {
                    session
                }
            }
        }
    }
    
    // New function to update session metadata
    suspend fun updateSessionMetadata(sessionId: String, metadata: Map<String, String>) = withContext(Dispatchers.IO) {
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(
                        metadata = session.metadata + metadata,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    session
                }
            }
        }
    }
    
    // New function to get session by ID
    suspend fun getSession(sessionId: String): Session? = withContext(Dispatchers.IO) {
        return@withContext _sessions.value.find { it.id == sessionId }
    }
    
    // New function to get all sessions
    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        return@withContext _sessions.value
    }
}
