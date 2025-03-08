package android.llama.cpp

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LLamaAndroid {
    private val tag: String? = this::class.simpleName

    private val threadLocalState: ThreadLocal<State> = ThreadLocal.withInitial { State.Idle }

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {
            Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

            // No-op if called more than once.
            System.loadLibrary("llama-android")

            // Set llama log handler to Android
            log_to_android()
            backend_init(false)

            Log.d(tag, system_info())

            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "Unhandled exception", exception)
            }
        }
    }.asCoroutineDispatcher()

    private val nlen: Int = 400 // Further reduced for faster initial response

    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun backend_free()
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(): Long
    private external fun free_sampler(sampler: Long)
    private external fun bench_model(
        context: Long,
        model: Long,
        batch: Long,
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String

    private external fun system_info(): String

    private external fun completion_init(
        context: Long,
        batch: Long,
        text: String,
        formatChat: Boolean,
        nLen: Int
    ): Int

    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?

    private external fun kv_cache_clear(context: Long)

    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    Log.d(tag, "bench(): $state")
                    bench_model(state.context, state.model, state.batch, pp, tg, pl, nr)
                }

                else -> throw IllegalStateException("No model loaded")
            }
        }
    }

    // Optimized model loading with thread configuration
    private external fun load_model_with_config(
        filename: String,
        threads: Int,
        contextSize: Int,
        batchSize: Int,
        gpuLayers: Int = 0,
        ropeScaling: Float = 1.0f
    ): Long

    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Idle -> {
                    // Get available processors for optimal threading
                    val availableProcessors = Runtime.getRuntime().availableProcessors()
                    val optimalThreads = maxOf(1, availableProcessors - 1) // Leave one core free

                    // Calculate optimal parameters based on device capabilities
                    val availableMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    
                    // Adjust context size based on available memory
                    val contextSize = when {
                        availableMemoryMB > 3000 -> 4096  // High memory devices
                        availableMemoryMB > 1500 -> 2048  // Medium memory devices
                        else -> 1024                      // Low memory devices
                    }
                    
                    // Adjust batch size based on available memory
                    val batchSize = when {
                        availableMemoryMB > 3000 -> 1024  // High memory devices
                        availableMemoryMB > 1500 -> 512   // Medium memory devices
                        else -> 256                       // Low memory devices
                    }
                    
                    Log.i(tag, "Device memory: ${availableMemoryMB}MB, using contextSize=$contextSize, batchSize=$batchSize")
                    
                    // Use optimized loading with configuration parameters
                    val model = try {
                        Log.i(tag, "Loading model with optimized config: threads=$optimalThreads, contextSize=$contextSize, batchSize=$batchSize")
                        load_model_with_config(
                            pathToModel,
                            threads = optimalThreads,
                            contextSize = contextSize,
                            batchSize = batchSize,
                            gpuLayers = 0,  // No GPU acceleration by default
                            ropeScaling = 1.0f
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Optimized loading failed, falling back to standard loading", e)
                        load_model(pathToModel)
                    }

                    if (model == 0L) throw IllegalStateException("load_model() failed")

                    val context = new_context(model)
                    if (context == 0L) throw IllegalStateException("new_context() failed")

                    val batch = new_batch(4096, 0, 1) // Increased batch size
                    if (batch == 0L) throw IllegalStateException("new_batch() failed")

                    val sampler = new_sampler()
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    Log.i(tag, "Loaded model $pathToModel with ${optimalThreads} threads")
                    threadLocalState.set(State.Loaded(model, context, batch, sampler))
                }
                else -> throw IllegalStateException("Model already loaded")
            }
        }
    }

    fun send(context: String, formatChat: Boolean = false): Flow<String> = flow {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {
                val ncur = IntVar(completion_init(state.context, state.batch, context, formatChat, nlen))
                Log.d(tag, "ncur: ${ncur.value.toString()}")
                Log.d(tag, "nlen: ${nlen.toString()}")

                // Emit each token as it's generated
                while (ncur.value <= nlen) {
                    val str = completion_loop(state.context, state.batch, state.sampler, nlen, ncur)
                    if (str == null) {
                        break
                    }
                    
                    // Emit each chunk immediately without accumulating
                    emit(str)
                }
            }
            else -> {}
        }
    }.flowOn(runLoop)

    /**
     * Unloads the model and frees resources.
     *
     * This is a no-op if there's no model loaded.
     */
    suspend fun unload() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    free_context(state.context)
                    free_model(state.model)
                    free_batch(state.batch)
                    free_sampler(state.sampler);

                    threadLocalState.set(State.Idle)
                }
                else -> {}
            }
        }
    }

    companion object {
        private class IntVar(value: Int) {
            @Volatile
            var value: Int = value
                private set

            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }

        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
        }

        // Enforce only one instance of Llm.
        private val _instance: LLamaAndroid = LLamaAndroid()

        fun instance(): LLamaAndroid = _instance
    }
}
