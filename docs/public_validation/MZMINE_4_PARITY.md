# MZmine 4.0 functional-parity plan

## Target definition

The first external target is the public scientific behavior of the MZmine 4.0 release line, using
`v4.0.3` as the initial release reference and `v4.0.8` as its maintenance checkpoint.

“Equivalent” means that an open-offline implementation can execute the same relevant scientific
workflow and produce equivalent structured results within documented tolerances. It does not require
identical internal classes, identical GUI layout, or binary compatibility.

The following are explicitly outside the parity target:

- account creation and user profiles;
- authentication and Keycloak clients;
- license validation or commercial feature entitlements;
- `io.mzio` proprietary binaries;
- cloud services and mandatory network dependencies;
- reconstruction from decompiled or distributed proprietary code.

## Status vocabulary

Each public 4.0 capability is assigned one of four states:

- **equivalent** — same relevant inputs and scientifically equivalent outputs;
- **adapted** — same analytical purpose with a documented open implementation or interface change;
- **not implemented** — public functionality is relevant but has not yet been ported;
- **out of scope** — proprietary, commercial, network-only, vendor-restricted, or irrelevant to the
  low-resolution LC-MS objective.

A source class being present is not sufficient for equivalent status. Equivalence requires frozen
public input, deterministic output assertions, and relevant platform/thread coverage.

## Current matrix

| Area | Capability | State | Evidence/gap |
|---|---|---|---|
| Startup | Offline headless startup | Equivalent | Linux/Windows smoke test without login |
| Batch | XML batch execution | Equivalent for validated 3.9 workflow | Five-step deterministic reference |
| Tasks | Blocking task execution | Adapted | Public 2024 API surface ported to Java 20 |
| Tasks | Bounded executors and shutdown | Not implemented | Milestone 2B |
| Tasks | Scheduler independent of JavaFX/Desktop | Not implemented | Milestone 2C |
| Memory | Open memory-map storage | Adapted baseline | Lifecycle/load tests still required |
| Import | Basic indexed mzML | Equivalent for current fixtures | Synthetic fixture plus frozen Zenodo real mzML |
| Import | Real centroided negative LC-MS | Equivalent for frozen input | 5,221 scans validated on Linux/Windows |
| Import | MS1/MS2 metadata and scan counts | Equivalent for frozen input | 4,081 MS1 and 1,140 MS2 assertions |
| Import | Broader profile/centroid coverage | Partial | Positive/profile/mixed-polarity fixtures pending |
| Import | Waters vendor/open formats | Not implemented as a public gate | MSV000091372 selected; reliable transfer pending |
| Workflow | Published MZmine XML inventory | Equivalent structurally | Hash, version, order, parameter versions and source presence validated |
| Processing | Centroid mass detection | Equivalent for frozen synthetic scope | Real-data output statistics pending |
| Processing | ADAP chromatogram building | Equivalent for frozen synthetic scope | Real-data feature reference pending |
| Processing | Smoothing | Source present, validation pending | Published module/parameters identified |
| Processing | Local-minimum resolver | Equivalent for frozen synthetic scope | Real-data resolved feature reference pending |
| Processing | Isotope finder | Source present, validation pending | Public real-data relationships pending |
| Processing | Rows filter | Source present, validation pending | Published filter mapping pending |
| Processing | Join alignment | Equivalent for synthetic pair | Public technical replicates pending |
| Processing | Single-thread peak-finder gap filling | Equivalent for synthetic gap | Public technical replicates pending |
| Processing | Multithreaded peak finder | Source present, validation pending | Determinism and equivalence to single-thread behavior pending |
| Processing | Duplicate filter | Source present, validation pending | Public real-data decisions pending |
| Processing | Correlation grouping | Source present, validation pending | Public real-data groups pending |
| Processing | Adduct/ion identity grouping | Not implemented in parity suite | Public workflow/reference required |
| Processing | Blank subtraction/classification | Not implemented in parity suite | Public blank corpus selected, files not yet frozen |
| Annotation | Spectral-library import | Present in 3.9, not validated for parity | Public library fixtures required |
| Annotation | Spectral-library matching | Present in 3.9, not validated for parity | Public MS2 corpus required |
| Acquisition | MSe processing | Not implemented in parity suite | Official MSe reference identified |
| Export | Legacy CSV | Equivalent for frozen fields | Modern 4.0 exports need inventory |
| Packaging | Portable Windows runtime | Not implemented | Final operational milestone |

## Completed public gates

### Governed corpus

The repository now enforces fail-closed manifests. A file is downloadable only after explicit reuse
terms, exact source, byte size and SHA-256 are frozen. Large dataset bytes are never committed.

### First real mzML

The frozen Zenodo file `Banane_30ngmL_001.mzML` is verified by size, repository MD5 and SHA-256 before
use. Import is deterministic on Linux and Windows:

- 5,221 total scans;
- 4,081 MS1 scans;
- 1,140 MS2 scans;
- negative polarity;
- centroided spectra;
- RT range 0.77684957–27.61178589 minutes;
- m/z range 50.00162125–749.99395752;
- 6,477,349 total data points;
- no empty scans or zero/negative intensities reported by the imported model.

### Published workflow settings

The frozen `MZmine processing settings.xml` from Zenodo record `14000687` identifies itself as
MZmine 3.4.27. It contains ten ordered processing modules, all of whose exact public source classes
exist in the open-offline MIT tree. Structural validation does not execute the downloaded XML.

See [`PUBLISHED_WORKFLOW_COMPATIBILITY.md`](PUBLISHED_WORKFLOW_COMPATIBILITY.md) for module status and
next evidence.

## Remaining validation sequence

### Gate 1 — broader mzML format conformance

Use explicitly licensed public examples to exercise:

- indexed and non-indexed mzML;
- zlib and uncompressed arrays;
- 32-bit and 64-bit arrays;
- profile and centroid flags;
- positive, negative and mixed polarities;
- MS1 and MSn precursor/isolation information;
- retention-time units;
- malformed or incomplete metadata handling.

### Gate 2 — published parameter compatibility

For each of the ten public workflow steps:

- compare top-level and nested parameter names;
- classify direct, renamed, transformed, deprecated and unsupported values;
- generate an adapted batch only from reviewed mappings;
- reject silent loss of settings.

### Gate 3 — single-sample real processing

On the frozen public mzML, establish:

- mass-list statistics;
- ADAP chromatogram count;
- smoothing output;
- resolved feature count;
- isotope relationships;
- selected feature m/z, RT, height and area;
- runtime and peak memory;
- equality across repeated runs and thread counts where applicable.

### Gate 4 — blanks and technical replicates

Freeze a minimal public subset from the same study:

1. `blank_001.mzML`;
2. `Banane_30ngmL_001.mzML`;
3. `Banane_30ngmL_002.mzML`.

Validate alignment, multithreaded gap filling, rows filtering, duplicate removal, correlation grouping,
blank classification and replicate agreement.

### Gate 5 — official MZmine references

Inventory and execute the relevant public workshop, MSe and GC-TOF tests after their raw-file
provenance is frozen. Record every parameter mapping to the early 4.0 public line.

### Gate 6 — Waters

Process a public CC0 Waters subset before private laboratory data. Separate assertions into:

1. vendor conversion reproducibility;
2. open-format import correctness;
3. scan/acquisition metadata reporting;
4. scientific feature-processing repeatability.

A compatibility dataset is not a mass-accuracy reference unless its depositor explicitly qualifies it
for that purpose.

## Required regression gates for every parity PR

1. independence audit;
2. manifest-policy tests;
3. Java 20 Linux and Windows build;
4. headless startup without account/network requirement;
5. deterministic synthetic scientific pipeline;
6. deterministic XML batch pipeline;
7. equality with untouched MZmine 3.9.0 for the frozen baseline;
8. relevant public-corpus checks;
9. provenance record for every source port or fixture.

## Recommended implementation order

1. Complete the published settings inventory and parameter compatibility map.
2. Freeze the public blank and second technical replicate.
3. Validate single-sample mass detection, ADAP, smoothing, resolving and isotope finding.
4. Validate alignment, multithreaded gap filling, rows filter, duplicate filter and correlation grouping.
5. Finish TaskController bounded executors and shutdown before load/concurrency benchmarks.
6. Add spectral-library import and matching validation with public MS2 data.
7. Freeze the Waters Synapt XS subset and validate conversion/import.
8. Add MSe reference processing.
9. Decouple scheduling and errors from JavaFX/Desktop.
10. Complete performance, cancellation and Windows packaging gates.
