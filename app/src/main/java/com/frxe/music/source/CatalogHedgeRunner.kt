package com.frxe.music.source

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CatalogHedgeTask<T>(
    val startDelayMs: Long,
    val timeoutMs: Long,
    val block: suspend () -> List<T>
)

suspend fun <T> hedgedFirstNonEmpty(
    tasks: List<CatalogHedgeTask<T>>
): List<T> {
    if (tasks.isEmpty()) return emptyList()

    val result = CompletableDeferred<List<T>>()
    val remaining = AtomicInteger(tasks.size)
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    tasks.forEach { task ->
        scope.launch {
            if (task.startDelayMs > 0L) {
                delay(task.startDelayMs)
            }

            val value: List<T> = withTimeoutOrNull(task.timeoutMs) {
                try {
                    task.block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    emptyList()
                }
            }.orEmpty()

            if (value.isNotEmpty()) {
                result.complete(value)
            } else if (remaining.decrementAndGet() == 0) {
                result.complete(emptyList())
            }
        }
    }

    return try {
        result.await()
    } finally {
        scope.cancel()
    }
}
