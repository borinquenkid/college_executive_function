package com.borinquenterrier.cef

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class IngestWorkerPool(
    private val workerCount: Int = 4,
    private val channelCapacity: Int = 100,
    scope: CoroutineScope
) {
    private val channel = Channel<suspend () -> Unit>(channelCapacity)

    private val workerJobs: List<Job> = (1..workerCount).map {
        scope.launch {
            for (task in channel) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // error already captured in task's CompletableDeferred
                }
            }
        }
    }

    suspend fun <T> submit(block: suspend () -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        channel.send {
            try {
                deferred.complete(block())
            } catch (e: CancellationException) {
                deferred.completeExceptionally(e)
                throw e
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    fun shutdown() {
        channel.close()
    }

    suspend fun awaitShutdown() {
        workerJobs.forEach { it.join() }
    }
}
