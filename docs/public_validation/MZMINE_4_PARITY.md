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

## Current matrix

| Area | Capability | State | Evidence/gap |
|---|---|---|---|
| Startup | Offline headless startup | Equivalent | Linux/Windows smoke test without login |
| Batch | XML batch execution | Equivalent for validated 3.9 workflow | Five-step deterministic reference |
| Tasks | Blocking task execution | Adapted | Public 2024 API surface ported to Java 20 |
| Tasks | Bounded executors and shutdown | Not implemented | Milestone 2B |
| Tasks | Scheduler independent of JavaFX/Desktop | Not implemented | Milestone 2C |
| Memory | Open memory-map storage | Adapted baseline | Lifecycle/load tests still required |
| Import | Basic indexed mzML | Equivalent for current fixtures | HUPO conformance expansion pending |
| Import | Profile and centroid coverage | Not implemented as a validation gate | Public corpus required |
| Import | MS1/MS2/precursor semantics | Partial | More public files required |
| Import | Waters vendor/open formats | Not implemented as a public gate | MSV000091372 selected |
| Processing | Centroid mass detection | Equivalent for frozen reference | Public real-data expansion pending |
| Processing | ADAP chromatogram building | Equivalent for frozen reference | Public real-data expansion pending |
| Processing | Local-minimum resolver | Equivalent for frozen reference | Additional resolver comparisons pending |
| Processing | Join alignment | Equivalent for synthetic pair | Real replicates pending |
| Processing | Peak-finder gap filling | Equivalent for synthetic gap | Real replicates pending |
| Processing | Isotope grouping | Not implemented in parity suite | Port/test required |
| Processing | Adduct/ion identity grouping | Not implemented in parity suite | Port/test required |
| Processing | Blank subtraction | Not implemented in parity suite | Public blank corpus required |
| Annotation | Spectral-library import | Present in 3.9, not validated for parity | Public library fixtures required |
| Annotation | Spectral-library matching | Present in 3.9, not validated for parity | Public MS2 corpus required |
| Acquisition | MSe processing | Not implemented in parity suite | Official MSe reference identified |
| Export | Legacy CSV | Equivalent for frozen fields | Modern 4.0 exports need inventory |
| Packaging | Portable Windows runtime | Not implemented | Final operational milestone |

## Validation sequence

### Gate 1 — format conformance

Use public HUPO-PSI examples to exercise:

- indexed and non-indexed mzML;
- zlib and uncompressed arrays;
- 32-bit and 64-bit arrays;
- profile and centroid flags;
- MS1 and MSn metadata;
- precursor/isolation information;
- retention-time units;
- malformed or incomplete metadata handling.

### Gate 2 — public official references

Inventory the public MZmine workshop, MSe, and GC-TOF tests. Recreate only those workflows relevant
to the open scientific core and record every parameter mapping.

### Gate 3 — small real LC-MS

Freeze one public mzML small enough for routine CI. Establish:

- import summary;
- deterministic feature count;
- selected feature m/z, RT, height, and area;
- output hash or normalized CSV comparison;
- runtime and peak memory;
- equality across repeated runs and thread counts.

### Gate 4 — blanks and replicates

Freeze a minimal blank/sample/technical-replicate subset. Validate alignment, gap filling, blank
classification, and replicate agreement before implementing broader E&L-specific scoring.

### Gate 5 — Waters

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

1. Finish TaskController bounded executors and shutdown.
2. Freeze and test the HUPO-PSI conformance fixture after reuse terms are resolved.
3. Freeze one small real mzML with an explicit license and hash.
4. Add isotope grouping and spectral-library matching validation.
5. Freeze blank/sample/replicate files and implement blank subtraction tests.
6. Freeze the Waters Synapt XS subset and validate conversion/import.
7. Add MSe reference processing.
8. Decouple scheduling and errors from JavaFX/Desktop.
9. Complete performance, cancellation, and Windows packaging gates.
