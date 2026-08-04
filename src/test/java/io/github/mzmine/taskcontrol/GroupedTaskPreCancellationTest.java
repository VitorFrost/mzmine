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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Locks the fork-local pre-start cancellation contract and its idempotence. */
class GroupedTaskPreCancellationTest {

  @Test
  void cancelBeforeRunMarksAllChildrenCompleteWithoutExecutingThem() {
    final CancelCountingTask first = new CancelCountingTask("first");
    final CancelCountingTask second = new CancelCountingTask("second");
    final GroupedTask grouped = new GroupedTask("pre-canceled", List.of(first, second), 2);

    grouped.cancel();
    grouped.cancel();
    grouped.run();

    assertEquals(TaskStatus.CANCELED, grouped.getStatus());
    assertEquals(1d, grouped.getFinishedPercentage(), 0d);
    assertEquals(1, first.cancelCalls.get());
    assertEquals(1, second.cancelCalls.get());
    assertEquals(0, first.runCalls.get());
    assertEquals(0, second.runCalls.get());
  }

  private static final class CancelCountingTask extends AbstractTask {

    private final String description;
    private final AtomicInteger cancelCalls = new AtomicInteger();
    private final AtomicInteger runCalls = new AtomicInteger();

    private CancelCountingTask(final String description) {
      super(null, Instant.parse("2026-08-04T00:00:00Z"));
      this.description = description;
    }

    @Override
    public String getTaskDescription() {
      return description;
    }

    @Override
    public double getFinishedPercentage() {
      return getStatus() == TaskStatus.CANCELED ? 1d : 0d;
    }

    @Override
    public void run() {
      runCalls.incrementAndGet();
      setStatus(TaskStatus.FINISHED);
    }

    @Override
    public void cancel() {
      cancelCalls.incrementAndGet();
      super.cancel();
    }
  }
}
