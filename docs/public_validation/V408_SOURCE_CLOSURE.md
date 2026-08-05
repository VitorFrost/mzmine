# MZmine v4.0.8 public parser and centroid source closure

## Purpose

This record defines the public Java source slice that will be used to build the first executable
MZmine v4.0.8 behavioral oracle for direct comparison with the open-offline fork.

The oracle is limited to:

- non-IMS LC-MS mzML parsing;
- normalized scan and precursor metadata;
- primitive m/z and intensity arrays;
- the exact public centroid mass-detection array overload with an explicit noise level;
- a manually selected MS1 or MS2 source-data stream outside the parser.

It does not include project registration, feature lists, graphical interfaces, account or cloud
services, task scheduling, ion-mobility frame construction, or automatic instrument/manufacturer
inference.

## Frozen source

| Field | Value |
|---|---|
| Repository | `mzmine/mzmine` |
| Tag | `v4.0.8` |
| Commit | `8029f930d28c0447f0acf2bcabef0a79865ad434` |
| License | MIT |
| Audit manifest | `datasets/parity/v408_source_closure_manifest.json` |
| Audit script | `scripts/audit_v408_source_closure.py` |

The workflow verifies the exact Git commit and both public license files before generating the source
closure.

## Why the full import module is not the oracle

The application-level `MSDKmzMLImportTask` converts parser records into `RawDataFile` objects and
registers them in the project. Following that class at source level reaches the feature model,
visualization modules, GUI bootstrap, account services, and other behavior unrelated to parsing the
same mzML bytes.

The first direct oracle therefore starts at the lower public parser layer:

- `MzMLFileImportMethod`;
- `MzMLParser`;
- `MzMLRawDataFile`;
- `BuildingMzMLMsScan`;
- the public centroid detector primitive-array method.

This keeps the evidence focused on the values that are compared, rather than on application
lifecycle behavior.

## Reviewed boundaries

Some public classes contain both the required scientific member and unrelated application methods.
The manifest records these as reviewed boundaries and names the preserved members. Examples include:

- `SimpleSpectralArrays`: preserve the primitive array record, exclude constructors from application
  `Scan` objects;
- `DataPointUtils`: preserve array conversion, exclude feature conversion;
- `StorageUtils`: preserve the required array/buffer conversion, exclude feature-series storage;
- `MetadataOnlyScan` and `RawDataFile`: no application object construction is performed;
- `ScanImportProcessorConfig`: use a reviewed no-filter/no-advanced-processing adapter;
- `CentroidMassDetector`: preserve `getMassValues(double[], double[], double)`;
- `AbstractTask`: preserve only the cancellation/timestamp surface required by the parser call.

A reviewed boundary is not evidence of equivalent behavior. It is an explicit statement that the
excluded behavior is not executed by this oracle and must not enter the scientific comparison.

## Null-only `MemoryMapStorage` type

The public v4.0.8 source references `io.github.mzmine.util.MemoryMapStorage`, but that type is absent
from the frozen public source tree. The parser constructors accept a nullable reference, and the
first oracle uses the null-storage path.

The fork therefore supplies one behavior-free boundary type at:

`oracle/v408-boundaries/src/main/java/io/github/mzmine/util/MemoryMapStorage.java`

The type:

- cannot be instantiated;
- exposes no methods or state;
- implements no memory mapping, cleanup, persistence, or lifecycle behavior;
- is mounted as an additional source root only during oracle closure/build work;
- is hashed and recorded separately from the upstream commit;
- is guarded by tests that reject public/protected methods or stored state.

This is a compile-time type boundary, not a reconstruction of unavailable code.

## Validated closure result

The first passing focused audit produced:

| Metric | Result |
|---|---:|
| Indexed public/boundary top-level types | 2,585 |
| Reachable source types | 48 |
| Dependency edges | 99 |
| Reviewed boundaries reached | 12 |
| Internal wildcard imports | 0 |
| Prohibited `io.mzio` references | 0 |
| Unresolved internal references | 0 |
| Policy violations | 0 |
| Canonical closure SHA-256 | `eeb5ac4f151b80b567da045b0fb1d360b5d754eafa212277ea0f4f696596cda3` |

The earlier unbounded application-level graph reached 2,326 types and 16,952 edges. Reducing it to
48 types was achieved by changing the entry layer and documenting unused API surfaces, not by
allowing unresolved or proprietary dependencies.

## What this proves

A passing source-closure audit proves that:

- the declared upstream commit is reproducible;
- the selected public source graph is deterministic;
- all internal references in that graph resolve after mounting the documented null-only boundary;
- no `io.mzio` reference is reachable;
- every excluded application dependency is stopped at a reviewed boundary.

It does **not** yet prove that:

- the source slice compiles;
- the parser produces the same normalized report as open-offline;
- centroid mass detection is behaviorally equivalent;
- the remaining LC-MS workflow stages have v4.0.8 parity.

## Next gate

The next implementation gate is an executable Java 21 source-slice build that:

1. checks out the exact v4.0.8 commit;
2. mounts only reviewed boundary sources/adapters;
3. compiles the declared parser and centroid method slice;
4. imports the exact governed `Banane_30ngmL_001.mzML` bytes;
5. applies the manually selected source MS level and explicit centroid noise level;
6. emits the normalized differential stage-report contract;
7. compares it with the existing open-offline report and retains every difference.
