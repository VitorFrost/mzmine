# MZmine v4.0.8 differential harness

## Purpose

This harness is the evidence boundary between the independent open-offline fork and the public
scientific behavior of MZmine v4.0.8. It prevents a capability from being called equivalent merely
because a class exists, a Git blob is unchanged, or a final export happens to look plausible.

The first executable differential slice is now complete for:

1. governed indexed-centroid mzML import;
2. explicit manual MS1 or MS2 source-data selection;
3. centroid mass detection at noise level `0.0`;
4. normalized per-scan and mass-list records;
5. strict and governed deterministic comparison that reports every detected difference.

The harness is now being extended downstream, beginning with ADAP Chromatogram Builder. Downstream
capabilities remain `Adapted` or `Not implemented` until their own direct differential evidence is
complete.

## Frozen oracle

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Exact commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Tag object | direct commit |
| Public source tree | `mzmine-community` |
| License | MIT |
| Oracle toolchain | Java 21 with preview enabled |
| Fork toolchain | Java 20 |
| Executed oracle scope | reviewed non-IMS mzML parser + metadata + primitive arrays + centroid primitive-array detector |

The **full unmodified application tree is not treated as the executable oracle**. Its version catalog
contains `io.mzio` coordinates that are prohibited by the fork's independence policy. Instead, the
accepted executable oracle for the first governed slice is a reviewed public-source closure built
from the exact v4.0.8 commit.

That closure:

- contains 48 reachable top-level types represented by 47 Java source files;
- uses 12 reviewed, version-locked boundary adapters;
- reaches no prohibited `io.mzio` reference;
- preserves only the public scientific members required by the declared parser/centroid comparison;
- uses a behavior-free null-only `MemoryMapStorage` compile-time boundary;
- compiles and executes under Java 21 without reconstructing proprietary behavior.

Source-closure evidence is documented in `V408_SOURCE_CLOSURE.md`; executable/oracle status is
summarized in `V4_0_8_ORACLE.md`.

## Machine-readable files

- `datasets/parity/mzmine_v408_lcms_core_inventory.schema.json` — inventory contract;
- `datasets/parity/mzmine_v408_lcms_core_inventory.json` — current 12-capability inventory;
- `datasets/parity/differential_stage_report.schema.json` — normalized import/mass-detection report;
- `datasets/parity/v408_import_mass_detection_tolerances_v1.json` — narrow RT-only governed
  tolerance contract for the first slice;
- `datasets/parity/v408_source_closure_manifest.json` — frozen parser/centroid public-source closure;
- `scripts/validate_mzmine_4_parity_inventory.py` — fail-closed project validator;
- `scripts/compare_mzmine_stage_reports.py` — deterministic differential comparator;
- `scripts/audit_v408_source_closure.py` — source-closure auditor;
- `scripts/prepare_v408_executable_slice.py` — verified executable-source preparation.

The JSON Schema files document data shape. Standard-library Python validators add project-specific,
fail-closed invariants without introducing another CI dependency.

## Inventory states

The only permitted classifications are:

- **Equivalent** — direct differential evidence is complete and within reviewed tolerances for a
  narrowly declared scope;
- **Adapted** — the capability exists, but parameters, algorithm, data model, integration, or direct
  evidence differ from the target;
- **Not implemented** — the public capability is in the declared LC-MS scope but implementation or
  adequate evidence is absent;
- **Out of scope** — excluded by the declared scientific, legal, or independence boundary.

`Equivalent` is rejected automatically when `direct_differential_complete` is false.

The inventory contains exactly these twelve initial scientific capabilities:

1. mzML import;
2. mass detection;
3. ADAP chromatogram builder;
4. smoothing;
5. minimum-search resolver;
6. isotope finder;
7. rows filter;
8. join aligner;
9. multithread peak finder;
10. duplicate filter;
11. correlation grouping;
12. legacy CSV export.

Current direct-evidence state:

- `mzml-import` — **Equivalent** only for the governed indexed-centroid, no-advanced-import slice;
- `mass-detection` — **Equivalent** only for the centroid detector at noise `0.0` with explicit MS1
  or MS2 source selection;
- every downstream inventory entry — still **Adapted** pending its own differential gate.

Source presence or identical source blobs remain provenance evidence, not behavioral parity.

## First completed report contract

Each import/centroid report records:

- repository, ref, exact commit and application identity;
- Java version, operating system, thread count and exact command;
- governed dataset ID, portable relative path, byte size and SHA-256;
- source MS-level selection;
- settings mapping ID and settings SHA-256;
- sorted normalized records for `import` and `mass_detection`.

Per-scan import records include:

- stable `scan_key` and scan number;
- MS level;
- polarity;
- spectrum representation;
- retention time normalized to minutes;
- point count, m/z bounds and intensity sum;
- deterministic sampled spectral points;
- precursor/isolation/charge/activation metadata when present.

Mass-detection records use the same stable scan key and compare the resulting mass list for the
explicitly selected source level. The governed mapping selects the centroid detector at noise `0.0`,
disables fragment-scan denormalization, and does not request legacy optional NetCDF output.

## Comparator behavior

Run:

```bash
python scripts/compare_mzmine_stage_reports.py \
  build/parity/oracle-v408.json \
  build/parity/open-offline.json \
  --output build/parity/comparison.json
```

Exit codes:

- `0` — equivalent within declared tolerances;
- `1` — valid reports differ;
- `2` — malformed report, provenance/precondition mismatch, or invalid tolerance configuration.

The comparator:

- rejects unsorted or duplicate record keys;
- rejects unsorted or duplicate sampled-point indexes;
- rejects non-finite numeric values;
- compares categorical and membership fields exactly;
- applies only explicitly declared absolute/relative numerical tolerances;
- accumulates all differences rather than stopping at the first one;
- creates a deterministic SHA-256 over the canonical comparison result.

For the first parity result, every run preserves a **strict** comparison and then evaluates a separate
**governed** comparison. The strict report is never rewritten or discarded merely because a
representation-specific tolerance is later accepted.

## Completed first parity evidence

Governed input:

- dataset: `zenodo-14001110-banane-30ngml-001`;
- file: `Banane_30ngmL_001.mzML`;
- size: `87,090,777` bytes;
- SHA-256: `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d`.

Import on both sides:

- 5,221 scans;
- 6,477,349 spectral points.

Explicit MS1 path on both sides:

- 4,081 selected scans;
- 6,450,546 centroid points.

Explicit MS2 path on both sides:

- 1,140 selected scans;
- 26,803 centroid points.

All normalized non-RT values matched. Strict differences were deterministic retention-time
representation differences caused by the legacy binary32 seconds conversion. The accepted contract
is limited to retention time:

```json
{
  "retention_time_minutes": {
    "absolute": 1e-7,
    "relative": 6e-8
  }
}
```

No m/z or intensity tolerance was broadened. Governed difference count is zero for both the MS1 and
MS2 paths. Complete hashes, strict difference counts, failure records F-015 through F-018, and scope
limits are retained in `V408_IMPORT_MASS_DETECTION_PARITY.md`.

## Failure-first rule

Any newly observed runtime, infrastructure, representation, or scientific discrepancy must be
registered before corrective implementation with the next `F-xxx` identifier. The record must
contain:

1. original observation and retained evidence;
2. affected gate and impact;
3. root-cause hypothesis with confidence;
4. scientific/provenance risk;
5. proposed correction **before** implementation;
6. validation criteria defined **before** implementation;
7. final resolution while retaining the original failure history.

A tolerance change is a scientific/governance change, not a convenience fix. It requires independent
characterization and must remain narrow enough that unrelated m/z, intensity, membership, ordering,
or algorithmic differences cannot be hidden.

## Active extension — ADAP Chromatogram Builder

Issue #50 is the next scientific gate. It reuses the already validated MS1 import/centroid path but
must independently freeze the v4.0.8 ADAP task/parameter/source closure and candidate mapping.

The normalized chromatogram contract must retain enough structure to expose algorithmic membership
changes. At minimum it must include:

- deterministic chromatogram key;
- ordered source scan membership;
- first/last source scan;
- point count;
- representative/working m/z where defined;
- RT start/end and apex RT where defined;
- maximum intensity/height where defined;
- complete or otherwise explicitly governed point series containing scan, RT, m/z and intensity;
- status/terminal state;
- total chromatogram and point counts;
- producer/input/settings provenance and deterministic hashes.

The oracle and candidate must execute independently. Every strict difference must be emitted and
registered before repair. Import/centroid tolerances or scientific behavior must not be weakened to
make ADAP pass.

After ADAP, the same differential design proceeds through smoothing, local-minimum resolving,
isotope grouping, and then the blank/replicate multi-file stages.

## Non-goals

This harness does not:

- retrieve or redistribute `io.mzio` binaries;
- emulate login, entitlement, licensing, or cloud services;
- decompile or reconstruct proprietary code;
- merge current upstream `master` into the fork;
- infer manufacturer or acquisition intent from the user's MS-level choice;
- claim GUI, source, binary, cloud, or account compatibility;
- treat identical source blobs as sufficient evidence;
- treat ROI-MCR as evidence for MZmine v4.0.8 parity;
- change the application version to 4.0 before the declared release gates pass.
