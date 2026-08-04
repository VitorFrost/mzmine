# TaskController selective port record

## Status

**Completed for the declared Java 20 open-offline scope.**

The task-controller work was implemented in four reviewed phases while preserving the mzmine 3.9
global scheduler, deterministic scientific output, and independence from proprietary services.

This file now records what was implemented and what remains intentionally outside the milestone. The
active roadmap has moved to functional LC-MS parity with public mzmine v4.0.8.

## Original decision

The later task-controller history was not cherry-picked wholesale into the monolithic mzmine 3.9
base. Public MIT-licensed commits were used selectively because the later code assumed:

- a partially modularized source tree;
- Java 21 features in some intermediate states;
- interfaces and infrastructure not present in the open-offline fork;
- unrelated GUI, importer, and service changes.

The implementation therefore retained Java 20, adapted only the required public ideas, and introduced
one original fork-local grouped-task capability with independently documented semantics.

## Public provenance

| Public reference | Purpose used by the fork | Result |
|---|---|---|
| `7ec9902b5f283f0607f6983192f756decf417d1f` | Early synchronous/controller direction | Inspected; not copied wholesale because it represented an intermediate API state |
| PR `#1628`, merge `ae38a04744a8c3e3a240b84c0c6acc0aa06db467` | Thread-pool/grouped-task design context | Design reference only; no broad TaskView/MVCI port |
| `9977c66c754c04f8b06572787aa6708ad210519f` | Controller service and executor context | Selectively adapted for Java 20 |
| `001a0c3c672d09a141faa56b42c7c145246f9dd6` | Synchronous execution and JavaFX decoupling | Selectively adapted to the monolithic tree |
| `25f4bc146a8869cd382861183a0ae8be0df7ad32` | Correct one-time service initialization behavior | Included in local facade behavior |
| `7ad3c265dcf71e4844518cda171f243a28718921` | Listener/subtask error-propagation context | Used as review context; fork behavior covered by dedicated tests |

All adapted source retains compatible licensing and records local modifications in its PR history.

## Completed phases

### Phase 0 — deterministic baseline

Completed before controller behavior was changed:

- Java 20 Linux and Windows tests;
- independence audit;
- no-login headless startup;
- deterministic synthetic LC-MS processing;
- deterministic XML batch execution;
- comparison with an untouched mzmine 3.9.0 checkout.

### Phase 1 / 2A — local service and synchronous adapters

Implemented:

- local `TaskService` with explicit initialization and access;
- `getSubmittedTaskQueue()` alias for the existing queue;
- `runTaskOnThisThreadBlocking(Task)`;
- deterministic sequential blocking execution for multiple tasks;
- existing asynchronous `addTask` / `addTasks` behavior preserved;
- duplicate-execution race prevented by assigning the blocking wrapper before queue exposure.

Validated:

- access before initialization;
- valid initialization and duplicate registration;
- empty, null, single, and multiple task input;
- ordering;
- success, error, and cancellation propagation;
- at-most-once execution.

Implementation PR: `#13`.

### Phase 2B — bounded Java 20 executors

Implemented:

- fixed-size platform-thread executor factory;
- bounded cached high-priority platform-thread executor factory;
- deterministic worker names;
- explicit normal/high-priority policy;
- daemon/non-daemon policy;
- strict saturation rejection;
- shutdown and termination behavior.

Explicitly excluded:

- virtual threads;
- Java 21 string templates or syntax;
- replacement of the legacy global scheduler.

Implementation PR: `#23`.

### Phase 2C — JavaFX/Desktop decoupling

Implemented:

- removal of direct desktop refresh calls from `TaskControllerImpl`;
- task-table refresh retained at the GUI/view boundary;
- removal of `MZmineCore.runLater` from wrapped-task priority changes;
- removal of desktop error dialogs from worker execution;
- controlled and unhandled failures preserved through status, error text, logs, and compact
  `FinishedTask` records;
- coverage for `AbstractTask` and direct `Task` implementations;
- source-boundary regression tests against GUI scheduling dependencies.

Implementation PR: `#24`.

### Phase 2D — original fork-local `GroupedTask`

Implemented as an original capability, not an upstream compatibility layer:

- one parent `Task` owning an immutable ordered child list;
- strict configurable concurrency ceiling using Java 20 platform threads;
- at-most-once parent execution;
- equal-weight deterministic arithmetic-mean progress;
- terminal child states treated as completed work for progress;
- independent children allowed to settle after another child fails;
- multiple errors aggregated in child-index order;
- unhandled child exceptions converted to explicit diagnostics;
- non-terminal child returns rejected;
- parent cancellation propagated to children, futures, and executor shutdown;
- atomic per-child cancellation guard;
- cancel-before-run prevents child execution;
- no JavaFX, Desktop, account, license, telemetry, or `io.mzio` dependency.

The first full cancellation test exposed duplicate child cancellation signals. The failure reproduced on
Linux and Windows and was corrected with an atomic per-child guard plus immediate canceled-result
recording. Dedicated cancel-before-run coverage was added.

Design record: `docs/milestones/PHASE_2D_GROUPED_TASK.md`.
Implementation PR: `#25`.

## Final acceptance result

The completed milestone passed:

1. independence and prohibited-dependency audit;
2. public-data fail-closed policy;
3. Java 20 Ubuntu tests;
4. Java 20 Windows tests;
5. focused task-controller and grouped-task tests;
6. no-login headless startup;
7. deterministic headless scientific batch;
8. equality with untouched mzmine 3.9.0;
9. no proprietary dependency or reconstructed account/license behavior.

## Current architectural classification

The task layer is **Adapted**, not “Equivalent” in the binary/API sense.

Reasons:

- the 3.9 global scheduler remains intentionally preserved;
- the fork uses Java 20 platform threads rather than later Java 21 facilities;
- `GroupedTask` is a fork-local API with independently defined semantics;
- no source or binary compatibility promise is made for proprietary or unavailable later components.

This classification is sufficient for the declared LC-MS scientific objective because behavior is
explicit, deterministic, headless-capable, and tested.

## Remaining task-related work belongs to later milestones

The following are not unfinished parts of this port; they are separate integration or release gates:

- adopt `GroupedTask` in a scientific module only after a workflow demonstrates need;
- run cancellation and recovery under realistic file-processing load;
- validate one-, two-, and N-thread scientific determinism;
- verify no abandoned threads or mapped files after repeated batches;
- define CLI exit codes and structured run reports;
- test packaged GUI/headless execution from clean Windows and Linux artifacts;
- evaluate Java 21 only in a dedicated future migration milestone.

## Deliberate non-goals

- observable/MVCI TaskView replacement;
- broad GUI task-manager redesign;
- modular Gradle extraction;
- virtual-thread migration;
- global scheduler replacement without a measured scientific need;
- account, authentication, licensing, or feature-entitlement behavior;
- reconstruction of unavailable `io.mzio` task-controller APIs.
