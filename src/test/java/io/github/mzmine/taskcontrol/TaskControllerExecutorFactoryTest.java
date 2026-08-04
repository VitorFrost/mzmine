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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Verifies the Java 20 platform-thread factories without changing the legacy scheduler. */
@Timeout(20)
class TaskControllerExecutorFactoryTest {

  @Test
  void invalidThreadCountsAndPrefixesAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> TaskController.createFixedThreadPool(0, "fixed"));
    assertThrows(IllegalArgumentException.class,
        () -> TaskController.createFixedThreadPool(-1, "fixed"));
    assertThrows(NullPointerException.class,
        () -> TaskController.createFixedThreadPool(1, null));
    assertThrows(IllegalArgumentException.class,
        () -> TaskController.createFixedThreadPool(1, "  "));
    assertThrows(IllegalArgumentException.class,
        () -> TaskController.createCachedHighPriorityThreadPool(0));
    assertThrows(IllegalArgumentException.class,
        () -> TaskController.createCachedHighPriorityThreadPool(-1));
  }

  @Test
  void fixedPoolUsesExactlyTheRequestedNumberOfPlatformThreads() throws Exception {
    final int threadCount = 3;
    final ThreadPoolExecutor executor =
        TaskController.createFixedThreadPool(threadCount, "fixed-test");
    final CountDownLatch started = new CountDownLatch(threadCount);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maximumActive = new AtomicInteger();
    final Set<String> names = ConcurrentHashMap.newKeySet();
    final Set<Class<?>> threadClasses = ConcurrentHashMap.newKeySet();
    final Set<Integer> priorities = ConcurrentHashMap.newKeySet();
    final Set<Boolean> daemonStates = ConcurrentHashMap.newKeySet();
    final List<Future<?>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < threadCount * 2; i++) {
        futures.add(executor.submit(() -> observeBlockingWorker(started, release, active,
            maximumActive, names, threadClasses, priorities, daemonStates)));
      }

      assertTrue(started.await(5, TimeUnit.SECONDS), "fixed workers did not start");
      assertEquals(threadCount, executor.getPoolSize());
      assertEquals(threadCount, executor.getActiveCount());
      assertEquals(threadCount, maximumActive.get());

      release.countDown();
      for (final Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }

      assertEquals(Set.of("fixed-test-1", "fixed-test-2", "fixed-test-3"), names);
      assertEquals(Set.of(Thread.class), threadClasses);
      assertEquals(Set.of(Thread.NORM_PRIORITY), priorities);
      assertEquals(Set.of(false), daemonStates);

      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(executor.isTerminated());
    } finally {
      release.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void cachedHighPriorityPoolIsBoundedAndRejectsExcessWork() throws Exception {
    final int maximumThreads = 2;
    final ThreadPoolExecutor executor =
        TaskController.createCachedHighPriorityThreadPool(maximumThreads);
    final CountDownLatch started = new CountDownLatch(maximumThreads);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maximumActive = new AtomicInteger();
    final Set<String> names = ConcurrentHashMap.newKeySet();
    final Set<Class<?>> threadClasses = ConcurrentHashMap.newKeySet();
    final Set<Integer> priorities = ConcurrentHashMap.newKeySet();
    final Set<Boolean> daemonStates = ConcurrentHashMap.newKeySet();

    final Runnable blockingWorker = () -> observeBlockingWorker(started, release, active,
        maximumActive, names, threadClasses, priorities, daemonStates);

    try {
      executor.execute(blockingWorker);
      executor.execute(blockingWorker);

      assertTrue(started.await(5, TimeUnit.SECONDS), "cached workers did not start");
      assertEquals(maximumThreads, executor.getPoolSize());
      assertEquals(maximumThreads, executor.getActiveCount());
      assertEquals(maximumThreads, maximumActive.get());
      assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
      }));

      assertEquals(Set.of("MZmine high-priority task-1", "MZmine high-priority task-2"), names);
      assertEquals(Set.of(Thread.class), threadClasses);
      assertEquals(Set.of(TaskController.HIGH_PRIORITY_THREAD_PRIORITY), priorities);
      assertEquals(Set.of(true), daemonStates);
      assertEquals(0, executor.getCorePoolSize());
      assertEquals(maximumThreads, executor.getMaximumPoolSize());
      assertEquals(TaskController.CACHED_THREAD_KEEP_ALIVE_SECONDS,
          executor.getKeepAliveTime(TimeUnit.SECONDS));
      assertTrue(executor.getQueue().isEmpty());

      release.countDown();
      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(executor.isTerminated());
    } finally {
      release.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void shutdownRejectsNewWork() throws InterruptedException {
    final ThreadPoolExecutor executor = TaskController.createFixedThreadPool(1, "shutdown-test");
    executor.shutdown();

    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    assertTrue(executor.isShutdown());
    assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
    }));
    assertFalse(executor.isTerminating());
  }

  private static void observeBlockingWorker(final CountDownLatch started,
      final CountDownLatch release, final AtomicInteger active,
      final AtomicInteger maximumActive, final Set<String> names,
      final Set<Class<?>> threadClasses, final Set<Integer> priorities,
      final Set<Boolean> daemonStates) {
    final Thread thread = Thread.currentThread();
    names.add(thread.getName());
    threadClasses.add(thread.getClass());
    priorities.add(thread.getPriority());
    daemonStates.add(thread.isDaemon());

    final int currentActive = active.incrementAndGet();
    maximumActive.accumulateAndGet(currentActive, Math::max);
    started.countDown();
    try {
      if (!release.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("worker release latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("worker was interrupted", e);
    } finally {
      active.decrementAndGet();
    }
  }
}
