# MZmine 4.0 LC-MS core parity plan

## Target definition

The active external target is the public scientific behavior of the mzmine 4.0 release line:

- initial reference: `v4.0.3`;
- maintenance checkpoint and release oracle: `v4.0.8`;
- fork base: public mzmine `v3.9.0`, commit
  `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- current fork application version: `3.9.1`.

The project targets **functional parity with the public LC-MS scientific core of v4.0.8**.

“Equivalent” means that the fork can execute the same declared scientific purpose on the same
reviewed public inputs and produce equivalent structured intermediate and final results within
explicit tolerances. It does not require identical classes, source-tree layout, GUI, Gradle modules,
or binary compatibility.

The following are outside the parity target:

- account creation, authentication, Keycloak, user profiles, or entitlement behavior;
- license validation or commercial feature infrastructure;
- proprietary `io.mzio` binaries or reconstructed implementations;
- cloud services and mandatory network dependencies;
- vendor-restricted readers that cannot be independently distributed;
- complete GC-MS, imaging, or ion-mobility parity unless later added to the declared scope.

## Evidence vocabulary

Each capability has one of four states:

- **Equivalent** — direct differential evidence against the frozen 4.0.8 oracle or an accepted public
  reference demonstrates equivalent relevant behavior.
- **Adapted** — the same analytical purpose is implemented through a documented open interface or
  fork-local design, with its differences and tests recorded.
- **Not implemented** — the capability is relevant to the declared LC-MS core but lacks adequate
  implementation or evidence.
- **Out of scope** — the capability is proprietary, vendor-restricted, network-only, or outside the
  declared LC-MS target.

A source class being present is not evidence of equivalence. A complete workflow finishing without
error is not sufficient when differences may be hidden by later filtering. Parity evidence must
include intermediate scientific outputs where practical.

## Current verified baseline

### Governed v4.0.8 import and centroid differential

The fork now has direct behavioral evidence against the exact public v4.0.8 commit for both
explicitly selected source-data paths:

- all 5,221 scans and 6,477,349 imported spectral points compared record by record;
- 4,081 MS1 records and 6,450,546 MS1 centroid points compared;
- 1,140 MS2 records and 26,803 MS2 centroid points compared;
- scan order, membership, MS level, polarity, spectrum type, point count, m/z, intensity, sampled
  points, and precursor/isolation/charge metadata equivalent;
- strict reports retain the deterministic binary32 retention-time representation differences;
- governed reports contain zero differences under a frozen RT-only binary32 tolerance contract;
- m/z and intensity tolerances were not relaxed;
- Linux, Windows, headless startup, deterministic batch, independence, and untouched-3.9 gates pass.

Manual workflow dispatch runs only the MS1 or MS2 level selected by the user. Push and pull-request
CI use two explicit jobs to validate both user-selectable paths. No manufacturer or acquisition-mode
inference is performed.

Complete evidence, hashes, failure history, and scope limits are recorded in
`docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md`.

### Scientific baseline

The fork has deterministic tests for:

- indexed mzML import;
- centroid mass detection;
- ADAP chromatogram building;
- local-minimum feature resolving;
- join alignment;
- single-thread peak-finder gap filling;
- legacy CSV export;
- direct-module and XML-batch execution;
- equality with an untouched mzmine 3.9.0 checkout for a frozen synthetic workflow.

### Public real-data workflow

The governed public workflow uses a CC BY 4.0 Zenodo study and the published MZmine 3.4.27 settings.
The executed batch contains:

1. mzML import;
2. mass detection;
3. ADAP chromatogram builder;
4. smoothing;
5. local-minimum feature resolver;
6. isotopic peaks finder;
7. feature-list rows filter;
8. join aligner;
9. multithreaded peak finder;
10. duplicate peak filter;
11. correlation grouping;
12. deterministic legacy CSV export.

The validated blank + technical-replicate run produced on both Ubuntu and Windows:

- return code `0` and no timeout;
- all twelve stages entered and completed;
- 5,207 blank scans and 5,232 replicate scans imported;
- 285 blank and 503 replicate smoothed chromatograms;
- 131 blank and 391 replicate resolved features;
- 101 blank and 309 replicate isotope patterns;
- 27 blank-side and 130 replicate-side gap-filled features;
- 13 duplicate rows removed;
- 176 final aligned/exported rows;
- byte-identical 22,422-byte CSV;
- SHA-256 `139a02d3305280937783acc1ac730fbb4c719c4e80730c2bf864f68806e89c11`.

This establishes robust public 3.x workflow compatibility. It is not direct 4.0.8 parity evidence for
the downstream feature-processing stages.

### Task infrastructure

Completed and tested on Java 20/Linux/Windows:

- local `TaskService` facade;
- synchronous blocking task adapters;
- bounded platform-thread executor factories;
- JavaFX/Desktop scheduling and error-path decoupling;
- compact completed-task diagnostics;
- original fork-local `GroupedTask` with bounded concurrency, deterministic progress, ordered error
  aggregation, and idempotent cancellation.

The task layer is classified as **adapted**, because the fork deliberately preserves the 3.9 global
scheduler and defines its own grouped-task API rather than claiming upstream binary compatibility.

## Current parity matrix

| Area | Capability | Current state | Evidence completed | Required 4.0.8 gate |
|---|---|---|---|---|
| Startup | Offline headless startup | Adapted | Linux/Windows no-login smoke test | Compare CLI semantics and structured failure handling |
| Batch | XML batch execution | Adapted | Deterministic 3.9 and public 3.4.27 workflows | Migrate/execute selected 4.0.8 workflows without silent loss |
| Tasks | Blocking task execution | Adapted | Java 20 synchronous tests | Document API differences only; no binary parity required |
| Tasks | Bounded executors and shutdown | Adapted | Saturation, naming, priority, shutdown tests | Load/cancellation integration with scientific modules |
| Tasks | Scheduler independent of JavaFX/Desktop | Adapted | Source-boundary and headless error tests | GUI-boundary regression and release packaging test |
| Tasks | Grouped scientific task | Adapted | Original fork-local deterministic implementation | Adopt only in modules that demonstrate need |
| Memory | Memory-map lifecycle | Not implemented | 3.9 baseline present | Close/delete/recovery/load tests on Linux and Windows |
| Import | Governed indexed centroid mzML | Equivalent | Direct v4.0.8 differential: 5,221 scans and 6,477,349 points; governed zero differences | Extend to broader mzML encodings and files |
| Import | Broader mzML conformance | Not implemented | Current governed file is indexed, centroided, negative LC-MS | Profile, positive, mixed polarity, encodings, units, malformed metadata |
| Import | Explicit MS1/MS2 metadata and source selection | Equivalent | Direct MS1/MS2 comparison including precursor/isolation/charge metadata | Extend metadata coverage with additional public files and MSn cases |
| Import | Waters through reproducible mzML conversion | Not implemented | Candidate corpus documented | Freeze converter, command, input/output hashes and metadata |
| Import | Native Waters RAW | Out of current scope | Explicitly deferred | Separate legal/technical milestone if independently distributable |
| Processing | Centroid mass detection at noise 0.0 | Equivalent | Direct per-scan MS1 and MS2 v4.0.8 comparison; governed zero differences | Add other noise levels and detector modes where in scope |
| Processing | ADAP chromatogram building | Adapted | Deterministic feature counts in current workflows | Feature-level differential comparison with v4.0.8 |
| Processing | Smoothing | Adapted | Public workflow completes with frozen counts | Point-series and feature-level differential comparison |
| Processing | Local-minimum resolver | Adapted | Synthetic and public real-data counts | Split boundaries, areas, heights, points, and MS2 pairing comparison |
| Processing | Isotope finder | Adapted | Frozen isotope-pattern counts | Membership, charge, representative, and isotope mass-error comparison |
| Processing | Rows filter | Adapted | Public workflow completes | Record every enabled decision and retained/removed row identities |
| Processing | Join alignment | Adapted | Synthetic pair and public workflow | Two-replicate/blank differential matching and tolerance behavior |
| Processing | Multithreaded gap filling | Adapted | Public workflow gap counts | 1/2/N-thread determinism and status/value comparison |
| Processing | Duplicate filter | Adapted | 13 rows removed in public workflow | Duplicate groups and representative choices compared |
| Processing | Correlation grouping | Not implemented as scientific evidence | Step completes but minimal fixture yields no useful groups | Multi-replicate corpus with known reproducible correlations |
| Processing | Blank classification | Not implemented | Published rows filter recorded only | Separate analytical blank ratios/categories and clear boundary from parity |
| Processing | Adduct/ion identity | Not implemented | Source may exist in 3.9 but no parity suite | Public workflow, parameter map, and deterministic relationships |
| MS2 | Feature-to-MS2 association | Not implemented | Raw MS2 scan/precursor import is equivalent | Association, RT, multiple spectra, and orphan handling |
| MS2 | MSe processing | Not implemented | Official reference identified | Freeze and execute public MSe workflow |
| Annotation | Spectral-library import | Not implemented as parity evidence | 3.9 source present | Public MSP/MGF/JSON fixtures and round-trip assertions |
| Annotation | Spectral-library matching | Not implemented | No direct public matching benchmark | Score, tolerances, filters, ranking, and top-N comparison |
| Export | Legacy CSV | Adapted | Frozen exact headers and byte-identical public output | Inventory modern 4.0.8 export semantics and normalize comparisons |
| Errors | CLI exit codes and run report | Not implemented | Success path and log parsing exist | Structured codes for invalid batch, task error, cancel, timeout, missing output |
| Performance | Runtime and peak memory | Not implemented | Elapsed time informational only | Small/medium/large benchmark with regression thresholds |
| Concurrency | Determinism across thread counts | Not implemented | Current fixed configurations validated | Repeated 1/2/N-thread output and group-order comparisons |
| Packaging | Portable Windows/Linux artifact | Not implemented | Source CI only | Build, launch, process, and cleanup from clean packaged artifact |
| Supply chain | Fully offline clean build | Not implemented | Runtime offline verified | Dependency lock/checksums/local repository/SBOM if pursued |

## Differential 4.0.8 oracle

The direct oracle harness now validates import and centroid behavior. The next parity work extends the
same design to chromatogram construction and feature detection.

### Required execution design

For each selected workflow:

1. freeze the upstream tag and exact commit;
2. build or obtain a reproducible public v4.0.8 reference execution environment without modifying
   scientific algorithms;
3. use the same governed mzML bytes;
4. translate parameters through a reviewed mapping;
5. run the same effective thread counts;
6. export normalized intermediate and final records;
7. compare scientific values using explicit tolerances;
8. report every difference before implementing a repair.

### Minimum intermediate records

- imported scan metadata and point counts;
- mass-list counts and selected values by MS level;
- chromatogram and smoothed-series counts;
- resolved feature m/z, RT, height, area, and point count;
- isotope membership and charge;
- alignment membership and per-file differences;
- gap-filled status and values;
- filter decisions;
- duplicate groups and retained representatives;
- correlation/adduct groups when exercised;
- MS2 associations and library matches when exercised;
- final normalized export.

## Active gate sequence

### Gate 1 — documentation and CI truth

- update status documents and issues;
- run standard CI on pushes to `open-offline-main` as well as `agent/**`;
- preserve reports for the exact integration commit;
- stop claiming clean-build offline operation until dependency freezing exists.

### Gate 2 — frozen 4.0.8 inventory

Create a machine-readable matrix containing:

- 3.9 class and parameter-set version;
- 4.0.8 class, path, commit/tag, and parameter-set version;
- algorithm and data-model changes;
- fork implementation mapping;
- current evidence state;
- required fixture and tolerance.

### Gate 3 — differential workflow harness

The first differential stage is complete for mzML import and centroid mass detection, including
explicit MS1 and MS2 paths. Continue Gate 3 with chromatogram construction and feature detection,
then extend to the blank/replicate workflow.

At least two public LC-MS workflows remain required before a 4.0-derived release version. One must
exercise the current untargeted blank/replicate path; the second must add meaningful feature-to-MS2
or another currently uncovered core capability.

### Gate 4 — mzML and MS2 conformance

Cover:

- indexed and non-indexed mzML;
- zlib and uncompressed arrays;
- 32-bit and 64-bit arrays;
- profile and centroid flags;
- positive, negative, and mixed polarities;
- RT units;
- MS1/MSn precursor, isolation, charge, and collision metadata;
- malformed or incomplete metadata handling;
- feature-to-MS2 association.

### Gate 5 — annotations and grouped chemistry

Validate spectral-library import/matching, ion/adduct identity, and a public MSe reference or classify
individual capabilities as out of scope with justification.

### Gate 6 — lifecycle, load, and failure behavior

Validate memory-map cleanup, file handles, repeated batches, cancellation under load, error recovery,
CLI exit codes, structured run reports, and deterministic 1/2/N-thread behavior.

### Gate 7 — packaged release candidate

Generate portable Windows and Linux artifacts, run them in clean environments, execute the governed
workflows from the package, produce checksums/SBOM/license inventory, and attach the validation report
to the release candidate.

## Minimum criteria before a 4.0-derived version

1. LC-MS core scope frozen and published;
2. direct differential comparison with v4.0.8 for at least two public workflows;
3. intermediate and final scientific results within recorded tolerances;
4. centroid/profile, positive/negative, and MS2 coverage;
5. memory, cleanup, cancellation, and error-code gates;
6. deterministic one-, two-, and N-thread execution;
7. portable artifacts tested in clean environments;
8. exact integration-commit CI and public validation report;
9. no proprietary dependency, bypass, or reconstructed implementation;
10. documentation and machine-readable parity matrix synchronized with the release.