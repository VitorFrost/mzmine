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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Fork-local parent task that executes an immutable ordered group of independent child tasks.
 *
 * <p>This is an original open-offline implementation. Its API and scheduling semantics are owned by
 * this fork and do not promise source or binary compatibility with an upstream MZmine grouped-task
 * implementation.</p>
 */
public final class GroupedTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GroupedTask.class.getName());
  private static final long TERMINATION_TIMEOUT_SECONDS = 30L;
  private static final String THREAD_NAME_PREFIX = "MZmine grouped task";

  private final String description;
  private final List<Task> children;
  private final int maximumConcurrency;
  private final TaskPriority taskPriority;
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean cancellationRequested = new AtomicBoolean();
  private final AtomicReferenceArray<ChildResult> childResults;
  private final List<IndexedFuture> submittedFutures = new CopyOnWriteArrayList<>();
  private final Object stateLock = new Object();

  private volatile ThreadPoolExecutor executor;

  /**
   * Creates a normal-priority grouped task with the current instant as module call date.
   */
  public GroupedTask(@NotNull final String description,
      @NotNull final List<? extends Task> children, final int maximumConcurrency) {
    this(description, children, maximumConcurrency, TaskPriority.NORMAL, Instant.now());
  }

  /**
   * Creates a grouped task with explicit priority and module call date.
   */
  public GroupedTask(@NotNull final String description,
      @NotNull final List<? extends Task> children, final int maximumConcurrency,
      @NotNull final TaskPriority taskPriority, @NotNull final Instant moduleCallDate) {
    super(null, Objects.requireNonNull(moduleCallDate, "moduleCallDate"));
    this.description = requireDescription(description);
    this.children = immutableChildren(children);
    if (maximumConcurrency <= 0) {
      throw new IllegalArgumentException("maximumConcurrency must be greater than zero");
    }
    this.maximumConcurrency = maximumConcurrency;
    this.taskPriority = Objects.requireNonNull(taskPriority, "taskPriority");
    childResults = new AtomicReferenceArray<>(this.children.size());
  }

  /**
   * Returns the immutable child order used for execution, progress, and diagnostics.
   */
  public List<Task> getChildTasks() {
    return children;
  }

  public int getMaximumConcurrency() {
    return maximumConcurrency;
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public TaskPriority getTaskPriority() {
    return taskPriority;
  }

  @Override
  public double getFinishedPercentage() {
    if (children.isEmpty()) {
      return getStatus() == TaskStatus.FINISHED ? 1d : 0d;
    }

    double total = 0d;
    for (int index = 0; index < children.size(); index++) {
      if (childResults.get(index) != null) {
        total += 1d;
      } else {
        total += clampedProgress(children.get(index));
      }
    }
    return total / children.size();
  }

  @Override
  public void run() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    synchronized (stateLock) {
      if (cancellationRequested.get() || getStatus() == TaskStatus.CANCELED) {
        return;
      }
      setStatus(TaskStatus.PROCESSING);
    }

    if (children.isEmpty()) {
      completeParent(Collections.emptyList());
      return;
    }

    final int workerCount = Math.min(maximumConcurrency, children.size());
    executor = TaskController.createFixedThreadPool(workerCount, THREAD_NAME_PREFIX);
    final List<ChildResult> collectedResults = new ArrayList<>(children.size());

    try {
      submitChildren();
      collectResults(collectedResults);
    } finally {
      terminateExecutor(collectedResults);
      fillMissingResults(collectedResults);
      completeParent(collectedResults);
    }
  }

  @Override
  public void cancel() {
    synchronized (stateLock) {
      if (isTerminal(getStatus())) {
        return;
      }
      cancellationRequested.set(true);
      setStatus(TaskStatus.CANCELED);
    }

    for (final Task child : children) {
      safeCancel(child);
    }
    for (final IndexedFuture indexedFuture : submittedFutures) {
      indexedFuture.future().cancel(true);
    }

    final ThreadPoolExecutor currentExecutor = executor;
    if (currentExecutor != null) {
      currentExecutor.shutdownNow();
    }
  }

  private void submitChildren() {
    for (int index = 0; index < children.size(); index++) {
      if (cancellationRequested.get()) {
        setResultIfAbsent(index,
            ChildResult.canceled(index, safeDescription(children.get(index))));
        continue;
      }

      final int childIndex = index;
      final Task child = children.get(index);
      try {
        final Future<ChildResult> future = executor.submit(() -> executeChild(childIndex, child));
        submittedFutures.add(new IndexedFuture(childIndex, future));
      } catch (RejectedExecutionException rejected) {
        final ChildResult result;
        if (cancellationRequested.get() || executor.isShutdown()) {
          safeCancel(child);
          result = ChildResult.canceled(childIndex, safeDescription(child));
        } else {
          result = ChildResult.error(childIndex, safeDescription(child),
              "Executor rejected child task: " + rejected);
        }
        setResultIfAbsent(childIndex, result);
      }
    }
  }

  private void collectResults(final List<ChildResult> collectedResults) {
    for (final IndexedFuture indexedFuture : submittedFutures) {
      final int childIndex = indexedFuture.index();
      ChildResult result = childResults.get(childIndex);
      if (result == null) {
        try {
          result = indexedFuture.future().get();
        } catch (CancellationException canceled) {
          safeCancel(children.get(childIndex));
          result = ChildResult.canceled(childIndex, safeDescription(children.get(childIndex)));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          cancel();
          result = ChildResult.canceled(childIndex, safeDescription(children.get(childIndex)));
        } catch (ExecutionException executionException) {
          result = ChildResult.error(childIndex, safeDescription(children.get(childIndex)),
              throwableText(executionException.getCause()));
        }
        setResultIfAbsent(childIndex, result);
      }
      collectedResults.add(childResults.get(childIndex));
    }
  }

  private ChildResult executeChild(final int index, final Task child) {
    final String childDescription = safeDescription(child);
    if (cancellationRequested.get()) {
      safeCancel(child);
      return recordResult(ChildResult.canceled(index, childDescription));
    }

    try {
      child.run();
      final TaskStatus status = child.getStatus();
      if (status == TaskStatus.FINISHED) {
        return recordResult(ChildResult.finished(index, childDescription));
      }
      if (status == TaskStatus.CANCELED) {
        return recordResult(ChildResult.canceled(index, childDescription));
      }
      if (status == TaskStatus.ERROR) {
        final String errorMessage = normalizedErrorMessage(child.getErrorMessage());
        ensureAbstractTaskError(child, errorMessage);
        return recordResult(ChildResult.error(index, childDescription, errorMessage));
      }

      final String errorMessage = "Child returned non-terminal status " + status;
      ensureAbstractTaskError(child, errorMessage);
      return recordResult(ChildResult.error(index, childDescription, errorMessage));
    } catch (Throwable throwable) {
      if (cancellationRequested.get() || Thread.currentThread().isInterrupted()) {
        safeCancel(child);
        return recordResult(ChildResult.canceled(index, childDescription));
      }

      final String errorMessage = throwableText(throwable);
      ensureAbstractTaskError(child, errorMessage);
      logger.log(Level.SEVERE,
          "Unhandled exception in grouped child " + index + " (" + childDescription + ")",
          throwable);
      return recordResult(ChildResult.error(index, childDescription, errorMessage));
    }
  }

  private void terminateExecutor(final List<ChildResult> collectedResults) {
    final ThreadPoolExecutor currentExecutor = executor;
    if (currentExecutor == null) {
      return;
    }

    if (cancellationRequested.get()) {
      currentExecutor.shutdownNow();
    } else {
      currentExecutor.shutdown();
    }

    try {
      if (!currentExecutor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        currentExecutor.shutdownNow();
        collectedResults.add(ChildResult.error(children.size(), description,
            "Grouped-task executor did not terminate within " + TERMINATION_TIMEOUT_SECONDS
                + " seconds"));
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      cancellationRequested.set(true);
      currentExecutor.shutdownNow();
    } finally {
      executor = null;
    }
  }

  private void fillMissingResults(final List<ChildResult> collectedResults) {
    for (int index = 0; index < children.size(); index++) {
      ChildResult result = childResults.get(index);
      if (result == null) {
        final Task child = children.get(index);
        if (cancellationRequested.get()) {
          safeCancel(child);
          result = ChildResult.canceled(index, safeDescription(child));
        } else {
          result = ChildResult.error(index, safeDescription(child),
              "Child did not produce a terminal result");
        }
        setResultIfAbsent(index, result);
      }
      if (!collectedResults.contains(childResults.get(index))) {
        collectedResults.add(childResults.get(index));
      }
    }
    collectedResults.sort((left, right) -> Integer.compare(left.index(), right.index()));
  }

  private void completeParent(final List<ChildResult> results) {
    final List<ChildResult> errors = results.stream()
        .filter(result -> result.status() == TaskStatus.ERROR)
        .sorted((left, right) -> Integer.compare(left.index(), right.index()))
        .toList();

    synchronized (stateLock) {
      if (!errors.isEmpty()) {
        setErrorMessage(aggregateErrors(errors));
        setStatus(TaskStatus.ERROR);
        return;
      }

      final boolean childCanceled = results.stream()
          .anyMatch(result -> result.status() == TaskStatus.CANCELED);
      if (cancellationRequested.get() || childCanceled) {
        setStatus(TaskStatus.CANCELED);
      } else {
        setStatus(TaskStatus.FINISHED);
      }
    }
  }

  private ChildResult recordResult(final ChildResult result) {
    setResultIfAbsent(result.index(), result);
    return childResults.get(result.index());
  }

  private void setResultIfAbsent(final int index, final ChildResult result) {
    if (index >= 0 && index < childResults.length()) {
      childResults.compareAndSet(index, null, result);
    }
  }

  private static String aggregateErrors(final List<ChildResult> errors) {
    final StringBuilder message = new StringBuilder("Grouped task failed:");
    for (final ChildResult error : errors) {
      message.append(System.lineSeparator())
          .append('[').append(error.index()).append("] ")
          .append(error.description()).append(": ")
          .append(error.errorMessage());
    }
    return message.toString();
  }

  private static List<Task> immutableChildren(final List<? extends Task> children) {
    Objects.requireNonNull(children, "children");
    final ArrayList<Task> copy = new ArrayList<>(children.size());
    for (int index = 0; index < children.size(); index++) {
      copy.add(Objects.requireNonNull(children.get(index), "child at index " + index));
    }
    return List.copyOf(copy);
  }

  private static String requireDescription(final String description) {
    Objects.requireNonNull(description, "description");
    if (description.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
    return description;
  }

  private static double clampedProgress(final Task child) {
    try {
      final double progress = child.getFinishedPercentage();
      if (!Double.isFinite(progress)) {
        return 0d;
      }
      return Math.max(0d, Math.min(1d, progress));
    } catch (Throwable ignored) {
      return 0d;
    }
  }

  private static String normalizedErrorMessage(final String errorMessage) {
    return errorMessage == null || errorMessage.isBlank() ? "Unspecified error" : errorMessage;
  }

  private static String throwableText(final Throwable throwable) {
    if (throwable == null) {
      return "Unknown child execution failure";
    }
    final String message = throwable.getMessage();
    return throwable.getClass().getSimpleName()
        + (message == null || message.isBlank() ? "" : ": " + message);
  }

  private static String safeDescription(final Task child) {
    try {
      final String childDescription = child.getTaskDescription();
      return childDescription == null || childDescription.isBlank()
          ? child.getClass().getSimpleName() : childDescription;
    } catch (Throwable ignored) {
      return child.getClass().getSimpleName();
    }
  }

  private static void ensureAbstractTaskError(final Task child, final String errorMessage) {
    if (child instanceof AbstractTask abstractTask) {
      abstractTask.setErrorMessage(errorMessage);
      if (abstractTask.getStatus() != TaskStatus.ERROR) {
        abstractTask.setStatus(TaskStatus.ERROR);
      }
    }
  }

  private static void safeCancel(final Task child) {
    try {
      child.cancel();
    } catch (Throwable throwable) {
      logger.log(Level.WARNING, "Child cancellation failed for " + safeDescription(child),
          throwable);
    }
  }

  private static boolean isTerminal(final TaskStatus status) {
    return status == TaskStatus.FINISHED || status == TaskStatus.CANCELED
        || status == TaskStatus.ERROR;
  }

  private record IndexedFuture(int index, Future<ChildResult> future) {
  }

  private record ChildResult(int index, String description, TaskStatus status,
                             String errorMessage) {

    private static ChildResult finished(final int index, final String description) {
      return new ChildResult(index, description, TaskStatus.FINISHED, null);
    }

    private static ChildResult canceled(final int index, final String description) {
      return new ChildResult(index, description, TaskStatus.CANCELED, null);
    }

    private static ChildResult error(final int index, final String description,
        final String errorMessage) {
      return new ChildResult(index, description, TaskStatus.ERROR,
          normalizedErrorMessage(errorMessage));
    }
  }
}
