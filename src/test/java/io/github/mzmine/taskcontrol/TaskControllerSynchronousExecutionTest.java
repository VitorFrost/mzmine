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
package io.github.mzmine.taskcontrol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mzmine.taskcontrol.impl.TaskControllerImpl;
import io.github.mzmine.taskcontrol.impl.WrappedTask;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies the selective synchronous-controller API without changing asynchronous behavior. */
class TaskControllerSynchronousExecutionTest {

  private final TaskController controller = TaskControllerImpl.getInstance();

  @Test
  void submittedQueueAliasPreservesLegacyQueueIdentity() {
    assertSame(controller.getTaskQueue(), controller.getSubmittedTaskQueue());
  }

  @Test
  void successfulTaskRunsOnCallingThreadAndPreservesStatusTransitions() {
    final List<TaskStatus> transitions = new ArrayList<>();
    final ControlledTask task = new ControlledTask("success", Outcome.FINISH, null);
    task.addTaskStatusListener(
        (changedTask, newStatus, oldStatus) -> transitions.add(newStatus));
    final long callingThread = Thread.currentThread().getId();

    final WrappedTask wrapped = controller.runTaskOnThisThreadBlocking(task);

    assertEquals(callingThread, task.executingThreadId);
    assertEquals(1, task.runCount.get());
    assertArrayEquals(new TaskStatus[]{TaskStatus.PROCESSING, TaskStatus.FINISHED},
        transitions.toArray(TaskStatus[]::new));
    assertEquals(TaskStatus.FINISHED, task.getStatus());
    assertEquals(TaskStatus.FINISHED, wrapped.getActualTask().getStatus());
    assertEquals("success", wrapped.getActualTask().getTaskDescription());
    assertEquals(1.0, wrapped.getActualTask().getFinishedPercentage(), 0.0001);
  }

  @Test
  void managedErrorIsPreservedInFinishedWrapper() {
    final ControlledTask task = new ControlledTask("managed error", Outcome.ERROR, null);

    final WrappedTask wrapped = controller.runTaskOnThisThreadBlocking(task);

    assertEquals(1, task.runCount.get());
    assertEquals(TaskStatus.ERROR, task.getStatus());
    assertEquals("expected test error", task.getErrorMessage());
    assertEquals(TaskStatus.ERROR, wrapped.getActualTask().getStatus());
    assertEquals("expected test error", wrapped.getActualTask().getErrorMessage());
  }

  @Test
  void cancellationIsPreservedInFinishedWrapper() {
    final ControlledTask task = new ControlledTask("cancelled", Outcome.CANCEL, null);

    final WrappedTask wrapped = controller.runTaskOnThisThreadBlocking(task);

    assertEquals(1, task.runCount.get());
    assertEquals(TaskStatus.CANCELED, task.getStatus());
    assertEquals(TaskStatus.CANCELED, wrapped.getActualTask().getStatus());
  }

  @Test
  void multipleTasksRunSequentiallyInInputOrder() {
    final List<Integer> order = new ArrayList<>();
    final ControlledTask first = new ControlledTask("first", Outcome.FINISH, () -> order.add(1));
    final ControlledTask second = new ControlledTask("second", Outcome.FINISH, () -> order.add(2));
    final ControlledTask third = new ControlledTask("third", Outcome.FINISH, () -> order.add(3));

    final WrappedTask[] wrapped =
        controller.runTasksOnThisThreadBlocking(first, second, third);

    assertEquals(3, wrapped.length);
    assertEquals(List.of(1, 2, 3), order);
    for (final WrappedTask task : wrapped) {
      assertNotNull(task);
      assertEquals(TaskStatus.FINISHED, task.getActualTask().getStatus());
    }
  }

  @Test
  void emptyInputIsANoOpAndNullInputIsRejected() {
    assertEquals(0, controller.runTasksOnThisThreadBlocking().length);
    assertThrows(NullPointerException.class,
        () -> controller.runTasksOnThisThreadBlocking((Task[]) null));
    assertThrows(NullPointerException.class,
        () -> controller.runTaskOnThisThreadBlocking(null));
  }

  @Test
  void asynchronousSchedulerDoesNotExecuteBlockingTaskTwice() throws InterruptedException {
    final ControlledTask task = new ControlledTask("single execution", Outcome.FINISH, null);

    controller.runTaskOnThisThreadBlocking(task);
    // The legacy scheduler polls every 100-300 ms. Waiting past one cycle verifies that the
    // pre-assigned wrapper was skipped rather than submitted a second time.
    Thread.sleep(450);

    assertEquals(1, task.runCount.get());
  }

  private enum Outcome {
    FINISH,
    ERROR,
    CANCEL
  }

  private static final class ControlledTask extends AbstractTask {

    private final String description;
    private final Outcome outcome;
    private final Runnable action;
    private final AtomicInteger runCount = new AtomicInteger();
    private volatile long executingThreadId = -1;

    private ControlledTask(final String description, final Outcome outcome,
        final Runnable action) {
      super(null, Instant.parse("2026-08-03T00:00:00Z"));
      this.description = description;
      this.outcome = outcome;
      this.action = action;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return switch (getStatus()) {
        case FINISHED -> 1.0;
        case ERROR, CANCELED -> 0.5;
        default -> 0.0;
      };
    }

    @Override
    public void run() {
      executingThreadId = Thread.currentThread().getId();
      runCount.incrementAndGet();
      setStatus(TaskStatus.PROCESSING);
      if (action != null) {
        action.run();
      }

      switch (outcome) {
        case FINISH -> setStatus(TaskStatus.FINISHED);
        case ERROR -> {
          setErrorMessage("expected test error");
          setStatus(TaskStatus.ERROR);
        }
        case CANCEL -> cancel();
      }
    }
  }
}
