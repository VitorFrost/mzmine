# Published MZmine workflow compatibility

## Source and purpose

This benchmark uses the public `MZmine processing settings.xml` from Zenodo record `14000687`,
licensed CC BY 4.0. Its frozen verification record is:

- byte size: `17,443`;
- repository MD5: `f8cf3ca41f2702c1781c1a55742e374f`;
- SHA-256: `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c`;
- XML root: `batch`;
- XML `mzmine_version`: `3.4.27`;
- ordered processing steps: `10`;
- parameter elements: `179`;
- unique parameter names: `122`.

The associated article describes MZmine 3.4.16, while the actual settings XML identifies itself as
3.4.27. The exact XML metadata and frozen hash are authoritative for this compatibility benchmark.

The workflow is a real public scientific reference. It establishes configuration and execution
compatibility with a complex 3.x workflow. It is not direct proof of mzmine 4.0.8 parity; that requires
separate differential execution against the frozen public 4.0.8 oracle.

## Governed inputs

All files are from Zenodo record `14001110`, licensed CC BY 4.0, downloaded through HTTPS, verified by
size and SHA-256, and deleted from CI workspaces after use.

| Role | File | Size | SHA-256 | Imported scans |
|---|---|---:|---|---:|
| Technical replicate 1 | `Banane_30ngmL_001.mzML` | 87,090,777 | `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d` | 5,221 |
| Technical replicate 2 | `Banane_30ngmL_002.mzML` | 86,540,418 | `350292e2497d9df1c1139f67dba27f333331c1ff6902e25b36d5fcba8c713214` | 5,232 |
| Analytical blank | `blank_001.mzML` | 86,111,130 | `3bd67cbe55e6e1d7dbe5073985a263621045eb5f51a4809070e1ae0019764aab` | 5,207 |

All three files are negative-polarity centroid mzML files and have frozen MS1/MS2, RT, m/z-range,
data-point, and empty-scan assertions.

## Parameter compatibility result

The published workflow contains ten ordered processing modules:

1. Mass detection;
2. ADAP chromatogram builder;
3. Smoothing;
4. Local-minimum feature resolver;
5. Isotopic peaks finder;
6. Feature-list rows filter;
7. Join aligner;
8. Peak finder, multithreaded;
9. Duplicate peak filter;
10. Correlation grouping (`metaCorrelate`).

The exact source classes and parameter-set versions exist in the open-offline 3.9 tree.

The compatibility audits established on Java 20/Linux/Windows:

- 10 ordered processing steps;
- 93 top-level parameters audited;
- 235 semantic XML nodes audited;
- zero unknown parameters;
- zero loading exceptions;
- zero parameter-set version mismatches;
- zero missing local top-level parameters;
- zero semantic changes after narrowly scoped representation normalization;
- zero non-idempotent serializers;
- no silent parameter loss.

The two accepted representation normalizations are deliberately narrow:

- duplicate tokens in the explicitly named `Chemical elements` set are deduplicated while preserving
  first-seen order;
- a leading slash before a Windows drive prefix is normalized only for the `current_file` XML element.

Free-text parameters are not normalized by either rule.

## Executed workflow

The generated headless batch contains twelve sequential stages:

1. approved mzML import;
2. published Mass detection;
3. published ADAP chromatogram builder;
4. published Smoothing;
5. published Local-minimum feature resolver;
6. published Isotopic peaks finder;
7. published Feature-list rows filter;
8. published Join aligner;
9. published multithreaded Peak finder;
10. published Duplicate peak filter;
11. published Correlation grouping;
12. deterministic legacy CSV export.

Dynamic `BATCH_LAST` raw-data and feature-list selectors are validated by `BatchTask` after runtime
binding. The fixture generator does not incorrectly reject them before the preceding stage has
produced the selected objects.

## Frozen cross-platform result

The same workflow passed on Java 20 in Ubuntu and Windows:

- process return code: `0`;
- timeout: `false`;
- all twelve stages entered in order;
- final `Finished a batch of 12 steps` marker present;
- authentication, licensing, and telemetry tokens: `0`;
- extracted error lines: `0`;
- fallback prefix batches: not required.

Scientific checkpoints were identical:

| Checkpoint | Blank | Technical replicate |
|---|---:|---:|
| Imported scans | 5,207 | 5,232 |
| Smoothed chromatograms | 285 | 503 |
| Resolved features | 131 | 391 |
| Isotope patterns | 101 | 309 |
| Gap-filled features | 27 | 130 |

Additional final checkpoints:

- duplicate rows removed: `13`;
- final aligned/exported rows: `176`;
- CSV rows including header: `177`;
- CSV size: `22,422` bytes;
- CSV SHA-256: `139a02d3305280937783acc1ac730fbb4c719c4e80730c2bf864f68806e89c11`;
- Linux and Windows output: byte-identical.

One nonfatal isotope warning at m/z `61.0378` was reproduced on both platforms. The rows-filter stage
continued normally and the workflow completed. The warning is retained as part of the reference log
rather than hidden.

The correlation-grouping stage completed but found no useful row-to-row correlations in the minimal
blank-plus-single-replicate execution. Completion is therefore operational evidence only; scientific
correlation-group parity remains unvalidated and requires a larger replicate corpus.

## Current module classification

| # | Published module | 3.x compatibility evidence | 4.0.8 status |
|---:|---|---|---|
| 1 | Mass detection | Parameters and real workflow validated | Differential mass-list comparison pending |
| 2 | ADAP chromatogram builder | Parameters and real workflow validated | Differential feature-series comparison pending |
| 3 | Smoothing | Parameters and frozen output count validated | Differential point-series comparison pending |
| 4 | Local-minimum resolver | Synthetic and real workflow validated | Differential split/value comparison pending |
| 5 | Isotopic peaks finder | Frozen pattern counts validated | Membership/charge comparison pending |
| 6 | Feature-list rows filter | Workflow completed without silent parameter loss | Per-row decision comparison pending |
| 7 | Join aligner | Synthetic and public workflow validated | Replicate membership comparison pending |
| 8 | Multithreaded peak finder | Frozen gap counts validated | 1/2/N-thread and v4.0.8 comparison pending |
| 9 | Duplicate peak filter | Frozen removal count validated | Duplicate groups/representatives pending |
| 10 | Correlation grouping | Operational completion only | Multi-replicate scientific benchmark required |

## Blank-handling boundary

The published rows filter is not an extractables-and-leachables blank-subtraction model. Its behavior
must be recorded exactly as published.

A separate analytical blank-classification layer may later add:

- sample/blank ratios;
- detection consistency across replicates;
- carryover or cleaning-file evidence;
- reporting categories and confidence levels.

That layer must remain distinct from claims of published workflow compatibility or mzmine 4.0.8
parity.

## Next evidence

This benchmark is now complete for its stated 3.x compatibility purpose. Its next use is as one input
to the differential 4.0.8 harness:

1. freeze the corresponding v4.0.8 modules and parameter mappings;
2. execute the same governed mzML bytes where the 4.0.8 reference can accept them;
3. capture normalized intermediate records after each stage;
4. compare counts, memberships, m/z, RT, height, area, point counts, statuses, and filter decisions;
5. classify each module as equivalent, adapted, not implemented, or out of scope.

No proprietary `io.mzio` binary, authentication service, licensing behavior, or decompiled source is
part of this benchmark.
