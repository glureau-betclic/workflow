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
package com.squareup.workflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.jetbrains.annotations.TestOnly
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract

/**
 * Tuple of rendering and snapshot used by [runWorkflow].
 */
data class RenderingAndSnapshot<out RenderingT>(
  val rendering: RenderingT,
  val snapshot: Snapshot
)

@UseExperimental(ExperimentalCoroutinesApi::class)
internal typealias Configurator <O, R> = CoroutineScope.(
  renderingsAndSnapshots: Flow<RenderingAndSnapshot<R>>,
  outputs: Flow<O>
) -> Unit

/**
 *  TODO write documentation
 */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalContracts::class)
suspend fun <InputT, OutputT : Any, RenderingT> runWorkflow(
  workflow: Workflow<InputT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialSnapshot: Snapshot? = null,
  beforeStart: CoroutineScope.(
    renderingsAndSnapshots: Flow<RenderingAndSnapshot<RenderingT>>,
    outputs: Flow<OutputT>
  ) -> Unit
): Nothing {
  contract { callsInPlace(beforeStart, AT_MOST_ONCE) }
  runWorkflowImpl(
      workflow.asStatefulWorkflow(),
      inputs,
      initialSnapshot = initialSnapshot,
      initialState = null,
      beforeStart = beforeStart
  )
}

/**
 *  TODO write documentation
 */
@TestOnly
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalContracts::class)
suspend fun <InputT, StateT, OutputT : Any, RenderingT> runWorkflowForTestFromState(
  workflow: StatefulWorkflow<InputT, StateT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialState: StateT,
  beforeStart: CoroutineScope.(
    renderingsAndSnapshots: Flow<RenderingAndSnapshot<RenderingT>>,
    outputs: Flow<OutputT>
  ) -> Unit
): Nothing {
  contract { callsInPlace(beforeStart, AT_MOST_ONCE) }
  runWorkflowImpl(
      workflow,
      inputs,
      initialState = initialState,
      initialSnapshot = null,
      beforeStart = beforeStart
  )
}

/**
 *  TODO write documentation
 */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
private suspend fun <InputT, StateT, OutputT : Any, RenderingT> runWorkflowImpl(
  workflow: StatefulWorkflow<InputT, StateT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialSnapshot: Snapshot?,
  initialState: StateT?,
  beforeStart: Configurator<OutputT, RenderingT>
): Nothing = coroutineScope {
  val renderingsAndSnapshots = ConflatedBroadcastChannel<RenderingAndSnapshot<RenderingT>>()
  val outputs = BroadcastChannel<OutputT>(capacity = 1)

  // Ensure we close the channels when we're done, so that they propagate errors.
  coroutineContext[Job]!!.invokeOnCompletion { cause ->
    // We need to unwrap the cancellation exception so that we *complete* the channels instead
    // of cancelling them if our coroutine was merely cancelled.
    val realCause = cause?.unwrapCancellationCause()
    renderingsAndSnapshots.close(realCause)
    outputs.close(realCause)
  }

  // Give the caller a chance to start collecting outputs.
  beforeStart(renderingsAndSnapshots.asFlow(), outputs.asFlow())

  // Run the workflow processing loop forever, or until it fails or is cancelled.
  runWorkflowLoop(
      workflow,
      inputs,
      initialSnapshot = initialSnapshot,
      initialState = initialState,
      onRendering = renderingsAndSnapshots::send,
      onOutput = outputs::send
  )
}
