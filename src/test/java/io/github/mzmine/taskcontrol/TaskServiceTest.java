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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

  private TaskController previousController;

  @BeforeEach
  void isolateServiceState() {
    previousController = TaskService.replaceForTesting(null);
  }

  @AfterEach
  void restoreServiceState() {
    TaskService.replaceForTesting(previousController);
  }

  @Test
  void accessBeforeInitializationFailsClearly() {
    assertThrows(IllegalStateException.class, TaskService::getController);
  }

  @Test
  void initializationRegistersAndReturnsController() {
    final TaskController controller = mock(TaskController.class);

    assertSame(controller, TaskService.init(controller));
    assertSame(controller, TaskService.getController());
  }

  @Test
  void duplicateInitializationIsRejected() {
    final TaskController first = mock(TaskController.class);
    final TaskController second = mock(TaskController.class);

    TaskService.init(first);

    assertThrows(IllegalStateException.class, () -> TaskService.init(second));
    assertSame(first, TaskService.getController());
  }

  @Test
  void nullInitializationIsRejected() {
    assertThrows(NullPointerException.class, () -> TaskService.init(null));
  }
}
