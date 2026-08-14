# Milestone — MZmine v4.0.8 LC-MS core parity

## Objective

Demonstrate functional parity between the open-offline fork and the public LC-MS scientific core of MZmine v4.0.8 using governed public inputs, reviewed parameter mappings, normalized intermediate outputs, explicit tolerances, and cross-platform execution.

This milestone does not merge the modern upstream tree or reproduce proprietary `io.mzio` infrastructure. Scientifically relevant gaps are identified by differential execution and are adapted only from public MIT-licensed source or implemented as independently documented fork-local behavior.

## Frozen references

- fork scientific base: MZmine v3.9.0 commit `2ac3ce3b25190430f3ae0e02c28ddbb94bc248ed`;
- parity oracle: MZmine v4.0.8 exact commit `8029f930d28c0447f0acf2bcabef0a79865ad434`;
- governed 3.x benchmark: Zenodo LC-MS blank/replicate workflow using published MZmine 3.4.27 settings;
- current application version: `3.9.1` until all release gates are complete.

ROI-MCR is a separate fork-local experimental LC-MS milestone and is not evidence for Milestone 4 parity.

## Current established v4.0.8 parity

Direct differential evidence is complete for three governed capabilities:

1. **indexed centroid mzML import** with advanced import processors disabled;
2. **centroid mass detection at noise `0.0`**, including explicit MS1 and MS2 source-data selection;
3. **ADAP Chromatogram Builder** for the governed non-imaging LC-MS path using the exact published workflow settings after equivalent MS1 centroid input.

### Import / centroid result

- 5,221 imported scans and 6,477,349 spectral points;
- 4,081 MS1 scans / 6,450,546 centroid points;
- 1,140 MS2 scans / 26,803 centroid points;
- all non-RT normalized fields equivalent;
- strict RT representation differences retained;
- governed difference count `0` under the frozen RT-only binary32 contract;
- no m/z or intensity tolerance relaxation.

### ADAP result

Dedicated direct differential run `31812225494` executed candidate and v4.0.8 oracle in independent JVM/test-worker processes on the same governed bytes and settings:

- input SHA-256 `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d`;
- settings SHA-256 `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c`;
- 4,081 MS1 scans on both sides;
- published ADAP settings: 8 consecutive scans, 50,000 minimum consecutive-scan intensity, 100,000 minimum absolute height, `0.001 m/z or 5.0 ppm` tolerance;
- 517 normalized chromatogram/feature records on each side;
- candidate record SHA-256 `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa`;
- oracle record SHA-256 identical;
- `records_equal: true`;
- `first_mismatch: null`.

The ADAP records include row order/ID, m/z, RT, height, area, representative scan, RT/m/z/intensity ranges, scan count, full-series SHA-256, and deterministic sampled points. No ADAP production algorithm or numerical tolerance was changed to obtain equality.

The earlier same-JVM heap exhaustion remains lifecycle/performance evidence for later release gates; it ceased to block scientific comparison after the two sides were isolated into fresh JVMs.

## Scope

### Included

- mzML import required by selected LC-MS workflows;
- MS1/MS2 scientific metadata;
- mass detection;
- chromatogram building and smoothing;
- feature resolving;
- isotope grouping;
- alignment and gap filling;
- row filtering;
- duplicate and correlation grouping;
- spectral-library import/matching;
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

## Work packages

### 4A — governance and CI truth

- [x] v4.0.8 target and exact oracle identity;
- [x] runtime-offline versus clean-build distinction;
- [x] TaskController declared scope completed;
- [x] standard CI on `open-offline-main` and `agent/**`;
- [x] exact tested commit metadata;
- [x] repository-global failure-first `F-xxx` process;
- [x] machine-readable import/centroid state synchronized;
- [x] machine-readable ADAP state synchronized after direct evidence;
- [ ] keep issues/docs/inventory synchronized after every later promotion.

### 4B — machine-readable v4.0.8 inventory

Foundation complete:

- [x] 12-capability schema/inventory;
- [x] frozen source roots and oracle commit;
- [x] governed datasets and tolerance profiles;
- [x] import and mass-detection source/parameter mappings;
- [x] ADAP source/parameter mapping and stage-specific direct evidence;
- [x] three directly demonstrated capabilities promoted to `Equivalent`.

Still required per downstream capability:

- [ ] parameter additions/removals/renames/transformations;
- [ ] algorithm/data-model changes;
- [ ] required fixture/tolerance/evidence.

Source equality alone never permits `Equivalent`.

### 4C — differential execution harness

Established:

- [x] identical governed input bytes;
- [x] independent oracle/candidate producers;
- [x] Java/OS/commit/settings/input provenance;
- [x] normalized intermediate records;
- [x] complete difference retention;
- [x] fail-closed comparison;
- [x] CI artifact retention;
- [x] isolated-producer execution for memory-intensive downstream stages where needed.

Completed stages:

- [x] import;
- [x] centroid mass detection — MS1 and MS2;
- [x] ADAP Chromatogram Builder.

**Next stage:**

- [ ] feature smoothing.

Then:

- [ ] local-minimum resolver;
- [ ] isotope grouping;
- [ ] alignment;
- [ ] row filtering;
- [ ] gap filling;
- [ ] duplicate filtering;
- [ ] correlation/adduct grouping;
- [ ] MS2 association and library matching;
- [ ] normalized final export.

### 4D — first differential workflow

Single-file chain:

- [x] import;
- [x] centroid mass detection;
- [x] ADAP chromatogram construction;
- [ ] smoothing;
- [ ] local-minimum feature resolving;
- [ ] isotope grouping.

Multi-file chain after the single-file stages are governed:

- [ ] blank + replicate 1 + replicate 2 alignment;
- [ ] row-filter decisions without relabeling the MZmine rows filter as an E&L blank classifier;
- [ ] gap filling with one, two, and N threads;
- [ ] duplicate groups and representatives;
- [ ] normalized final export.

Correlation grouping requires a larger public replicate set than the minimal blank/replicate fixture.

### 4E — second workflow with MS2 or MSe

At least one additional public workflow must exercise a core capability not demonstrated by the first benchmark.

Preferred order:

1. feature-to-MS2 association plus spectral-library matching;
2. official public MSe reference;
3. ion/adduct identity workflow.

### 4F — mzML conformance

Still required:

- [ ] indexed and non-indexed;
- [ ] zlib and uncompressed arrays;
- [ ] 32-bit and 64-bit arrays;
- [x] centroid coverage on the first governed file;
- [ ] profile spectra;
- [ ] positive and mixed polarity beyond the current negative fixture;
- [ ] alternate RT units;
- [x] MS1/MS2 and exercised precursor/isolation/charge/activation metadata;
- [ ] MSn;
- [ ] empty/incomplete scans;
- [ ] malformed/truncated failure behavior.

### 4G — lifecycle, memory, cancellation, errors

- [ ] close raw/feature data deterministically;
- [ ] release mapped/temp paths and file handles on Linux/Windows;
- [ ] delete files after successful and failed runs;
- [ ] repeated batches without unbounded heap/native/thread/handle growth;
- [ ] cancellation during import and scientific processing;
- [ ] recovery after module failure;
- [ ] structured CLI exit codes;
- [ ] `run-report.json`.

The ADAP same-JVM OOM is retained as evidence for this package even though isolated producers resolved the parity harness.

### 4H — performance and determinism

- [ ] runtime by module;
- [ ] peak heap/RSS;
- [ ] mapped/temp storage;
- [ ] throughput;
- [ ] repeated-run equality;
- [ ] one-/two-/N-thread comparison;
- [ ] file-order comparison;
- [ ] explicit regression thresholds.

### 4I — Waters conversion compatibility

Native Waters RAW is not required for this milestone.

- [ ] freeze public Waters source bytes/license;
- [ ] freeze converter/version/command/filters;
- [ ] freeze converted mzML hash;
- [ ] validate acquisition metadata and repeatable processing.

### 4J — packaged release candidate

- [ ] Windows portable artifact;
- [ ] Linux artifact;
- [ ] clean-environment GUI/headless workflows;
- [ ] spaces/Unicode/non-admin paths;
- [ ] cleanup/log verification;
- [ ] checksums, dependencies, licenses, SBOM;
- [ ] machine-readable and human-readable final validation reports.

## Failure-first rule

Every new runtime, infrastructure, representation, or scientific discrepancy receives a repository-global `F-xxx` record **before correction**, containing original evidence, impact, root-cause hypothesis/confidence, scientific/provenance risk, proposed correction, and predefined validation criteria.

The original strict evidence is retained after resolution. Tolerance broadening is prohibited as a convenience repair.

## Release acceptance criteria

A 4.0-derived version may be assigned only when:

1. declared LC-MS scope is frozen;
2. at least two public workflows pass direct v4.0.8 differential comparison;
3. relevant intermediate/final values meet explicit contracts;
4. centroid/profile, positive/negative, and MS2 coverage are adequate for the declared scope;
5. memory, cleanup, cancellation, and error-code gates pass;
6. one-/two-/N-thread determinism is demonstrated where applicable;
7. Windows/Linux packaged artifacts pass clean-environment workflows;
8. exact integration-commit CI is green;
9. validation reports, checksums, licenses, and SBOM are published;
10. no proprietary binary, bypass, fake entitlement, or decompiled implementation is present;
11. documentation, issues, and machine-readable inventory match the release.

## Immediate implementation order

1. freeze v4.0.8 smoothing source/parameter mapping;
2. execute the published smoothing step independently on the governed equivalent ADAP output;
3. compare complete smoothed point series and derived feature values;
4. register every discrepancy before repair;
5. continue to local-minimum resolving and isotope grouping;
6. move to the multi-file alignment/filter/gap/duplicate chain;
7. add the second MS2/library workflow;
8. complete conformance, lifecycle, determinism, packaging, and release reports.
