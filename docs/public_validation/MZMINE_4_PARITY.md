# MZmine 4.0 LC-MS core parity plan

## Target definition

The active external target is the public scientific behavior of the mzmine 4.0 release line:

- initial reference: `v4.0.3`;
- maintenance checkpoint and release oracle: `v4.0.8`;
- frozen v4.0.8 oracle commit: `8029f930d28c0447f0acf2bcabef0a79865ad434`;
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

**Current active scientific gate:** ADAP Chromatogram Builder differential parity, tracked in issue
#50. ROI-MCR is a separate experimental fork-local track and is not evidence for this parity target.

## Evidence vocabulary

Each capability has one of four states:

- **Equivalent** — direct differential evidence against the frozen 4.0.8 oracle or an accepted public
  reference demonstrates equivalent relevant behavior for the explicitly stated tested scope.
- **Adapted** — the same analytical purpose exists through a documented open interface or fork-local
  design, but direct parity evidence or some parameter/data-model/integration behavior still differs.
- **Not implemented** — the capability is relevant to the declared LC-MS core but lacks adequate
  implementation or evidence of an implementation.
- **Out of scope** — the capability is proprietary, vendor-restricted, network-only, or outside the
  declared LC-MS target.

A source class being present is not evidence of equivalence. A complete workflow finishing without
error is not sufficient when differences may be hidden by later filtering. Parity evidence must
include intermediate scientific outputs where practical.

## Current verified baseline

### Governed v4.0.8 import and centroid differential

The fork has direct behavioral evidence against the exact public v4.0.8 commit for both explicitly
selected source-data paths:

- all 5,221 scans and 6,477,349 imported spectral points compared record by record;
- 4,081 MS1 records and 6,450,546 MS1 centroid points compared;
- 1,140 MS2 records and 26,803 MS2 centroid points compared;
- scan order, membership, MS level, polarity, spectrum type, point count, m/z, intensity, sampled
  points, and precursor/isolation/charge metadata equivalent;
- strict reports retain deterministic binary32 retention-time representation differences;
- governed reports contain zero differences under a frozen RT-only binary32 tolerance contract;
- m/z and intensity tolerances were not relaxed;
- Linux, Windows, headless startup, deterministic batch, independence, and untouched-3.9 gates pass.

Manual workflow dispatch runs only the MS1 or MS2 level selected by the user. The dedicated direct
v4.0.8 CI runs separate MS1 and MS2 cases so both selectable paths remain validated without automatic
manufacturer or acquisition-mode inference.

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

These 3.9-line tests prove regression stability, not downstream v4.0.8 equivalence.

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

The task layer is classified as **Adapted**, because the fork deliberately preserves the 3.9 global
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
| Import | Broader mzML conformance | Not implemented | Current governed file covers one indexed centroid LC-MS encoding | Profile, positive/mixed polarity, encodings, units, malformed metadata |
| Import | Explicit MS1/MS2 metadata and source selection | Equivalent | Direct MS1/MS2 comparison including precursor/isolation/charge metadata | Extend metadata coverage with additional public files and MSn cases |
| Import | Waters through reproducible mzML conversion | Not implemented | Candidate corpus documented | Freeze converter, command, input/output hashes and metadata |
| Import | Native Waters RAW | Out of scope for current milestone | Explicitly deferred | Separate legal/technical milestone if independently distributable |
| Processing | Centroid mass detection at noise 0.0 | Equivalent | Direct per-scan MS1 and MS2 v4.0.8 comparison; governed zero differences | Add other noise levels and detector modes only if declared in scope |
| Processing | ADAP chromatogram building | Adapted | Deterministic 3.x feature counts; direct v4.0.8 evidence pending | **Active issue #50:** source/parameter closure plus chromatogram membership/point-series differential |
| Processing | Smoothing | Adapted | Public 3.x workflow completes with frozen counts | Point-series and feature-level differential comparison |
| Processing | Local-minimum resolver | Adapted | Synthetic and public real-data counts | Split boundaries, areas, heights, points, and MS2 pairing comparison |
| Processing | Isotope finder | Adapted | Frozen 3.x isotope-pattern counts | Membership, charge, representative, and isotope mass-error comparison |
| Processing | Rows filter | Adapted | Public 3.x workflow completes | Record every enabled decision and retained/removed row identities |
| Processing | Join alignment | Adapted | Synthetic pair and public 3.x workflow | Two-replicate/blank differential matching and tolerance behavior |
| Processing | Multithreaded gap filling | Adapted | Public 3.x workflow gap counts | 1/2/N-thread determinism and status/value comparison |
| Processing | Duplicate filter | Adapted | 13 rows removed in public 3.x workflow | Duplicate groups and representative choices compared |
| Processing | Correlation grouping | Adapted | Stage exists and completes; current minimal corpus gives insufficient scientific grouping evidence | Larger multi-replicate corpus with reproducible correlations |
| Processing | Blank classification | Not implemented | Published rows filter recorded only | Separate analytical blank ratios/categories; do not relabel rows-filter parity |
| Processing | Adduct/ion identity | Not implemented | Source may exist in 3.9 but no parity suite | Public workflow, parameter map, and deterministic relationships |
| MS2 | Feature-to-MS2 association | Not implemented | Raw MS2 scan/precursor import is equivalent | Association, RT, multiple spectra, and orphan handling |
| MS2 | MSe processing | Not implemented | Candidate official references identified | Freeze and execute public MSe workflow or classify explicitly out of scope |
| Annotation | Spectral-library import | Not implemented | 3.9 source exists but no direct parity evidence | Public MSP/MGF/JSON fixtures and normalized assertions |
| Annotation | Spectral-library matching | Not implemented | No direct public matching benchmark | Score, tolerances, filters, ranking, and top-N comparison |
| Export | Legacy CSV | Adapted | Frozen exact headers and byte-identical 3.x public output | Inventory modern 4.0.8 export semantics and normalize comparisons |
| Errors | CLI exit codes and run report | Not implemented | Success path and log parsing exist | Structured codes for invalid batch, task error, cancel, timeout, missing output |
| Performance | Runtime and peak memory | Not implemented | Elapsed time informational only | Small/medium/large benchmark with regression thresholds |
| Concurrency | Determinism across thread counts | Not implemented | Current fixed configurations validated | Repeated 1/2/N-thread output and group-order comparisons |
| Packaging | Portable Windows/Linux artifact | Not implemented | Source CI only | Build, launch, process, and cleanup from clean packaged artifact |
| Supply chain | Fully offline clean build | Not implemented | Runtime offline verified | Dependency lock/checksums/local repository/SBOM if pursued |

The twelve entries in `datasets/parity/mzmine_v408_lcms_core_inventory.json` remain the
machine-readable source of truth for the initial module inventory. Human wording in this document
must not contradict those classifications.

## Differential 4.0.8 oracle

The direct oracle harness validates import and centroid behavior and is now being extended to
chromatogram construction and feature detection.

### Required execution design

For each selected stage/workflow:

1. freeze the upstream tag and exact commit;
2. freeze the stage-specific public source/adapter closure required by the executed path;
3. use the same governed input bytes;
4. translate parameters through a reviewed mapping;
5. use controlled thread counts appropriate to the stage;
6. export normalized intermediate and final records independently from oracle and candidate;
7. preserve a strict comparison;
8. apply only predeclared, field-specific governed tolerances when independently justified;
9. report every difference before implementing a repair;
10. record each newly discovered discrepancy through the failure-first `F-xxx` process.

### Minimum intermediate records

- imported scan metadata and point counts;
- mass-list counts and selected values by MS level;
- chromatogram membership and point series;
- smoothed point series;
- resolved feature m/z, RT, height, area, point count, and membership;
- isotope membership and charge;
- alignment membership and per-file differences;
- gap-filled status and values;
- filter decisions;
- duplicate groups and retained representatives;
- correlation/adduct groups when exercised;
- MS2 associations and library matches when exercised;
- final normalized export.

## Gate status

### Gate 1 — governance, documentation, and CI truth: established

Completed foundation includes:

- active v4.0.8 target and exact oracle identity;
- runtime-offline versus clean-build distinction;
- integration and agent-branch CI coverage;
- exact tested commit metadata;
- synchronized import/centroid machine-readable state;
- failure-first `F-xxx` process.

This gate remains an ongoing invariant: issues, documents, and the inventory must be updated whenever
a capability changes state.

### Gate 2 — frozen 4.0.8 inventory: foundation complete, downstream audit ongoing

The 12-capability schema/inventory, source roots, datasets, tolerance profiles, and first direct
classifications exist. Each downstream stage still requires its own parameter/algorithm/data-model
audit before it can be promoted.

### Gate 3 — differential workflow harness: active

Completed:

- identical governed bytes;
- independent oracle and candidate producers;
- normalized import/centroid records;
- explicit MS1 and MS2 paths;
- strict comparison retention;
- governed zero-difference result for the first slice.

Active next stage: **ADAP Chromatogram Builder (#50)**. Then continue through smoothing, resolver,
isotope grouping, and the blank/replicate multi-file stages.

At least two public LC-MS workflows remain required before a 4.0-derived release version. One must
exercise the current untargeted blank/replicate path; the second should add meaningful feature-to-MS2
association and spectral-library matching or another explicitly accepted uncovered core capability.

### Gate 4 — mzML and MS2 conformance: pending

Cover indexed/non-indexed mzML, zlib/uncompressed arrays, 32-/64-bit arrays, profile/centroid flags,
positive/negative/mixed polarities, RT units, MS1/MSn precursor/isolation/charge/collision metadata,
malformed/incomplete metadata behavior, and feature-to-MS2 association.

### Gate 5 — annotations and grouped chemistry: pending

Validate spectral-library import/matching, ion/adduct identity, and a public MSe reference or classify
individual capabilities as out of scope with justification.

### Gate 6 — lifecycle, load, and failure behavior: pending

Validate memory-map cleanup, file handles, repeated batches, cancellation under load, error recovery,
CLI exit codes, structured run reports, and deterministic 1/2/N-thread behavior.

### Gate 7 — packaged release candidate: pending

Generate portable Windows and Linux artifacts, run them in clean environments, execute governed
workflows from the packages, produce checksums/SBOM/license inventory, and attach the validation
report to the release candidate.

## Failure-first requirement

Every new runtime, infrastructure, representation, or scientific discrepancy must be recorded before
correction with a unique `F-xxx` identifier containing the observation/evidence, impact, root-cause
hypothesis, scientific/provenance risk, proposed correction, and validation criteria defined before
implementation. The original strict evidence is retained after resolution.

Tolerance broadening is prohibited as a convenience repair. A new tolerance requires quantitative,
independent characterization and a narrowly scoped scientific/representation justification.

## Minimum criteria before a 4.0-derived version

1. LC-MS core scope frozen and published;
2. direct differential comparison with v4.0.8 for at least two public workflows;
3. intermediate and final scientific results within recorded tolerances;
4. centroid/profile, positive/negative, and MS2 coverage adequate for the declared scope;
5. memory, cleanup, cancellation, and error-code gates;
6. deterministic one-, two-, and N-thread execution where applicable;
7. portable artifacts tested in clean environments;
8. exact integration-commit CI and public validation report;
9. no proprietary dependency, bypass, fake entitlement, or reconstructed implementation;
10. documentation, issues, and machine-readable parity inventory synchronized with the release.
