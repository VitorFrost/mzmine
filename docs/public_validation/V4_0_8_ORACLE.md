# MZmine v4.0.8 oracle — frozen executable reference

Status: **exact identity frozen; independent public scientific source slice compiled and executed; import/centroid oracle operational**.

This document records the immutable upstream source reference and the independently governed executable slice used by Milestone 4. It must remain consistent with `datasets/parity/mzmine_v408_lcms_core_inventory.json` and the retained differential evidence.

## Frozen identity

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Exact commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Source tree | `mzmine-community` |
| License | MIT |
| Oracle Java | 21 with preview enabled |
| Fork candidate Java | 20 |
| Intended role | Behavioral oracle for the declared public LC-MS scientific core |

The tag resolves directly to the commit above. Scientific comparisons use source frozen at that commit, not only the version-bump diff.

## Independence resolution

The unmodified upstream build graph declares proprietary `io.mzio` coordinates, so the project did not use the full application build as the oracle. Instead, the repository now constructs a reviewed public scientific source slice for the capability under test.

For the established import/centroid gate the slice contains:

- 48 reachable public/boundary types represented by 47 Java files;
- 36 verified upstream public classes;
- 12 exact, locked boundary adapters;
- zero prohibited `io.mzio` references;
- zero unresolved internal references;
- a behavior-free, non-instantiable nullable `MemoryMapStorage` type boundary where the public source references a type unavailable in the public tree.

The adapters are not treated as upstream implementations. They are separately hashed and contract-checked boundaries that exclude unrelated application infrastructure while preserving the public scientific behavior exercised by the oracle.

Current source-closure SHA-256:

`6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6`

Adapter byte-lock SHA-256:

`1f3fa33550bfd1742c1af0b809ebb700794979553489d6278731f43cfdfb2e43`

Prepared-source manifest SHA-256:

`b1cbac55fbe28a32dda9fea0043bb9b97879b2cb0bece89ca12a0ddc494df23a`

The oracle execution state is therefore:

```text
execution_status = complete
```

for the currently declared parser/centroid source slice. This does not imply that every future downstream v4.0.8 module is already available in the oracle; each added scientific stage must extend and re-audit the source closure.

## Established executable behavior

The governed oracle has executed the exact public `Banane_30ngmL_001.mzML` bytes and produced independent normalized reports for both explicit source-data selections:

- MS1;
- MS2.

The open-offline candidate produced its report independently using its own parser and `CentroidMassDetector`. The comparator retained strict differences and separately applied the frozen RT-only tolerance contract.

The established result is:

- imported scans: `5,221` on both sides;
- imported spectral points: `6,477,349` on both sides;
- selected MS1 scans: `4,081` on both sides;
- MS1 centroid points at noise `0.0`: `6,450,546` on both sides;
- selected MS2 scans: `1,140` on both sides;
- MS2 centroid points at noise `0.0`: `26,803` on both sides;
- all non-RT normalized fields equivalent;
- governed MS1 differences: `0`;
- governed MS2 differences: `0`.

The only strict numerical differences were deterministic retention-time representation effects from the legacy binary32 storage path. The governed contract applies only to retention time and does not relax m/z or intensity tolerances.

Complete evidence is recorded in `docs/public_validation/V408_IMPORT_MASS_DETECTION_PARITY.md`.

## What this oracle currently proves

Within the governed dataset and explicit settings, the executable oracle supports a direct parity claim for:

- indexed centroid mzML parsing;
- scan ordering and membership;
- MS level, polarity, spectrum type, and spectral values;
- precursor, isolation, charge, activation, and parent-scan metadata exercised by the file;
- explicit user selection of MS1 or MS2 as source data;
- centroid mass detection at noise level `0.0`.

It does not yet prove downstream parity for chromatogram construction, smoothing, feature resolving, isotope grouping, alignment, gap filling, row filtering, duplicate/correlation grouping, feature-to-MS2 association, or library matching.

## Next oracle extension

The next source-closure and differential extension is **ADAP Chromatogram Builder** on the validated MS1 path.

Required evidence before any ADAP difference is repaired:

1. freeze the exact v4.0.8 ADAP classes, parameter mapping, and reachable source closure;
2. run the identical governed mzML bytes after the already validated import/centroid stage;
3. emit stable chromatogram membership and point-series records;
4. compare counts, membership, m/z, RT range, intensities, point count, and status;
5. retain every strict difference;
6. register each implementation/runtime/scientific failure as a new `F-xxx` record before correction;
7. do not promote ADAP from `Adapted` to `Equivalent` until the governed differential gate passes.

## Acceptance state for issue #29

| Criterion | Current state |
|---|---|
| Exact commit frozen and documented | Passed |
| Java/Gradle requirements documented | Passed |
| Reproducible public scientific-slice build tested | Passed |
| Prohibited proprietary binary absent from the slice | Passed |
| Runnable scientific oracle produced | Passed for import/centroid scope |
| Direct behavioral comparison retained | Passed for import/centroid scope |

Issue #29 is therefore complete for its declared oracle-freeze objective. Future downstream oracle extensions are tracked under their capability-specific Milestone 4 work.

## Non-goals

- merging upstream `master`;
- redistributing proprietary binaries;
- reconstructing unavailable `io.mzio` behavior;
- treating source-file identity as behavioral equivalence;
- claiming complete MZmine 4.0 parity from the current import/centroid result.
