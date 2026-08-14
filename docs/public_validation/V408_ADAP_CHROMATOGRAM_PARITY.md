# MZmine v4.0.8 governed ADAP Chromatogram Builder parity

## Purpose

This record documents the first direct v4.0.8 downstream feature-processing result after the already established mzML import and centroid-mass-detection gates.

The governed scope is deliberately narrow:

- non-imaging LC-MS;
- exact governed `Banane_30ngmL_001.mzML` bytes;
- explicit MS1 source data;
- centroid mass detection at noise level `0.0`;
- the exact published MZmine workflow ADAP settings;
- public v4.0.8 `ModularADAPChromatogramBuilderTask` behavior compared directly with the open-offline task;
- candidate and oracle executed in independent fresh JVM/test-worker processes;
- normalized feature/chromatogram records compared exactly.

This result is independent of ROI-MCR. ROI-MCR remains a separate fork-local experimental LC-MS method and is not evidence for MZmine v4.0.8 parity.

## Frozen provenance

### Oracle

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| Role | frozen public ADAP source/task oracle |

### Candidate evidence head

`c80c524d3961ad03140e9340377c6d9f3fa51c7a`

### Governed input

| Field | Value |
|---|---|
| Dataset | `zenodo-14001110-banane-30ngml-001` |
| File | `Banane_30ngmL_001.mzML` |
| SHA-256 | `2eb189e193925983ddf8348a13a4c96fa7382e4e790665a204251eb036aea77d` |
| Explicit source level | MS1 |
| MS1 scan count | 4,081 |

### Published workflow settings

| Field | Value |
|---|---|
| Settings file SHA-256 | `87845675b0cda32e7ce4c70a2a03f1a8ad465d18d018ede1db57fe61adee074c` |
| Centroid noise level | `0.0` |
| Minimum consecutive scans | `8` |
| Minimum intensity for consecutive scans | `50,000` |
| Minimum absolute height | `100,000` |
| Scan-to-scan m/z tolerance | `0.001 m/z or 5.0 ppm` |

The same published XML is parsed independently before each side is executed.

## Source audit

The ADAP-local public source comparison shows:

- `ADAPChromatogram.java` is byte-identical between the 3.9/open-offline and v4.0.8 lines;
- `ExpandedDataPoint.java` is byte-identical;
- parameter, module, and task source files differ and therefore require behavioral evidence rather than source inference;
- the scientific ParameterSet remains version `1`;
- the correct parameter class on both sides is `ADAPChromatogramBuilderParameters`.

The exact source identities and reviewed differences are frozen in:

`datasets/parity/v408_adap_chromatogram_builder_inventory.json`.

## Non-imaging default-sort boundary

The v4.0.8 task calls:

`FeatureListUtils.sortByDefault(newFeatureList, true)`

while the 3.9/open-offline task calls:

`FeatureListUtils.sortByDefaultRT(newFeatureList, true)`.

Direct inspection of `FeatureListUtils.sortByDefault` at the exact v4.0.8 commit shows that **non-imaging** raw data dispatch to `sortByDefaultRT`; only imaging data dispatch to m/z sorting. Therefore, replacing the unavailable broader v4 call with the RT call inside the isolated non-imaging task probe is behavior-preserving for this governed scope. The earlier hypothesis that this call-site difference changed LC-MS row ordering was rejected and retained as resolved failure history.

## Executable probe boundary

The v4.0.8 task source is frozen by Git blob SHA and transformed only through fail-closed, reviewed mechanical/boundary operations required to compile it beside the 3.9-based candidate.

The probe preparation records:

- exact oracle commit;
- exact upstream task blob;
- exact class-identifier rename count;
- the governed non-imaging default-sort mapping;
- output SHA-256.

Source closure and reviewed boundary manifests are retained in the repository and revalidated in dedicated CI.

## Failure-first execution history

### Lifecycle failure

The first real direct differential attempted to execute candidate and oracle sequentially in a single test-worker JVM. The candidate completed, but the v4.0.8 task exhausted the heap while materializing its all-data-point `ExpandedDataPoint[]` working representation. Increasing the shared-worker heap did not make the same-JVM architecture reliable.

The failure was classified as a differential-harness lifecycle problem rather than a scientific mismatch. The scientific algorithm was not modified.

### Resolution

The gate was redesigned so candidate and oracle execute in **separate fresh Gradle/JVM test-worker processes**. Each side:

1. verifies the exact mzML and settings hashes;
2. imports one raw file;
3. applies the same explicit MS1 centroid mass lists;
4. loads the same published ADAP settings;
5. runs only its own ADAP implementation;
6. writes a normalized side report;
7. exits before the other implementation starts.

A final comparison step runs only after both reports exist.

## Normalized comparison contract

Each feature/chromatogram record contains:

- final row index;
- row ID;
- m/z;
- RT;
- height;
- area;
- representative scan number;
- RT range;
- m/z range;
- intensity range;
- scan count;
- SHA-256 over the complete ordered feature series;
- deterministic first/middle/last sampled points.

The complete ordered record list is serialized deterministically and hashed independently for candidate and oracle.

## Governed result

Dedicated workflow run:

`31812225494`

Retained artifact:

- artifact ID: `9223692834`;
- ZIP SHA-256: `95c850dd90a1c83a5c4e54f764854f166a291ce77754afa7902dab6ae391c268`.

| Metric | open-offline | v4.0.8 oracle |
|---|---:|---:|
| MS1 scans with centroid input | 4,081 | 4,081 |
| ADAP feature/chromatogram records | 517 | 517 |
| Normalized record SHA-256 | `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa` | `a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa` |
| Records equal | `true` | `true` |
| First mismatch | `null` | `null` |

The comparison is exact for the normalized records. No ADAP numerical tolerance was widened or needed to convert a mismatch into equality.

## What this proves

For the frozen public input, published settings, explicit MS1 centroid input, and non-imaging LC-MS path, the open-offline fork is behaviorally equivalent to the frozen public MZmine v4.0.8 ADAP chromatogram-builder task for the recorded:

- chromatogram/feature count;
- ordering and row identity;
- m/z and RT;
- height and area;
- representative scan;
- RT, m/z, and intensity ranges;
- scan count;
- complete feature-series content;
- deterministic sampled points.

The machine-readable LC-MS parity inventory may therefore classify this **governed ADAP scope** as `Equivalent`.

## What this does not prove

This result does not establish parity for:

- imaging ADAP behavior;
- ion-mobility/IMS paths;
- other ADAP parameter combinations or defaults not exercised by the published workflow;
- other mass detectors/noise settings;
- smoothing;
- chromatographic resolving/deconvolution;
- isotope grouping;
- alignment/gap filling/filtering;
- memory-efficiency equivalence or acceptable production memory use;
- performance equivalence.

The observed heap pressure is retained as a lifecycle/performance signal for later Milestone 4 memory/load gates even though it no longer blocks the scientific differential.

## Next scientific gate

The next direct stage is **feature smoothing**.

It must start from the now-governed equivalent ADAP output, freeze the v4.0.8 smoothing source/parameter mapping, run the published smoothing settings on identical inputs, and compare complete point-series output before moving to the local-minimum resolver.
