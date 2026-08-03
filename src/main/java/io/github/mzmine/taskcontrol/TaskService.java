/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Process-local access point for the open-source task controller.
 *
 * <p>This is a selective adaptation of the publicly MIT-licensed TaskService introduced in
 * commits {@code 9977c66c754c04f8b06572787aa6708ad210519f} and corrected in
 * {@code 25f4bc146a8869cd382861183a0ae8be0df7ad32}. It deliberately contains no account,
 * authentication, licensing, feature-entitlement, or network behavior.</p>
 *
 * <p>The MZmine 3.9 controller remains responsible for constructing and running the queue. This
 * service only provides explicit one-time registration and a stable access point for later,
 * incremental refactoring.</p>
 */
public final class TaskService {

  private static volatile TaskController controller;

  private TaskService() {
  }

  /**
   * Registers the process-wide controller exactly once.
   *
   * @param newController controller created by the existing MZmine 3.9 singleton
   * @return the registered controller
   * @throws NullPointerException if {@code newController} is null
   * @throws IllegalStateException if a controller was already registered
   */
  @NotNull
  public static synchronized TaskController init(@NotNull final TaskController newController) {
    Objects.requireNonNull(newController, "newController");
    if (controller != null) {
      throw new IllegalStateException("Cannot initialize TaskController twice");
    }
    controller = newController;
    return controller;
  }

  /**
   * Returns the registered controller.
   *
   * @throws IllegalStateException if the MZmine core has not initialized the controller yet
   */
  @NotNull
  public static TaskController getController() {
    final TaskController current = controller;
    if (current == null) {
      throw new IllegalStateException("Initialize TaskController first");
    }
    return current;
  }

  /** Clears global state between unit tests. Not part of the production API. */
  static synchronized void resetForTesting() {
    controller = null;
  }
}
