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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.taskcontrol.impl;

import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;

/**
 * Compact replacement for a completed task in the controller queue.
 *
 * <p>This releases the original task object while preserving description, progress, final status,
 * and error text for headless diagnostics and the task view.</p>
 */
public class FinishedTask extends AbstractTask {

  private final String description;
  private final double finishedPercentage;

  public FinishedTask(Task task) {
    this(task, task.getStatus(), task.getErrorMessage());
  }

  FinishedTask(Task task, TaskStatus finalStatus, String finalErrorMessage) {
    super(null, Instant.now()); // date is irrelevant for the compact queue record
    setStatus(finalStatus);
    setErrorMessage(finalErrorMessage);
    description = task.getTaskDescription();
    finishedPercentage = task.getFinishedPercentage();
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public void run() {
    // Ignore attempts to run a task that has already completed.
  }

  @Override
  public void cancel() {
    // Ignore attempts to cancel a task that has already completed.
  }

  @Override
  public double getFinishedPercentage() {
    return finishedPercentage;
  }
}
