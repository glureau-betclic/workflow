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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.annotations.TestOnly
import kotlin.experimental.ExperimentalTypeInference

/**
 * Don't use this typealias for the public API, better to just use the function directly so it's
 * more obvious how to use it.
 */
@UseExperimental(ExperimentalCoroutinesApi::class)
internal typealias Configurator <O, R, T> = WorkflowScope<T>.(
  renderingsAndSnapshots: Flow<RenderingAndSnapshot<R>>,
  outputs: Flow<O>
) -> Unit

/**
 *  TODO write documentation
 */
@UseExperimental(
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
    ExperimentalTypeInference::class
)
suspend fun <InputT, OutputT : Any, RenderingT, ResultT> runWorkflow(
  workflow: Workflow<InputT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialSnapshot: Snapshot? = null,
  @BuilderInference beforeStart: WorkflowScope<ResultT>.(
    renderingsAndSnapshots: Flow<RenderingAndSnapshot<RenderingT>>,
    outputs: Flow<OutputT>
  ) -> Unit
): ResultT {
  return runWorkflowImpl(
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
@UseExperimental(
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
    ExperimentalTypeInference::class
)
suspend fun <InputT, StateT, OutputT : Any, RenderingT, ResultT> runWorkflowForTestFromState(
  workflow: StatefulWorkflow<InputT, StateT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialState: StateT,
  @BuilderInference beforeStart: WorkflowScope<ResultT>.(
    renderingsAndSnapshots: Flow<RenderingAndSnapshot<RenderingT>>,
    outputs: Flow<OutputT>
  ) -> Unit
): ResultT {
  return runWorkflowImpl(
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
private suspend fun <InputT, StateT, OutputT : Any, RenderingT, ResultT> runWorkflowImpl(
  workflow: StatefulWorkflow<InputT, StateT, OutputT, RenderingT>,
  inputs: Flow<InputT>,
  initialSnapshot: Snapshot?,
  initialState: StateT?,
  beforeStart: Configurator<OutputT, RenderingT, ResultT>
): ResultT = coroutineScope {
  val renderingsAndSnapshots = ConflatedBroadcastChannel<RenderingAndSnapshot<RenderingT>>()
  val outputs = BroadcastChannel<OutputT>(capacity = 1)
  val resultDeferred = CompletableDeferred<ResultT>(parent = coroutineContext[Job])

  // Ensure we close the channels when we're done, so that they propagate errors.
  coroutineContext[Job]!!.invokeOnCompletion { cause ->
    // We need to unwrap the cancellation exception so that we *complete* the channels instead
    // of cancelling them if our coroutine was merely cancelled.
    val realCause = cause?.unwrapCancellationCause()
    renderingsAndSnapshots.close(realCause)
    outputs.close(realCause)
  }

  // Launch a new coroutine to actually run the runtime. This is the coroutine that will get
  // cancelled when beforeStart calls WorkflowScope.finishWorkflow.
  launch {
    val workflowJob = coroutineContext[Job]!!
    @Suppress("ThrowableNotThrown")
    val workflowScope = WorkflowScope<ResultT>(this) { result ->
      workflowJob.cancel(CancellationException("Workflow was finished normally"))
      return@WorkflowScope resultDeferred.complete(result)
    }

    // Give the caller a chance to start collecting outputs.
    beforeStart(workflowScope, renderingsAndSnapshots.asFlow(), outputs.asFlow())

    // Yield to allow any coroutines created by beforeSetup to collect the flows to start.
    // This means that collection will start before the runtime, even if launch(UNDISPATCHED) is not
    // used.
    yield()

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

  return@coroutineScope resultDeferred.await()
}
