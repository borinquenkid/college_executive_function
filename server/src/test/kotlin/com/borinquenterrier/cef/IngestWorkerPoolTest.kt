package com.borinquenterrier.cef

import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class IngestWorkerPoolTest {

    // Each test gets its own pool; tests are responsible for shutting it down.
    private fun pool(workers: Int = 2, capacity: Int = 10) =
        IngestWorkerPool(
            workerCount = workers,
            channelCapacity = capacity,
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )

    // ── basic submit / result ─────────────────────────────────────────────────

    @Test
    fun `submit executes the task and resolves the Deferred with its result`() = runBlocking {
        val p = pool()
        val result = p.submit { 42 }.await()
        assertEquals(42, result)
        p.shutdown()
    }

    @Test
    fun `submit returns a Deferred before the task has completed`() = runBlocking {
        val p = pool()
        val blocker = CompletableDeferred<Unit>()

        val deferred = p.submit { blocker.await(); "done" }
        assertFalse(deferred.isCompleted)

        blocker.complete(Unit)
        assertEquals("done", deferred.await())
        p.shutdown()
    }

    @Test
    fun `multiple independent tasks all complete with their own results`() = runBlocking {
        val p = pool(workers = 4)
        val results = (1..10).map { n -> p.submit { n * n } }.map { it.await() }
        assertEquals((1..10).map { it * it }, results)
        p.shutdown()
    }

    // ── error isolation ───────────────────────────────────────────────────────

    @Test
    fun `a failing task completes its Deferred with the exception`() = runBlocking {
        val p = pool()
        val deferred = p.submit { error("boom") }
        val ex = assertFailsWith<IllegalStateException> { deferred.await() }
        assertEquals("boom", ex.message)
        p.shutdown()
    }

    @Test
    fun `a failing task does not prevent subsequent tasks from completing`() = runBlocking {
        val p = pool(workers = 1, capacity = 20)

        val failing = p.submit<String> { error("boom") }
        val succeeding = p.submit { "ok" }

        assertFailsWith<IllegalStateException> { failing.await() }
        assertEquals("ok", succeeding.await())
        p.shutdown()
    }

    @Test
    fun `multiple consecutive failures do not kill the pool`() = runBlocking {
        val p = pool(workers = 2, capacity = 20)

        val failures = (1..5).map { p.submit<Unit> { error("fail-$it") } }
        failures.forEach { assertFailsWith<Exception> { it.await() } }

        val result = p.submit { "still alive" }.await()
        assertEquals("still alive", result)
        p.shutdown()
    }

    // ── concurrency ───────────────────────────────────────────────────────────

    @Test
    fun `executes up to workerCount tasks concurrently`() = runBlocking {
        val workers = 3
        val p = pool(workers = workers, capacity = 10)

        val startLatch = CountDownLatch(workers)
        val blocker = CountDownLatch(1)

        repeat(workers) {
            p.submit {
                startLatch.countDown()
                blocker.await(2, TimeUnit.SECONDS)
            }
        }

        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "all $workers tasks should start concurrently")

        blocker.countDown()
        p.shutdown()
        p.awaitShutdown()
    }

    @Test
    fun `tasks beyond workerCount queue and execute when a worker becomes free`() = runBlocking {
        val p = pool(workers = 1, capacity = 10)
        val blocker = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        p.submit { blocker.await() }  // keeps the single worker busy
        val queued = p.submit { secondStarted.complete(Unit); "queued" }

        assertFalse(secondStarted.isCompleted, "second task should not start while worker is busy")

        blocker.complete(Unit)
        assertEquals("queued", queued.await())
        p.shutdown()
    }

    @Test
    fun `maximum observed concurrency never exceeds workerCount`() = runBlocking {
        val workers = 2
        val p = pool(workers = workers, capacity = 20)

        val active = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val blocker = CountDownLatch(1)

        val tasks = (1..6).map {
            p.submit {
                val n = active.incrementAndGet()
                maxSeen.updateAndGet { max -> maxOf(max, n) }
                blocker.await(2, TimeUnit.SECONDS)
                active.decrementAndGet()
            }
        }

        // Give all submitted tasks a chance to run
        Thread.sleep(100)
        blocker.countDown()
        tasks.forEach { it.await() }

        assertTrue(maxSeen.get() <= workers, "concurrency ${maxSeen.get()} exceeded worker count $workers")
        p.shutdown()
    }

    // ── backpressure ──────────────────────────────────────────────────────────

    @Test
    fun `submit suspends when the channel is full`() = runBlocking {
        // workerCount=1, capacity=1: worker takes 1 + channel buffers 1 = 2 slots before backpressure
        val p = pool(workers = 1, capacity = 1)
        val blocker = CompletableDeferred<Unit>()

        p.submit { blocker.await() }  // occupies worker
        p.submit { blocker.await() }  // fills the single channel slot

        val submitCompleted = AtomicBoolean(false)
        val submitJob = launch(Dispatchers.Default) {
            p.submit { "3rd" }        // should suspend — no room
            submitCompleted.set(true)
        }

        delay(150)
        assertFalse(submitCompleted.get(), "submit should be suspended when channel is full")

        blocker.complete(Unit)
        submitJob.join()
        assertTrue(submitCompleted.get())

        p.shutdown()
        p.awaitShutdown()
    }

    // ── graceful shutdown ─────────────────────────────────────────────────────

    @Test
    fun `awaitShutdown returns only after all in-flight tasks finish`() = runBlocking {
        val p = pool(workers = 2, capacity = 10)
        val finished = AtomicInteger(0)
        val blocker = CountDownLatch(1)

        repeat(4) { p.submit { blocker.await(2, TimeUnit.SECONDS); finished.incrementAndGet() } }

        p.shutdown()
        blocker.countDown()
        p.awaitShutdown()

        assertEquals(4, finished.get())
    }

    @Test
    fun `submit after shutdown throws ClosedSendChannelException`() = runBlocking {
        val p = pool()
        p.shutdown()

        assertFailsWith<kotlinx.coroutines.channels.ClosedSendChannelException> {
            p.submit { "too late" }
        }
    }
}
