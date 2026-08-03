/*
 * Copyright (c) 2004-2024 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package io.github.mzmine.taskcontrol;

import io.github.mzmine.taskcontrol.impl.TaskQueue;
import io.github.mzmine.taskcontrol.impl.WrappedTask;
import java.util.Objects;

/**
 * Controls asynchronous and deterministic synchronous execution of MZmine tasks.
 *
 * <p>The synchronous API is a selective Java 20-compatible adaptation of the public MIT-licensed
 * controller revision at commit {@code 001a0c3c672d09a141faa56b42c7c145246f9dd6}. The legacy
 * MZmine 3.9 queue API is retained for compatibility.</p>
 */
public interface TaskController {

  void addTask(Task task);

  WrappedTask[] addTasks(Task[] tasks);

  void addTask(Task task, TaskPriority priority);

  WrappedTask[] addTasks(Task[] tasks, TaskPriority[] priority);

  void setTaskPriority(Task task, TaskPriority priority);

  void addTaskControlListener(TaskControlListener listener);

  /**
   * Legacy MZmine 3.9 queue accessor.
   */
  TaskQueue getTaskQueue();

  /**
   * Clearer alias introduced by the public 2024 task-controller work.
   *
   * @return the same submitted-task queue returned by {@link #getTaskQueue()}
   */
  default TaskQueue getSubmittedTaskQueue() {
    return getTaskQueue();
  }

  /**
   * Add a task to the submitted-task view and execute it completely on the calling thread.
   *
   * @param task non-null task to execute
   * @return wrapper containing the final task description, status, progress, and error message
   */
  WrappedTask runTaskOnThisThreadBlocking(Task task);

  /**
   * Execute tasks sequentially on the calling thread.
   *
   * <p>This small compatibility helper is intentionally deterministic and does not create an
   * executor. Empty input returns an empty array.</p>
   */
  default WrappedTask[] runTasksOnThisThreadBlocking(Task... tasks) {
    Objects.requireNonNull(tasks, "tasks");
    final WrappedTask[] wrappedTasks = new WrappedTask[tasks.length];
    for (int i = 0; i < tasks.length; i++) {
      wrappedTasks[i] = runTaskOnThisThreadBlocking(tasks[i]);
    }
    return wrappedTasks;
  }

  boolean isTaskInstanceRunningOrQueued(Class<? extends AbstractTask> clazz);

}
