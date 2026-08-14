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
  JavaFX/Desktop scheduling decoupling, and fork-local deterministic `GroupedTask`;
- exact MZmine v4.0.8 oracle identity frozen at commit
  `8029f930d28c0447f0acf2bcabef0a79865ad434`;
- reviewed public v4.0.8 parser/centroid source closure compiled and executed as an isolated Java 21
  oracle without proprietary `io.mzio` binaries;
- direct v4.0.8 differential comparison completed for the governed indexed-centroid mzML fixture;
- explicit MS1 and MS2 source-data paths compared independently with no manufacturer or
  acquisition-mode inference;
- governed indexed-centroid mzML import and centroid mass detection at noise level `0.0` classified
  **Equivalent** for the tested scope, with strict RT differences retained and zero governed
  differences under the frozen RT-only tolerance contract;
- machine-readable parity inventory synchronized with that first direct result.

### Active scientific gate

The next direct v4.0.8 gate is **ADAP Chromatogram Builder** (issue #50). It starts from the already
validated import/centroid output, freezes the v4.0.8 parameter and source mapping, emits normalized
chromatogram membership/point-series records, and records every new discrepancy through the
failure-first `F-xxx` process before any corrective implementation.

After ADAP, the planned single-file sequence is smoothing, local-minimum resolving, and isotope
grouping, followed by the multi-file alignment/filter/gap-filling/duplicate sequence.

### Still required before a 4.0-derived release

- direct v4.0.8 parity evidence for ADAP and all downstream declared scientific stages;
- broader mzML conformance, including profile, positive, mixed-polarity, binary encoding, and richer
  precursor/MSn metadata;
- a second public workflow with meaningful feature-to-MS2 association and spectral-library matching
  or another explicitly accepted uncovered core capability;
- memory-map lifecycle, cleanup, structured error/exit-code, cancellation, and load benchmarks;
- deterministic one-, two-, and N-thread comparisons where concurrency affects behavior;
- portable Windows and Linux release artifacts tested from clean environments;
- release checksums, dependency/license inventory, SBOM, and synchronized machine/human validation
  reports.

See:

- [`OPEN_OFFLINE_FORK.md`](OPEN_OFFLINE_FORK.md) — project charter and scope;
- [`docs/public_validation/MZMINE_4_PARITY.md`](docs/public_validation/MZMINE_4_PARITY.md) — current
  parity matrix and release gates;
- [`docs/public_validation/DIFFERENTIAL_HARNESS.md`](docs/public_validation/DIFFERENTIAL_HARNESS.md)
  — current oracle/candidate comparison contract and next-stage extension rules;
- [`docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md`](docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md)
  — first direct v4.0.8 behavioral parity evidence, hashes, tolerances, and scope limits;
- [`docs/public_validation/V4_0_8_ORACLE.md`](docs/public_validation/V4_0_8_ORACLE.md) — frozen oracle
  identity, executable public-source slice, and independence boundary;
- [`docs/public_validation/SOURCE_MS_LEVEL_SELECTION.md`](docs/public_validation/SOURCE_MS_LEVEL_SELECTION.md)
  — manual MS1/MS2 source-data selection and its scientific boundary;
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

For a governed differential stage report, the user explicitly selects the original mzML level used
as the source-data stream:

```bash
python scripts/generate_mzmine_stage_report.py \
  --input data/file.mzML \
  --output parity-artifacts/report.json \
  --producer-ref open-offline-main \
  --producer-commit <40-character-commit-sha> \
  --source-ms-level 1
```

Use `--source-ms-level 2` to select MS2. This option filters the source stream for processing; it
does not infer the instrument configuration or relabel scans.

The complete CI additionally compares the frozen scientific baseline with an untouched mzmine 3.9.0
checkout. The dedicated v4.0.8 oracle workflow runs explicit MS1 and MS2 differential jobs and
retains strict plus governed comparison evidence.

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

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the required provenance, failure-first, and validation
process.

## License and attribution

The source is distributed under the MIT license inherited from the public mzmine source tree. Dataset
licenses and attribution requirements are tracked separately in the governed manifests and public
validation documentation.

This is an independent community fork and is not an official mzmine release.
