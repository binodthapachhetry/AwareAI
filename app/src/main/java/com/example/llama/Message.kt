package com.example.llama

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MessageMetadata = MessageMetadata()
) {
    enum class SenderType {
        USER, AI, SYSTEM
    }
    
    data class MessageMetadata(
        val tokens: Int = 0,
        val contextReferences: List<String> = emptyList(),
        val modelParams: ModelParams? = null,
        val performanceMetrics: PerformanceMetrics? = null
    )
    
    data class ModelParams(
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val maxTokens: Int = 2048
    )
    
    data class PerformanceMetrics(
        val promptTokenCount: Int = 0,
        val responseTokenCount: Int = 0,
        val timeToFirstToken: Long = 0,  // in milliseconds
        val averageTimePerToken: Float = 0f,  // in milliseconds
        val totalGenerationTime: Long = 0  // in milliseconds
    )
}
