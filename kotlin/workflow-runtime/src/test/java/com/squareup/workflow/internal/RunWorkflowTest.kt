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
package com.squareup.workflow.internal

import org.junit.Test

class RunWorkflowTest {

  @Test fun `renderings flow replays to new collectors`() {
//    val host = RealWorkflowHost<Nothing, String>(Unconfined) { onRendering, _ ->
//      onRendering(RenderingAndSnapshot("foo", Snapshot.EMPTY))
//      suspendCancellableCoroutine<Nothing> { }
//    }
//    host.start()
//
//    val firstRendering = runBlocking { host.renderingsAndSnapshots.first() }
//    assertEquals("foo", firstRendering.rendering)

    TODO()
  }

  // TODO move to new tests
//  @Test fun `outputs flow does not replay to new collectors`() {
//    val trigger = CompletableDeferred<Unit>()
//    val host = RealWorkflowHost<String, Unit>(Unconfined) { _, onOutput ->
//      onOutput("one")
//      trigger.await()
//      onOutput("two")
//    }
//    host.start()
//
//    val outputs = GlobalScope.async(Unconfined) { host.outputs.toList() }
//    trigger.complete(Unit)
//    assertEquals(listOf("two"), runBlocking { outputs.await() })
//  }

  // TODO move to new tests
//  @Test fun `renderings flow is multicasted`() {
//    val host = RealWorkflowHost<Nothing, String>(Unconfined) { onRendering, _ ->
//      onRendering(RenderingAndSnapshot("one", Snapshot.EMPTY))
//      onRendering(RenderingAndSnapshot("two", Snapshot.EMPTY))
//    }
//    val renderings1 = GlobalScope.async(Unconfined) {
//      host.renderingsAndSnapshots.map { it.rendering }
//          .toList()
//    }
//    val renderings2 = GlobalScope.async(Unconfined) {
//      host.renderingsAndSnapshots.map { it.rendering }
//          .toList()
//    }
//    host.start()
//
//    assertEquals(listOf("one", "two"), runBlocking { renderings1.await() })
//    assertEquals(listOf("one", "two"), runBlocking { renderings2.await() })
//  }

  // TODO move to new tests
//  @Test fun `outputs flow is multicasted`() {
//    val host = RealWorkflowHost<String, Unit>(Unconfined) { _, onOutput ->
//      onOutput("one")
//      onOutput("two")
//    }
//    val outputs1 = GlobalScope.async(Unconfined) {
//      host.outputs.toList()
//    }
//    val outputs2 = GlobalScope.async(Unconfined) {
//      host.outputs.toList()
//    }
//    host.start()
//
//    assertEquals(listOf("one", "two"), runBlocking { outputs1.await() })
//    assertEquals(listOf("one", "two"), runBlocking { outputs2.await() })
//  }

//  @Test fun `renderings flow has no backpressure`() {
//    val host = RealWorkflowHost<Nothing, String>(Unconfined) { onRendering, _ ->
//      onRendering(RenderingAndSnapshot("one", Snapshot.EMPTY))
//      onRendering(RenderingAndSnapshot("two", Snapshot.EMPTY))
//      suspendCancellableCoroutine<Nothing> { }
//    }
//    host.start()
//
//    val firstRendering = runBlocking { host.renderingsAndSnapshots.first() }
//    assertEquals("two", firstRendering.rendering)
//  }
//
//  @Test fun `outputs flow has no backpressure when not subscribed`() {
//    val emittedOutputs = Channel<String>(UNLIMITED)
//    val host = RealWorkflowHost<String, Unit>(Unconfined) { _, onOutput ->
//      onOutput("one")
//      emittedOutputs.send("one")
//      onOutput("two")
//      emittedOutputs.send("two")
//      emittedOutputs.close()
//    }
//    host.start()
//
//    assertEquals("one", emittedOutputs.poll())
//    assertEquals("two", emittedOutputs.poll())
//  }
//
//  @Test fun `outputs flow honors backpressure`() {
//    val emittedOutputs = Channel<Int>(UNLIMITED)
//    val host = RealWorkflowHost<Int, Unit>(Unconfined) { _, onOutput ->
//      for (i in 0 until 10) {
//        onOutput(i)
//        emittedOutputs.send(i)
//      }
//      emittedOutputs.close()
//    }
//    val outputs = GlobalScope.produce(Unconfined, capacity = 0) {
//      host.outputs.collect {
//        send(it)
//      }
//    }
//    host.start()
//
//    // There is effectively a two-element buffer: the BroadcastChannel has a buffer of one, and
//    // the produce coroutine above acts as another buffer.
//    assertEquals(0, emittedOutputs.poll())
//    assertEquals(1, emittedOutputs.poll())
//    assertNull(emittedOutputs.poll())
//
//    // Reading one item out of the actual outputs Flow should allow another one to get buffered.
//    assertEquals(0, outputs.poll())
//    assertEquals(2, emittedOutputs.poll())
//    assertNull(emittedOutputs.poll())
//
//    assertEquals(1, outputs.poll())
//    assertEquals(3, emittedOutputs.poll())
//    assertNull(emittedOutputs.poll())
//    assertEquals(2, outputs.poll())
//  }

  @Test fun `exception thrown from configurator cancels flows`() {
    TODO()
  }

  @Test fun `flows complete immediately when base context is already cancelled on start`() {
    TODO()
  }

  @Test fun `cancelling base context cancels host`() {
    TODO()
  }
}
