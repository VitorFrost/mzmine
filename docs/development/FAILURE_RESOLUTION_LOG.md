# Failure resolution log

## Status model

The original failure entries are preserved as the state recorded before implementation. This file is
the append-only authoritative record of subsequent implementation, validation, rejection, or
supersession decisions.

A failure is `validated` only when the predefined acceptance criteria have been exercised by a
recorded workflow or deterministic local test. Progressing to a later failure is not sufficient.
Compilation is not scientific equivalence.

## Current status

| Failure | Current status | Validation evidence |
|---|---|---|
| F-001 | validated | Run `31012106085` reached dependency resolution and `javac` using isolated Gradle 8.10.2 on Java 21. |
| F-002 | validated | Run `31012378811` converted Java 21 preview syntax errors into warnings and reached ordinary type resolution. |
| F-003 | validated for source preparation and compilation | Run `31019779360` required 12 locked boundary adapters, prepared 48 classes/47 files, rejected physical paths, and compiled successfully. |
| F-004 | validated for declared executable member contract | Run `31019779360` matched the 12 reached boundaries to the semantic adapter contract and compiled the unchanged public parser path. |
| F-005 | validated | Checked-out local inventory and lock verification passed without network access in run `31019779360`. |
| F-006 | validated | Comment/literal/top-level parsing tests passed and the real 12-file inventory succeeded in runs `31018232351` and `31019779360`. |
| F-007 | validated | Portable `root_id` inventory hash `1f3fa33550bfd1742c1af0b809ebb700794979553489d6278731f43cfdfb2e43` passed in run `31019779360`. |
| F-008 | validated | Portable preparation manifest hash `b1cbac55fbe28a32dda9fea0043bb9b97879b2cb0bece89ca12a0ddc494df23a` contained no physical workspace paths and compiled in run `31019779360`. |
| F-009 | validated | Corrected closure hash `6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6` was strictly bound to the adapter contract and passed preparation/compilation in run `31019779360`. |

---

## Resolution R-001 — F-001

- **Implemented:** isolated standalone oracle build uses Gradle 8.10.2 and Java 21; the main fork
  wrapper remains unchanged.
- **Validated by:** run `31012106085`.
- **Result:** dependency resolution succeeded and execution reached `compileJava`.
- **Decision:** validated.
- **Lesson retained:** an application repository wrapper is not an implicit compatibility guarantee
  for a separately targeted runtime.

## Resolution R-002 — F-002

- **Implemented:** `--enable-preview` is passed to compilation, tests, and Java execution.
- **Validated by:** runs `31012378811` and `31019779360`.
- **Result:** public Java 21 string templates compile as preview warnings; the final source slice
  compiles successfully.
- **Decision:** validated.
- **Lesson retained:** compile and runtime preview modes must be governed together.

## Resolution R-003 — F-003

- **Implemented:**
  - all 12 reached reviewed boundaries now have versioned Java adapters;
  - `oracle/v408-executable/adapter-lock.json` freezes their byte lengths and SHA-256 values;
  - `oracle/v408-executable/adapter-contract.json` freezes execution mode, preserved members, and
    excluded behavior;
  - `scripts/prepare_v408_executable_slice.py` requires exact equality among reached boundaries,
    adapter lock, checked-out inventory, and semantic contract before copying any source;
  - missing, changed, extra, or uncontracted adapters fail before output mutation;
  - full JavaFX, project, feature-list, account/cloud, IMS-frame, and scheduler graphs are not added.
- **Validated by:** run `31019779360`, job `92353216083`.
- **Results:**
  - 12 locked adapters verified;
  - 48 compiled classes;
  - 47 physical Java source files;
  - 36 upstream public classes and 12 reviewed adapters;
  - zero `io.mzio` references;
  - Java 21 preview compilation successful.
- **Decision:** validated for source preparation and compilation.
- **Remaining boundary:** this does not yet validate parser output or centroid equivalence on the
  governed mzML bytes.

## Resolution R-004 — F-004

- **Implemented:** corrected member-level inventory for `AbstractTask`,
  `ScanImportProcessorConfig`, `SimpleSpectralArrays`, `MetadataOnlyScan`, `RawDataFile`,
  `MobilityType`, `MsMsInfo`, `DDAMsMsInfoImpl`, `DataPointUtils`, `StorageUtils`,
  `CentroidMassDetector`, and `MemoryMapStorage`.
- **Explicit behavior:**
  - import filter accepts all scans without manufacturer or instrument inference;
  - import processor is identity and import-time mass detection is inactive;
  - centroid processing remains a separate governed stage;
  - null storage uses an in-memory buffer; non-null storage is rejected;
  - application precursor-object construction throws instead of fabricating behavior;
  - mobility constants preserve metadata identity only; no frames are constructed.
- **Validated by:** 34 unit/policy tests plus Java compilation in run `31019779360`.
- **Decision:** validated for the declared source-compatible member contract.
- **Remaining boundary:** executed scientific values require the next real-data oracle gate.

## Resolution R-005 — F-005

- **Implemented:** `scripts/inventory_v408_adapters.py` hashes only files in the checked-out
  repository. No raw GitHub download or external adapter fetch is used.
- **Validated by:** run `31019779360` with lock verification enabled.
- **Result:** all 12 adapter files were identified, hashed, and matched the committed lock.
- **Decision:** validated.
- **Lesson retained:** checked-out repository bytes are the provenance source of truth; external
  network retrieval is unnecessary and less reproducible.

## Resolution R-006 — F-006

- **Implemented:** adapter identity parsing strips comments, Javadoc, strings, and character
  literals, and indexes exactly one declaration at Java brace depth zero.
- **Tests added:** misleading `class and`, `interface`, `record`, and `enum` words in documentation;
  nested types; no active type; package/path mismatch.
- **Validated by:** real inventories in runs `31018232351`, `31018670376`, and `31019779360`.
- **Decision:** validated.

## Resolution R-007 — F-007

- **Implemented:** physical adapter roots are used only for safe file access. Canonical inventory
  records stable logical IDs:
  - `executable-adapters`;
  - `boundary-adapters`.
- **Validated by:** run `31019779360` and cross-parent-directory unit tests.
- **Canonical adapter inventory SHA-256:**
  `1f3fa33550bfd1742c1af0b809ebb700794979553489d6278731f43cfdfb2e43`.
- **Decision:** validated.

## Resolution R-008 — F-008

- **Implemented:** canonical source-preparation evidence records only:
  - upstream repository-relative source paths; or
  - adapter `root_id` plus root-relative paths.
  Physical checkout roots and absolute source paths are excluded from the hashed manifest.
- **Validated by:**
  - cross-parent-directory deterministic tests;
  - explicit CI rejection of `/home/runner`, Windows drive paths, `/Users`, and `/tmp`;
  - run `31019779360`.
- **Final preparation evidence:**
  - classes: 48;
  - source files: 47;
  - adapters: 12;
  - manifest SHA-256:
    `b1cbac55fbe28a32dda9fea0043bb9b97879b2cb0bece89ca12a0ddc494df23a`;
  - adapter-contract SHA-256:
    `ee6a6b2db93bd7923b81686854f9cc649c161cafb33b7a0baea8a1d3ad116d86`.
- **Decision:** validated.

## Resolution R-009 — F-009

- **Implemented:** adapter contract rebound to the semantically corrected source closure.
- **Historical closure retained:**
  `eeb5ac4f151b80b567da045b0fb1d360b5d754eafa212277ea0f4f696596cda3`.
- **Current closure:**
  `6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6`.
- **Unchanged graph:** 2,585 indexed types, 48 reachable types, 99 edges, 12 reviewed boundaries,
  and zero wildcard, prohibited, unresolved, or policy violations.
- **Validated by:** strict contract equality, locked preparation, and Java compilation in run
  `31019779360`.
- **Decision:** validated.
- **Lesson retained:** correcting semantic boundary metadata intentionally invalidates downstream
  contracts even when the source graph is unchanged.

## Next unresolved gate

No failure is currently open in the source-closure and compilation gate. The next task is a new
scientific execution gate:

1. execute the compiled v4.0.8 parser on the exact governed public mzML bytes;
2. apply the manually selected source MS level outside the parser;
3. apply the explicit centroid noise level;
4. emit the normalized differential report;
5. compare every value with open-offline;
6. register any execution or scientific difference before proposing its correction.
