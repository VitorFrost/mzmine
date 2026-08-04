/*
 * Copyright (c) 2026 Contributors to the open offline fork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.mzmine.taskcontrol.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskPriority;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.taskcontrol.TaskStatusListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that worker execution and priority changes require no desktop or JavaFX scheduler. */
class WorkerThreadHeadlessDecouplingTest {

  @Test
  void successfulTaskIsCompactedWithoutDesktopInitialization() {
    final ControlledAbstractTask task = new ControlledAbstractTask("success", Outcome.FINISH);
    final WrappedTask wrapped = execute(task);

    assertTrue(wrapped.getActualTask() instanceof FinishedTask);
    assertEquals(TaskStatus.FINISHED, wrapped.getActualTask().getStatus());
    assertEquals(1d, wrapped.getActualTask().getFinishedPercentage(), 0.0001);
    assertNull(wrapped.getActualTask().getErrorMessage());
  }

  @Test
  void managedErrorWithoutMessageIsNormalizedAndPreserved() {
    final ControlledAbstractTask task = new ControlledAbstractTask("managed", Outcome.ERROR);
    final WrappedTask wrapped = execute(task);

    assertEquals(TaskStatus.ERROR, wrapped.getActualTask().getStatus());
    assertEquals("Unspecified error", wrapped.getActualTask().getErrorMessage());
    assertEquals(List.of(TaskStatus.PROCESSING, TaskStatus.ERROR), task.transitions);
  }

  @Test
  void unhandledAbstractTaskExceptionBecomesTaskError() {
    final ControlledAbstractTask task = new ControlledAbstractTask("abstract boom", Outcome.THROW);
    final WrappedTask wrapped = execute(task);

    assertEquals(TaskStatus.ERROR, task.getStatus());
    assertTrue(task.getErrorMessage().contains("IllegalStateException: expected boom"));
    assertEquals(TaskStatus.ERROR, wrapped.getActualTask().getStatus());
    assertEquals(task.getErrorMessage(), wrapped.getActualTask().getErrorMessage());
  }

  @Test
  void unhandledPlainTaskExceptionIsStillPreservedInFinishedTask() {
    final PlainThrowingTask task = new PlainThrowingTask();
    final WrappedTask wrapped = execute(task);

    assertInstanceOf(FinishedTask.class, wrapped.getActualTask());
    assertEquals(TaskStatus.ERROR, wrapped.getActualTask().getStatus());
    assertTrue(wrapped.getActualTask().getErrorMessage()
        .contains("IllegalArgumentException: plain boom"));
  }

  @Test
  void priorityChangesDoNotRequireMZmineCoreRunLater() {
    final ControlledAbstractTask task = new ControlledAbstractTask("priority", Outcome.FINISH);
    final WrappedTask wrapped = new WrappedTask(task, TaskPriority.NORMAL);

    wrapped.setPriority(TaskPriority.HIGH);

    assertEquals(TaskPriority.HIGH, wrapped.getPriority());
    assertEquals(TaskPriority.HIGH, wrapped.priorityProperty().getValue());
  }

  @Test
  void controllerInternalsContainNoDesktopOrJavaFxSchedulingCalls() throws IOException {
    assertSourceExcludes(
        "src/main/java/io/github/mzmine/taskcontrol/impl/TaskControllerImpl.java",
        "io.github.mzmine.gui.Desktop",
        "io.github.mzmine.gui.HeadLessDesktop",
        "MZmineCore.getDesktop()",
        "javafx.application.Platform");
    assertSourceExcludes(
        "src/main/java/io/github/mzmine/taskcontrol/impl/WorkerThread.java",
        "io.github.mzmine.main.MZmineCore",
        "displayErrorMessage",
        "javafx.application.Platform");
    assertSourceExcludes(
        "src/main/java/io/github/mzmine/taskcontrol/impl/WrappedTask.java",
        "io.github.mzmine.main.MZmineCore",
        "MZmineCore.runLater",
        "javafx.application.Platform");

    final String guiSource = Files.readString(
        Path.of("src/main/java/io/github/mzmine/gui/mainwindow/MainWindowController.java"));
    assertTrue(guiSource.contains("getTasksView().getTable().refresh()"),
        "the existing JavaFX refresh must remain at the view boundary");
  }

  private static WrappedTask execute(final Task task) {
    final WrappedTask wrapped = new WrappedTask(task, TaskPriority.NORMAL);
    final WorkerThread worker = new WorkerThread(wrapped);

    worker.run();

    assertTrue(worker.isFinished());
    return wrapped;
  }

  private static void assertSourceExcludes(final String sourcePath,
      final String... forbiddenTokens) throws IOException {
    final String source = Files.readString(Path.of(sourcePath));
    for (final String token : forbiddenTokens) {
      assertTrue(!source.contains(token), sourcePath + " must not contain " + token);
    }
  }

  private enum Outcome {
    FINISH,
    ERROR,
    THROW
  }

  private static final class ControlledAbstractTask extends AbstractTask {

    private final String description;
    private final Outcome outcome;
    private final List<TaskStatus> transitions = new ArrayList<>();

    private ControlledAbstractTask(final String description, final Outcome outcome) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
      this.outcome = outcome;
      addTaskStatusListener((changedTask, newStatus, oldStatus) -> transitions.add(newStatus));
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.FINISHED ? 1d : 0.5d;
    }

    @Override
    public void run() {
      setStatus(TaskStatus.PROCESSING);
      switch (outcome) {
        case FINISH -> setStatus(TaskStatus.FINISHED);
        case ERROR -> setStatus(TaskStatus.ERROR);
        case THROW -> throw new IllegalStateException("expected boom");
      }
    }
  }

  private static final class PlainThrowingTask implements Task {

    @Override
    public String getTaskDescription() {
      return "plain throwing task";
    }

    @Override
    public double getFinishedPercentage() {
      return 0.25d;
    }

    @Override
    public TaskStatus getStatus() {
      return TaskStatus.PROCESSING;
    }

    @Override
    public String getErrorMessage() {
      return null;
    }

    @Override
    public TaskPriority getTaskPriority() {
      return TaskPriority.NORMAL;
    }

    @Override
    public void cancel() {
    }

    @Override
    public void addTaskStatusListener(TaskStatusListener listener) {
    }

    @Override
    public boolean removeTaskStatusListener(TaskStatusListener listener) {
      return false;
    }

    @Override
    public void clearTaskStatusListener() {
    }

    @Override
    public void run() {
      throw new IllegalArgumentException("plain boom");
    }
  }
}
