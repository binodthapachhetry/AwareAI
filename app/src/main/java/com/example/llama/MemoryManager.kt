package com.example.llama

class MemoryManager {
    // Simple in-memory storage for now
    private val memories = mutableMapOf<String, Pair<String, String>>() // key -> (content, sessionId)

    suspend fun setMemory(key: String, content: String, sessionId: String) {
        memories[key] = Pair(content, sessionId)
    }

    suspend fun getMemory(key: String): String? {
        return memories[key]?.first
    }

    suspend fun getRelatedMemories(query: String, sessionId: String, limit: Int = 5): List<String> {
        // Simple implementation that returns memories from the same session
        // In a real implementation, this would use embeddings or other semantic search
        return memories.entries
            .filter { it.value.second == sessionId }
            .map { it.value.first }
            .take(limit)
    }

    suspend fun deleteMemory(key: String) {
        memories.remove(key)
    }

    suspend fun clearSessionMemories(sessionId: String) {
        memories.entries.removeIf { it.value.second == sessionId }
    }
}
