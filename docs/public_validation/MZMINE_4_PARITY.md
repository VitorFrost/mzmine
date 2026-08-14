# MZmine 4.0 LC-MS core parity plan

## Target

The active external target is functional parity with the public LC-MS scientific core of MZmine v4.0.8.

Frozen references:

- public v3.9.0 scientific base: `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- public v4.0.8 oracle: `8029f930d28c0447f0acf2bcabef0a79865ad434`;
- current fork application version: `3.9.1` until all release gates are complete.

`Equivalent` means direct behavioral evidence on governed inputs and explicitly stated settings/scope. It does not mean identical source trees, GUI, Gradle structure, or binary/API compatibility.

Excluded from the current parity claim are proprietary account/licensing/cloud infrastructure, unavailable `io.mzio` implementations, vendor-native readers that cannot be independently distributed, and complete GC-MS/imaging/ion-mobility parity.

ROI-MCR remains a separate fork-local experimental LC-MS track and is never evidence for this Milestone 4 target.

**Current active scientific gate: local-minimum feature resolver.**

## Evidence hierarchy

Scientific claims follow this precedence:

1. retained machine-readable CI artifact/reports;
2. committed machine-readable evidence registry and parity inventories;
3. generated/validated documentation;
4. human summaries and issue/PR prose.

F-046 was introduced after a successful smoothing run was transcribed with incorrect hashes in later prose. The primary artifact remained internally consistent. The committed evidence registry now prevents that later prose from silently becoming the scientific source of truth.

## Established direct v4.0.8 evidence

### 1. mzML import and centroid mass detection — Equivalent

The governed import/centroid gate compares the same public mzML bytes through the exact v4.0.8 scientific slice and open-offline candidate.

Established results:

- 5,221 imported scans and 6,477,349 spectral points;
- 4,081 explicitly selected MS1 scans and 6,450,546 centroid points;
- 1,140 explicitly selected MS2 scans and 26,803 centroid points;
- scan order, membership, MS level, polarity, spectrum type, spectral values, and exercised precursor/isolation/charge metadata equivalent;
- strict differences limited to deterministic binary32 RT representation;
- governed difference count `0` under the frozen RT-only contract;
- no m/z or intensity tolerance relaxation.

Complete evidence: `docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md`.

### 2. ADAP Chromatogram Builder — Equivalent for the governed non-imaging LC-MS path

Governed scope:

- exact `Banane_30ngmL_001.mzML` bytes;
- explicit MS1 source data;
- centroid noise `0.0`;
- exact published MZmine processing settings;
- non-imaging LC-MS;
- candidate and oracle executed in independent fresh JVM/test-worker processes.

Direct result from workflow run `31812225494`:

- 4,081 MS1 scans on both sides;
- published ADAP settings: 8 consecutive scans, 50,000 minimum consecutive-scan intensity, 100,000 minimum absolute height, `0.001 m/z or 5.0 ppm` scan-to-scan tolerance;
- 517 feature/chromatogram records on both sides;
- candidate normalized-record SHA-256 `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle normalized-record SHA-256 identical;
- `records_equal: true`;
- `first_mismatch: null`.

The earlier same-JVM heap exhaustion was a harness lifecycle failure. Process isolation resolved it without changing ADAP production code or broadening scientific tolerances.

Complete evidence: `docs/public_validation/V408_ADAP_CHROMATOGRAM_PARITY.md`.

### 3. Feature smoothing — Equivalent for the published Savitzky-Golay path

The governed headless/scientific smoothing source set is byte-identical between open-offline and exact v4.0.8, including `SmoothingTask`, `SmoothingParameters`, and the Savitzky-Golay implementation/parameters. Source identity remains supporting provenance; the stage was also executed directly.

Accepted direct run `31814860815`, retained artifact `9224679187`:

- candidate ADAP input: 517 records;
- oracle ADAP input: 517 records;
- upstream normalized SHA-256 `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa` on both sides;
- candidate smoothing output: 517 records;
- oracle smoothing output: 517 records;
- candidate normalized-output SHA-256 `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`;
- oracle normalized-output SHA-256 identical;
- `records_equal: true`;
- `first_mismatch: null`;
- no numerical tolerance applied.

The retained artifact ZIP SHA-256 is `676191a22491899832d7426eef2ed2ca1b4ae29fd03c88c38a8a7d7b05fe98c9`.

Machine-readable accepted evidence: `datasets/parity/v408_smoothing_accepted_evidence.json`.

Complete narrative evidence and F-046 erratum: `docs/public_validation/V408_SMOOTHING_PARITY.md`.

## Public 3.x workflow regression baseline

The governed public MZmine 3.4.27 workflow remains the regression/compatibility baseline and executes:

1. mzML import;
2. mass detection;
3. ADAP chromatogram builder;
4. smoothing;
5. local-minimum feature resolver;
6. isotope finder;
7. rows filter;
8. join aligner;
9. multithreaded gap filler;
10. duplicate filter;
11. correlation grouping;
12. legacy CSV export.

The blank + technical-replicate run is deterministic on Ubuntu/Windows and produces 176 final rows and the byte-identical 22,422-byte CSV with SHA-256 `139a02d3305280937783acc1ac730fbb4c719c4e80730c2bf864f68806e89c11`.

This proves robust 3.x compatibility; only stages with direct v4.0.8 differential evidence are promoted to `Equivalent`.

## Current parity matrix

| Area | Capability | State | Evidence / next gate |
|---|---|---|---|
| Import | Governed indexed centroid mzML | **Equivalent** | Direct v4.0.8 record comparison |
| Import | Explicit MS1/MS2 metadata/source selection | **Equivalent** | Direct MS1/MS2 comparison |
| Processing | Centroid mass detection, noise 0.0 | **Equivalent** | Direct per-scan MS1/MS2 comparison |
| Processing | ADAP chromatogram builder, governed non-imaging published-settings path | **Equivalent** | 517 exact normalized records; direct v4.0.8 gate |
| Processing | Published Savitzky-Golay smoothing | **Equivalent** | 517 exact normalized records; accepted artifact-derived SHA `1c8fb4…ec971` |
| Processing | Local-minimum resolver | Adapted | **Next direct gate:** compare boundaries and complete resolved series from accepted smoothing state |
| Processing | Isotope finder | Adapted | Differential after resolved features |
| Processing | Join alignment | Adapted | Blank + two-replicate multi-file differential |
| Processing | Rows filter | Adapted | Compare each rule decision and retained/removed identity |
| Processing | Multithreaded gap filling | Adapted | Compare values/status under 1/2/N threads |
| Processing | Duplicate filter | Adapted | Compare duplicate groups and retained representative |
| Processing | Correlation grouping | Adapted | Requires larger public replicate corpus |
| Processing | Adduct/ion identity | Not implemented as parity evidence | Public workflow required |
| MS2 | Feature-to-MS2 association | Not implemented as parity evidence | Second workflow |
| Annotation | Spectral-library import/matching | Not implemented as parity evidence | Second workflow; scores/ranking/top-N |
| Import | Broader mzML conformance | Not implemented | profile/centroid, positive/mixed, encodings, units, malformed inputs |
| Import | Reproducible Waters→mzML conversion | Not implemented | Freeze source/converter/command/hashes |
| Import | Native Waters RAW | Out of current scope | Separate legal/technical milestone if independently distributable |
| Lifecycle | Memory/cleanup/cancellation/error codes | Not implemented | Release gate |
| Concurrency | 1/2/N-thread determinism | Not implemented as release evidence | Release gate |
| Packaging | Portable Windows/Linux artifacts | Not implemented | Release gate |
| Supply chain | Fully offline clean build | Not implemented | Separate dependency-freezing/SBOM gate |

The machine-readable source of truth is `datasets/parity/mzmine_v408_lcms_core_inventory.json`. Stage-specific retained smoothing evidence is frozen separately in `datasets/parity/v408_smoothing_accepted_evidence.json`.

## Differential methodology

Every stage must:

1. freeze exact v4.0.8 source identity and executed dependency/boundary closure;
2. use identical governed input bytes;
3. use reviewed semantically equivalent parameters;
4. execute oracle and candidate independently;
5. emit normalized intermediate records before downstream filtering can hide differences;
6. retain all strict differences;
7. use only predeclared, field-specific tolerances with independent justification;
8. register every newly discovered runtime/representation/scientific failure as `F-xxx` before correction;
9. promote a capability only after the direct gate passes;
10. derive promoted evidence identifiers from retained machine-readable artifacts rather than manually retyping them into prose.

The ADAP work added an important lifecycle rule: large candidate/oracle feature-processing stages should use independent producer processes when keeping both object graphs alive in one JVM changes memory viability.

## Immediate scientific sequence

### Gate 3A — single-file feature processing

- [x] import;
- [x] centroid mass detection;
- [x] ADAP chromatogram building;
- [x] published Savitzky-Golay smoothing;
- [ ] local-minimum resolver;
- [ ] isotope grouping.

The resolver gate must start from exactly 517 post-smoothing records with artifact-derived normalized SHA-256 `1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971`. Earlier resolver runs that stopped on the superseded `fcf45b…` assertion did not execute the resolver and provide no candidate/oracle behavioral verdict.

### Gate 3B — multi-file processing

After the single-file chain is governed:

- [ ] join alignment on blank + replicate 1 + replicate 2;
- [ ] rows-filter decisions;
- [ ] gap filling under 1/2/N threads;
- [ ] duplicate groups/representatives;
- [ ] normalized final export;
- [ ] correlation grouping on a sufficiently large replicate corpus.

### Gate 4 — second workflow

At least one second public workflow must cover meaningful feature-to-MS2 association and preferably spectral-library matching. MSe/ion identity may be added where exercised or explicitly scoped out with justification.

### Gate 5 — mzML conformance

Cover indexed/non-indexed, compressed/uncompressed, 32-/64-bit arrays, profile/centroid, positive/negative/mixed polarity, alternate RT units, MSn metadata, empty/incomplete scans, and malformed/truncated failure behavior.

### Gate 6 — lifecycle, load, failures, determinism

Validate memory-map/file/temp cleanup, repeated batches, cancellation during import/processing, recovery after module failure, structured exit codes/run reports, performance thresholds, and deterministic 1/2/N-thread behavior.

The ADAP OOM history remains relevant evidence for this gate even though process isolation removed it as a scientific-parity blocker.

### Gate 7 — packaged release candidate

Generate and test portable Windows/Linux artifacts in clean environments; verify GUI/headless startup, spaces/Unicode/non-admin paths, cleanup/logging, checksums, dependency/license inventory, SBOM, and final validation reports.

## Failure-first rule

No observed failure may be silently repaired. A new `F-xxx` record must preserve:

- original evidence;
- impact;
- root-cause hypothesis/confidence;
- scientific/provenance risk;
- proposed correction before implementation;
- validation criteria;
- final resolution without deleting the original observation.

Tolerance broadening is prohibited as a convenience fix.

## Minimum criteria before version 4.0

1. declared LC-MS scope frozen;
2. at least two public workflows pass direct v4.0.8 differential comparison;
3. relevant intermediate/final scientific outputs meet recorded contracts;
4. centroid/profile, positive/negative, and MS2 coverage adequate for declared scope;
5. memory, cleanup, cancellation, and structured error gates pass;
6. one-/two-/N-thread determinism demonstrated where applicable;
7. portable Windows/Linux artifacts pass clean-environment workflows;
8. exact integration-commit CI is green;
9. validation reports, checksums, licenses, and SBOM are published;
10. no proprietary binary, bypass, fake entitlement, or decompiled implementation is present;
11. documentation, issues, and machine-readable inventory are synchronized with the release.
