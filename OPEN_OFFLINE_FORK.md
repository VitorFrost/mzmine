# Open Offline Fork Charter

## Origin

The stable integration branch starts from the exact public mzmine 3.9.0 source state:

- upstream tag: `v3.9.0`;
- base commit: `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- current application version file: `3.9.1`.

The branch is intentionally independent from the modern upstream `master`. It is not maintained by
merging thousands of later commits indiscriminately. Each later capability is evaluated against a
frozen scientific requirement, public source provenance, license compatibility, and regression suite.

## Mission

Build an independently maintainable, offline-runtime mzmine fork that:

1. preserves the public MIT scientific-processing behavior of the 3.9 line;
2. requires no account, authentication, license service, telemetry, or mandatory network access at
   runtime;
3. contains no proprietary `io.mzio` binary or reconstructed implementation;
4. selectively adapts public MIT-licensed improvements from later history;
5. adds original fork-local functionality only when a documented scientific workflow requires it;
6. validates all parity claims with governed public data and cross-platform tests.

## Current external target

The active release target is:

> **Functional parity with the public LC-MS scientific core of mzmine v4.0.8.**

The initial comparison line is v4.0.3 and the maintenance checkpoint is v4.0.8. Version 4.0 will be
claimed only after direct differential execution against the frozen v4.0.8 reference satisfies the
release gates in `docs/milestones/MILESTONE_4_0_CORE_PARITY.md`.

### Included LC-MS core scope

- open mzML import and metadata handling;
- MS1/MS2 scientific data model required by the selected workflows;
- mass detection;
- chromatogram building and smoothing;
- feature resolving;
- isotope grouping;
- alignment and gap filling;
- row filtering and analytical blank relationships;
- duplicate and correlation grouping;
- spectral-library import/matching and MS2 association;
- ion/adduct identity where supported by the selected public references;
- deterministic headless batch processing and structured export;
- task execution, cancellation, error propagation, cleanup, and portable operation.

### Not implied by the 4.0 target

- identical source-tree or Gradle modularization;
- identical GUI or binary/API compatibility;
- proprietary account, authentication, licensing, analytics, global-event, memory-management, or
  cloud infrastructure;
- vendor-native readers whose distribution or SDK terms are incompatible with the fork;
- complete parity for GC-MS, imaging, ion mobility, or every optional upstream module unless added to
  the declared scope later.

## Independence boundaries

The independent build must not depend on or redistribute proprietary artifacts such as:

- `io.mzio:user-client`;
- `io.mzio:user-management`;
- `io.mzio:user-management-fx`;
- `io.mzio:global-events`;
- proprietary `io.mzio:taskcontroller`;
- proprietary `io.mzio:memory-management`;
- proprietary `io.mzio:mzmine-core`.

The project also prohibits:

- decompilation or reconstruction of proprietary implementation details;
- authentication or license bypasses;
- fake users, fake entitlements, or “always authenticated” compatibility layers;
- silently dropping unsupported scientific parameters;
- weakening TLS, hash, license, provenance, or numerical comparison checks to make CI pass.

When later public source references unavailable proprietary infrastructure, the accepted choices are:

1. omit the integration when it is unrelated to scientific processing;
2. define and implement an original local open interface with independent semantics;
3. classify the capability as out of scope.

## Runtime and build network policy

The application runtime must not require an account or mandatory network access for local scientific
processing.

A clean source build currently resolves third-party dependencies from external Maven repositories.
Therefore, “runtime offline” is the current verified claim. A fully network-independent clean build
requires a future frozen dependency repository, dependency locking, checksums, license inventory, and
SBOM; until that work is complete, documentation must not claim that a clean build needs no network.

## Completed milestones

### Milestone 1 — deterministic offline scientific baseline

Completed:

- independence and prohibited-dependency audit;
- Java 20 Linux and Windows compilation/tests;
- no-login headless startup;
- deterministic synthetic mzML import and LC-MS processing;
- mass detection, ADAP building, local-minimum resolving, alignment, gap filling, and CSV export;
- real XML batch execution;
- equality with an untouched mzmine 3.9.0 checkout for the frozen baseline.

### Milestone 2 — task-controller modernization

Completed:

- local `TaskService` facade;
- deterministic synchronous task adapters;
- fixed and bounded high-priority Java 20 platform-thread executors;
- scheduler/error-path decoupling from JavaFX/Desktop services;
- compact completed-task diagnostics;
- original fork-local `GroupedTask` with deterministic progress, bounded concurrency, ordered error
  aggregation, idempotent cancellation, and cross-platform tests.

The legacy global scheduler remains in place. Future scientific modules may opt into `GroupedTask`
without requiring a broad scheduler rewrite.

### Milestone 3A — public workflow compatibility gate

Completed:

- fail-closed public dataset and settings manifests;
- frozen Zenodo LC-MS sample, second technical replicate, and analytical blank;
- frozen MZmine 3.4.27 processing settings;
- structural and canonical audit of all ten published modules and 93 top-level parameters;
- twelve-stage public headless workflow on Linux and Windows;
- 176 final aligned rows and byte-identical 22,422-byte CSV;
- no authentication, licensing, telemetry, timeout, or silent parameter loss in the validated run.

This proves robust public 3.x workflow compatibility. It is not by itself proof of 4.0 parity.

### Milestone 4 foundation — first direct v4.0.8 behavioral slice

Completed for the declared narrow scope:

- exact public v4.0.8 oracle commit frozen at
  `8029f930d28c0447f0acf2bcabef0a79865ad434`;
- deterministic public parser/centroid source closure with reviewed boundaries and locked adapters;
- isolated Java 21 executable oracle built without proprietary `io.mzio` binaries;
- independent Java 20 open-offline normalized producer;
- identical governed `Banane_30ngmL_001.mzML` bytes executed on both sides;
- explicit MS1 and MS2 source-data paths validated independently;
- all non-RT normalized import/centroid fields matched;
- strict binary32 retention-time representation differences retained;
- zero governed differences under the narrow RT-only tolerance contract;
- governed indexed-centroid mzML import and centroid mass detection at noise `0.0` promoted to
  **Equivalent** only for the tested scope.

The evidence and scope limits are recorded in
`docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md`. This foundation does not imply parity
for chromatogram construction or any downstream feature-processing stage.

## Active milestone — differential 4.0.8 LC-MS core parity

The infrastructure needed to prove parity is now established. The active task is to extend that same
failure-first differential contract through the scientific pipeline.

Current implementation sequence:

1. **ADAP Chromatogram Builder** — freeze the exact v4.0.8 module/task/parameter mapping and source
   closure, then compare normalized chromatogram membership and point-series records (issue #50);
2. smoothing — compare pre/post point series and boundaries;
3. local-minimum resolver — compare feature boundaries, apex, m/z, height, area, membership, and
   status;
4. isotope grouping — compare memberships, representative isotope, charge, and mass error;
5. freeze the resulting single-file feature-detection parity state;
6. extend to blank + replicate 1 + replicate 2 for alignment, row filtering, gap filling, duplicate
   filtering, and final normalized export;
7. use a larger public replicate corpus before treating correlation grouping as scientifically
   evidenced;
8. add a second public workflow exercising feature-to-MS2 association and spectral-library matching
   or another explicitly accepted uncovered LC-MS core capability;
9. complete broader mzML conformance, lifecycle, cancellation, structured errors, performance,
   1/2/N-thread determinism, packaging, checksums, licenses, SBOM, and release reports;
10. assign a 4.0-derived version only after all declared release gates pass.

ROI-MCR remains a separate fork-local experimental track and must not be used as evidence for this
v4.0.8 parity milestone.

## Failure-first parity rule

Every newly discovered runtime, infrastructure, representation, or scientific discrepancy in parity
or regression work must be recorded **before** corrective implementation. The record uses the next
`F-xxx` identifier and retains:

- original observation/evidence;
- impact and affected gate;
- root-cause hypothesis with confidence;
- scientific/provenance risk;
- proposed correction before implementation;
- validation criteria defined before the code change;
- final resolution without deleting the original failure record.

A tolerance must not be broadened as a repair unless the representation effect is independently
characterized, quantitatively justified, narrowly scoped, and the original strict difference remains
retained.

## Validation corpus policy

Current validation prioritizes:

- MZmine-owned public batch/settings/project fixtures where provenance permits reuse;
- openly documented interchange formats, primarily mzML;
- public datasets with explicit licenses, immutable identifiers, frozen URLs, byte sizes, and hashes;
- reproducible external vendor conversion with converter version, command, input provenance, and
  output hash recorded.

Vendor-native formats, including Waters RAW, remain a follow-up. They are not required for the first
LC-MS core parity milestone when a reproducible mzML conversion route is available. Native support
must not be represented as complete until separately validated.

## Version policy

The application remains on the 3.9.x version line while it primarily represents the 3.9 scientific
core plus selected adaptations. A 4.0-derived version may be assigned only when:

- the declared LC-MS scope is frozen;
- at least two public workflows are compared directly with v4.0.8;
- intermediate and final results meet recorded tolerances;
- memory, cleanup, concurrency, error, packaging, and clean-environment gates pass;
- the public validation report is attached to the release.

A suitable pre-release identifier may be introduced before that point, but it must clearly state that
4.0 parity is incomplete.

## Branch strategy

- `master` follows modern upstream history and carries only the minimum CI bridge needed by GitHub;
- `open-offline-main` is the stable integration branch;
- `agent/*` branches contain focused changes and target `open-offline-main` through pull requests.

## Required regression gates

Every scientific or infrastructure pull request must preserve:

1. independence/prohibited-dependency audit;
2. fail-closed public-data policy tests;
3. Java 20 Linux and Windows tests;
4. no-login headless startup;
5. deterministic synthetic scientific pipeline;
6. deterministic XML batch pipeline;
7. equality with untouched 3.9.0 for the frozen baseline;
8. relevant governed public-corpus checks;
9. explicit public-source provenance or original fork-local design record;
10. failure-first discrepancy records when a gate exposes a new failure.

## Porting record requirements

Every selectively adapted public source change must record:

- upstream repository and commit SHA;
- original path;
- license header and license compatibility;
- modifications made for Java 20 or the monolithic source tree;
- tests added;
- scientific behavior affected;
- whether the code was publicly available before any proprietary binary distribution.

Original fork-local implementations must document their API and behavior before or with the first
implementation commit and must not claim upstream compatibility unless separately demonstrated.
