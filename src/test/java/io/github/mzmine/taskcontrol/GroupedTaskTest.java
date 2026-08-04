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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests the original fork-local grouped-task contract documented for Phase 2D. */
@Timeout(30)
class GroupedTaskTest {

  @Test
  void validatesConstructionAndFreezesChildOrder() {
    final CountingTask first = new CountingTask("first");
    final ArrayList<Task> mutableChildren = new ArrayList<>();
    mutableChildren.add(first);

    final GroupedTask grouped = new GroupedTask("group", mutableChildren, 1,
        TaskPriority.HIGH, Instant.parse("2026-08-04T00:00:00Z"));
    mutableChildren.add(new CountingTask("later"));

    assertEquals(List.of(first), grouped.getChildTasks());
    assertEquals(1, grouped.getMaximumConcurrency());
    assertEquals(TaskPriority.HIGH, grouped.getTaskPriority());
    assertThrows(UnsupportedOperationException.class,
        () -> grouped.getChildTasks().add(new CountingTask("blocked")));

    assertThrows(NullPointerException.class, () -> new GroupedTask(null, List.of(), 1));
    assertThrows(IllegalArgumentException.class, () -> new GroupedTask(" ", List.of(), 1));
    assertThrows(NullPointerException.class, () -> new GroupedTask("group", null, 1));
    assertThrows(NullPointerException.class,
        () -> new GroupedTask("group", java.util.Arrays.asList(first, null), 1));
    assertThrows(IllegalArgumentException.class,
        () -> new GroupedTask("group", List.of(first), 0));
    assertThrows(IllegalArgumentException.class,
        () -> new GroupedTask("group", List.of(first), -1));
    assertThrows(NullPointerException.class,
        () -> new GroupedTask("group", List.of(first), 1, null, Instant.now()));
    assertThrows(NullPointerException.class,
        () -> new GroupedTask("group", List.of(first), 1, TaskPriority.NORMAL, null));
  }

  @Test
  void emptyGroupFinishesAndReportsCompleteProgress() {
    final GroupedTask grouped = new GroupedTask("empty", List.of(), 3);

    assertEquals(TaskStatus.WAITING, grouped.getStatus());
    assertEquals(0d, grouped.getFinishedPercentage(), 0d);

    grouped.run();

    assertEquals(TaskStatus.FINISHED, grouped.getStatus());
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
  }

  @Test
  void enforcesStrictConcurrencyAndDeterministicPlatformThreadNames() throws Exception {
    final int childCount = 6;
    final int maximumConcurrency = 2;
    final CountDownLatch started = new CountDownLatch(maximumConcurrency);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maximumActive = new AtomicInteger();
    final Set<String> threadNames = ConcurrentHashMap.newKeySet();
    final Set<Class<?>> threadClasses = ConcurrentHashMap.newKeySet();
    final List<Task> children = new ArrayList<>();
    for (int index = 0; index < childCount; index++) {
      children.add(new BlockingTask("child-" + index, 0.5d, started, release, active,
          maximumActive, threadNames, threadClasses));
    }
    final GroupedTask grouped = new GroupedTask("bounded", children, maximumConcurrency);
    final Thread parentThread = new Thread(grouped, "group-parent-test");

    parentThread.start();
    assertTrue(started.await(5, TimeUnit.SECONDS));
    assertEquals(maximumConcurrency, active.get());
    assertEquals(maximumConcurrency, maximumActive.get());

    release.countDown();
    parentThread.join(10_000);

    assertFalse(parentThread.isAlive());
    assertEquals(TaskStatus.FINISHED, grouped.getStatus());
    assertEquals(maximumConcurrency, maximumActive.get());
    assertEquals(Set.of("MZmine grouped task-1", "MZmine grouped task-2"), threadNames);
    assertEquals(Set.of(Thread.class), threadClasses);
  }

  @Test
  void progressIsEqualWeightClampedAndTerminalChildrenContributeOne() throws Exception {
    final CountDownLatch started = new CountDownLatch(4);
    final CountDownLatch release = new CountDownLatch(1);
    final List<Task> children = List.of(
        new ProgressTask("quarter", 0.25d, started, release),
        new ProgressTask("half", 0.5d, started, release),
        new ProgressTask("above", 1.5d, started, release),
        new ProgressTask("below", -0.2d, started, release));
    final GroupedTask grouped = new GroupedTask("progress", children, 4);
    final Thread parentThread = new Thread(grouped, "progress-parent");

    parentThread.start();
    assertTrue(started.await(5, TimeUnit.SECONDS));
    assertEquals(0.4375d, grouped.getFinishedPercentage(), 0.000001d);

    release.countDown();
    parentThread.join(10_000);

    assertEquals(TaskStatus.FINISHED, grouped.getStatus());
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
  }

  @Test
  void aggregatesControlledErrorsInChildIndexOrderWithoutFailFast() {
    final CountingTask successfulMiddle = new CountingTask("middle success");
    final GroupedTask grouped = new GroupedTask("multiple errors", List.of(
        new ErrorTask("zero", "first error"), successfulMiddle,
        new ErrorTask("two", null)), 3);

    grouped.run();

    assertEquals(TaskStatus.ERROR, grouped.getStatus());
    assertEquals(1, successfulMiddle.runCount.get());
    final String error = grouped.getErrorMessage();
    final int firstPosition = error.indexOf("[0] zero: first error");
    final int secondPosition = error.indexOf("[2] two: Unspecified error");
    assertTrue(firstPosition >= 0);
    assertTrue(secondPosition > firstPosition);
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
  }

  @Test
  void unhandledPlainChildExceptionBecomesParentError() {
    final CountingTask otherChild = new CountingTask("other");
    final GroupedTask grouped = new GroupedTask("throwing group",
        List.of(new PlainThrowingTask(), otherChild), 2);

    grouped.run();

    assertEquals(TaskStatus.ERROR, grouped.getStatus());
    assertTrue(grouped.getErrorMessage().contains("IllegalArgumentException: plain boom"));
    assertEquals(1, otherChild.runCount.get());
  }

  @Test
  void nonTerminalChildResultIsRejected() {
    final GroupedTask grouped = new GroupedTask("non-terminal",
        List.of(new PlainNonTerminalTask()), 1);

    grouped.run();

    assertEquals(TaskStatus.ERROR, grouped.getStatus());
    assertTrue(grouped.getErrorMessage().contains("Child returned non-terminal status PROCESSING"));
  }

  @Test
  void independentChildCancellationDoesNotPreventOtherChildrenRunning() {
    final CountingTask successful = new CountingTask("still runs");
    final GroupedTask grouped = new GroupedTask("child cancellation",
        List.of(new SelfCancelingTask(), successful), 2);

    grouped.run();

    assertEquals(TaskStatus.CANCELED, grouped.getStatus());
    assertEquals(1, successful.runCount.get());
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
  }

  @Test
  void explicitParentCancellationPropagatesToAllChildrenAndStopsPendingWork() throws Exception {
    final int childCount = 5;
    final CountDownLatch started = new CountDownLatch(2);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger cancelCalls = new AtomicInteger();
    final List<Task> children = new ArrayList<>();
    for (int index = 0; index < childCount; index++) {
      children.add(new CancelAwareTask("cancel-" + index, started, release, cancelCalls));
    }
    final GroupedTask grouped = new GroupedTask("cancel group", children, 2);
    final Thread parentThread = new Thread(grouped, "cancel-parent");

    parentThread.start();
    assertTrue(started.await(5, TimeUnit.SECONDS));
    grouped.cancel();
    release.countDown();
    parentThread.join(10_000);

    assertFalse(parentThread.isAlive());
    assertEquals(TaskStatus.CANCELED, grouped.getStatus());
    assertEquals(childCount, cancelCalls.get());
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
  }

  @Test
  void parentExecutesAtMostOnce() {
    final CountingTask child = new CountingTask("once");
    final GroupedTask grouped = new GroupedTask("run once", List.of(child), 1);

    grouped.run();
    grouped.run();

    assertEquals(TaskStatus.FINISHED, grouped.getStatus());
    assertEquals(1, child.runCount.get());
  }

  @Test
  void groupedTaskSourceHasNoGuiProprietaryOrUpstreamCompatibilityDependency()
      throws IOException {
    final String source = Files.readString(
        Path.of("src/main/java/io/github/mzmine/taskcontrol/GroupedTask.java"));

    for (final String forbidden : List.of(
        "javafx.", "MZmineCore", "getDesktop()", "displayErrorMessage", "io.mzio",
        "UserController", "License")) {
      assertFalse(source.contains(forbidden), "GroupedTask must not contain " + forbidden);
    }
    assertTrue(source.contains("original open-offline implementation"));
    assertTrue(source.contains("do not promise source or binary compatibility"));
  }

  private static final class CountingTask extends AbstractTask {

    private final String description;
    private final AtomicInteger runCount = new AtomicInteger();

    private CountingTask(final String description) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.FINISHED ? 1d : 0d;
    }

    @Override
    public void run() {
      runCount.incrementAndGet();
      setStatus(TaskStatus.PROCESSING);
      setStatus(TaskStatus.FINISHED);
    }
  }

  private static final class ErrorTask extends AbstractTask {

    private final String description;
    private final String error;

    private ErrorTask(final String description, final String error) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
      this.error = error;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return 0.25d;
    }

    @Override
    public void run() {
      setStatus(TaskStatus.PROCESSING);
      setErrorMessage(error);
      setStatus(TaskStatus.ERROR);
    }
  }

  private static final class BlockingTask extends AbstractTask {

    private final String description;
    private final double progress;
    private final CountDownLatch started;
    private final CountDownLatch release;
    private final AtomicInteger active;
    private final AtomicInteger maximumActive;
    private final Set<String> threadNames;
    private final Set<Class<?>> threadClasses;

    private BlockingTask(final String description, final double progress,
        final CountDownLatch started, final CountDownLatch release, final AtomicInteger active,
        final AtomicInteger maximumActive, final Set<String> threadNames,
        final Set<Class<?>> threadClasses) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
      this.progress = progress;
      this.started = started;
      this.release = release;
      this.active = active;
      this.maximumActive = maximumActive;
      this.threadNames = threadNames;
      this.threadClasses = threadClasses;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.FINISHED ? 1d : progress;
    }

    @Override
    public void run() {
      setStatus(TaskStatus.PROCESSING);
      final Thread current = Thread.currentThread();
      threadNames.add(current.getName());
      threadClasses.add(current.getClass());
      final int currentActive = active.incrementAndGet();
      maximumActive.accumulateAndGet(currentActive, Math::max);
      started.countDown();
      try {
        if (!release.await(10, TimeUnit.SECONDS)) {
          setErrorMessage("release timeout");
          setStatus(TaskStatus.ERROR);
          return;
        }
        setStatus(TaskStatus.FINISHED);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        setStatus(TaskStatus.CANCELED);
      } finally {
        active.decrementAndGet();
      }
    }
  }

  private static final class ProgressTask extends AbstractTask {

    private final String description;
    private final double progress;
    private final CountDownLatch started;
    private final CountDownLatch release;

    private ProgressTask(final String description, final double progress,
        final CountDownLatch started, final CountDownLatch release) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
      this.progress = progress;
      this.started = started;
      this.release = release;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.FINISHED ? 1d : progress;
    }

    @Override
    public void run() {
      setStatus(TaskStatus.PROCESSING);
      started.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
        setStatus(TaskStatus.FINISHED);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        setStatus(TaskStatus.CANCELED);
      }
    }
  }

  private static final class CancelAwareTask extends AbstractTask {

    private final String description;
    private final CountDownLatch started;
    private final CountDownLatch release;
    private final AtomicInteger cancelCalls;

    private CancelAwareTask(final String description, final CountDownLatch started,
        final CountDownLatch release, final AtomicInteger cancelCalls) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
      this.started = started;
      this.release = release;
      this.cancelCalls = cancelCalls;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.WAITING ? 0d : 0.5d;
    }

    @Override
    public void run() {
      if (getStatus() == TaskStatus.CANCELED) {
        return;
      }
      setStatus(TaskStatus.PROCESSING);
      started.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
        if (getStatus() != TaskStatus.CANCELED) {
          setStatus(TaskStatus.FINISHED);
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        setStatus(TaskStatus.CANCELED);
      }
    }

    @Override
    public void cancel() {
      cancelCalls.incrementAndGet();
      super.cancel();
    }
  }

  private static final class SelfCancelingTask extends AbstractTask {

    private SelfCancelingTask() {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
    }

    @Override
    public String getTaskDescription() {
      return "self canceled";
    }

    @Override
    public double getFinishedPercentage() {
      return 0.5d;
    }

    @Override
    public void run() {
      setStatus(TaskStatus.PROCESSING);
      setStatus(TaskStatus.CANCELED);
    }
  }

  private static final class PlainThrowingTask extends PlainTaskBase {

    @Override
    public String getTaskDescription() {
      return "plain throwing";
    }

    @Override
    public void run() {
      throw new IllegalArgumentException("plain boom");
    }
  }

  private static final class PlainNonTerminalTask extends PlainTaskBase {

    @Override
    public String getTaskDescription() {
      return "plain non-terminal";
    }

    @Override
    public void run() {
    }
  }

  private abstract static class PlainTaskBase implements Task {

    @Override
    public double getFinishedPercentage() {
      return 0.4d;
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
    public void addTaskStatusListener(final TaskStatusListener listener) {
    }

    @Override
    public boolean removeTaskStatusListener(final TaskStatusListener listener) {
      return false;
    }

    @Override
    public void clearTaskStatusListener() {
    }
  }
}
