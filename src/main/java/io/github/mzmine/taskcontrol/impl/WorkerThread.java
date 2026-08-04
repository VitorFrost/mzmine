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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker thread that executes one task and then terminates.
 *
 * <p>Error handling is headless-safe: failures are written to the task status/error message and to
 * the log. Displaying a dialog is a responsibility of listeners in the GUI layer.</p>
 */
class WorkerThread extends Thread {

  private static final Logger logger = Logger.getLogger(WorkerThread.class.getName());

  private final WrappedTask wrappedTask;
  private volatile boolean finished;

  WorkerThread(WrappedTask wrappedTask) {
    super("Thread executing task " + wrappedTask);
    this.wrappedTask = wrappedTask;
    wrappedTask.assignTo(this);
  }

  @Override
  public void run() {
    final Task actualTask = wrappedTask.getActualTask();
    TaskStatus finalStatus = actualTask.getStatus();
    String finalErrorMessage = actualTask.getErrorMessage();

    try {
      logger.info("Starting processing of task " + actualTask.getTaskDescription());
      actualTask.run();
      finalStatus = actualTask.getStatus();
      finalErrorMessage = actualTask.getErrorMessage();

      if (finalStatus == TaskStatus.ERROR) {
        finalErrorMessage = normalizedErrorMessage(finalErrorMessage);
        ensureErrorState(actualTask, finalErrorMessage);
        logger.severe(
            "Error of task " + actualTask.getTaskDescription() + ": " + finalErrorMessage);
      } else {
        logger.info("Processing of task " + actualTask.getTaskDescription() + " done, status "
            + finalStatus);
      }
    } catch (Throwable throwable) {
      finalStatus = TaskStatus.ERROR;
      finalErrorMessage = "Unhandled exception in task "
          + actualTask.getTaskDescription() + ": " + throwable;
      ensureErrorState(actualTask, finalErrorMessage);
      logger.log(Level.SEVERE, finalErrorMessage, throwable);
    } finally {
      // Preserve explicit diagnostics even for Task implementations that do not extend AbstractTask.
      wrappedTask.removeTaskReference(finalStatus, finalErrorMessage);
      finished = true;
    }
  }

  private static String normalizedErrorMessage(final String errorMessage) {
    return errorMessage == null || errorMessage.isBlank() ? "Unspecified error" : errorMessage;
  }

  private static void ensureErrorState(final Task task, final String errorMessage) {
    if (task instanceof AbstractTask abstractTask) {
      abstractTask.setErrorMessage(errorMessage);
      if (abstractTask.getStatus() != TaskStatus.ERROR) {
        abstractTask.setStatus(TaskStatus.ERROR);
      }
    }
  }

  boolean isFinished() {
    return finished;
  }

  public WrappedTask getWrappedTask() {
    return wrappedTask;
  }
}
