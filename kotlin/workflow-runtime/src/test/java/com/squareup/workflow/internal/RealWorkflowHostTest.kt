package com.squareup.workflow.internal

import com.squareup.workflow.Configurator
import com.squareup.workflow.RealWorkflowHost
import com.squareup.workflow.RenderingAndSnapshot
import com.squareup.workflow.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@UseExperimental(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class
)
class RealWorkflowHostTest {

  private class ExpectedException : RuntimeException()

  @Test fun `exception from run doesn't cancel base context`() {
    val baseJob = Job()
    val host = RealWorkflowHost<Nothing, Unit>(Unconfined + baseJob) {
      throw ExpectedException()
    }
    val job = host.start()

    assertFalse(baseJob.isCompleted)
    assertTrue(job.isCancelled)
  }

  @Test fun `exception from run is propagated to start job`() {
    val host = RealWorkflowHost<Nothing, Unit>(Unconfined) {
      throw ExpectedException()
    }
    val job = host.start()

    assertTrue(job.isCancelled)
    assertTrue(job.getCancellationException().hasCause { it is ExpectedException })
  }

  @Suppress("ReplaceSingleLineLet")
  @Test fun `exceptions from run are propagated to flows`() {
    val host = RealWorkflowHost<Unit, Unit>(Unconfined) {
      throw ExpectedException()
    }
    host.start()

    runBlocking {
      runCatching { host.renderingsAndSnapshots.first() }.apply {
        assertTrue(exceptionOrNull().hasCause {
          it is ExpectedException
        })
      }

      runCatching { host.outputs.first() }.apply {
        assertTrue(exceptionOrNull().hasCause {
          it is ExpectedException
        })
      }
    }
  }

  @Test fun `exceptions from renderings collector cancels host`() {
    val host = RealWorkflowHost<Unit, Unit>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          flowOf(RenderingAndSnapshot(Unit, Snapshot.EMPTY)),
          emptyFlow()
      )
      suspendCancellableCoroutine<Nothing> { }
    }

    val job = GlobalScope.launch(Unconfined + Job()) {
      host.renderingsAndSnapshots.collect {
        throw ExpectedException()
      }
    }
    host.start()

    assertTrue(job.getCancellationException().hasCause { it is ExpectedException })
  }

  @Test fun `exceptions from outputs collector cancels host`() {
    val host = RealWorkflowHost<Unit, Unit>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          emptyFlow(),
          flowOf(Unit)
      )
      suspendCancellableCoroutine<Nothing> { }
    }

    val job = GlobalScope.launch(Unconfined) {
      host.outputs.collect {
        throw ExpectedException()
      }
    }
    host.start()

    assertTrue(job.getCancellationException().hasCause { it is ExpectedException })
  }

  @Test fun `cancelling start Job doesn't cancel base context`() {
    val baseJob = Job()
    val host = RealWorkflowHost<Unit, Unit>(Unconfined + baseJob, ::runForever)
    val job = host.start()

    job.cancel()
    assertFalse(baseJob.isCompleted)
  }

  @Test fun `cancelling base context cancels host`() {
    val baseJob = Job()
    val host = RealWorkflowHost<Unit, Unit>(Unconfined + baseJob, ::runForever)
    val job = host.start()

    baseJob.cancel()
    assertTrue(job.isCancelled)
  }

  @Test fun `cancelling start Job completes flows`() {
    val baseJob = Job()
    val host = RealWorkflowHost<Unit, Unit>(Unconfined + baseJob, ::runForever)
    val renderingsJob = GlobalScope.launch(Unconfined) {
      host.renderingsAndSnapshots.collect()
    }
    val outputsJob = GlobalScope.launch(Unconfined) {
      host.outputs.collect()
    }
    val job = host.start()

    job.cancel()

    assertTrue(renderingsJob.isCompleted)
    assertFalse(renderingsJob.isCancelled)
    assertTrue(outputsJob.isCompleted)
    assertFalse(outputsJob.isCancelled)
  }

  @Test fun `flows complete immediately when base context is already cancelled on start`() {
    val baseJob = Job().apply { cancel() }
    val host = RealWorkflowHost<Unit, Unit>(Unconfined + baseJob, ::runForever)
    val renderingsJob = GlobalScope.launch(Unconfined) {
      host.renderingsAndSnapshots.collect()
    }
    val outputsJob = GlobalScope.launch(Unconfined) {
      host.outputs.collect()
    }
    host.start()

    assertTrue(renderingsJob.isCompleted)
    assertFalse(renderingsJob.isCancelled)
    assertTrue(outputsJob.isCompleted)
    assertFalse(outputsJob.isCancelled)
  }

  @Test fun `renderings flow replays to new collectors`() {
    val host = RealWorkflowHost<Nothing, String>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          flowOf(RenderingAndSnapshot("foo", Snapshot.EMPTY)),
          emptyFlow()
      )
      suspendCancellableCoroutine<Nothing> { }
    }
    host.start()

    val firstRendering = runBlocking { host.renderingsAndSnapshots.first() }
    assertEquals("foo", firstRendering.rendering)
  }

  @Test fun `renderings flow is multicasted`() {
    val host = RealWorkflowHost<Nothing, String>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          flowOf(
              RenderingAndSnapshot("one", Snapshot.EMPTY),
              RenderingAndSnapshot("two", Snapshot.EMPTY)
          ),
          emptyFlow()
      )
      suspendCancellableCoroutine<Nothing> { }
    }
    val renderings1 = GlobalScope.async(Unconfined) {
      host.renderingsAndSnapshots.map { it.rendering }
          .toList()
    }
    val renderings2 = GlobalScope.async(Unconfined) {
      host.renderingsAndSnapshots.map { it.rendering }
          .toList()
    }
    host.start()

    assertEquals(listOf("one", "two"), runBlocking { renderings1.await() })
    assertEquals(listOf("one", "two"), runBlocking { renderings2.await() })
  }

  @Test fun `outputs flow is multicasted`() {
    val host = RealWorkflowHost<String, Unit>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          emptyFlow(),
          flowOf("one", "two")
      )
      suspendCancellableCoroutine<Nothing> { }
    }
    val outputs1 = GlobalScope.async(Unconfined) {
      host.outputs.toList()
    }
    val outputs2 = GlobalScope.async(Unconfined) {
      host.outputs.toList()
    }
    host.start()

    assertEquals(listOf("one", "two"), runBlocking { outputs1.await() })
    assertEquals(listOf("one", "two"), runBlocking { outputs2.await() })
  }

  @Test fun `start is idempotent`() {
    var starts = 0
    val host = RealWorkflowHost<Nothing, Unit>(Unconfined) {
      starts++
      suspendCancellableCoroutine<Nothing> { }
    }

    assertEquals(0, starts)
    val job1 = host.start()
    val job2 = host.start()
    assertEquals(1, starts)
    assertSame(job1, job2)

    job2.cancel()

    val job3 = host.start()
    assertEquals(1, starts)
    assertEquals(job2, job3)
  }

  @Test fun `renderings flow has no backpressure`() {
    val host = RealWorkflowHost<Nothing, String>(Unconfined) { configurator ->
      configurator(
          CoroutineScope(Unconfined),
          flowOf(
              RenderingAndSnapshot("one", Snapshot.EMPTY),
              RenderingAndSnapshot("two", Snapshot.EMPTY),
              RenderingAndSnapshot("three", Snapshot.EMPTY)
          ),
          emptyFlow()
      )
      suspendCancellableCoroutine<Nothing> { }
    }
    host.start()

    runBlocking {
      val renderings = host.renderingsAndSnapshots.map { it.rendering }
          .toList()
      assertEquals(listOf("one", "three"), renderings)
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private suspend fun <O, R> runForever(
    configurator: Configurator<O, R>
  ): Nothing {
    suspendCancellableCoroutine<Nothing> { }
  }

  private inline fun Throwable?.hasCause(predicate: (Throwable) -> Boolean): Boolean =
    causeChain.any(predicate)

  private val Throwable?.causeChain
    get() = this?.let { e ->
      generateSequence(e) { it.cause }
    } ?: emptySequence()
}
