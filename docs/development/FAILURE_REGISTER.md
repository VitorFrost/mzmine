# Failure and correction register

## Purpose

This register is the mandatory history for failures found during implementation, validation, CI, or
scientific comparison in the open-offline fork.

A correction must not be implemented until the corresponding failure entry contains, at minimum:

1. the observed failure and reproducible evidence;
2. the affected scope and impact;
3. the current root-cause hypothesis;
4. the proposed correction;
5. the validation criteria that can accept or reject the proposal.

The original observation and hypothesis must not be erased after a correction. Results are appended
so that unsuccessful approaches, changed assumptions, and repeated failure patterns remain visible.

## Required workflow

1. **Preserve evidence.** Retain the workflow run, job log, artifact, input commit, tool versions,
   exact command, and relevant hashes.
2. **Open a failure entry.** Set the status to `observed` or `proposal-ready`. Describe facts
   separately from hypotheses.
3. **Review the correction boundary.** State whether the proposal changes scientific behavior,
   source provenance, dependency scope, normalized outputs, or only build infrastructure.
4. **Define validation before coding.** Include positive checks, regression checks, and conditions
   that must remain unchanged.
5. **Implement one traceable correction.** Reference the failure ID in the commit and PR update.
6. **Run validation.** Record the new run, result, remaining failures, and whether the hypothesis was
   confirmed.
7. **Close or revise.** Mark the entry `validated`, `rejected`, or `superseded`. A revised proposal is
   appended as a new revision; the previous proposal remains recorded.

### Prohibited shortcuts

- changing code before recording the failure and proposed correction;
- deleting or rewriting the original evidence after a successful fix;
- expanding the dependency graph merely to make compilation pass without a scope review;
- replacing unavailable behavior with an unrecorded stub;
- marking a failure resolved because execution progressed to a different failure;
- treating compilation success as scientific equivalence;
- combining unrelated corrections in one undocumented change.

## Entry template

```markdown
## F-XXX — Short title

- **Status:** observed | proposal-ready | implementing | validating | validated | rejected | superseded
- **First observed:** date/time and timezone
- **Scope:** component or gate
- **Input revision:** commit/tag
- **Evidence:** workflow run, job, artifact, command, error excerpt
- **Observed behavior:** facts only
- **Impact:** what could not proceed or what result became unreliable
- **Root-cause hypothesis:** current explanation and confidence
- **Scientific/provenance risk:** none | low | medium | high, with justification
- **Proposed correction:** written before implementation
- **Validation criteria:** written before implementation
- **Implemented change:** commit and exact change, or `not implemented`
- **Validation result:** run and outcome, or `not run`
- **Decision and lessons:** append-only conclusion
```

---

## F-001 — Repository Gradle wrapper cannot run on Java 21

- **Status:** validated
- **First observed:** 2026-08-05 10:48 BRT
- **Scope:** standalone v4.0.8 executable-oracle build infrastructure
- **Input revision:** `a8291df365026f4292cd1d5efc889c194477983a`
- **Evidence:** workflow run `31011939050`, job `92326109127`, artifact
  `v408-executable-oracle-31011939050`; command
  `./gradlew -p oracle/v408-executable printCompileClasspath --no-daemon`.
- **Observed behavior:** the repository wrapper downloaded Gradle 8.1.1 and failed before dependency
  resolution with `Unsupported class file major version 65` while loading the Gradle Actions
  initialization script.
- **Impact:** the public dependency classpath was not resolved and `javac` was never invoked. No
  conclusion about the source slice could be drawn from this run.
- **Root-cause hypothesis:** Gradle 8.1.1/Groovy in the repository wrapper was not compatible with
  running the standalone build on Java 21. Confidence: high, because the failure occurred during
  Gradle initialization and identified class-file version 65.
- **Scientific/provenance risk:** low. The failure and correction concern the standalone build tool;
  the frozen upstream commit, source hashes, parser source, and normalized scientific contract are
  unchanged.
- **Proposed correction:** provision an explicit Java-21-compatible Gradle version for the standalone
  oracle and invoke that executable instead of the repository wrapper. Keep the main fork wrapper
  unchanged. Upgrade `setup-java` to the maintained major version in the same isolated workflow.
- **Validation criteria:**
  - Gradle initializes on Java 21;
  - the exact closure and source-preparation tests remain green;
  - the Maven Central classpath resolves and is recorded;
  - execution reaches `compileJava` without changing the closure hash
    `eeb5ac4f151b80b567da045b0fb1d360b5d754eafa212277ea0f4f696596cda3`.
- **Implemented change:** commit `b5ff1f6a502dd3040b6d0954fefbbdc8416145c8` provisioned Gradle
  8.10.2, used `gradle` for the isolated build, and moved the workflow to `actions/setup-java@v5`.
  This correction was implemented before this register became mandatory and is recorded
  retroactively.
- **Validation result:** run `31012106085` resolved the public classpath successfully and reached
  `compileJava`. The closure remained 48 reachable types, 99 edges, zero prohibited references, and
  zero unresolved internal references.
- **Decision and lessons:** hypothesis confirmed. Do not assume the application repository wrapper is
  suitable for a separately targeted Java runtime. Standalone oracle tool versions must be explicit
  evidence, not inherited implicitly.

## F-002 — Java 21 preview source compiled without preview enabled

- **Status:** validated
- **First observed:** 2026-08-05 10:51 BRT
- **Scope:** compilation flags for the frozen public v4.0.8 source slice
- **Input revision:** `b5ff1f6a502dd3040b6d0954fefbbdc8416145c8`
- **Evidence:** workflow run `31012106085`, job `92326664563`, artifact
  `v408-executable-oracle-31012106085`; `javac` reported three errors in
  `MzMLPeaksDecoder`, `ScanImportProcessorConfig`, and `AbstractTask`: string templates were a Java
  21 preview feature and were disabled by default.
- **Observed behavior:** dependency resolution succeeded, but compilation stopped at the first syntax
  gate because the frozen source contains `STR` string templates.
- **Impact:** type-resolution errors behind this syntax gate were not yet observable.
- **Root-cause hypothesis:** the public v4.0.8 source was authored for Java 21 with preview features;
  the isolated build specified release 21 but did not pass `--enable-preview`. Confidence: high.
- **Scientific/provenance risk:** low. Enabling the language mode required by the unchanged public
  source does not alter parsing or centroid algorithms. Runtime tasks must use the same preview mode
  to avoid a compile-only success.
- **Proposed correction:** add `--enable-preview` to Java compilation and to all test/JavaExec runtime
  tasks. Retain the upstream source unchanged and continue to report preview warnings.
- **Validation criteria:**
  - the three preview errors disappear without source rewriting;
  - the compiler records warnings rather than errors for the same lines;
  - execution advances to ordinary type/signature resolution;
  - future tests and Java execution receive `--enable-preview`;
  - closure and source-preparation hashes remain unchanged.
- **Implemented change:** commit `78b7cd27d3f8430a041a7df3556baad727651a6b` enabled preview for
  `JavaCompile`, `Test`, and `JavaExec`. This correction was committed immediately before the user
  required the formal register and is recorded retroactively.
- **Validation result:** run `31012378811` reported six preview warnings and no preview errors, then
  progressed to boundary/type-resolution errors. Closure hash and preparation hash remained
  unchanged.
- **Decision and lessons:** hypothesis confirmed. Build and runtime language modes must be recorded
  together; a compiler-only preview flag would create a later runtime failure.

## F-003 — Reviewed boundaries were copied as full application classes

- **Status:** proposal-ready
- **First observed:** 2026-08-05 10:54 BRT
- **Scope:** preparation and compilation of the focused public parser/centroid source slice
- **Input revision:** `78b7cd27d3f8430a041a7df3556baad727651a6b`
- **Evidence:** workflow run `31012378811`, job `92327693496`, artifact
  `v408-executable-oracle-31012378811`; `compileJava` ended with 100 displayed errors and six preview
  warnings. The source-preparation manifest reported 48 classes, 47 files, but only two adapter
  classes.
- **Observed behavior:** the preview gate passed. Compilation then showed that several classes marked
  as reviewed boundaries in the closure manifest were copied from upstream as complete application
  classes. They referenced JavaFX, project scans/features, task listeners, IMS types, persistence,
  and advanced import processors that are intentionally outside the first scientific oracle.
- **Observed error groups:**
  - `AbstractTask`: JavaFX properties plus missing `Task`, listeners, and priority types;
  - `ScanImportProcessorConfig`: missing `ScanSelection`, processor lists, sort/mass-detector
    processors;
  - `MetadataOnlyScan`, `RawDataFile`, and `SimpleSpectralArrays`: missing complete application scan,
    mass-list, feature, JavaFX, and project surfaces;
  - `MobilityType`: feature, preference, and application bootstrap dependencies;
  - `MsMsInfo` and `DDAMsMsInfoImpl`: scan/IMS/project-persistence surfaces;
  - `DataPointUtils` and `StorageUtils`: unrelated feature and storage helpers.
- **Impact:** the source slice does not compile. More importantly, satisfying these errors by adding
  the full missing graph would undo the reviewed 48-type scientific boundary and reintroduce the
  application graph that the closure deliberately excluded.
- **Root-cause hypothesis:** the closure correctly records where traversal must stop, but the
  preparation script currently treats a boundary as an upstream source unless an adapter happens to
  exist. Only `MemoryMapStorage` and `CentroidMassDetector` had versioned adapters, so ten other
  boundary classes were copied in full. Confidence: high, supported by `adapter_class_count: 2` and
  the error classes matching the manifest boundaries.
- **Scientific/provenance risk:** high if corrected by dependency expansion or broad stubbing. Low if
  corrected with explicit, reviewed member-level adapters whose preserved behavior is already
  declared in the manifest.
- **Proposed correction — revision 1 (not implemented):**
  1. Change source preparation to fail closed when a reached reviewed boundary has no explicit
     adapter, unless the manifest marks that boundary as `compile_upstream_source: true` with a
     justification.
  2. Create versioned adapters only for the members already declared as preserved:
     - `AbstractTask`: timestamp and cancellation surface used by direct parser execution, without
       JavaFX or task-controller services;
     - `ScanImportProcessorConfig`: explicit no-filter/no-advanced-processing configuration required
       by the parser; no manufacturer inference;
     - `SimpleSpectralArrays`: primitive m/z and intensity arrays only;
     - `MetadataOnlyScan` and `RawDataFile`: type/signature boundary only where unavoidable; no
       project registration, feature lists, GUI, or stored scans;
     - `MobilityType`: non-IMS `NONE` identity only;
     - `MsMsInfo` and `DDAMsMsInfoImpl`: do not construct application precursor objects; retain only
       signatures that public parser records require, or remove the conversion path through a
       reviewed `BuildingMzMLMsScan` method slice if that yields a smaller and clearer adapter;
     - `DataPointUtils`: only the declared primitive array conversion;
     - `StorageUtils`: only the declared array/buffer operation used by parser records.
  3. Prefer slicing an already reviewed mixed-purpose class over introducing a chain of empty types.
     No adapter may invent analytical values, infer MS level, alter mzML metadata, or implement
     unavailable storage behavior.
  4. Add a machine-readable adapter inventory containing FQCN, preserved members, source origin,
     hash, reason, and tests. The preparation manifest must report every boundary as `adapter` or
     explicitly approved upstream source.
- **Validation criteria — defined before implementation:**
  - preparation fails when a reached boundary lacks an adapter/explicit upstream-source approval;
  - all adapter FQCNs and preserved members match the closure manifest;
  - no JavaFX dependency is added to the standalone oracle;
  - no full feature-list, project, account, cloud, IMS, or GUI dependency is added;
  - no `io.mzio` reference becomes reachable;
  - upstream commit and closure hash remain unchanged;
  - adapter hashes and exact origins are recorded;
  - focused unit/API tests cover cancellation, explicit no-processing config, primitive arrays,
    non-IMS identity, and centroid threshold behavior;
  - `compileJava` progresses beyond the error groups above;
  - any new failure is registered as a new entry before its correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** the closure audit and compile preparation are distinct gates. A reviewed
  traversal boundary must become an explicit compilation boundary; otherwise the preparer silently
  restores the excluded dependency graph.
