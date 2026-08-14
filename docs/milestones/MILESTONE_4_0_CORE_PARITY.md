# Milestone — MZmine v4.0.8 LC-MS core parity

## Objective

Demonstrate functional parity between the open-offline fork and the public LC-MS scientific core of MZmine v4.0.8 using governed public inputs, reviewed parameter mappings, normalized intermediate outputs, explicit tolerances, and cross-platform execution.

This milestone does not merge the modern upstream tree or reproduce proprietary `io.mzio` infrastructure. Scientifically relevant gaps are identified by differential execution and are adapted only from public MIT-licensed source or implemented as independently documented fork-local behavior.

## Frozen references

- fork scientific base: MZmine v3.9.0 commit `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- first 4.0 reference: upstream tag `v4.0.3`;
- parity oracle: upstream tag `v4.0.8`, exact commit `8029f930d28c0447f0acf2bcabef0a79865ad434`;
- current governed 3.x benchmark: Zenodo LC-MS blank/replicate workflow using MZmine 3.4.27 settings;
- current application version: `3.9.1` until all release gates are complete.

## Current established v4.0.8 parity

Direct differential evidence is complete for the first governed scientific slice:

- indexed centroid mzML import with advanced import processors disabled;
- scan ordering, membership, polarity, spectrum type, spectral values and exercised precursor metadata;
- explicit manual MS1 or MS2 source-data selection without manufacturer/acquisition inference;
- centroid mass detection at explicit noise level `0.0`.

The same governed `Banane_30ngmL_001.mzML` bytes were executed by the exact public v4.0.8 source slice and the open-offline candidate. Strict reports retain deterministic binary32 RT representation differences; governed reports contain zero differences under the frozen RT-only tolerance contract. m/z and intensity tolerances were not relaxed.

These capabilities are `Equivalent` only within that declared scope. Downstream feature-processing stages remain `Adapted` or `Not implemented` until directly compared.

## Scope

### Included

- mzML import required by selected LC-MS workflows;
- MS1/MS2 metadata and feature association;
- mass detection;
- chromatogram building and smoothing;
- feature resolving;
- isotope grouping;
- alignment and gap filling;
- row filtering;
- duplicate and correlation grouping;
- spectral-library import and matching;
- ion/adduct identity when exercised by selected references;
- MSe processing or an explicit capability-level out-of-scope decision;
- deterministic headless batch execution and normalized export;
- task cancellation, error behavior, memory cleanup, load behavior, and packaged operation.

### Excluded unless separately added

- proprietary account, authentication, licensing, global-event, cloud, and commercial services;
- proprietary or reconstructed `io.mzio` implementations;
- native vendor readers that cannot be independently and legally distributed;
- full GUI/source/binary compatibility;
- complete GC-MS, imaging, and ion-mobility parity.

ROI-MCR is a separate fork-local experimental LC-MS milestone and is not evidence for Milestone 4 parity.

## Work packages

### 4A — documentation, issue, and CI truth

- [x] Define v4.0.8 LC-MS core as the active external target.
- [x] Distinguish runtime-offline operation from a clean build that may resolve dependencies online.
- [x] Mark TaskController phases 2A–2D complete for their declared scope.
- [x] Record the completed public 3.x workflow evidence.
- [x] Run standard CI on pushes to `open-offline-main` and `agent/**`.
- [x] Preserve exact tested integration/source commit metadata.
- [x] Establish failure-first `F-xxx` records before corrections.
- [x] Synchronize the machine-readable inventory with the established import/centroid result.
- [ ] Keep roadmap issues and documents synchronized after each promoted capability.

### 4B — frozen v4.0.8 inventory

Completed foundation:

- [x] exact v4.0.8 tag and commit;
- [x] machine-readable 12-capability inventory and schema;
- [x] source roots and Git blob identities;
- [x] classification policy;
- [x] governed dataset groups;
- [x] tolerance profiles;
- [x] import/mass-detection parameter mapping and differential state;
- [x] import and centroid promotion to `Equivalent` after direct evidence.

Still capability-specific:

- [ ] parameter additions/removals/renames/transformations for every downstream stage;
- [ ] algorithm/data-model changes for every downstream stage;
- [ ] required fixture/tolerance/evidence for every downstream stage.

No capability may be marked `Equivalent` because similarly named source classes or identical source blobs exist.

### 4C — differential execution harness

Core harness capabilities now established:

- [x] identical governed mzML bytes;
- [x] independent v4.0.8 and open-offline producers;
- [x] Java/OS/commit/settings/input provenance;
- [x] normalized JSON records;
- [x] exact categorical comparison;
- [x] field-specific numerical tolerances;
- [x] complete strict difference reports;
- [x] separately governed comparison result;
- [x] CI artifact retention;
- [x] rejection of silent parameter loss in the validated slice.

Current completed stages:

- [x] import;
- [x] centroid mass detection, MS1 and MS2.

Next stage:

- [ ] ADAP Chromatogram Builder.

Then extend the same contract through:

- [ ] smoothing;
- [ ] local-minimum resolver;
- [ ] isotope grouping;
- [ ] alignment;
- [ ] row filtering;
- [ ] gap filling;
- [ ] duplicate grouping/filtering;
- [ ] correlation/adduct grouping;
- [ ] MS2 association and library matching;
- [ ] normalized final export.

### 4D — first differential workflow

Use the current governed untargeted LC-MS benchmark.

Single-file sequence:

- [x] import and centroid records;
- [ ] ADAP chromatogram construction;
- [ ] smoothing;
- [ ] local-minimum feature resolving;
- [ ] isotope grouping.

Multi-file sequence:

- [ ] blank + replicate 1 + replicate 2 alignment;
- [ ] row-filter decisions without relabeling the MZmine rows filter as an E&L blank classifier;
- [ ] gap filling with one, two, and N threads;
- [ ] duplicate groups and representatives;
- [ ] final normalized export.

Correlation grouping requires a larger public replicate set than the minimal blank/replicate fixture.

### 4E — second workflow with MS2 or MSe

At least one additional public workflow must exercise a core capability not demonstrated by the first benchmark.

Preferred order:

1. public MS2 feature-association and spectral-library matching workflow;
2. official public MSe reference;
3. ion/adduct identity workflow.

Required evidence:

- [x] raw precursor/isolation/charge metadata on the first governed file;
- [ ] feature-to-MS2 association;
- [ ] multiple spectra and orphan handling;
- [ ] spectral preprocessing and score;
- [ ] candidate ranking/top-N;
- [ ] deterministic library identity output;
- [ ] MSe/pseudo-spectrum semantics if included.

### 4F — mzML conformance

Add explicitly licensed or governed synthetic fixtures for:

- [ ] indexed and non-indexed files;
- [ ] zlib and uncompressed arrays;
- [ ] 32-bit and 64-bit binary values;
- [x] centroid spectra on the first governed file;
- [ ] profile spectra;
- [ ] positive, negative, and mixed polarity coverage beyond the current negative fixture;
- [ ] alternate RT units;
- [x] MS1/MS2 metadata on the first governed file;
- [ ] MSn metadata;
- [x] exercised precursor, isolation, charge, and activation metadata;
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
- [ ] Distinguish invalid batch, missing input, task error, cancellation, timeout, missing output, and unhandled exception through exit codes.
- [ ] Generate a structured `run-report.json`.

### 4H — performance and determinism

For small, medium, and large governed fixtures:

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
- [ ] Attach machine-readable and human-readable validation reports.

A completely network-independent clean build remains a separate supply-chain gate unless a frozen local dependency repository is produced.

## Classification policy

### Equivalent

Requires direct differential evidence against v4.0.8 or another explicitly accepted public oracle, with the tested scope stated narrowly enough not to overclaim adjacent behavior.

### Adapted

Requires documented differences, reviewed analytical purpose, deterministic tests, and no misleading compatibility claim.

### Not implemented

Used when a capability is relevant to the declared LC-MS core but implementation or evidence is incomplete.

### Out of scope

Used only with an explicit reason tied to the declared LC-MS target, licensing, vendor restriction, or proprietary infrastructure.

## Failure-first rule

Every newly discovered runtime, infrastructure, representation, or scientific difference must be registered before correction with a unique `F-xxx` record containing:

- observation and retained evidence;
- impact;
- root-cause hypothesis and confidence;
- scientific/provenance risk;
- proposed correction before implementation;
- predefined validation criteria.

The correction and resolution record must not erase the original observation.

## Release acceptance criteria

A 4.0-derived version may be assigned only when:

1. the declared LC-MS scope is frozen;
2. v4.0.8 tag and exact commit are frozen;
3. at least two public workflows pass differential comparison;
4. intermediate and final values meet explicit tolerances;
5. centroid/profile, positive/negative, and MS2 coverage is adequate for the declared release scope;
6. memory, cleanup, cancellation, and error-code gates pass;
7. one-, two-, and N-thread determinism is demonstrated;
8. Windows and Linux packaged artifacts pass clean-environment workflows;
9. exact integration-commit CI is green;
10. validation reports, checksums, licenses, and SBOM are published;
11. no proprietary binary, bypass, fake entitlement, or decompiled implementation is present;
12. documentation, issues, and machine-readable parity inventory match the release.

## Immediate implementation order

1. complete this governance synchronization and merge it after CI;
2. freeze ADAP v4.0.8 parameter/class/source-closure mapping;
3. extend the oracle and candidate reports to chromatogram records;
4. execute the same governed MS1 bytes through both;
5. retain and classify every strict difference before repair;
6. resolve only demonstrated ADAP gaps;
7. continue to smoothing, resolver, and isotope grouping;
8. only then move to the multi-file alignment/filter/gap/duplicate sequence;
9. add the second MS2/library workflow;
10. complete conformance, lifecycle, determinism, packaging, and release reports.
