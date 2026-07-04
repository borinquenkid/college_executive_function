package com.borinquenterrier.cef

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * Observability hook for [CriticActorAIService]'s actor/critique passes. Opt-in via the
 * coroutine context (same pattern as [HttpOtelTracer]'s SpanCtx) so callers that don't care —
 * the vast majority — pay nothing and CriticActorAIService stays decoupled from any specific
 * listener (e.g. the web server's SSE stream).
 */
enum class CriticPhase { ACTOR_START, ACTOR_DONE, CRITIQUE_START, CRITIQUE_DONE }

fun interface CriticProgressListener {
    suspend fun onPhase(phase: CriticPhase)
}

class CriticProgressContext(val listener: CriticProgressListener) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CriticProgressContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

suspend fun reportCriticProgress(phase: CriticPhase) {
    currentCoroutineContext()[CriticProgressContext.Key]?.listener?.onPhase(phase)
}
