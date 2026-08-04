# mzmine — Open Offline Fork

> **This is an independent community fork of [mzmine](https://github.com/mzmine/mzmine).**
> It runs entirely offline, requires no account or login, and depends on **no proprietary `io.mzio` binaries**.
> The fork goal is to restore the fully open-source experience of mzmine 3.9 while tracking scientific improvements from the 4.x lineage.

[![Open Offline Build](https://img.shields.io/badge/build-open--offline-brightgreen)](https://github.com/VitorFrost/mzmine/tree/open-offline-main)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.txt)
[![Fork base](https://img.shields.io/badge/upstream-mzmine%203.9.0-orange)](https://github.com/mzmine/mzmine/releases/tag/v3.9.0)

mzmine is an open-source software for mass spectrometry data processing, covering LC-MS, GC-MS, IMS, and MS imaging workflows.

More information about the original project: [mzmine.github.io](http://mzmine.github.io)

---

## Why this fork?

Starting from mzmine 4.0, the upstream project introduced:

- mandatory **mzio account login** before running the application;
- closed-source proprietary JARs (`io.mzio:user-client`, `io.mzio:user-management`, etc.) shipped as local Maven binaries;
- automatic network telemetry and update checks.

This fork removes all of the above, returning mzmine to its roots as a zero-dependency, fully open-source, offline-capable research tool accessible to any laboratory worldwide — including those without internet access or institutional accounts.

See [OPEN_OFFLINE_FORK.md](OPEN_OFFLINE_FORK.md) for the full technical charter and porting record.

---

## Branch strategy

| Branch | Purpose |
|---|---|
| `master` | Tracks upstream mzmine for CI bridging only |
| `open-offline-main` | **Stable integration branch** — this is what you want |
| `agent/open-offline-base` | Current changes under active validation |
| `agent/*` | Individual feature/decoupling work in progress |

---

## License

mzmine source code is distributed under the [MIT license](LICENSE.txt). This fork inherits and preserves that license. No proprietary code is included.

---

## Building

### Requirements

- Java Development Kit (JDK) **20 or newer** — [jdk.java.net](http://jdk.java.net)
- No account, no internet connection required at build or runtime.

### Build

```bash
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

The distribution will be placed in `build/jpackage`.

### Verify no proprietary dependencies

Before any release, run:

```bash
# Check source for any remaining io.mzio references
git grep -n -E "io\.mzio|AuthRequiredEvent|CurrentUserService|UserAuthStore|LicenseUtils"

# Check dependency graph
./gradlew dependencies | grep -E "io\.mzio"
```

Both commands should produce **no output** on a clean build.

### Run tests

```bash
./gradlew test
```

---

## Milestone status

- [x] Fork created from exact mzmine 3.9.0 commit
- [x] Source/JAR audit for all prohibited `io.mzio` components
- [x] CI added for Linux and Windows (Java 20)
- [x] Google Analytics telemetry replaced with inert facade
- [x] Automatic network update checks disabled
- [ ] Gradle build completes with zero `io.mzio` dependencies
- [ ] GUI starts without network access
- [ ] Headless batch execution
- [ ] mzML import and deterministic reference output
- [ ] `user-client` stub fully replaces closed binary
- [ ] TaskController fully decoupled from desktop/JavaFX services

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on porting commits from upstream, implementing stubs, and submitting pull requests.
