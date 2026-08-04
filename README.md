# mzmine Open Offline Fork

> Independent, MIT-licensed fork based on the public mzmine 3.9.0 source line.
> Runtime operation is offline, requires no account, and uses no proprietary `io.mzio` binaries.

This project preserves the open scientific-processing core of mzmine 3.9 while selectively adapting
publicly licensed improvements from later history and implementing fork-local capabilities where a
validated scientific requirement exists.

The current application version remains **3.9.1**. The next release target is not a wholesale copy of
modern upstream mzmine. It is a documented and test-backed target:

> **Functional parity with the public LC-MS scientific core of mzmine v4.0.8.**

Parity means equivalent scientific behavior for the declared workflows and inputs within recorded
tolerances. It does not imply identical internal architecture, GUI layout, binary compatibility, or
support for proprietary account, licensing, cloud, or vendor-restricted infrastructure.

## Current status

### Completed

- exact public mzmine 3.9.0 base preserved as the scientific regression reference;
- Java 20 compilation and tests on Linux and Windows;
- no-login headless startup;
- startup telemetry and automatic update checks disabled;
- deterministic mzML import, mass detection, chromatogram building, resolving, alignment, gap
  filling, and legacy CSV export;
- direct-module and XML-batch output compared with an untouched 3.9.0 checkout;
- governed public-data manifests with size, license, URL, and SHA-256 verification;
- frozen public LC-MS sample, technical replicate, analytical blank, and published MZmine 3.4.27
  settings;
- public 12-stage workflow executed successfully on Linux and Windows;
- byte-identical 22,422-byte final CSV with 176 data rows;
- local `TaskService`, synchronous task adapters, bounded Java 20 platform-thread executors,
  JavaFX/Desktop scheduling decoupling, and fork-local deterministic `GroupedTask`.

### In progress

- differential validation against the public mzmine v4.0.8 source line;
- per-module 3.9 → 4.0.8 → open-offline compatibility matrix;
- broader mzML conformance, including profile, positive, mixed-polarity, binary encoding, and richer
  precursor metadata;
- MS2 association, spectral-library matching, ion/adduct identity, and MSe reference workflows;
- memory-map lifecycle, cleanup, error/exit-code, cancellation, and load benchmarks;
- deterministic one-, two-, and N-thread comparisons;
- portable Windows and Linux release artifacts tested from a clean environment.

See:

- [`OPEN_OFFLINE_FORK.md`](OPEN_OFFLINE_FORK.md) — project charter and scope;
- [`docs/public_validation/MZMINE_4_PARITY.md`](docs/public_validation/MZMINE_4_PARITY.md) — current
  parity matrix;
- [`docs/milestones/MILESTONE_4_0_CORE_PARITY.md`](docs/milestones/MILESTONE_4_0_CORE_PARITY.md) —
  active implementation sequence;
- [`docs/public_validation/DATASETS.md`](docs/public_validation/DATASETS.md) — governed public corpus.

## Branch strategy

| Branch | Purpose |
|---|---|
| `master` | Modern upstream mirror/history plus the minimum CI bridge; not the fork integration line |
| `open-offline-main` | Stable integration branch for the independent open-offline fork |
| `agent/*` | Focused implementation and validation branches targeting `open-offline-main` |

The branch is intentionally independent. It is thousands of commits behind the modern `master` by
design; progress is measured by frozen functional parity gates, not by merging the current upstream
tree.

## Building

### Requirements

- JDK 20;
- Git;
- network access for a clean dependency-resolution build unless a complete Gradle cache or future
  frozen offline dependency repository is available.

```bash
./gradlew clean test classes --no-daemon
```

Windows:

```bat
gradlew.bat clean test classes --no-daemon
```

The application runtime is designed to operate without an account or mandatory network access.
A clean source build is not yet guaranteed to resolve all third-party dependencies without network
access; this distinction is tracked as a release gate.

## Validation

Run the standard local checks:

```bash
python scripts/verify_open_offline.py
python scripts/fetch_public_test_data.py --validate
python -m unittest discover -s scripts/tests -p "test_*.py" -v
./gradlew clean test classes --no-daemon
python scripts/smoke_test_headless.py
python scripts/test_headless_batch.py
```

The complete CI additionally compares the frozen scientific baseline with an untouched mzmine 3.9.0
checkout and runs governed public-corpus workflows when requested by their dedicated actions.

## Independence policy

The fork must not include or depend on:

- proprietary `io.mzio` binaries;
- reconstructed or decompiled implementations;
- account, authentication, or license bypasses;
- fake entitlement or “always authenticated” behavior;
- mandatory cloud services for scientific processing.

Where later public source depends on unavailable proprietary services, contributors must either:

1. isolate and omit the unrelated integration;
2. implement a new local open interface with independently defined behavior; or
3. classify the capability as out of scope.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the required provenance and validation process.

## License and attribution

The source is distributed under the MIT license inherited from the public mzmine source tree. Dataset
licenses and attribution requirements are tracked separately in the governed manifests and public
validation documentation.

This is an independent community fork and is not an official mzmine release.
