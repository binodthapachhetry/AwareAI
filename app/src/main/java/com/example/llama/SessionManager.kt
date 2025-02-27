package com.example.llama

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SessionManager {
    data class Session(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String = "New Conversation",
        val messages: List<Message> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    private val _sessions = MutableStateFlow<List<Session>>(listOf(Session()))
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>(_sessions.value.first().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    fun requireActiveSession(): Session {
        return _sessions.value.find { it.id == _activeSessionId.value }
            ?: throw IllegalStateException("No active session found")
    }

    fun createNewSession(): String {
        val newSession = Session()
        _sessions.update { it + newSession }
        _activeSessionId.value = newSession.id
        return newSession.id
    }

    fun setActiveSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        } else {
            throw IllegalArgumentException("Session with ID $sessionId not found")
        }
    }

    fun addMessage(message: Message) {
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

    fun deleteSession(sessionId: String) {
        _sessions.update { it.filter { session -> session.id != sessionId } }
        if (_activeSessionId.value == sessionId && _sessions.value.isNotEmpty()) {
            _activeSessionId.value = _sessions.value.first().id
        }
    }

    fun renameSession(sessionId: String, newName: String) {
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

    fun clearSessionMessages(sessionId: String) {
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
}
