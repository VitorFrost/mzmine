# MZmine v4.0.8 oracle — frozen reference

Status: **identity frozen; executable oracle blocked pending an independent public-source build**.

This document records the immutable upstream source reference for Milestone 4B. It does not claim that issue #29 is complete.

## Frozen identity

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Exact commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Source tree in scope | `mzmine-community` |
| License | MIT |
| Intended role | Behavioral comparison oracle for the public LC-MS scientific core |

The tag resolves directly to the commit above. The commit itself is the patch-version update for the v4.0.8 release; scientific comparisons must therefore use the complete source tree at that commit, not only its final version-bump diff.

## Frozen toolchain metadata

The source and wrapper metadata at the frozen commit specify:

- Java 21;
- preview features enabled by the project build;
- Gradle Wrapper 8.5 (`gradle-8.5-bin.zip`).

Candidate public-source compilation command:

```bash
git clone https://github.com/mzmine/mzmine.git
cd mzmine
git checkout --detach 8029f930d28c0447f0acf2bcabef0a79865ad434
./gradlew :mzmine-community:classes --no-daemon
```

This command is recorded as the initial candidate for the public scientific source slice. It is **not yet an accepted reproducible oracle build**.

## Independence blocker

The frozen v4.0.8 version catalog declares `io.mzio` coordinates. The open-offline validation policy prohibits downloading, bundling, reconstructing, stubbing as equivalent, or fabricating entitlement for proprietary implementations.

Therefore the unmodified v4.0.8 tree is currently classified as:

```text
execution_status = blocked-by-independence-policy
```

Before the source can be used as an executable differential oracle, a reviewed build procedure must demonstrate that the public LC-MS scientific slice can compile and run without prohibited binaries and without silently removing scientific behavior under comparison.

Acceptable outcomes are:

1. a reproducible public-source build that excludes only out-of-scope account, GUI, vendor, or proprietary integration code and documents every exclusion; or
2. a documented failure showing which public scientific classes are inseparable from prohibited dependencies, leaving the direct executable comparison blocked.

## Release-note relevance

The v4.0.8 release notes include changes outside the direct LC-MS feature-processing scope. Two import-adjacent changes require explicit inventory review:

- mzXML MS1/MS2 closing-tag handling;
- Thermo parser extraction-path handling.

Neither should be assumed relevant to governed mzML behavior, but both must remain recorded as candidate import differences until the module inventory and differential tests resolve them.

## Relationship to the parity inventory

The machine-readable inventory in `datasets/parity/mzmine_v408_lcms_core_inventory.json` is the source of truth for:

- oracle repository, tag, and commit;
- Java and Gradle build metadata;
- independence blockers;
- module classifications;
- parameter-mapping status;
- governed datasets and tolerance profiles;
- differential-gate status.

This Markdown document is a human-readable summary and must not contradict the validated inventory.

## Acceptance state for issue #29

| Criterion | Current state |
|---|---|
| Exact commit frozen and documented | Passed |
| Java/Gradle requirements documented | Passed |
| Reproducible build tested | **Pending** |
| No proprietary `io.mzio` binary required | **Not demonstrated; currently blocked** |
| Runnable headless scientific oracle produced | **Pending** |

Issue #29 must remain open until the pending criteria are supported by retained CI or local-build evidence.

## Non-goals

- merging upstream `master`;
- changing the fork application version;
- redistributing proprietary binaries;
- treating source-file identity as behavioral equivalence;
- claiming parity before differential reports pass.
