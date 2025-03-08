package com.example.llama

enum class ContextStrategy {
    SLIDING_WINDOW,
    SUMMARY
}

data class ContextConfig(
    val strategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    val nContext: Int = 10,
    val summaryInterval: Int = 20
)
package com.example.llama

enum class ContextStrategy {
    SLIDING_WINDOW,
    SUMMARY
}

data class ContextConfig(
    val strategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    val maxTokens: Int = 2048,
    val useMemory: Boolean = false,
    val summarizeThreshold: Int = 10,
    val memoryRelevanceThreshold: Float = 0.7f
)
