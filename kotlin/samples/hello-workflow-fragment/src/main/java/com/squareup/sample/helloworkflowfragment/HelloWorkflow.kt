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
package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.HelloWorkflow.Rendering
import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State
import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State.Goodbye
import com.squareup.sample.helloworkflowfragment.HelloWorkflow.State.Hello
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Snapshot
import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.WorkflowAction.Companion.enterState
import com.squareup.workflow.parse

object HelloWorkflow : StatefulWorkflow<Unit, State, Unit, Rendering>() {
  enum class State {
    Hello,
    Goodbye;

    fun theOtherState(): State = when (this) {
      Hello -> Goodbye
      Goodbye -> Hello
    }
  }

  data class Rendering(
    val message: String,
    val onClick: (Unit) -> Unit
  )

  override fun initialState(
    input: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.bytes?.parse { source -> if (source.readInt() == 1) Hello else Goodbye }
      ?: Hello

  override fun render(
    input: Unit,
    state: State,
    context: RenderContext<State, Unit>
  ): Rendering = Rendering(
      message = state.name,
      onClick = context.onEvent { enterState(state.theOtherState()) }
  )

  override fun snapshotState(state: State): Snapshot = Snapshot.of(if (state == Hello) 1 else 0)
}
