/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow.testing

import com.squareup.workflow.RenderContext
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.Workflow
import com.squareup.workflow.asWorker
import com.squareup.workflow.onWorkerOutput
import com.squareup.workflow.stateless
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WorkflowTesterTest {

  private class ExpectedException : RuntimeException()

  @Test fun `propagates exception when block throws`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    assertFailsWith<ExpectedException> {
      workflow.testFromStart {
        throw ExpectedException()
      }
    }
  }

  @Test fun `propagates exception when workflow throws from render`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      throw ExpectedException()
    }

    workflow.testFromStart {
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `propagates exception when Job is cancelled in test block`() {
    val job = Job()
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    workflow.testFromStart(context = job) {
      job.cancel(CancellationException(null, ExpectedException()))
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `propagates close when Job is cancelled before starting`() {
    val job = Job().apply { cancel() }
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    workflow.testFromStart(context = job) {
      assertTrue(awaitFailure() is ClosedReceiveChannelException)
    }
  }

  @Test fun `propagates cancellation when Job failes before starting`() {
    val job = Job().apply { cancel(CancellationException(null, ExpectedException())) }
    val workflow = Workflow.stateless<Unit, Unit, Unit> { }

    workflow.testFromStart(context = job) {
      assertTrue(awaitFailure() is ExpectedException)
    }
  }

  @Test fun `propagates exception when workflow throws from initial state`() {
    val workflow = object : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {
      override fun initialState(
        input: Unit,
        snapshot: Snapshot?
      ) {
        assertNull(snapshot)
        throw ExpectedException()
      }

      override fun render(
        input: Unit,
        state: Unit,
        context: RenderContext<Unit, Nothing>
      ) {
        fail()
      }

      override fun snapshotState(state: Unit): Snapshot = fail()
    }

    workflow.testFromStart {
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `propagates exception when workflow throws from snapshot state`() {
    val workflow = object : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {
      override fun initialState(
        input: Unit,
        snapshot: Snapshot?
      ) {
        assertNull(snapshot)
        // Noop
      }

      override fun render(
        input: Unit,
        state: Unit,
        context: RenderContext<Unit, Nothing>
      ) {
        // Noop
      }

      override fun snapshotState(state: Unit): Snapshot = throw ExpectedException()
    }

    workflow.testFromStart {
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `propagates exception when workflow throws from restore state`() {
    val workflow = object : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {
      override fun initialState(
        input: Unit,
        snapshot: Snapshot?
      ) {
        if (snapshot != null) {
          throw ExpectedException()
        }
      }

      override fun render(
        input: Unit,
        state: Unit,
        context: RenderContext<Unit, Nothing>
      ) {
      }

      override fun snapshotState(state: Unit): Snapshot = Snapshot.EMPTY
    }

    // Get a valid snapshot (can't use Snapshot.EMPTY).
    val snapshot = workflow.testFromStart {
      awaitNextSnapshot()
    }

    workflow.testFromStart(snapshot) {
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `propagates exception when worker throws`() {
    val deferred = CompletableDeferred<Unit>()
    deferred.completeExceptionally(ExpectedException())
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      onWorkerOutput(deferred.asWorker()) { fail("Shouldn't get here.") }
    }

    workflow.testFromStart {
      awaitFailure()
          .let { error ->
            val causeChain = generateSequence(error) { it.cause }
            assertEquals(1, causeChain.count { it is ExpectedException })
          }
    }
  }

  @Test fun `does nothing when no outputs observed`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {}

    workflow.testFromStart {
      // The workflow should never start.
    }
  }

  @Test fun `workflow gets inputs from sendInput`() {
    val workflow = Workflow.stateless<String, Nothing, String> { input -> input }

    workflow.testFromStart("one") {
      assertEquals("one", awaitNextRendering())
      sendInput("two")
      assertEquals("two", awaitNextRendering())
    }
  }

  @Test fun `sendInput duplicate values all trigger render passes`() {
    var renders = 0
    val input = "input"
    val workflow = Workflow.stateless<String, Nothing, Unit> { renders++ }

    workflow.testFromStart(input) {
      assertEquals(1, renders)

      sendInput(input)
      assertEquals(2, renders)

      sendInput(input)
      assertEquals(3, renders)
    }
  }
}
