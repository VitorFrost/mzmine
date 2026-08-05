# F-004 — Reviewed boundary member inventory is incomplete

- **Status:** proposal-ready
- **First observed:** 2026-08-05 11:38 BRT
- **Scope:** source-closure manifest and executable adapter contract for the v4.0.8 parser/centroid oracle
- **Input revision:** `3c866f0259de8f100a278da4f1c02362c41237b9`
- **Related failure:** F-003
- **Evidence:** read-only review of the exact public v4.0.8 source at commit
  `8029f930d28c0447f0acf2bcabef0a79865ad434`, specifically:
  - `MzMLFileImportMethod` constructor, `run`, and direct parser lifecycle methods;
  - `MzMLParser.filterProcessFinalizeScan` and `processCharacters`;
  - `BuildingMzMLMsScan` metadata methods and `loadProcessMemMapData`;
  - workflow run `31012378811`, job `92327693496`, which exposed missing signatures after preview compilation was enabled.
- **Observed behavior:** the manifest correctly identifies mixed-purpose classes as reviewed boundaries,
  but several `preserved_members` lists do not match the members required by the unchanged public
  parser path. Concrete mismatches found before adapter implementation:
  - `DataPointUtils` is recorded with `getDataPointsAsDoubleArray`, while
    `BuildingMzMLMsScan.getDataPointMZRange` calls `getDoubleBufferAsArray(DoubleBuffer)`;
  - `MobilityType` is recorded as `NONE` only, while the unchanged public scan record constructs
    `MzMLMobility` with `DRIFT_TUBE` and `TIMS` when those CV terms occur;
  - `AbstractTask` records timestamp and cancellation only, while `MzMLFileImportMethod.run` also
    requires status transitions, error-message storage, and the `storage` field used by direct
    parsing;
  - `MetadataOnlyScan` has no preserved members listed, but the unchanged
    `BuildingMzMLMsScan` class relies on its abstract method surface for scan metadata and array
    access overrides;
  - `DDAMsMsInfoImpl` and `MsMsInfo` have no preserved compile surface listed, while
    `BuildingMzMLMsScan.getMsMsInfo` contains a constructor call and return type even though the
    first normalized oracle is intended to read precursor/isolation fields directly;
  - `SimpleSpectralArrays`, `ScanImportProcessorConfig`, and `StorageUtils` require exact signatures
    used by `loadProcessMemMapData`: identity processing, source-scan filtering, mass-detection status,
    primitive arrays, and null-storage buffer conversion.
- **Impact:** implementing adapters from the current manifest would either fail compilation, omit a
  source-compatible signature, or encourage ad hoc additions after each compiler error. That would
  defeat the purpose of the predeclared boundary contract and increase rework.
- **Root-cause hypothesis:** the initial boundary inventory was derived primarily from source-graph
  expansion control rather than from a complete member-level call audit of the exact unchanged entry
  path. Confidence: high; each mismatch is visible in the frozen source and corresponds to compiler
  errors or direct method calls.
- **Scientific/provenance risk:** medium. The missing inventory is not itself a scientific change,
  but an incorrect adapter could silently alter filtering, metadata classification, mobility
  handling, scan status, or array storage. Compile-only placeholders must not be mistaken for
  equivalent scientific behavior.
- **Proposed correction — revision 1 (not implemented):**
  1. Add a machine-readable executable-adapter manifest separate from the source-closure manifest.
     For every reached boundary, record:
     - FQCN;
     - adapter source path and SHA-256;
     - exact preserved constructors, methods, fields, enum constants, and failure behavior;
     - whether each member is executed, compile-only, or deliberately unsupported;
     - the upstream call sites that justify it.
  2. Correct the member inventory before adapter code is accepted:
     - `AbstractTask`: protected nullable `storage`, module-call timestamp, cancellation flag,
       `TaskStatus`, error message, status setters/getters, and abstract task-description/progress
       methods; no JavaFX listeners or scheduling services;
     - `ScanImportProcessorConfig`: an explicit always-match scan filter, identity processor, and
       `isMassDetectActive` returning false for the first no-advanced-processing oracle; no automatic
       manufacturer or MS-level inference;
     - `SimpleSpectralArrays`: primitive-array constructor/accessors and data-point count;
     - `MetadataOnlyScan`: only the abstract scan methods overridden by
       `BuildingMzMLMsScan`; unsupported inherited data operations remain explicit;
     - `RawDataFile`: type identity only because `BuildingMzMLMsScan.getDataFile` returns null;
     - `MobilityType`: `NONE`, `DRIFT_TUBE`, and `TIMS` identities required by unchanged source; the
       first governed dataset remains non-IMS, and no frame construction is added;
     - `MsMsInfo`: type identity only; `DDAMsMsInfoImpl` provides the constructor signature required
       for compilation but must fail explicitly if the excluded application conversion path is
       executed; the report producer uses `getIsolations`/parser records instead;
     - `DataPointUtils`: `getDoubleBufferAsArray(DoubleBuffer)` exactly;
     - `StorageUtils`: `storeValuesToDoubleBuffer(MemoryMapStorage, double[])`, using an in-memory
       read-only buffer when storage is null and refusing non-null storage;
     - `CentroidMassDetector`: retain the already recorded exact primitive-array method;
     - `MemoryMapStorage`: retain the already recorded null-only, non-instantiable type boundary.
  3. Make source preparation fail before copying when any reached boundary is missing from the
     adapter manifest, when an adapter hash differs, or when the declared member inventory and
     source-level API test disagree.
  4. Add API/behavior tests before rerunning compilation. Tests must distinguish executed behavior
     from compile-only types and must verify that excluded methods throw rather than fabricate
     values.
- **Validation criteria — defined before implementation:**
  - every one of the 12 reached boundaries is accounted for by a hashed adapter or an explicit,
    justified upstream-source exception;
  - the preparation step reports the expected adapter count and refuses a missing/undeclared
    boundary;
  - API tests verify exact method/constructor signatures used by the frozen source;
  - identity processing preserves array object values and ordering;
  - scan filtering always accepts without inspecting manufacturer, instrument model, or selected
    source MS level;
  - `isMassDetectActive` is false in the parser adapter because centroid processing occurs in the
    separate governed stage;
  - null storage yields an in-memory `DoubleBuffer`; non-null storage is rejected;
  - `MobilityType` constants compile but no IMS frame path is added or executed;
  - application precursor conversion throws if called, while direct isolation metadata remains
    available from unchanged public parser records;
  - no JavaFX, project, feature-list, account, cloud, or `io.mzio` dependency is added;
  - the exact upstream commit and closure SHA-256 remain unchanged;
  - the next compile run either passes these error groups or generates a newly registered failure
    before any further correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** a class-level boundary is insufficient for an executable oracle. The
  member-level contract must be completed and reviewed before adapter implementation, otherwise the
  compiler becomes an undocumented design loop.
