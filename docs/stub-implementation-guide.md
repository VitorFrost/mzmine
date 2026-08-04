# Stub Implementation Guide

This document details how to implement the open-source stub classes that replace the closed `io.mzio` binary JARs in the open-offline fork.

Each stub corresponds to a prohibited Maven artifact listed in `OPEN_OFFLINE_FORK.md`.

---

## 1. `io.mzio:user-client` → `UserService` stub

**Prohibited artifact:** `local-repo/io/mzio/user-client/1.0.0/user-client-1.0.0.jar`

This JAR handles authentication against the mzio cloud backend. In the offline fork it is replaced by a local stub that always returns a fully-authorized anonymous user.

### Interface to implement

Create the stub at:
`src/main/java/io/github/mzmine/users/offline/OfflineUserService.java`

```java
/*
 * Copyright (c) 2004-2024 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software...
 *
 * (MIT License — full text at LICENSE.txt)
 */
package io.github.mzmine.users.offline;

/**
 * Offline stub for io.mzio:user-client.
 * Always returns a locally-authenticated user. Never contacts any server.
 * Drop-in replacement for any call site that previously called
 * CurrentUserService.getUser() or similar io.mzio APIs.
 */
public final class OfflineUserService {

  private static final OfflineUser LOCAL_USER = new OfflineUser("local", "Local User");

  private OfflineUserService() {}

  /** Always returns the local offline user. Never null. */
  public static OfflineUser getUser() {
    return LOCAL_USER;
  }

  /** Always returns true — the offline user is always authenticated. */
  public static boolean isAuthenticated() {
    return true;
  }

  /** No-op. There is no remote session to log into. */
  public static void login() {
    // intentionally empty — offline mode
  }

  /** No-op. There is no remote session to log out of. */
  public static void logout() {
    // intentionally empty — offline mode
  }
}
```

### OfflineUser record

```java
package io.github.mzmine.users.offline;

/**
 * Immutable local user representation. Replaces the io.mzio User/Account model.
 */
public record OfflineUser(String username, String displayName) {
  public boolean hasFeature(String featureKey) {
    // Offline fork grants all features unconditionally.
    return true;
  }
}
```

---

## 2. `io.mzio:user-management-fx` → UI stub

**Prohibited artifact:** `local-repo/io/mzio/user-management-fx/`

This module provided the JavaFX login dialog. Replace with a no-op UI component that skips the login screen entirely.

Create at: `src/main/java/io/github/mzmine/users/offline/OfflineLoginController.java`

```java
package io.github.mzmine.users.offline;

/**
 * Stub for io.mzio:user-management-fx login controller.
 * Immediately signals successful login without showing any dialog.
 */
public final class OfflineLoginController {

  /** Called by the application startup. Skip login, proceed directly. */
  public static void showLoginOrProceed(Runnable onSuccess) {
    // Offline fork: no login required — proceed immediately.
    onSuccess.run();
  }
}
```

---

## 3. `io.mzio:global-events` → GlobalEventsStub

**Prohibited artifact:** `local-repo/io/mzio/global-events/`

This module provided a publish/subscribe event bus integrated with the mzio backend. Replace with a simple in-process bus.

```java
package io.github.mzmine.events;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Offline stub for io.mzio:global-events.
 * Pure in-process event bus — no network, no external serialization.
 */
public final class LocalEventBus {

  private static final Map<Class<?>, Set<Consumer<Object>>> subscribers =
      new ConcurrentHashMap<>();

  private LocalEventBus() {}

  @SuppressWarnings("unchecked")
  public static <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
    subscribers
        .computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet())
        .add((Consumer<Object>) handler);
  }

  public static <T> void publish(T event) {
    Set<Consumer<Object>> handlers = subscribers.get(event.getClass());
    if (handlers != null) {
      handlers.forEach(h -> h.accept(event));
    }
  }
}
```

---

## 4. `io.mzio:memory-management` → MemoryMapStorage delegation

**Strategy:** The MIT-licensed `MemoryMapStorage` class from mzmine 3.9.0 already implements all required memory mapping functionality. Simply ensure the `build.gradle` does **not** declare `io.mzio:memory-management` as a dependency, and that all import sites reference `io.github.mzmine.datamodel.impl.MemoryMapStorage` directly.

---

## Gradle dependency exclusion

In `build.gradle`, ensure these dependencies are **absent**:

```groovy
// These MUST NOT appear in any dependency block:
// implementation 'io.mzio:user-client:*'
// implementation 'io.mzio:user-management:*'
// implementation 'io.mzio:user-management-fx:*'
// implementation 'io.mzio:global-events:*'
// implementation 'io.mzio:memory-management:*'
// implementation 'io.mzio:mzmine-core:*'
// implementation 'io.mzio:taskcontroller:*'
```

To guard against accidental re-introduction, add a Gradle build check:

```groovy
configurations.all {
  resolutionStrategy {
    eachDependency { details ->
      if (details.requested.group == 'io.mzio') {
        throw new GradleException(
          "Prohibited dependency: ${details.requested.group}:${details.requested.name}. "
          + "This is the open-offline fork — all io.mzio artifacts are forbidden."
        )
      }
    }
  }
}
```

---

## Verification checklist

After implementing stubs:

- [ ] `git grep -rn "io.mzio"` returns no results in `src/`
- [ ] `./gradlew dependencies | grep io.mzio` returns no results
- [ ] Application starts without network connection
- [ ] No login dialog appears at startup
- [ ] All mass spectrometry modules function normally
- [ ] Headless batch mode runs without user token
