# Open replacement interface guide

## Purpose

This document replaces the earlier “stub implementation” guidance.

The open-offline fork does **not** recreate proprietary `io.mzio` services through fake users,
“always authenticated” responses, unconditional feature grants, or drop-in API emulation. Those
patterns would blur the independence boundary and could amount to an authentication or entitlement
bypass rather than an open scientific implementation.

When later public source refers to an unavailable proprietary service, contributors must first decide
whether the dependency is scientifically necessary.

## Allowed decisions

### 1. Remove or omit the integration

Use this when the dependency serves:

- account/login UI;
- remote profile management;
- license or entitlement checks;
- telemetry or update services;
- cloud-only workflow integration;
- functionality outside the declared LC-MS core scope.

The scientific workflow should call its local implementation directly rather than pass through a fake
account or entitlement layer.

### 2. Define an original local open interface

Use this only when the scientific or infrastructure workflow needs a legitimate local abstraction.
The interface must:

- have independently documented behavior;
- model the local function actually required by the fork;
- avoid user/authentication/license terminology when those concepts are not needed;
- fail explicitly for unsupported operations;
- make no mandatory network calls;
- have unit and headless tests;
- be described as fork-local unless compatibility is separately demonstrated.

Examples already present in the fork:

- `TaskService` — local access to the open task controller;
- JavaFX/Desktop task refresh at the view boundary rather than inside scheduling code;
- the original fork-local `GroupedTask` for bounded child-task execution.

These are not binary-compatible replacements for proprietary services.

### 3. Classify the capability as out of scope

Use this when:

- behavior cannot be independently specified;
- required code is proprietary or unavailable;
- distribution rights are unclear;
- a vendor SDK cannot be redistributed;
- the capability is not required by the declared scientific target.

The limitation must be visible in documentation and the parity matrix.

## Prohibited patterns

Do not implement:

```java
boolean isAuthenticated() {
  return true;
}
```

Do not create a fake local user that grants every feature.

Do not call a callback named `onLoginSuccess` merely to skip an account gate.

Do not reproduce proprietary package/class signatures solely to satisfy later binaries or source that
was not independently available under a compatible license.

Do not infer proprietary behavior through decompilation, binary probing, or error-driven API cloning.

Do not preserve a feature-gating architecture by making all gates return success. Remove the unrelated
gate from the open scientific path or define a new local capability interface with independent
semantics.

## Decision process

For each unavailable dependency:

1. identify every call site;
2. classify each call as scientific, local infrastructure, GUI convenience, remote integration, or
   account/license behavior;
3. remove account/license/remote-only call sites from the local scientific path;
4. define the minimum local behavior still required;
5. search public source history for a compatible MIT implementation;
6. when no suitable public implementation exists, write a short original design record;
7. add focused tests;
8. run the independence audit and scientific regressions;
9. record the result as Adapted, Not implemented, or Out of scope.

## Local event behavior

A local in-process event bus may be implemented only if a fork workflow genuinely needs event
publication/subscription. It should not be described as a replacement for a proprietary global-event
service unless protocol compatibility is established from public specifications.

Required design questions:

- Which local event types are needed?
- Are handlers synchronous or asynchronous?
- What is the ordering policy?
- How are handler exceptions propagated?
- How are subscriptions removed?
- Is JavaFX-thread marshalling a publisher or view responsibility?
- How is headless behavior tested?

Do not add a generic event bus preemptively.

## Memory management

The fork retains the public MIT memory-mapping implementation from the 3.9 source line. The correct
strategy is not to emulate a proprietary memory-management API. Instead:

- use the open `MemoryMapStorage` and related public data model directly;
- add lifecycle tests for close, cleanup, failure, cancellation, and repeated batches;
- implement new local interfaces only where the scientific workflow requires a stable abstraction;
- classify differences from v4.0.8 as Adapted unless direct parity is demonstrated.

Memory lifecycle and cleanup remain active release gates.

## Task controller

The task-controller milestone is complete through open local code:

- local `TaskService`;
- synchronous task adapters;
- Java 20 bounded executors;
- JavaFX/Desktop decoupling;
- fork-local `GroupedTask`.

Do not attempt to recreate proprietary task-controller artifacts or package signatures. See
[`TASKCONTROLLER_PORTING_PLAN.md`](../TASKCONTROLLER_PORTING_PLAN.md) and the Phase 2D design record.

## Dependency exclusion

The prohibited Maven coordinates must remain absent from source and resolved dependencies:

```text
io.mzio:user-client
io.mzio:user-management
io.mzio:user-management-fx
io.mzio:global-events
io.mzio:memory-management
io.mzio:mzmine-core
io.mzio:taskcontroller
```

The repository audit should check:

- build files and dependency reports;
- source imports and string references;
- committed JARs and archives;
- generated package contents where applicable.

A literal documentation reference to a prohibited artifact may be allowed by the audit only when it is
clearly part of the denylist or provenance documentation, not an executable dependency.

## Design record template

Before implementing a new local interface, document:

| Field | Required content |
|---|---|
| Problem | Local scientific/infrastructure need |
| Why omission is insufficient | Concrete workflow requirement |
| Public source search | Tags/commits/specifications reviewed |
| Independence statement | No proprietary binary/decompilation/auth bypass |
| API boundary | Classes and methods owned by the fork |
| Semantics | Success, error, cancellation, concurrency, ordering |
| Non-goals | Features intentionally excluded |
| Tests | Unit, headless, cross-platform, scientific regression |
| Parity classification | Adapted / Not implemented / Out of scope |

## Verification checklist

- [ ] no prohibited executable dependency or bundled JAR;
- [ ] no fake authenticated user or unconditional entitlement;
- [ ] no account/license gate remains in the local scientific path;
- [ ] fork-local behavior is independently documented;
- [ ] unsupported behavior fails explicitly;
- [ ] headless operation requires no account or mandatory network access;
- [ ] Linux and Windows tests pass;
- [ ] untouched 3.9 scientific regression remains valid;
- [ ] parity documentation records the adaptation or scope decision.
