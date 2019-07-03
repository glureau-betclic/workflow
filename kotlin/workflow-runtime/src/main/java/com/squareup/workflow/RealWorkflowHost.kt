@file:Suppress("DEPRECATION")

package com.squareup.workflow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.ATOMIC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.switchMap
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Implements the [WorkflowHost] interface by [launching][CoroutineScope.launch] a new coroutine
 * on [start].
 *
 * @param context The context used to launch the runtime coroutine.
 * @param run Gets invoked from a new coroutine started with `context`, and passed functions
 * that will cause [renderingsAndSnapshots] and [outputs] to emit, respectively. If any exception
 * is thrown, those Flows will both rethrow the exception.
 */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class RealWorkflowHost<O : Any, R>(
  context: CoroutineContext,
  private val run: suspend (Configurator<O, R>) -> Nothing
) : WorkflowHost<O, R> {

  /**
   * This must be a [SupervisorJob] because if any of the workflow coroutines fail, we will handle
   * the error explicitly by using it to close the [renderingsAndSnapshots] and [outputs] streams.
   */
  private val scope = CoroutineScope(context + SupervisorJob(parent = context[Job]))

  // todo rename
  private val stuff = CompletableDeferred<Pair<Flow<RenderingAndSnapshot<R>>, Flow<O>>>()
  private val stuffFlow = stuff::await.asFlow()
      .catch { e ->
        e.unwrapCancellationCause()
            ?.let { throw it }
      }

  private var job: Job? = null

  @UseExperimental(InternalCoroutinesApi::class)
  override val renderingsAndSnapshots: Flow<RenderingAndSnapshot<R>> =
    stuffFlow.switchMap { it.first }
        .conflate()

  override val outputs: Flow<O> = stuffFlow.switchMap { it.second }

  override fun start(): Job {
    return job ?: scope
        // We need to launch atomically so that, if scope is already cancelled, the coroutine is
        // still allowed to handle the error by closing the channels.
        .launch(start = ATOMIC) {
          try {
            @Suppress("IMPLICIT_NOTHING_AS_TYPE_PARAMETER")
            run { renderings, outputs ->
              stuff.complete(Pair(renderings, outputs))
            }
          } catch (e: Throwable) {
            stuff.completeExceptionally(e)
            throw e
          }
        }
        .also { job = it }
  }
}

internal tailrec fun Throwable.unwrapCancellationCause(): Throwable? {
  if (this !is CancellationException) return this
  return cause?.unwrapCancellationCause()
}
