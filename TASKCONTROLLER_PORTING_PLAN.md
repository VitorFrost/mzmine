# TaskController selective port plan

## Decision

Do not cherry-pick the 2024 TaskController work directly into the MZmine 3.9 base.

The public commits are useful as MIT-licensed design and implementation history, but they already
assume a partially modularized codebase and, in places, Java 21 features. The initial open-offline
branch deliberately remains on the MZmine 3.9 Java 20 toolchain until the baseline build, headless
startup, and mzML processing tests are stable.

## Public provenance

| Order | Commit | Purpose | Initial decision |
|---|---|---|---|
| 1 | `7ec9902b5f283f0607f6983192f756decf417d1f` | Start new task controller, add synchronous task execution and internal thread-pool tasks | Port selected controller APIs only; do not cherry-pick broad GUI, batch, and importer changes |
| 2 | PR `#1628`, merge `ae38a04744a8c3e3a240b84c0c6acc0aa06db467` | Thread-pool controller and observable task view | Use as design reference; defer TaskView/MVCI refactor |
| 3 | `9977c66c754c04f8b06572787aa6708ad210519f` | Enforce single TaskController initialization | Port only together with the correction below |
| 4 | `001a0c3c672d09a141faa56b42c7c145246f9dd6` | Decouple task controller from JavaFX | High-priority architectural goal; adapt to the monolithic 3.9 source tree |
| 5 | `25f4bc146a8869cd382861183a0ae8be0df7ad32` | Correct missing return in `TaskService.init` | Mandatory if the initialization service is ported |
| 6 | `7ad3c265dcf71e4844518cda171f243a28718921` | Fix task-listener threading and subtask error propagation | Port after the controller API is stable |

All listed code must retain its original MIT license header and provenance in the commit message.

## Compatibility constraints

### Java version

The first new-controller commit contains Java 21-specific constructs, including virtual-thread
executors and string templates. These cannot be copied unchanged into the current Java 20 build.

For the first milestone:

- keep Java 20;
- use fixed/cached platform-thread executors;
- avoid `Executors.newVirtualThreadPerTaskExecutor()`;
- replace `STR.` string templates with ordinary concatenation or formatting;
- evaluate Java 21 only in a separate branch after deterministic LC-MS processing is established.

### Source-tree architecture

MZmine 3.9 is largely monolithic. The later commits refer to separate projects such as
`:taskcontroller`, `:memory-management`, `:utils`, and `:javafx-framework`.

The first port should preserve the existing package names inside the monolithic source tree. Module
extraction is a later refactor and must not be combined with behavior changes.

### GUI separation

The controller must not depend on JavaFX for scheduling or state transitions. GUI updates should
subscribe to controller state and marshal changes onto the JavaFX thread at the view boundary.

Headless processing must remain fully usable without initializing JavaFX.

## Phased implementation

### Phase 0 — baseline

Required before controller changes:

- Linux and Windows build pass on Java 20;
- independence audit passes;
- `--version` headless startup smoke test passes;
- no startup telemetry, update check, login, or license validation occurs.

### Phase 1 — local service facade

Add a small `TaskService` facade in the existing `io.github.mzmine.taskcontrol` package:

- explicit one-time initialization;
- `getController()` fails clearly before initialization;
- no user, license, feature, or authorization service;
- unit tests for first initialization, duplicate initialization, and access before initialization.

Do not change batch execution behavior in this phase.

### Phase 2 — controller API adapters

Add selected APIs while preserving existing behavior:

- `getSubmittedTaskQueue()` as a clearer alias for the existing queue;
- synchronous `runTaskOnThisThread(...)` for deterministic batch orchestration;
- fixed and cached executor factories using platform threads;
- explicit cancellation and exception propagation;
- no virtual threads yet.

Add tests for:

- priority ordering;
- cancellation;
- exception propagation;
- task status transitions;
- controller shutdown;
- deterministic completion of a fixed task set.

### Phase 3 — JavaFX decoupling

Adapt the intent of commit `001a0c3...`:

- controller and wrapped tasks must not import GUI services or JavaFX thread helpers;
- errors are recorded and logged in the task model;
- the GUI observes task changes and decides how to display them;
- headless errors remain available through logs and exit status.

### Phase 4 — batch integration

Only after controller tests pass:

- run batch-step subtasks through the synchronous controller adapter;
- preserve the existing MZmine 3.9 batch XML format;
- compare outputs against the unmodified 3.9 baseline;
- reject any change in feature count, m/z, retention time, area, height, or exported values unless
  explicitly explained and approved.

### Phase 5 — optional grouped thread-pool task

Introduce a platform-thread-only grouped task inspired by PR `#1628`:

- one parent task represents many subtasks;
- progress equals completed subtasks divided by total subtasks;
- parent cancellation cancels pending children;
- child error propagates to the parent;
- executor always shuts down cleanly.

A Java 21 virtual-thread implementation, if desired, belongs in a later optional module.

## Explicitly deferred work

- observable/MVCI TaskView replacement;
- broad GUI task-manager redesign;
- batch timing/log formatting changes unrelated to correctness;
- importer refactors bundled into PR `#1628`;
- Java 21 migration;
- modular Gradle project extraction;
- any account, authentication, licensing, or feature-entitlement behavior.

## Acceptance criteria

The controller port is acceptable only when:

1. the open-offline audit still passes;
2. Linux and Windows CI pass;
3. headless startup passes without network or authentication output;
4. a reference mzML batch produces deterministic output;
5. output matches the MZmine 3.9 baseline within explicitly defined tolerances;
6. every ported source change records its upstream MIT commit;
7. no proprietary JAR or decompiled implementation is used.
