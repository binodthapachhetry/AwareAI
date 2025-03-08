package com.example.llama

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Float = 1.0f,
    val metadata: Map<String, String> = emptyMap()
)

class MemoryManager {
    // Enhanced in-memory storage with full Memory objects
    private val memories = mutableMapOf<String, Memory>() // id -> Memory

    suspend fun setMemory(key: String, content: String, sessionId: String, importance: Float = 1.0f) = withContext(Dispatchers.IO) {
        val memory = Memory(
            key = key,
            content = content,
            sessionId = sessionId,
            importance = importance
        )
        memories[memory.id] = memory
    }

    suspend fun getMemory(key: String): String? = withContext(Dispatchers.IO) {
        return@withContext memories.values.firstOrNull { it.key == key }?.content
    }

    suspend fun getRelatedMemories(query: String, sessionId: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        // Enhanced implementation with keyword matching
        val keywords = query.lowercase().split(" ")
            .filter { it.length > 3 } // Only consider words longer than 3 chars
            
        return@withContext memories.values
            .filter { memory -> 
                memory.sessionId == sessionId && 
                (keywords.isEmpty() || keywords.any { keyword ->
                    memory.content.lowercase().contains(keyword)
                })
            }
            .sortedByDescending { memory ->
                // Score based on keyword matches and importance
                val keywordMatches = keywords.count { keyword ->
                    memory.content.lowercase().contains(keyword)
                }
                keywordMatches * memory.importance
            }
            .take(limit)
            .map { it.content }
    }

    suspend fun deleteMemory(key: String) = withContext(Dispatchers.IO) {
        val memoryId = memories.values.firstOrNull { it.key == key }?.id
        if (memoryId != null) {
            memories.remove(memoryId)
        }
    }

    suspend fun clearSessionMemories(sessionId: String) = withContext(Dispatchers.IO) {
        memories.entries.removeIf { it.value.sessionId == sessionId }
    }
    
    // New function to get all memories for a session
    suspend fun getSessionMemories(sessionId: String): List<Memory> = withContext(Dispatchers.IO) {
        return@withContext memories.values.filter { it.sessionId == sessionId }
    }
    
    // New function to search memories with more advanced criteria
    suspend fun searchMemories(
        query: String? = null,
        sessionId: String? = null,
        keyPrefix: String? = null,
        limit: Int = 10,
        minImportance: Float = 0.0f
    ): List<Memory> = withContext(Dispatchers.IO) {
        return@withContext memories.values
            .filter { memory ->
                (sessionId == null || memory.sessionId == sessionId) &&
                (keyPrefix == null || memory.key.startsWith(keyPrefix)) &&
                (memory.importance >= minImportance) &&
                (query == null || memory.content.lowercase().contains(query.lowercase()))
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
}
