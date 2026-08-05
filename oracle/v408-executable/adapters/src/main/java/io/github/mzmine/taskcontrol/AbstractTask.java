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

import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal lifecycle surface required by the frozen v4.0.8 direct mzML parser.
 *
 * <p>This adapter deliberately provides no JavaFX properties, listeners, scheduler integration,
 * priority handling, or task-controller services. It stores only the nullable storage reference,
 * module-call timestamp, cancellation flag, status, and error message used by
 * {@code MzMLFileImportMethod}.</p>
 */
public abstract class AbstractTask implements Runnable {

  protected final @Nullable MemoryMapStorage storage;
  private final @NotNull Instant moduleCallDate;
  private volatile TaskStatus status = TaskStatus.WAITING;
  private volatile boolean canceled;
  private volatile @Nullable String errorMessage;

  protected AbstractTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate) {
    this.storage = storage;
    this.moduleCallDate = moduleCallDate;
  }

  public final @NotNull Instant getModuleCallDate() {
    return moduleCallDate;
  }

  public final boolean isCanceled() {
    return canceled || status == TaskStatus.CANCELED;
  }

  public final void cancel() {
    canceled = true;
    status = TaskStatus.CANCELED;
  }

  public final @NotNull TaskStatus getStatus() {
    return status;
  }

  protected final void setStatus(@NotNull TaskStatus status) {
    this.status = status;
    if (status == TaskStatus.CANCELED) {
      canceled = true;
    }
  }

  public final @Nullable String getErrorMessage() {
    return errorMessage;
  }

  protected final void setErrorMessage(@Nullable String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public abstract String getTaskDescription();

  public abstract double getFinishedPercentage();
}
