package com.example.demo.infrastructure.config

import io.micrometer.tracing.CurrentTraceContext
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Propagates the active Micrometer [TraceContext] across Kotlin coroutines using [ThreadContextElement].
 * Whenever a coroutine suspends and resumes on a different worker thread, this element ensures
 * the [TraceContext] is bound to the new thread's [CurrentTraceContext] and cleaned up on suspension.
 */
class TracerContextElement(
    private val tracer: Tracer,
    private val traceContext: TraceContext? = tracer.currentTraceContext().context(),
) : ThreadContextElement<CurrentTraceContext.Scope?> {
    companion object Key : CoroutineContext.Key<TracerContextElement>

    override val key: CoroutineContext.Key<*>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): CurrentTraceContext.Scope? = traceContext?.let { tracer.currentTraceContext().maybeScope(it) }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: CurrentTraceContext.Scope?,
    ) {
        oldState?.close()
    }
}

/**
 * Extension function converting Micrometer [Tracer]'s current trace context into a coroutine [ThreadContextElement].
 */
fun Tracer.asContextElement(): ThreadContextElement<CurrentTraceContext.Scope?> = TracerContextElement(this, this.currentTraceContext().context())
