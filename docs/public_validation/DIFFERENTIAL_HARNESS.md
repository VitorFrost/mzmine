# MZmine v4.0.8 differential harness

## Purpose

This harness is the evidence boundary between the independent open-offline fork and the public
scientific behavior of MZmine v4.0.8. It prevents a module from being called equivalent merely
because a class exists, a Git blob is unchanged, or a final CSV happens to look plausible.

The first executable contract covers:

1. ordinary mzML import;
2. explicit centroid mass detection;
3. normalized per-scan records;
4. deterministic comparison that reports every detected difference before failing.

Downstream contracts are inventoried now, but their report producers and comparators remain pending.

## Frozen oracle

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Exact commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Tag object | direct commit |
| Public source tree | `mzmine-community` |
| License | MIT |
| Java toolchain | Java 21 |
| Preview features | enabled |
| Build system | Gradle Wrapper |

The unmodified v4.0.8 source tree is **not yet an accepted executable oracle** for this fork. Its
version catalog declares `io.mzio` coordinates that are prohibited by the open-offline independence
policy. The next implementation step is therefore to define a reproducible public scientific-slice
build without proprietary binaries, fake authentication/entitlement, decompilation, or reconstructed
closed behavior.

Until that build exists, v4.0.8 is a frozen public source oracle and the differential report producer
for the oracle remains pending.

## Machine-readable files

- `datasets/parity/mzmine_v408_lcms_core_inventory.schema.json` — inventory contract;
- `datasets/parity/mzmine_v408_lcms_core_inventory.json` — frozen 12-capability inventory;
- `datasets/parity/differential_stage_report.schema.json` — normalized import/mass-detection report;
- `scripts/validate_mzmine_4_parity_inventory.py` — fail-closed project validator;
- `scripts/compare_mzmine_stage_reports.py` — deterministic differential comparator.

The JSON Schema files document the data shape. The standard-library Python validators enforce
project-specific invariants without adding a new CI dependency.

## Inventory states

The only permitted classifications are:

- **Equivalent** — direct differential evidence is complete and within reviewed tolerances;
- **Adapted** — the capability exists, but parameters, algorithm, data model, or integration differ;
- **Not implemented** — the public capability is in the declared LC-MS scope but absent;
- **Out of scope** — excluded by the declared scientific or independence boundary.

`Equivalent` is rejected automatically when `direct_differential_complete` is false.

The initial inventory contains exactly these capabilities:

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

All currently remain `Adapted`. Source presence is evidence, but not parity.

## First report contract

Each report records:

- repository, ref, exact commit and application version;
- Java version, operating system, thread count and exact command;
- governed dataset ID, relative path, byte size and SHA-256;
- settings mapping ID and settings SHA-256;
- sorted normalized records for `import` and/or `mass_detection`.

Per-scan import records include:

- stable `scan_key` and scan number;
- MS level;
- polarity;
- spectrum representation;
- retention time normalized to minutes;
- point count, m/z bounds and intensity sum;
- deterministic sampled data points;
- precursor/isolation metadata when present.

Mass-detection records use the same stable scan key and compare the resulting mass list. The first
mapping explicitly selects the centroid detector, disables denormalization, and does not request the
legacy optional NetCDF output.

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
- `2` — malformed report, provenance mismatch precondition, or invalid tolerance configuration.

The comparator:

- rejects unsorted or duplicate scan keys;
- rejects unsorted or duplicate sampled-point indexes;
- rejects non-finite numeric values;
- compares exact provenance and categorical fields;
- applies explicit absolute/relative numerical tolerances;
- accumulates all differences rather than stopping at the first one;
- creates a deterministic SHA-256 over the canonical comparison result.

## Current evidence

Completed in this contract-first step:

- exact v4.0.8 tag commit frozen;
- Java 21/preview Gradle build requirements recorded;
- independence blocker recorded rather than bypassed;
- 12 module source paths inventoried;
- source Git blob identity/difference frozen for both code lines;
- import and mass-detection report schema defined;
- deterministic comparator implemented;
- fail-closed inventory validator implemented;
- unit tests cover equality, tolerances, accumulated differences, ordering, duplicate indexes,
  unknown datasets, missing modules, and false equivalence claims.

Still required before the first parity result:

1. create a reviewed public scientific-slice build for the v4.0.8 oracle;
2. implement the same normalized report producer against v4.0.8 and open-offline;
3. execute both against the exact frozen `Banane_30ngmL_001.mzML` bytes;
4. retain both reports and the complete comparison report as CI artifacts;
5. inspect every difference before changing scientific code;
6. promote a capability only after reviewed evidence satisfies the gate.

## Non-goals

This harness does not:

- retrieve or redistribute `io.mzio` binaries;
- emulate a successful login or entitlement;
- decompile proprietary code;
- merge current upstream `master` into the fork;
- claim GUI, source, binary, cloud, or account compatibility;
- change the application version to 4.0;
- treat identical source blobs as sufficient evidence.
