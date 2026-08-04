# Phase 2D — fork-local grouped task

## Status and intent

Phase 2D is an original extension of the open-offline fork. It is not intended as a compatibility
layer for a future merge into the upstream MZmine repository, and it does not attempt to reproduce
an unpublished or binary-only implementation.

The design exists to support future scientific modules that need one parent task to coordinate a
bounded set of independent child tasks while retaining deterministic progress, cancellation, and
error reporting in headless mode.

## Public API boundary

The fork introduces a concrete `GroupedTask` in the public `taskcontrol` package. The grouped task:

- is itself one ordinary `Task` and can be submitted to the existing controller;
- owns an immutable ordered list of child tasks;
- owns a strict maximum concurrency configured at construction;
- uses the fork-local Java 20 platform-thread executor factory from Phase 2B;
- does not submit children back into the global task queue;
- has no JavaFX, desktop, authentication, licensing, telemetry, or `io.mzio` dependency.

The class name, constructors, error text, and scheduling semantics are fork-local API. No source or
binary compatibility with an upstream grouped-task implementation is promised.

## Execution semantics

1. The parent transitions from `WAITING` to `PROCESSING` once, and executes at most once.
2. Child order is the immutable construction order and is used for deterministic diagnostics.
3. At most `maximumConcurrency` child tasks run simultaneously.
4. Child tasks are independent: a child error does not race-cancel unrelated children. All children
   are allowed to reach a terminal result unless the parent is explicitly canceled.
5. Final status precedence is deterministic:
   - `ERROR` when one or more children fail or return a non-terminal status;
   - otherwise `CANCELED` when the parent was canceled or any child ended canceled;
   - otherwise `FINISHED`.
6. Multiple child errors are aggregated in child-index order, not completion order.
7. Missing child error text is normalized to `Unspecified error`.
8. An unhandled child `Throwable` is converted into a child result with `ERROR` status. When the
   child extends `AbstractTask`, its status and error text are updated as well.
9. A child that returns in `WAITING` or `PROCESSING` is treated as an error because the grouped task
   requires terminal child results.
10. The executor is always shut down and awaited before the parent returns.

## Progress semantics

- Every child has equal weight.
- Before a child is terminal, its contribution is its reported percentage clamped to `[0, 1]`.
- A child in any terminal state contributes `1`, because its share of group work is complete even
  when the result is canceled or erroneous.
- The parent percentage is the arithmetic mean in construction order.
- An empty group reports `0` while waiting and `1` after successful execution.

This policy makes progress independent of thread completion order and prevents failed children from
leaving the parent permanently below 100% after the group has terminated.

## Cancellation semantics

Calling `cancel()` on the parent:

- records a cancellation request atomically;
- transitions the parent to `CANCELED`;
- calls `cancel()` on every child;
- cancels submitted futures with interruption enabled;
- calls `shutdownNow()` on the owned executor;
- prevents pending children from starting where the executor can still stop them.

A child cancellation that was not initiated by the parent does not automatically interrupt other
children. It is reflected in the final parent status after all non-parent-canceled work settles.

## Deliberate non-goals

- no dependency graph or child-to-child prerequisites;
- no dynamic child insertion after construction;
- no weighted progress in the first implementation;
- no nested-group-specific optimization;
- no retry policy;
- no global scheduler replacement;
- no GUI workflow or dashboard;
- no claim of upstream compatibility.

These capabilities can be added later only when a validated scientific workflow requires them.

## Acceptance gates

- constructor validation and immutable child order;
- at-most-once parent execution;
- strict concurrency ceiling and deterministic platform-thread names;
- deterministic aggregate progress;
- successful empty and non-empty groups;
- controlled child error propagation;
- unhandled `Throwable` propagation for `AbstractTask` and direct `Task` children;
- deterministic multi-error aggregation;
- child non-terminal result detection;
- parent cancellation propagation to children, futures, and executor;
- no JavaFX/Desktop source dependency;
- Java 20 tests on Ubuntu and Windows;
- no-login headless startup;
- deterministic scientific batch regression;
- equality with untouched MZmine 3.9.0;
- independence and public-data fail-closed audits.
