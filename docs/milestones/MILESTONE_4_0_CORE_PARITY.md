# Milestone — mzmine v4.0.8 LC-MS core parity

## Objective

Demonstrate functional parity between the open-offline fork and the public LC-MS scientific core of
mzmine v4.0.8 using governed public inputs, reviewed parameter mappings, normalized intermediate
outputs, explicit tolerances, and cross-platform execution.

This milestone does not attempt to merge the modern upstream tree or reproduce proprietary `io.mzio`
infrastructure. It identifies scientifically relevant gaps and implements only those gaps through
public MIT-licensed source adaptation or original fork-local code.

## Frozen references

- fork scientific base: mzmine v3.9.0 commit
  `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- first 4.0 reference: upstream tag `v4.0.3`;
- parity oracle: upstream tag `v4.0.8`;
- current governed 3.x benchmark: Zenodo LC-MS blank/replicate workflow using MZmine 3.4.27 settings;
- current application version: `3.9.1` until release gates are complete.

The exact commit resolved by the upstream `v4.0.8` tag must be frozen in the machine-readable parity
inventory before differential execution is accepted.

## Scope

### Included

- mzML import required by selected LC-MS workflows;
- MS1/MS2 metadata and feature association;
- mass detection;
- chromatogram building and smoothing;
- feature resolving;
- isotope grouping;
- alignment and gap filling;
- rows filtering and analytical blank relationships;
- duplicate and correlation grouping;
- spectral-library import and matching;
- ion/adduct identity when exercised by the selected references;
- MSe processing or an explicit capability-level out-of-scope decision;
- deterministic headless batch execution and normalized export;
- task cancellation, errors, memory cleanup, load behavior, and packaged operation.

### Excluded unless separately added

- proprietary account, authentication, licensing, global-event, cloud, and commercial services;
- proprietary or reconstructed `io.mzio` implementations;
- native vendor readers that cannot be independently and legally distributed;
- full GUI/source/binary compatibility;
- complete GC-MS, imaging, and ion-mobility parity.

## Work packages

### 4A — documentation, issue, and CI truth

- [x] Define v4.0.8 LC-MS core as the active external target.
- [x] Distinguish runtime-offline operation from a clean build that may resolve dependencies online.
- [x] Mark TaskController phases 2A–2D complete for their declared scope.
- [x] Record the completed public 3.x workflow evidence.
- [ ] Run standard CI on pushes to `open-offline-main` and `agent/**`.
- [ ] Ensure integration-branch CI records the exact tested commit.
- [ ] Update roadmap issues to match the repository state.

### 4B — frozen v4.0.8 inventory

Create a machine-readable inventory for each relevant capability containing:

- [ ] 3.9 module/class/path;
- [ ] 3.9 parameter-set version;
- [ ] v4.0.8 module/class/path;
- [ ] v4.0.8 tag and exact commit;
- [ ] parameter additions, removals, renames, and transformations;
- [ ] algorithm changes;
- [ ] data-model/output changes;
- [ ] open-offline mapping;
- [ ] current classification;
- [ ] required dataset and tolerance;
- [ ] public-source provenance or fork-local design record.

No capability may be marked equivalent only because similarly named source classes exist.

### 4C — differential execution harness

The harness must run the same governed inputs through the v4.0.8 oracle and open-offline fork.

- [ ] Use identical mzML bytes.
- [ ] Use reviewed semantically equivalent parameters.
- [ ] Record Java, operating system, commit, thread count, and commands.
- [ ] Capture normalized intermediate and final JSON/CSV records.
- [ ] Compare categorical values exactly unless a documented mapping exists.
- [ ] Compare numerical values with field-specific tolerances.
- [ ] Emit a complete difference report before failing.
- [ ] Preserve artifacts for failed CI runs.
- [ ] Reject silent parameter loss.

Minimum intermediate records:

- imported scan metadata and point counts;
- mass-list counts and selected points;
- chromatogram counts and point series;
- resolved feature m/z, RT, height, area, and point count;
- isotope membership and charge;
- alignment membership;
- gap-filled values/statuses;
- row-filter decisions;
- duplicate groups and representatives;
- correlation/adduct groups;
- MS2 associations and library matches;
- normalized final export.

### 4D — first differential workflow

Use the current governed untargeted LC-MS benchmark.

- [ ] Freeze a v4.0.8-compatible parameter mapping.
- [ ] Run technical replicate 1 independently for single-sample intermediate records.
- [ ] Run blank + replicate 1 + replicate 2 where workflow semantics permit.
- [ ] Compare all ten published scientific stages that remain relevant.
- [ ] Record blank/sample relationships without relabeling the published rows filter as an E&L blank
  classifier.
- [ ] Test gap filling with one, two, and N threads.
- [ ] Record all intentional adaptations.

The current blank-plus-single-replicate workflow is insufficient for scientific correlation parity.
A larger public replicate set is required for that capability.

### 4E — second workflow with MS2 or MSe

At least one additional public workflow must exercise a core capability not demonstrated by the first
benchmark.

Preferred order:

1. public MS2 feature-association and spectral-library matching workflow;
2. official public MSe reference;
3. ion/adduct identity workflow.

Acceptance evidence should include:

- [ ] precursor and isolation metadata;
- [ ] feature-to-MS2 association;
- [ ] multiple spectra and orphan handling;
- [ ] spectral preprocessing and score;
- [ ] candidate ranking/top-N;
- [ ] deterministic library identity output;
- [ ] MSe/pseudo-spectrum semantics when used.

### 4F — mzML conformance

Add explicitly licensed fixtures for:

- [ ] indexed and non-indexed files;
- [ ] zlib and uncompressed arrays;
- [ ] 32-bit and 64-bit binary values;
- [ ] centroid and profile spectra;
- [ ] positive, negative, and mixed polarity;
- [ ] RT units;
- [ ] MS1/MS2/MSn;
- [ ] precursor, isolation, charge, and collision energy;
- [ ] empty scans;
- [ ] incomplete optional metadata;
- [ ] malformed/truncated input and explicit failure behavior.

### 4G — memory, cancellation, and errors

- [ ] Close raw files and feature data deterministically.
- [ ] Release memory-mapped files and temporary paths on Linux and Windows.
- [ ] Delete files after successful and failed execution.
- [ ] Run multiple batches in one JVM without unbounded heap, native memory, thread, or handle growth.
- [ ] Cancel during import and scientific processing.
- [ ] Recover after a module failure.
- [ ] Distinguish invalid batch, missing input, task error, cancellation, timeout, missing output, and
  unhandled exception through exit codes.
- [ ] Generate a structured `run-report.json`.

Recommended run-report fields:

- application version and commit;
- Java and operating system;
- input paths and hashes;
- batch/settings hash;
- thread count;
- entered/completed stages;
- timings and peak memory;
- warnings and errors;
- cancellation/timeout status;
- output paths, sizes, and hashes.

### 4H — performance and determinism

For small, medium, and large public fixtures:

- [ ] runtime by module;
- [ ] peak heap and process RSS;
- [ ] mapped/temp storage usage;
- [ ] scan throughput;
- [ ] repeated-run equality;
- [ ] one-, two-, and N-thread comparison;
- [ ] file-order comparison;
- [ ] explicit regression thresholds relative to 3.9 and v4.0.8.

### 4I — Waters conversion compatibility

Native Waters RAW support is not required for this milestone.

For the first reproducible conversion gate:

- [ ] freeze a public Waters source dataset and exact file;
- [ ] record source size/hash and license;
- [ ] freeze converter name, version/build, command, and filters;
- [ ] freeze converted mzML size/hash;
- [ ] validate polarity, scan type, MS levels, precursor metadata, RT, and profile/centroid state;
- [ ] run the open-offline processing workflow repeatedly;
- [ ] classify acquisition limitations explicitly.

### 4J — packaged release candidate

- [ ] Generate Windows portable artifact.
- [ ] Generate Linux artifact.
- [ ] Run GUI startup from clean packaged artifacts.
- [ ] Run headless governed workflows from the packages.
- [ ] Test spaces, Unicode paths, and non-admin installation.
- [ ] Verify cleanup and logs.
- [ ] Generate checksums, dependency inventory, licenses, and SBOM.
- [ ] Attach a machine-readable and human-readable validation report.

A completely network-independent clean build is a separate supply-chain gate unless a frozen local
dependency repository is produced.

## Classification policy

### Equivalent

Requires direct differential evidence against v4.0.8 or another explicitly accepted public oracle.

### Adapted

Requires documented differences, reviewed analytical purpose, deterministic tests, and no misleading
compatibility claim.

### Not implemented

Used when the capability is relevant but the implementation or evidence is incomplete.

### Out of scope

Used only with an explicit reason tied to the declared LC-MS target, licensing, vendor restriction, or
proprietary infrastructure.

## Release acceptance criteria

A 4.0-derived version may be assigned only when:

1. the declared LC-MS scope is frozen;
2. v4.0.8 tag and exact commit are frozen;
3. at least two public workflows pass differential comparison;
4. intermediate and final values meet explicit tolerances;
5. profile/centroid, positive/negative, and MS2 coverage is complete;
6. memory, cleanup, cancellation, and error-code gates pass;
7. one-, two-, and N-thread determinism is demonstrated;
8. Windows and Linux packaged artifacts pass clean-environment workflows;
9. exact integration-commit CI is green;
10. validation reports, checksums, licenses, and SBOM are published;
11. no proprietary binary, bypass, fake entitlement, or decompiled implementation is present;
12. documentation, issues, and machine-readable parity inventory match the release.

## Immediate implementation order

1. merge documentation and integration-CI corrections;
2. create the v4.0.8 inventory schema and generator;
3. freeze the v4.0.8 exact tag commit;
4. implement the first differential single-sample stage exporter;
5. compare import and mass detection before expanding downstream;
6. extend stage by stage through the current public workflow;
7. add the second MS2/MSe workflow;
8. implement only gaps demonstrated by the differential reports.
