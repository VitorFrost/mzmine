# Contributing to mzmine Open Offline Fork

Thank you for contributing to this community fork. This document explains our standards for keeping the fork clean, reproducible, and fully open-source.

## Core rules

1. **No `io.mzio` dependencies.** Never add any dependency on `io.mzio:*` artifacts — binary or source.
2. **No login gates.** Any code path that checks authentication, requires an account, or contacts a remote server for authorization must be replaced with a local no-op stub.
3. **No closed binaries in the repository.** Do not commit `.jar` files from `io.mzio`. The `local-repo/io/mzio/` directory must not exist in `open-offline-main`.
4. **MIT license headers.** Every Java file must carry the MIT license header. Use the IntelliJ template in `license_header_intellij.xml`.

---

## How to port a commit from upstream (mzmine 4.x)

Before porting any file or commit from upstream:

1. Confirm the file carries the MIT license header in the upstream commit.
2. Record the upstream commit SHA, original path, and any modifications in the [Porting Record](#porting-record-template) section of `OPEN_OFFLINE_FORK.md`.
3. Remove any `import io.mzio.*` statements from the ported file.
4. Replace any usage of `io.mzio` services with their stub equivalents (see [Stub API](#stub-api) below).
5. Add or update tests for the ported functionality.

---

## Stub API

The following stub modules replace the closed `io.mzio` binaries. They always behave as if a local, anonymous user is logged in with full access — no network call is ever made.

### `UserService` (replaces `io.mzio:user-client` and `io.mzio:user-management`)

Stub location: `src/main/java/io/github/mzmine/users/`

```java
// Example usage — replaces CurrentUserService.getUser()
UserService.getLocalUser(); // returns a LocalOfflineUser instance, always non-null

// Example usage — replaces AuthRequiredEvent checks
UserService.isAuthenticated(); // always returns true in the offline fork
```

The stub must never:
- make a network call;
- throw an exception when offline;
- block the UI thread waiting for a server response.

### `GlobalEventsStub` (replaces `io.mzio:global-events`)

A no-op implementation of the global event bus. All `publish()` calls succeed silently. All `subscribe()` calls register but are never invoked by system events.

### `MemoryManagementStub` (replaces `io.mzio:memory-management`)

Delegates directly to the MIT-licensed `MemoryMapStorage` already present in the 3.9.0 codebase.

---

## Branch workflow

```
upstream mzmine 3.9.0 ──► open-offline-main  (stable)
                                  ▲
         agent/open-offline-base ─┤
         agent/taskcontroller-*  ─┤
         agent/synthetic-*       ─┘
```

- **All feature work** happens in `agent/*` branches.
- **Pull requests** target `open-offline-main`.
- **`master`** is never used for feature work — it only carries the CI bridge to upstream.
- Squash commits are preferred to keep `open-offline-main` history readable.

---

## CI requirements

Every PR targeting `open-offline-main` must pass:

1. `./gradlew clean build` — zero compilation errors.
2. `./gradlew test` — all tests green.
3. `git grep -E "io\.mzio|AuthRequiredEvent|CurrentUserService"` — **must produce no output**.
4. `./gradlew dependencies | grep -E "io\.mzio"` — **must produce no output**.

If a PR introduces any `io.mzio` reference, it will be rejected regardless of functionality.

---

## Porting record template

When porting a file from upstream, add an entry to the table in `OPEN_OFFLINE_FORK.md`:

| Field | Value |
|---|---|
| Upstream commit SHA | e.g. `abc1234` |
| Original path | e.g. `mzmine-community/src/main/java/...` |
| License header present | Yes / No |
| Modifications made | Brief description |
| Tests added | Yes / No — test class name |
| `io.mzio` references removed | List removed imports |

---

## Questions

Open an issue on this repository. Do not report open-offline-fork issues to the upstream mzmine project — they are separate codebases.
