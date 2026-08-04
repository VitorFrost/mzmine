# Phase 2C — task-controller JavaFX and desktop decoupling

## Objective

Keep task scheduling, priority changes, completion, and error propagation usable in headless mode
without initializing a JavaFX application or calling a desktop service.

This phase is a selective Java 20-compatible adaptation of the public MIT-licensed MZmine history,
including commit `001a0c3c672d09a141faa56b42c7c145246f9dd6` (`decouple taskcontroller from
javafx`). It does not copy an `io.mzio` binary or introduce authentication, licensing, telemetry,
virtual threads, or grouped tasks.

## Boundary after this phase

### Controller core

- owns queue scheduling and worker threads;
- updates task status, error text, progress, and controller listeners;
- writes controlled and unhandled errors to `java.util.logging`;
- compacts completed tasks while preserving their final diagnostics;
- does not call `MZmineCore.getDesktop()`, `displayErrorMessage`, `MZmineCore.runLater`, or
  `javafx.application.Platform`.

### GUI/view layer

- owns table refresh and user-facing dialogs;
- already refreshes the task table from `MainWindowController` using its JavaFX timeline;
- may observe `TaskControlListener`, task status listeners, queue contents, and JavaFX properties;
- decides whether and how a logged task error is displayed to a user.

## Error policy

1. A task that returns with `TaskStatus.ERROR` keeps its own message.
2. A missing/blank controlled error message is normalized to `Unspecified error`.
3. An unhandled `Throwable` is logged with a stack trace and converted to `TaskStatus.ERROR`.
4. `AbstractTask` instances receive the error state directly.
5. Direct implementations of `Task` still produce a compact `FinishedTask` with explicit
   `ERROR` status and error text.
6. The original task reference is released after execution, while description, progress, status,
   and error text remain available to headless batch diagnostics and the task table.

## Scope deliberately unchanged

- the legacy MZmine 3.9 scheduler and queue algorithm;
- dynamic thread-count lookup from existing configuration;
- the JavaFX properties retained by `WrappedTask` for compatibility with the current task table;
- controller listeners and task status listeners;
- scientific modules and batch semantics.

## Acceptance gates

- no desktop/JavaFX scheduling tokens in `TaskControllerImpl`, `WorkerThread`, or `WrappedTask`;
- successful task compaction without desktop initialization;
- controlled error preservation and normalization;
- unhandled error propagation for both `AbstractTask` and plain `Task` implementations;
- priority update without `MZmineCore.runLater`;
- Java 20 tests on Linux and Windows;
- no-login headless startup;
- deterministic headless batch;
- equality with untouched MZmine 3.9.0 within the established tolerance;
- independence and public-data-policy audit.
