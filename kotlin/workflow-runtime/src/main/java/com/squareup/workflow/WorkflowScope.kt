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

/** TODO write documentation */
interface WorkflowScope<in T> : CoroutineScope {

  /**
   * TODO kdoc
   */
  fun finishWorkflow(result: T): Boolean
}

internal inline fun <T> WorkflowScope(
  coroutineScope: CoroutineScope,
  crossinline finishWorkflow: (T) -> Boolean
): WorkflowScope<T> = object : WorkflowScope<T>, CoroutineScope by coroutineScope {
  override fun finishWorkflow(result: T): Boolean = finishWorkflow(result)
}
