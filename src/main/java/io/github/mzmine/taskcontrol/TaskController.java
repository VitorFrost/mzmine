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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controls asynchronous and deterministic synchronous execution of MZmine tasks.
 *
 * <p>The synchronous API and executor factories are selective Java 20-compatible adaptations of
 * the public MIT-licensed controller revisions at commits
 * {@code 9977c66c754c04f8b06572787aa6708ad210519f} and
 * {@code 001a0c3c672d09a141faa56b42c7c145246f9dd6}. The legacy MZmine 3.9 queue API
 * remains unchanged.</p>
 */
public interface TaskController {

  int HIGH_PRIORITY_THREAD_PRIORITY = 8;
  long CACHED_THREAD_KEEP_ALIVE_SECONDS = 120L;

  /**
   * Create a fixed-size executor backed only by Java platform threads.
   *
   * <p>The factory deliberately uses {@code new Thread(...)} rather than virtual-thread APIs, so it
   * remains compatible with Java 20. Fixed-pool threads are non-daemon, use normal priority, and
   * receive deterministic names within the returned executor.</p>
   *
   * @param numThreads exact number of worker threads, greater than zero
   * @param threadNamePrefix non-blank prefix used for deterministic thread names
   * @return a fixed-size platform-thread executor
   */
  static ThreadPoolExecutor createFixedThreadPool(final int numThreads,
      final String threadNamePrefix) {
    requirePositiveThreadCount(numThreads, "numThreads");
    final ThreadFactory threadFactory = createPlatformThreadFactory(threadNamePrefix,
        Thread.NORM_PRIORITY, false);
    return new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(), threadFactory, new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * Create a bounded cached executor for short, high-priority subtasks.
   *
   * <p>This is a Java 20-compatible adaptation of the public MZmine factory. It keeps no idle core
   * threads, creates at most {@code maxNumThreads} platform threads, expires idle threads after
   * 120 seconds, and rejects work once all workers are occupied because the hand-off queue stores
   * no pending tasks.</p>
   *
   * @param maxNumThreads strict maximum number of concurrent workers, greater than zero
   * @return a bounded cached high-priority platform-thread executor
   */
  static ThreadPoolExecutor createCachedHighPriorityThreadPool(final int maxNumThreads) {
    requirePositiveThreadCount(maxNumThreads, "maxNumThreads");
    final ThreadFactory threadFactory = createPlatformThreadFactory(
        "MZmine high-priority task", HIGH_PRIORITY_THREAD_PRIORITY, true);
    return new ThreadPoolExecutor(0, maxNumThreads, CACHED_THREAD_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory,
        new ThreadPoolExecutor.AbortPolicy());
  }

  private static ThreadFactory createPlatformThreadFactory(final String threadNamePrefix,
      final int priority, final boolean daemon) {
    Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
    if (threadNamePrefix.isBlank()) {
      throw new IllegalArgumentException("threadNamePrefix must not be blank");
    }
    if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
      throw new IllegalArgumentException("priority must be between Thread.MIN_PRIORITY and "
          + "Thread.MAX_PRIORITY");
    }

    final AtomicInteger threadNumber = new AtomicInteger();
    return task -> {
      final Thread thread = new Thread(task,
          threadNamePrefix + "-" + threadNumber.incrementAndGet());
      thread.setDaemon(daemon);
      thread.setPriority(priority);
      return thread;
    };
  }

  private static void requirePositiveThreadCount(final int threadCount,
      final String parameterName) {
    if (threadCount <= 0) {
      throw new IllegalArgumentException(parameterName + " must be greater than zero");
    }
  }

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
