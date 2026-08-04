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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.taskcontrol.impl;

import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskPriority;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Wrapper class for tasks that stores additional queue information.
 *
 * <p>The wrapper retains JavaFX properties for the existing task table, but it does not schedule
 * work through JavaFX or access desktop services. Callers at the GUI boundary are responsible for
 * invoking priority changes from their appropriate UI thread.</p>
 */
public class WrappedTask {

  private final StringProperty name = new SimpleStringProperty("");
  private final Property<TaskPriority> priority;
  private Task task;
  private volatile WorkerThread assignedTo;

  public WrappedTask(Task task, TaskPriority priority) {
    this.task = task;
    this.priority = new SimpleObjectProperty<>(priority);
  }

  public final String getName() {
    return name.get();
  }

  public final void setName(String value) {
    name.set(value);
  }

  public StringProperty nameProperty() {
    return name;
  }

  /**
   * @return the current priority
   */
  TaskPriority getPriority() {
    return priority.getValue();
  }

  /**
   * Change the model priority directly, without dispatching to JavaFX from the controller core.
   */
  void setPriority(TaskPriority priority) {
    this.priority.setValue(priority);
    final WorkerThread worker = assignedTo;
    if (worker != null) {
      switch (priority) {
        case HIGH -> worker.setPriority(Thread.MAX_PRIORITY);
        case NORMAL -> worker.setPriority(Thread.NORM_PRIORITY);
      }
    }
  }

  public Property<TaskPriority> priorityProperty() {
    return priority;
  }

  /**
   * @return whether this wrapper has already been assigned to a worker
   */
  boolean isAssigned() {
    return assignedTo != null;
  }

  void assignTo(WorkerThread thread) {
    assignedTo = thread;
  }

  /**
   * @return the actual or compact finished task
   */
  public synchronized Task getActualTask() {
    return task;
  }

  @Override
  public synchronized String toString() {
    return task.getTaskDescription();
  }

  synchronized void removeTaskReference() {
    task = new FinishedTask(task);
  }
}
