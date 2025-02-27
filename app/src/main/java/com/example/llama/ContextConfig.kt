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
